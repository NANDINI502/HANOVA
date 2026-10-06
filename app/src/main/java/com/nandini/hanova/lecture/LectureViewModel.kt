package com.nandini.hanova.lecture

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nandini.hanova.HanovaApp
import com.nandini.hanova.Filler
import com.nandini.hanova.Segment
import com.nandini.hanova.VoiceOut
import com.nandini.hanova.data.Homework
import com.nandini.hanova.data.Lecture
import com.nandini.hanova.data.Line
import com.nandini.hanova.homework.HomeworkDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.os.SystemClock
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

data class CaptionLine(
    val id: Long,
    val tMs: Long,
    val zh: String,
    val en: String,
    val starred: Boolean = false,
    val homeworkDue: LocalDate? = null,
    val isHomework: Boolean = false,
    val translationPending: Boolean = false,
    /** Quick version from pass 1; replaced by the accurate line a moment later. */
    val draft: Boolean = false,
)

data class LectureUiState(
    val course: String = "",
    val lectureId: Long = 0,
    val running: Boolean = false,
    val paused: Boolean = false,
    val elapsedMs: Long = 0,
    val partial: String = "",
    /** Live English of [partial], refreshed about every second while the professor talks. */
    val partialEn: String = "",
    val level: Float = 0f,
    val lines: List<CaptionLine> = emptyList(),
)

/**
 * One live lecture: mic → pass 1 (live text + segmentation) → pass 2 (accurate) →
 * translation → saved to the Lecture Diary, homework lines saved to the Homework Diary.
 */
class LectureViewModel(app: Application) : AndroidViewModel(app) {

    private val c = (app as HanovaApp).container
    private val db = c.db

    /** English voice in the earbuds on/off (shared with conversation). */
    val voiceOn: StateFlow<Boolean> = c.voiceOn
    fun toggleVoice() {
        c.voiceOn.value = !c.voiceOn.value
        if (!c.voiceOn.value) c.voice.clear()
    }
    fun headsetConnected() = c.voice.headsetConnected()

    private val _state = MutableStateFlow(LectureUiState())
    val state: StateFlow<LectureUiState> = _state

    private val segments = Channel<Pair<Segment, Long>>(Channel.UNLIMITED)
    private val queued = AtomicInteger(0)
    private var startedAt = 0L
    private var pausedTotal = 0L
    private var pausedSince = 0L
    private var timerJob: Job? = null
    private var wav: WavWriter? = null
    private var draftJob: Job? = null
    private var lastDraftAt = 0L
    private var draftIds = -1L   // negative ids for draft lines (never clash with DB ids)

    init {
        viewModelScope.launch { for ((seg, t) in segments) process(seg, t) }
    }

