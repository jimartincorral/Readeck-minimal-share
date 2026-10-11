package io.github.jimartincorral.readeckshare

/** A link pulled out of a share intent. */
data class SharedLink(val url: String, val title: String?)

object UrlExtractor {
    private val urlPattern = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)
    private const val TRAILING_PUNCTUATION = ".,;:!?'\""

    /**
     * Apps share links in different shapes: just the URL, "Title https://…", or a
     * sentence with the link in it. Take the first http(s) URL from [text]. Only
     * [subject] is used as a title; otherwise Readeck picks the page's own title.
     */
    fun fromShare(text: String?, subject: String?): SharedLink? {
        val match = text?.let { urlPattern.find(it) } ?: return null
        val url = trimTrailing(match.value)
        val title = subject?.trim()?.takeIf { it.isNotEmpty() && it != url }
        return SharedLink(url, title)
    }

    // Drop sentence punctuation and unbalanced closing brackets that got glued to the URL.
    private fun trimTrailing(raw: String): String {
        var url = raw
        while (url.isNotEmpty()) {
            val last = url.last()
            url = when {
                last in TRAILING_PUNCTUATION -> url.dropLast(1)
                last == ')' && url.count { it == '(' } < url.count { it == ')' } -> url.dropLast(1)
                last == ']' && url.count { it == '[' } < url.count { it == ']' } -> url.dropLast(1)
                else -> return url
            }
        }
        return url
    }
}
