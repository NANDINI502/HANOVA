package com.nandini.hanova

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/**
 * Speaks translations out loud with the phone's built-in text-to-speech
 * (free, offline once the voices are on the phone). Lines play one after another.
 *
 * Route.EARBUDS       → normal output: Bluetooth earbuds if connected, else phone speaker
 * Route.PHONE_SPEAKER → forced to the loudspeaker even with earbuds in,
 *                       so the person in front of you hears it
 */
class VoiceOut(private val context: Context) {
    enum class Lang { EN, ZH }
    enum class Route { EARBUDS, PHONE_SPEAKER }

    /** One offline voice on the phone, e.g. name "en-in-x-end-local", label "Indian 3". */
    data class Choice(val name: String, val label: String)

    private class Item(
        val text: String, val lang: Lang, val route: Route, val droppable: Boolean,
        val voiceName: String? = null,   // preview a voice that isn't the saved one yet
    )

    // The user's picks (Voice screen). null = phone default for that language.
    private val prefs = context.getSharedPreferences("voice", Context.MODE_PRIVATE)
    var enVoice: String?
        get() = prefs.getString("en", null)
        set(v) = prefs.edit().putString("en", v).apply()
    var zhVoice: String?
        get() = prefs.getString("zh", null)
        set(v) = prefs.edit().putString("zh", v).apply()
    /**
     * Fast voice: speak the quick (pass-1) translation of short chunks right away,
     * instead of waiting for whole sentences and the accurate pass. Less delay,
     * slightly rougher English. Captions on screen are still replaced by the accurate text.
     */
    var fastVoice: Boolean
        get() = prefs.getBoolean("fast", true)
        set(v) = prefs.edit().putBoolean("fast", v).apply()
    /** 1.0 = natural. Faster sounds more robotic. */
    var rate: Float
        get() = prefs.getFloat("rate", 1.0f)
        set(v) = prefs.edit().putFloat("rate", v).apply()

