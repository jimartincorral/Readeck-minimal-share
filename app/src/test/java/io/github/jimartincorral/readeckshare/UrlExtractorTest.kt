package io.github.jimartincorral.readeckshare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlExtractorTest {

    @Test
    fun plainUrl() {
        assertEquals(
            SharedLink("https://example.org/a?b=1#c", null),
            UrlExtractor.fromShare("https://example.org/a?b=1#c", null),
        )
    }

    @Test
    fun titleThenUrlUsesSubjectAsTitle() {
        assertEquals(
            SharedLink("https://example.org/post", "A post"),
            UrlExtractor.fromShare("A post https://example.org/post", "A post"),
        )
    }

    @Test
    fun surroundingTextIsNotUsedAsTitle() {
        assertEquals(
            SharedLink("https://example.org/post", null),
            UrlExtractor.fromShare("Check this out: https://example.org/post", null),
        )
    }

    @Test
    fun trailingPunctuationIsDropped() {
        assertEquals(
            "https://example.org/post",
            UrlExtractor.fromShare("Read https://example.org/post.", null)?.url,
        )
        assertEquals(
            "https://example.org/post",
            UrlExtractor.fromShare("(see https://example.org/post)", null)?.url,
        )
    }

    @Test
    fun balancedParenthesesAreKept() {
        assertEquals(
            "https://en.wikipedia.org/wiki/Mercury_(planet)",
            UrlExtractor.fromShare("https://en.wikipedia.org/wiki/Mercury_(planet)", null)?.url,
        )
    }

    @Test
    fun firstUrlWins() {
        assertEquals(
            "http://one.example",
            UrlExtractor.fromShare("http://one.example and https://two.example", null)?.url,
        )
    }

    @Test
    fun subjectEqualToUrlIsIgnored() {
        assertNull(UrlExtractor.fromShare("https://example.org", "https://example.org")?.title)
    }

    @Test
    fun noUrl() {
        assertNull(UrlExtractor.fromShare("just some text", "subject"))
        assertNull(UrlExtractor.fromShare(null, null))
    }

    @Test
    fun normalizeServerUrl() {
        assertEquals("https://readeck.example.com", Prefs.normalizeServerUrl(" readeck.example.com/ "))
        assertEquals("http://192.168.1.2:8000", Prefs.normalizeServerUrl("http://192.168.1.2:8000/"))
        assertEquals("", Prefs.normalizeServerUrl("  "))
    }

    @Test
    fun parseLabels() {
        assertEquals(listOf("phone", "to read"), Prefs.parseLabels(" phone, ,to read,phone"))
        assertEquals(emptyList<String>(), Prefs.parseLabels(""))
    }
}
