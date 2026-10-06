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
        _state.update { it.copy(listening = who, partial = "") }
        viewModelScope.launch(Dispatchers.IO) { c.asr.start() }
    }

    fun pressEnd(who: Speaker) {
        if (_state.value.listening != who) return
        _state.update { it.copy(listening = null, busy = true) }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { c.asr.stop() }      // flushes the tail segment
            val segs = synchronized(collected) { collected.toList() }
            val heard = withContext(Dispatchers.Default) {
                segs.map { s -> runCatching { c.accurate.recognize(s) }.getOrDefault(s.offlineText).ifBlank { s.offlineText } }
                    .filter { it.isNotBlank() && !Filler.isFiller(it) }
                    .joinToString(if (who == Speaker.ME) " " else "")
            }
            if (heard.isBlank()) {
                _state.update { it.copy(busy = false, partial = "") }
                return@launch
            }
            when (who) {
                Speaker.THEM -> {
                    val en = runCatching { c.zhToEn.translate(heard) }.getOrNull()
                    _state.update { it.copy(forMeEn = en ?: "(translation unavailable)", forMeZh = heard, busy = false, partial = "") }
                    // English for me → my earbuds (or the speaker if none)
                    if (en != null && c.voiceOn.value) c.voice.say(en, VoiceOut.Lang.EN, VoiceOut.Route.EARBUDS)
                }
                Speaker.ME -> {
                    val zh = runCatching { c.enToZh.translate(heard) }.getOrNull()
                    _state.update { it.copy(forThemZh = zh ?: "(無法翻譯)", forThemEn = heard, busy = false, partial = "") }
                    // Chinese for them → phone loudspeaker, even when my earbuds are in
                    if (zh != null && c.voiceOn.value) c.voice.say(zh, VoiceOut.Lang.ZH, VoiceOut.Route.PHONE_SPEAKER)
                }
            }
        }
    }

    override fun onCleared() {
        if (_state.value.listening != null) c.asr.stop()
    }
}