    private var tts: TextToSpeech? = null
    private var zhLocale: Locale = Locale.TAIWAN
    private val audio = context.getSystemService(AudioManager::class.java)
    private val queue = Channel<Item>(Channel.UNLIMITED)
    private val pending = AtomicInteger(0)
    private val waiting = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Returns problems to show the user (e.g. a missing voice). Empty list = all good. */
    suspend fun init(): List<String> {
        val ready = CompletableDeferred<Int>()
        // Prefer Google TTS: it has Taiwan Mandarin. Samsung TTS has no Chinese voice.
        val engine = if (isInstalled(GOOGLE_TTS)) GOOGLE_TTS else null
        val t = withContext(Dispatchers.Main) { TextToSpeech(context, { ready.complete(it) }, engine) }
        if (ready.await() != TextToSpeech.SUCCESS) return listOf("Voice output not available")
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String) {}
            override fun onDone(id: String) { waiting.remove(id)?.complete(Unit) }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String) { waiting.remove(id)?.complete(Unit) }
            override fun onError(id: String, errorCode: Int) { waiting.remove(id)?.complete(Unit) }
            override fun onStop(id: String, interrupted: Boolean) { waiting.remove(id)?.complete(Unit) }
        })
        tts = t
        if (!available(t, Locale.TAIWAN)) zhLocale = Locale.CHINA
        scope.launch { for (item in queue) play(item) }

        return buildList {
            if (!available(t, Locale.US)) add("English voice missing")
            if (!available(t, zhLocale)) add("Chinese voice missing")
        }
    }

    /**
     * droppable = lecture lines: if [MAX_BACKLOG] or more lines are still waiting,
     * older lines are skipped (still on screen) so what you hear stays live.
     */
    fun say(text: String, lang: Lang, route: Route, droppable: Boolean = false) {
        if (text.isBlank() || tts == null) return
        pending.incrementAndGet()
        queue.trySend(Item(text, lang, route, droppable))
    }

    /**
     * Offline, already-downloaded voices for a language, grouped by accent.
     * (Network voices are skipped: they'd stop working in airplane mode.)
     */
    fun choices(lang: Lang): List<Choice> {
        val t = tts ?: return emptyList()
        val accents = if (lang == Lang.EN) listOf("US" to "American", "IN" to "Indian", "GB" to "British")
        else listOf("TW" to "Taiwan", "CN" to "Mainland")
        val language = if (lang == Lang.EN) "en" else "zh"
        val usable = runCatching { t.voices }.getOrNull().orEmpty().filter {
            it.locale.language == language && !it.isNetworkConnectionRequired &&
                "notInstalled" !in it.features && "-x-" in it.name   // skip generic "en-US-language"
        }
        return accents.flatMap { (country, accent) ->
            usable.filter { it.locale.country == country }.sortedBy { it.name }
                .mapIndexed { i, v -> Choice(v.name, "$accent ${i + 1}") }
        }
    }

    /** Play a short sample in the given voice, replacing whatever is speaking. */
    fun preview(lang: Lang, voiceName: String) {
        clear()
        val sample = if (lang == Lang.EN) "Today we will talk about data structures and algorithms."
        else "今天我們要討論資料結構和演算法。"
        pending.incrementAndGet()
        queue.trySend(Item(sample, lang, Route.EARBUDS, droppable = false, voiceName = voiceName))
    }

    fun headsetConnected(): Boolean =
        audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in HEADSET_TYPES }

    /** System screen where the user downloads missing voices. */
    fun installVoicesIntent() = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
        if (isInstalled(GOOGLE_TTS)) setPackage(GOOGLE_TTS)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Drop everything not yet spoken and stop the current line (e.g. before using the mic). */
    fun clear() {
        while (queue.tryReceive().isSuccess) pending.decrementAndGet()
        tts?.stop()
        player?.let { runCatching { it.stop() } }
    }

    @Volatile private var player: MediaPlayer? = null

    private suspend fun play(item: Item) {
        val left = pending.decrementAndGet()
        if (item.droppable && left >= MAX_BACKLOG) return // behind live speech: skip
        val t = tts ?: return

        val speakerDevice = if (item.route == Route.PHONE_SPEAKER && headsetConnected() &&
            Build.VERSION.SDK_INT >= 28) builtinSpeaker() else null

        val wanted = item.voiceName ?: if (item.lang == Lang.EN) enVoice else zhVoice
        val voice = wanted?.let { n -> runCatching { t.voices }.getOrNull()?.firstOrNull { it.name == n } }
        if (voice != null) t.voice = voice
        else t.language = if (item.lang == Lang.EN) Locale.US else zhLocale
        t.setSpeechRate(rate)
        val id = UUID.randomUUID().toString()
        val done = CompletableDeferred<Unit>().also { waiting[id] = it }
        if (speakerDevice == null) {
            if (t.speak(item.text, TextToSpeech.QUEUE_ADD, null, id) != TextToSpeech.SUCCESS) {
                waiting.remove(id); return
            }
            withTimeoutOrNull(TIMEOUT_MS) { done.await() }
        } else {
            // TTS can't choose an output device, so render to a file and play that on the speaker
            val f = File(context.cacheDir, "tts_$id.wav")
            if (t.synthesizeToFile(item.text, null, f, id) != TextToSpeech.SUCCESS) {
                waiting.remove(id); return
            }
            withTimeoutOrNull(TIMEOUT_MS) { done.await() }
            if (f.length() > 0) withTimeoutOrNull(TIMEOUT_MS) { playFile(f, speakerDevice) }
            f.delete()
        }
    }

    private suspend fun playFile(f: File, device: AudioDeviceInfo) =
        suspendCancellableCoroutine { cont ->
            val mp = MediaPlayer()
            val finish = {
                player = null
                runCatching { mp.release() }
                if (cont.isActive) cont.resume(Unit)
            }
            try {
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                mp.setDataSource(f.path)
                if (Build.VERSION.SDK_INT >= 28) mp.setPreferredDevice(device)
                mp.setOnCompletionListener { finish() }
                mp.setOnErrorListener { _, _, _ -> finish(); true }
                mp.prepare()
                player = mp
                mp.start()
                cont.invokeOnCancellation { player = null; runCatching { mp.release() } }
            } catch (e: Exception) {
                finish()
            }
        }

    private fun builtinSpeaker(): AudioDeviceInfo? =
        audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }

    private fun available(t: TextToSpeech, locale: Locale) =
        t.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE

    private fun isInstalled(pkg: String) = try {
        context.packageManager.getPackageInfo(pkg, 0); true
    } catch (e: Exception) { false }

    companion object {
        private const val GOOGLE_TTS = "com.google.android.tts"
        private const val MAX_BACKLOG = 1
        private const val TIMEOUT_MS = 30_000L
        private val HEADSET_TYPES = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            26, // AudioDeviceInfo.TYPE_BLE_HEADSET (API 31)
        )
    }
}
