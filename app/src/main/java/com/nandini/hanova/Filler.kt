package com.nandini.hanova

/** "嗯", "啊", "um"… on their own: noise or hesitation, not worth a caption or a voice line. */
object Filler {
    private val FILLER = Regex("^(嗯|啊|呃|哦|喔|唉|欸|诶|um|uh|ah|oh|hmm|mm|er)+$")

    fun isFiller(text: String): Boolean {
        val core = text.lowercase().filter { it.isLetter() }
        return core.isEmpty() || FILLER.matches(core)
    }
}