    fun start(course: String) {
        if (_state.value.running) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val id = withContext(Dispatchers.IO) { db.lectures().insert(Lecture(course = course, startedAt = now)) }
            startedAt = now
            wav = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = getApplication<Application>().getExternalFilesDir("lectures")!!.apply { mkdirs() }
                    WavWriter(java.io.File(dir, "lecture_$id.wav")).also { db.lectures().setAudioPath(id, it.file.absolutePath) }
                }.getOrNull()   // recording is a bonus; captions still work without it
            }
            _state.value = LectureUiState(course = course, lectureId = id, running = true)
            attachMic()
            c.asr.start()
            timerJob = viewModelScope.launch {
                while (isActive) {
                    if (!_state.value.paused) _state.update { it.copy(elapsedMs = elapsedNow()) }
                    delay(500)
                }
            }
        }
    }

    private fun elapsedNow() = System.currentTimeMillis() - startedAt - pausedTotal

    private fun attachMic() {
        c.asr.onPartial = { t ->
            _state.update { if (t.isEmpty()) it.copy(partial = "", partialEn = "") else it.copy(partial = t) }
            if (t.isNotEmpty()) requestLiveTranslation()
        }
        c.asr.onLevel = { l -> _state.update { it.copy(level = l) } }
        c.asr.onAudio = { bytes -> wav?.write(bytes) }
        c.asr.onSegment = { seg ->
            queued.incrementAndGet()
            segments.trySend(seg to elapsedNow())
        }
    }

    /**
     * Live English while the sentence is still being spoken, so captions don't wait
     * for the sentence to end. At most one translation in flight, about every second.
     */
    private fun requestLiveTranslation() {
        if (draftJob?.isActive == true) return
        draftJob = viewModelScope.launch {
            val wait = LIVE_EVERY_MS - (SystemClock.elapsedRealtime() - lastDraftAt)
            if (wait > 0) delay(wait)
            val zh = _state.value.partial
            if (zh.isBlank()) return@launch
            lastDraftAt = SystemClock.elapsedRealtime()
            val en = runCatching { c.zhToEn.translate(zh) }.getOrNull() ?: return@launch
            // Ignore if the sentence ended meanwhile (its draft line has taken over)
            _state.update { if (it.partial.startsWith(zh.take(2))) it.copy(partialEn = en) else it }
        }
    }

    fun togglePause() {
        val s = _state.value
        if (!s.running) return
        viewModelScope.launch(Dispatchers.IO) {
            if (s.paused) {
                pausedTotal += System.currentTimeMillis() - pausedSince
                attachMic(); c.asr.start()
            } else {
                pausedSince = System.currentTimeMillis()
                c.asr.stop()
            }
            _state.update { it.copy(paused = !s.paused, partial = "", level = 0f) }
        }
    }

    /** Star the most recent caption line ("this part is important"). */
    fun starLatest() {
        val last = _state.value.lines.lastOrNull { !it.draft } ?: return
        viewModelScope.launch(Dispatchers.IO) { db.lines().setStarred(last.id, !last.starred) }
        _state.update { st -> st.copy(lines = st.lines.map { if (it.id == last.id) it.copy(starred = !it.starred) else it }) }
    }

    /** Stops recording and saves. Returns the lecture id for the diary. */
    suspend fun stop(): Long {
        val s = _state.value
        timerJob?.cancel()
        c.voice.clear()
        withContext(Dispatchers.IO) {
            c.asr.stop()                                 // flushes the last sentence
            c.asr.onAudio = {}
            wav?.close(); wav = null
            db.lectures().setDuration(s.lectureId, elapsedNow())
            retryPendingTranslations()
        }
        _state.update { it.copy(running = false, partial = "", level = 0f) }
        return s.lectureId
    }

    private suspend fun process(seg: Segment, tMs: Long) {
        val backlog = queued.decrementAndGet()
        val lectureId = _state.value.lectureId

        // 1) Show a quick line right away from the pass-1 text (translation takes ~0.1 s)
        val draftId = draftIds--
        val quickZh = seg.offlineText
        if (quickZh.isNotBlank() && !Filler.isFiller(quickZh)) {
            val quickEn = runCatching { c.zhToEn.translate(quickZh) }.getOrNull()
            if (quickEn != null) _state.update {
                it.copy(lines = it.lines + CaptionLine(id = draftId, tMs = tMs, zh = quickZh, en = quickEn, draft = true))
            }
        }

        // 2) Accurate pass. Lag fallback: if sentences pile up, skip it so captions stay live.
        val zh = if (backlog > 3) seg.offlineText else withContext(Dispatchers.Default) {
            runCatching { c.accurate.recognize(seg) }.getOrDefault(seg.offlineText)
        }.ifBlank { seg.offlineText }
        if (zh.isBlank() || Filler.isFiller(zh)) {
            _state.update { st -> st.copy(lines = st.lines.filter { it.id != draftId }) }
            return
        }

        // Translation fallback: keep the Chinese, retry later.
        val en = runCatching { c.zhToEn.translate(zh) }.getOrNull()
        val pending = en == null
        // English voice only into earbuds: on the loudspeaker it would disturb the class
        // and the mic would pick it up instead of the professor
        if (en != null && c.voiceOn.value && c.voice.headsetConnected()) {
            c.voice.say(en, VoiceOut.Lang.EN, VoiceOut.Route.EARBUDS, droppable = true)
        }

        val line = Line(lectureId = lectureId, tMs = tMs, zh = zh, en = en ?: "", translationPending = pending)
        val lineId = withContext(Dispatchers.IO) { db.lines().insert(line) }

        val hw = HomeworkDetector.detect(zh, en ?: "")
        if (hw != null) {
            withContext(Dispatchers.IO) {
                db.homework().insert(
                    Homework(
                        lectureId = lectureId, tMs = tMs, course = _state.value.course,
                        en = en ?: zh, zh = zh, dueEpochDay = hw.due?.toEpochDay(),
                        createdAt = System.currentTimeMillis(),
                    )
                )
            }
        }
        // 3) Replace the quick line with the accurate one (same place in the list)
        val final = CaptionLine(
            id = lineId, tMs = tMs, zh = zh, en = en ?: "", isHomework = hw != null,
            homeworkDue = hw?.due, translationPending = pending,
        )
        _state.update { st ->
            if (st.lines.any { it.id == draftId }) st.copy(lines = st.lines.map { if (it.id == draftId) final else it })
            else st.copy(lines = st.lines + final)
        }
    }

    private suspend fun retryPendingTranslations() {
        for (l in db.lines().pending()) {
            val en = runCatching { c.zhToEn.translate(l.zh) }.getOrNull() ?: continue
            db.lines().setTranslation(l.id, en)
        }
    }

    private companion object {
        const val LIVE_EVERY_MS = 900L
    }

    override fun onCleared() {
        if (_state.value.running) c.asr.stop()
        c.asr.onAudio = {}
        wav?.close()
        segments.close()
    }
}
