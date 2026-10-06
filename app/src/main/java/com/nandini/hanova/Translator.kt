package com.nandini.hanova

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await

/** Swappable translation backend: ML Kit now, Opus-MT / small LLM later. */
interface SentenceTranslator {
    suspend fun prepare()
    suspend fun translate(text: String): String
    fun close()
}

/**
 * Free, on-device ML Kit translation.
 * zh→en for lectures and "they said"; en→zh for "I said" in conversation mode.
 */
class MlKitTranslator(
    source: String = TranslateLanguage.CHINESE,
    target: String = TranslateLanguage.ENGLISH,
    private val useGlossary: Boolean = source == TranslateLanguage.CHINESE,
) : SentenceTranslator {
    private val client = Translation.getClient(
        TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build()
    )

    /** Needs internet ONCE (~30 MB per language). After that it works fully offline. */
    override suspend fun prepare() {
        client.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
    }

    override suspend fun translate(text: String): String =
        client.translate(if (useGlossary) Glossary.apply(text) else text).await()

    override fun close() = client.close()
}

/**
 * Course-specific term fixes applied BEFORE zh→en translation.
 * Add the terms your professors use; generic MT often mangles them.
 */
object Glossary {
    private val terms = linkedMapOf(
        "演算法" to "algorithm",   // Taiwan usage
        "算法" to "algorithm",     // mainland usage
        "資料結構" to "data structure",
        "数据结构" to "data structure",
        "類神經網路" to "neural network",
        "神经网络" to "neural network",
    )
    fun apply(text: String): String =
        terms.entries.fold(text) { acc, (k, v) -> acc.replace(k, " $v ") }
}
