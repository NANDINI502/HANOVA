package com.nandini.hanova

import android.content.res.AssetManager
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig

/**
 * Second pass, 100% free + offline.
 * The streaming model is small (fast live text) but weak on far voice.
 * SenseVoice sees the WHOLE sentence at once and is much more robust,
 * so every finished segment is re-recognised here before translation.
 */
class AccurateAsr(assets: AssetManager, language: String = "") {
    private val recognizer: OfflineRecognizer

    init {
        val dir = "sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09"
        recognizer = OfflineRecognizer(
            assetManager = assets,
            config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
                modelConfig = OfflineModelConfig(
                    senseVoice = OfflineSenseVoiceModelConfig(
                        model = "$dir/model.int8.onnx",
                        language = language,                // "" = auto (zh / en), needed for conversation mode
                        useInverseTextNormalization = true, // adds punctuation, digits
                    ),
                    tokens = "$dir/tokens.txt",
                    numThreads = 4,
                ),
            ),
        )
    }

    fun recognize(seg: Segment): String {
        val pcm = seg.pcm16
        val samples = FloatArray(pcm.size / 2) { i ->
            val lo = pcm[2 * i].toInt() and 0xff
            val hi = pcm[2 * i + 1].toInt()
            ((hi shl 8) or lo).toShort() / 32768f
        }
        if (samples.size < seg.sampleRate / 4) return seg.offlineText // <0.25s: not worth it
        val stream = recognizer.createStream()
        try {
            stream.acceptWaveform(samples, seg.sampleRate)
            recognizer.decode(stream)
            return recognizer.getResult(stream).text.trim()
        } finally {
            stream.release()
        }
    }

    fun release() = recognizer.release()
}
