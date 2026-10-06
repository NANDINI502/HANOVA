package com.nandini.hanova

import android.app.Application
import com.google.mlkit.nl.translate.TranslateLanguage
import com.nandini.hanova.data.HanovaDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Holds the heavy, shared things: database, the two ASR models and the translators.
 * Models load ONCE in the background at app start (they take a few seconds).
 */
class HanovaApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.warmUp()
    }
}

class AppContainer(private val app: Application) {

    sealed interface Status {
        data object Loading : Status
        data object Ready : Status
        data class Failed(val message: String) : Status
    }

    val db: HanovaDb = HanovaDb.create(app)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _status = MutableStateFlow<Status>(Status.Loading)
    val status: StateFlow<Status> = _status

    lateinit var asr: AsrEngine
        private set
    lateinit var accurate: AccurateAsr
        private set

    val zhToEn: SentenceTranslator = MlKitTranslator(TranslateLanguage.CHINESE, TranslateLanguage.ENGLISH)
    val enToZh: SentenceTranslator = MlKitTranslator(TranslateLanguage.ENGLISH, TranslateLanguage.CHINESE)

    /** Spoken translations: English into earbuds, Chinese out of the phone speaker. */
    val voice = VoiceOut(app)
    /** User switch (lecture screen speaker button). */
    val voiceOn = MutableStateFlow(true)
    /** e.g. "Chinese voice missing" — shown on Home with a tap-to-download. */
    private val _voiceProblem = MutableStateFlow<String?>(null)
    val voiceProblem: StateFlow<String?> = _voiceProblem

    fun warmUp() {
        scope.launch(Dispatchers.IO) {
            try {
                asr = AsrEngine(app.assets)
                accurate = AccurateAsr(app.assets, language = "")   // auto zh/en
                zhToEn.prepare()
                enToZh.prepare()
                _status.value = Status.Ready
                // Captions work without voices, so this never blocks Ready
                _voiceProblem.value = runCatching { voice.init() }.getOrDefault(listOf("Voice output not available"))
                    .takeIf { it.isNotEmpty() }?.joinToString()
            } catch (e: Exception) {
                _status.value = Status.Failed(
                    (e.message ?: "Unknown error") +
                        "\nFirst launch needs internet once for the translation models."
                )
            }
        }
    }
}
