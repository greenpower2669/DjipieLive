package fr.djipie.live

data class ChatLine(val id: String, val author: String, val text: String) {
    val isQuestion: Boolean get() = text.contains("?") || text.contains("？")
}

object ChatPolicy {
    private val urlPattern = Regex("https?://\\S+", RegexOption.IGNORE_CASE)

    fun clean(content: String): String =
        urlPattern.replace(content.replace(Regex("[\\r\\n]+"), " "), "lien")
            .trim().take(240)

    fun shouldRead(line: ChatLine, questionsOnly: Boolean): Boolean =
        line.text.isNotBlank() && (!questionsOnly || line.isQuestion)
}
