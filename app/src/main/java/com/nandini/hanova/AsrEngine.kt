package com.nandini.hanova

import android.annotation.SuppressLint
import android.content.res.AssetManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineParaformerModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import java.io.ByteArrayOutputStream
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/** One finished sentence: live text + the raw audio (for the accurate second pass). */
class Segment(val offlineText: String, val pcm16: ByteArray, val sampleRate: Int)

/**
 * Streaming Mandarin recognition, fully offline (sherpa-onnx Paraformer).
 * Also acts as the SEGMENTER: every endpoint emits a Segment with its audio,
 * so AccurateAsr can re-recognise it with a stronger model.
 */
class AsrEngine(assets: AssetManager) {
    // Listeners are swappable: one engine (model loaded once) is shared by
    // the lecture screen and the conversation screen. Called on the mic thread.
    @Volatile var onPartial: (String) -> Unit = {}
    @Volatile var onSegment: (Segment) -> Unit = {}
    @Volatile var onLevel: (Float) -> Unit = {}
    /** Every 100 ms chunk of (boosted) PCM16 audio — used to save the full lecture as WAV. */
    @Volatile var onAudio: (ByteArray) -> Unit = {}
    /**
     * Fast voice: once a segment is this long, also end it at the next short breath
     * (~0.3 s quieter audio) instead of waiting for the model's 0.8 s pause, and cut it
     * at [FAST_MAX_MS] at the latest. 0 = off (whole sentences only).
     */
    @Volatile var softCutAfterMs: Long = 0

    val isRunning: Boolean get() = running

    private val sampleRate = 16000
    private val recognizer: OnlineRecognizer
    @Volatile private var running = false
    private var worker: Thread? = null

    // Far-field gain: tracks loudness and boosts quiet (distant) speech up to MAX_GAIN
    private var gain = 1f
    private val targetPeak = 0.5f
    private val maxGain = 12f

    init {
        val dir = "sherpa-onnx-streaming-paraformer-bilingual-zh-en"
        val config = OnlineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = sampleRate, featureDim = 80),
            modelConfig = OnlineModelConfig(
                paraformer = OnlineParaformerModelConfig(
                    encoder = "$dir/encoder.int8.onnx",
                    decoder = "$dir/decoder.int8.onnx",
                ),
                tokens = "$dir/tokens.txt",
                modelType = "paraformer",
                numThreads = 2,
            ),
            // rule1: 2.0s pure silence ends a segment even if nothing was recognised
            //        (important for far audio where the model may output nothing)
            // rule2: 0.8s silence after speech ends a sentence
            // rule3: hard cut at 6s. Professors rarely pause, and captions/voice arrive
            //        up to this long after the words were said (12s was far too laggy)
            endpointConfig = EndpointConfig(
                rule1 = EndpointRule(false, 2.0f, 0.0f),
                rule2 = EndpointRule(true, 0.8f, 0.0f),
                rule3 = EndpointRule(false, 0.0f, 6.0f),
            ),
            enableEndpoint = true,
        )
        recognizer = OnlineRecognizer(assetManager = assets, config = config)
    }

    @SuppressLint("MissingPermission") // permission is checked in MainActivity
    fun start() {
        if (running) return
        running = true
        worker = thread(name = "asr") {
            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, sampleRate * 2)
            )
            // Hardware/DSP helpers for distant speech, where the phone supports them
            val ns = if (NoiseSuppressor.isAvailable())
                NoiseSuppressor.create(record.audioSessionId)?.apply { enabled = true } else null
            val agc = if (AutomaticGainControl.isAvailable())
                AutomaticGainControl.create(record.audioSessionId)?.apply { enabled = true } else null

            val stream = recognizer.createStream()
            val chunk = ShortArray(sampleRate / 10) // 100 ms
            val segAudio = ByteArrayOutputStream()
            var lastPartial = ""
            var hadVoice = false
            var segSamples = 0L
            var speechRms = 0.05f   // running loudness of speech, to spot short pauses
            var quietChunks = 0
            record.startRecording()
            try {
                while (running) {
                    val n = record.read(chunk, 0, chunk.size)
                    if (n <= 0) continue
                    val boosted = applyGain(chunk, n)
                    onLevel(boosted.maxOf { abs(it) })

                    // keep boosted audio for the second pass + the full-lecture recording
                    val bytes = ByteArray(boosted.size * 2)
                    for (i in boosted.indices) {
                        val v = (boosted[i] * 32767f).toInt().coerceIn(-32768, 32767)
                        bytes[2 * i] = (v and 0xff).toByte(); bytes[2 * i + 1] = ((v shr 8) and 0xff).toByte()
                    }
                    segAudio.write(bytes)
                    onAudio(bytes)
                    if (boosted.any { abs(it) > 0.05f }) hadVoice = true
                    segSamples += n
                    val rms = sqrt(boosted.fold(0f) { acc, v -> acc + v * v } / n)
                    speechRms = if (rms > speechRms * 0.5f) speechRms * 0.9f + rms * 0.1f else speechRms * 0.999f
                    quietChunks = if (rms < speechRms * 0.3f) quietChunks + 1 else 0

                    stream.acceptWaveform(boosted, sampleRate)
                    while (recognizer.isReady(stream)) recognizer.decode(stream)

                    val text = recognizer.getResult(stream).text.trim()
                    if (text != lastPartial) { lastPartial = text; onPartial(text) }

                    val softAfter = softCutAfterMs
                    val segMs = segSamples * 1000 / sampleRate
                    val softCut = softAfter > 0 && text.isNotEmpty() &&
                        ((segMs >= softAfter && quietChunks >= SOFT_PAUSE_CHUNKS) || segMs >= FAST_MAX_MS)

                    if (recognizer.isEndpoint(stream) || softCut) {
                        // Emit if offline heard words OR there was audible voice
                        // (second pass may recognise what the small model missed)
                        if (text.isNotEmpty() || hadVoice) {
                            onSegment(Segment(text, segAudio.toByteArray(), sampleRate))
                        }
                        segAudio.reset(); hadVoice = false; segSamples = 0; quietChunks = 0
                        recognizer.reset(stream)
                        lastPartial = ""; onPartial("")
                    }
                }
                val tail = recognizer.getResult(stream).text.trim()
                if (tail.isNotEmpty() || hadVoice) {
                    onSegment(Segment(tail, segAudio.toByteArray(), sampleRate))
                }
            } finally {
                record.stop(); record.release()
                ns?.release(); agc?.release()
                stream.release()
            }
        }
    }

    /** Smooth automatic gain: boosts distant voice, never clips. */
    private fun applyGain(chunk: ShortArray, n: Int): FloatArray {
        var peak = 0f
        val out = FloatArray(n) { chunk[it] / 32768f }
        for (v in out) peak = maxOf(peak, abs(v))
        if (peak > 0.01f) {                          // ignore pure silence
            val wanted = min(targetPeak / peak, maxGain)
            gain += (wanted - gain) * if (wanted < gain) 0.5f else 0.05f // fast down, slow up
        }
        for (i in out.indices) out[i] = (out[i] * gain).coerceIn(-1f, 1f)
        return out
    }

    fun stop() { running = false; worker?.join(1500); worker = null }

    fun release() { stop(); recognizer.release() }

    private companion object {
        const val SOFT_PAUSE_CHUNKS = 3      // 3 × 100 ms of quieter audio = a breath between phrases
        const val FAST_MAX_MS = 4500L        // fast voice never waits longer than this
    }
}
