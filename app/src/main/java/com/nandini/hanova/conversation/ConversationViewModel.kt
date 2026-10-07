package com.nandini.hanova.conversation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nandini.hanova.HanovaApp
import com.nandini.hanova.Filler
import com.nandini.hanova.Segment
import com.nandini.hanova.VoiceOut
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections

enum class Speaker { THEM, ME }   // THEM speaks Chinese (top half), ME speaks English (bottom half)

data class ConversationUiState(
    val listening: Speaker? = null,
    val busy: Boolean = false,
    val partial: String = "",
    // Top half (rotated, for them): what I said, in Chinese
    val forThemZh: String = "",
    val forThemEn: String = "",
    // Bottom half (for me): what they said, in English
    val forMeEn: String = "",
    val forMeZh: String = "",
)

/** Hold-to-speak, face-to-face translation. Same offline models as lectures. */
class ConversationViewModel(app: Application) : AndroidViewModel(app) {

    private val c = (app as HanovaApp).container
    private val _state = MutableStateFlow(ConversationUiState())
    val state: StateFlow<ConversationUiState> = _state

    private val collected = Collections.synchronizedList(mutableListOf<Segment>())

    fun pressStart(who: Speaker) {
        if (_state.value.listening != null || _state.value.busy) return
        collected.clear()
        c.voice.clear()   // stop any translation still playing, or the mic would hear it
        c.asr.onPartial = { t -> _state.update { it.copy(partial = t) } }
        c.asr.onLevel = {}
        c.asr.onAudio = {}
        c.asr.onSegment = { seg -> collected.add(seg) }
        c.asr.softCutAfterMs = 0   // hold-to-speak: the button decides when it ends
        _state.update { it.copy(listening = who, partial = "") }
        viewModelScope.launch(Dispatchers.IO) { c.asr.start() }
    }

    fun pressEnd(who: Speaker) {
        if (_state.value.listening != who) return
        _state.update { it.copy(listening = null, busy = true) }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { c.asr.stop() }      // flushes the tail segment
            val segs = synchronized(collected) { collected.toList() }
            val sep = if (who == Speaker.ME) " " else ""

            // Fast voice: translate + speak the live (pass-1) text immediately…
            var spoken = false
            if (c.voice.fastVoice) {
                val quick = segs.map { it.offlineText }.filter { it.isNotBlank() && !Filler.isFiller(it) }.joinToString(sep)
                if (quick.isNotBlank()) spoken = deliver(who, quick, speak = true, done = false)
            }

            // …then the accurate pass refines the text on screen (not spoken twice)
            val heard = withContext(Dispatchers.Default) {
                segs.map { s -> runCatching { c.accurate.recognize(s) }.getOrDefault(s.offlineText).ifBlank { s.offlineText } }
                    .filter { it.isNotBlank() && !Filler.isFiller(it) }
                    .joinToString(sep)
            }
            if (heard.isBlank()) {
                _state.update { it.copy(busy = false, partial = "") }
                return@launch
            }
            deliver(who, heard, speak = !spoken, done = true)
        }
    }

    /** Translate, show, and optionally speak. Returns true if a translation was spoken. */
    private suspend fun deliver(who: Speaker, heard: String, speak: Boolean, done: Boolean): Boolean {
        val busy = !done
        return when (who) {
            Speaker.THEM -> {
                val en = runCatching { c.zhToEn.translate(heard) }.getOrNull()
                _state.update { it.copy(forMeEn = en ?: "(translation unavailable)", forMeZh = heard, busy = busy, partial = "") }
                // English for me → my earbuds (or the speaker if none)
                val say = speak && en != null && c.voiceOn.value
                if (say) c.voice.say(en!!, VoiceOut.Lang.EN, VoiceOut.Route.EARBUDS)
                say
            }
            Speaker.ME -> {
                val zh = runCatching { c.enToZh.translate(heard) }.getOrNull()
                _state.update { it.copy(forThemZh = zh ?: "(無法翻譯)", forThemEn = heard, busy = busy, partial = "") }
                // Chinese for them → phone loudspeaker, even when my earbuds are in
                val say = speak && zh != null && c.voiceOn.value
                if (say) c.voice.say(zh!!, VoiceOut.Lang.ZH, VoiceOut.Route.PHONE_SPEAKER)
                say
            }
        }
    }

    override fun onCleared() {
        if (_state.value.listening != null) c.asr.stop()
    }
}
