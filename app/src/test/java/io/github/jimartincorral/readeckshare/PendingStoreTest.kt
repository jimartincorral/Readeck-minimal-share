package io.github.jimartincorral.readeckshare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendingStoreTest {

    private val a = SharedLink("https://a.example/1", "Article A")
    private val b = SharedLink("https://b.example/2", null)

    @Test
    fun encodeDecodeRoundTrip() {
        val list = PendingStore.upsert(
            PendingStore.upsert(emptyList(), a, listOf("phone", "to read"), now = 1),
            b, emptyList(), now = 2,
        ).let { PendingStore.setState(it, b.url, PendingLink.State.FAILED, "Invalid API token") }

        val decoded = PendingStore.decode(PendingStore.encode(list))

        assertEquals(list, decoded)
        assertNull(decoded[1].title)
        assertNull(decoded[0].error)
    }

    @Test
    fun addingAnExistingUrlResetsItInPlace() {
        val list = PendingStore.upsert(
            PendingStore.upsert(emptyList(), a, emptyList(), now = 1),
            b, emptyList(), now = 2,
        ).let { PendingStore.setState(it, a.url, PendingLink.State.FAILED, "boom") }

        val updated = PendingStore.upsert(list, SharedLink(a.url, null), listOf("x"), now = 3)

        assertEquals(listOf(a.url, b.url), updated.map { it.url })
        val entry = updated[0]
        assertEquals(PendingLink.State.PENDING, entry.state)
        assertNull(entry.error)
        assertEquals("Article A", entry.title)
        assertEquals(1L, entry.sharedAt)
        assertEquals(listOf("x"), entry.labels)
    }

    @Test
    fun setStateIgnoresUnknownUrls() {
        val list = PendingStore.upsert(emptyList(), a, emptyList(), now = 1)
        assertEquals(list, PendingStore.setState(list, "https://gone.example", PendingLink.State.FAILED, "x"))
    }

    @Test
    fun corruptOrMissingDataIsEmpty() {
        assertEquals(emptyList<PendingLink>(), PendingStore.decode(null))
        assertEquals(emptyList<PendingLink>(), PendingStore.decode(""))
        assertEquals(emptyList<PendingLink>(), PendingStore.decode("not json"))
    }
}
