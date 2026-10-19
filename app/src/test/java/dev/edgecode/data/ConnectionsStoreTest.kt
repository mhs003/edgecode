package dev.edgecode.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [ConnectionsStore] itself needs an Android `Context`, and this project has no
 * Robolectric (and no room for a new dependency), so these tests drive the pure
 * [Connections] logic and its JSON persistence directly.
 */
class ConnectionsStoreTest {

    private fun connection(
        id: String,
        url: String,
        username: String? = null,
        lastUsedAt: Long = 0L,
        lastStatus: String? = null,
    ) = SavedConnection(
        id = id,
        name = url,
        url = url,
        username = username,
        lastUsedAt = lastUsedAt,
        lastStatus = lastStatus,
    )

    @Test
    fun jsonRoundTripPreservesEveryField() {
        val recent = SavedConnection(
            id = "b",
            name = "Work",
            url = "http://10.0.0.2:4096",
            username = "opencode",
            lastUsedAt = 1_700_000_500_000L,
            lastStatus = "This server needs a password",
        )
        val older = SavedConnection(
            id = "a",
            name = "http://10.0.0.2:4096",
            url = "http://10.0.0.2:4096",
            username = null,
            lastUsedAt = 1_700_000_000_000L,
            lastStatus = null,
        )

        val decoded = Connections.decode(Connections.encode(listOf(recent, older)))

        assertEquals(listOf(recent, older), decoded)
    }

    @Test
    fun decodeToleratesEmptyMalformedAndIncompleteDocuments() {
        assertEquals(emptyList<SavedConnection>(), Connections.decode(null))
        assertEquals(emptyList<SavedConnection>(), Connections.decode(""))
        assertEquals(emptyList<SavedConnection>(), Connections.decode("not json"))
        assertEquals(emptyList<SavedConnection>(), Connections.decode("[{\"id\":\"x\"}]"))
    }

    @Test
    fun decodeOrdersByMostRecentFirst() {
        val decoded = Connections.decode(
            Connections.encode(
                listOf(
                    connection("old", "http://old", lastUsedAt = 10L),
                    connection("new", "http://new", lastUsedAt = 20L),
                ),
            ),
        )

        assertEquals(listOf("new", "old"), decoded.map { it.id })
    }

    @Test
    fun saveDeduplicatesByUrlAndUsername() {
        val first = connection("a", "http://server:4096", username = "opencode", lastUsedAt = 1L)
        val second = connection("b", "http://server:4096/", username = "opencode", lastUsedAt = 2L)

        val result = Connections.upsert(Connections.upsert(emptyList(), first), second)

        assertEquals(1, result.size)
        assertEquals("b", result.single().id)
    }

    @Test
    fun saveKeepsDifferentUsernamesApart() {
        val alice = connection("a", "http://server", username = "alice")
        val bob = connection("b", "http://server", username = "bob")

        val result = Connections.upsert(Connections.upsert(emptyList(), alice), bob)

        assertEquals(2, result.size)
    }

    @Test
    fun saveReplacesEntryWhenIdMatches() {
        val original = connection("a", "http://server", lastUsedAt = 1L)
        val updated = connection("a", "http://server", lastUsedAt = 5L)

        val result = Connections.upsert(listOf(original), updated)

        assertEquals(1, result.size)
        assertEquals(5L, result.single().lastUsedAt)
    }

    @Test
    fun removeDropsOnlyTheGivenId() {
        val list = listOf(connection("a", "http://a"), connection("b", "http://b"))

        assertEquals(listOf("b"), Connections.remove(list, "a").map { it.id })
    }

    @Test
    fun markUsedUpdatesTimestampClearsStatusAndReorders() {
        val list = listOf(
            connection("a", "http://a", lastUsedAt = 10L, lastStatus = "boom"),
            connection("b", "http://b", lastUsedAt = 20L),
        )

        val result = Connections.markUsed(list, "a", 30L)

        assertEquals(listOf("a", "b"), result.map { it.id })
        assertEquals(30L, result.first().lastUsedAt)
        assertNull(result.first().lastStatus)
    }

    @Test
    fun touchRecordsTheFailureMessage() {
        val list = listOf(connection("a", "http://a", lastUsedAt = 10L))

        val result = Connections.touch(list, "a", "This server needs a password")

        assertEquals("This server needs a password", result.single().lastStatus)
        assertEquals(10L, result.single().lastUsedAt)
    }

    @Test
    fun findMatchesOnNormalisedUrlAndUsername() {
        val list = listOf(connection("a", "http://server:4096", username = "opencode"))

        assertEquals("a", Connections.find(list, "http://server:4096/", "opencode")?.id)
        assertEquals("a", Connections.find(list, "HTTP://server:4096", "opencode")?.id)
        assertNull(Connections.find(list, "http://server:4096", "someone"))
        assertEquals("a", Connections.findById(list, "a")?.id)
        assertNull(Connections.findById(list, "missing"))
    }
}
