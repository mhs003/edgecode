package dev.edgecode.data

import android.content.Context
import dev.edgecode.core.util.JsonValue
import dev.edgecode.core.util.asArray
import dev.edgecode.core.util.encode
import dev.edgecode.core.util.longOr
import dev.edgecode.core.util.parseJson
import dev.edgecode.core.util.string
import dev.edgecode.core.util.stringOr
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A server the user has connected to before.
 *
 * `lastStatus` remembers why the last attempt failed so the list can show a dead
 * connection without probing it again on open.
 */
data class SavedConnection(
    val id: String,
    val name: String,
    val url: String,
    val username: String? = null,
    val lastUsedAt: Long = 0L,
    val lastStatus: String? = null,
) {
    val hasCredentials: Boolean get() = !username.isNullOrBlank()
}

/**
 * The pure half of [ConnectionsStore]: list ordering, de-duplication and the
 * JSON document.
 *
 * Kept free of Android and coroutine types so the rules that decide what the
 * list shows can be exercised on the JVM without a device.
 */
object Connections {

    /** Most recently used first, replacing any entry for the same server+user. */
    fun upsert(current: List<SavedConnection>, connection: SavedConnection): List<SavedConnection> =
        current
            .filterNot { it.id == connection.id || it.matches(connection.url, connection.username) }
            .plus(connection)
            .sortedByDescending { it.lastUsedAt }

    fun remove(current: List<SavedConnection>, id: String): List<SavedConnection> =
        current.filterNot { it.id == id }

    fun markUsed(current: List<SavedConnection>, id: String, at: Long): List<SavedConnection> =
        current
            .map { if (it.id == id) it.copy(lastUsedAt = at, lastStatus = null) else it }
            .sortedByDescending { it.lastUsedAt }

    fun touch(current: List<SavedConnection>, id: String, status: String?): List<SavedConnection> =
        current.map { if (it.id == id) it.copy(lastStatus = status) else it }

    fun find(current: List<SavedConnection>, url: String, username: String?): SavedConnection? =
        current.firstOrNull { it.matches(url, username) }

    fun findById(current: List<SavedConnection>, id: String): SavedConnection? =
        current.firstOrNull { it.id == id }

    fun encode(connections: List<SavedConnection>): String =
        JsonValue.Arr(connections.map { it.toJson() }).encode()

    fun decode(raw: String?): List<SavedConnection> {
        if (raw.isNullOrEmpty()) return emptyList()
        return runCatching {
            (parseJson(raw).asArray ?: emptyList())
                .mapNotNull { it.toConnection() }
                .sortedByDescending { it.lastUsedAt }
        }.getOrDefault(emptyList())
    }

    private fun SavedConnection.toJson(): JsonValue.Obj =
        JsonValue.Obj(
            buildMap {
                put("id", JsonValue.Str(id))
                put("name", JsonValue.Str(name))
                put("url", JsonValue.Str(url))
                username?.let { put("username", JsonValue.Str(it)) }
                put("lastUsedAt", JsonValue.Num(lastUsedAt.toDouble()))
                lastStatus?.let { put("lastStatus", JsonValue.Str(it)) }
            },
        )

    private fun JsonValue.toConnection(): SavedConnection? {
        val id = string("id") ?: return null
        val url = stringOr("url", "")
        if (url.isBlank()) return null
        return SavedConnection(
            id = id,
            name = stringOr("name", url),
            url = url,
            username = string("username"),
            lastUsedAt = longOr("lastUsedAt", 0L),
            lastStatus = string("lastStatus"),
        )
    }

    private fun SavedConnection.matches(otherUrl: String, otherUser: String?): Boolean =
        url.trimEnd('/').equals(otherUrl.trimEnd('/'), ignoreCase = true) &&
            username.orEmpty() == otherUser.orEmpty()
}

/**
 * The connection manager's store.
 *
 * Follows [SettingsStore] exactly: same SharedPreferences mechanism, same
 * `MutableStateFlow` mirror, same optimistic write. All of the decision-making
 * lives in [Connections] so it stays unit-testable off-device.
 *
 * The list is persisted as a single JSON document rather than a delimited
 * string, because a URL can contain any separator we might pick.
 *
 * **Passwords are never persisted.** [SettingsStore] already states this policy
 * and [dev.edgecode.core.model.ServerProfile.password] is documented as
 * memory-only, so a saved connection carries url, username and a name only.
 * Reconnecting asks for the password again, and `AppViewModel` keeps it in
 * memory for the life of the process so a retry does not re-prompt.
 */
class ConnectionsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("edgecode.connections", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(Connections.decode(prefs.getString(KEY_CONNECTIONS, null)))
    val state: StateFlow<List<SavedConnection>> = _state.asStateFlow()

    fun save(connection: SavedConnection) = persist(Connections.upsert(_state.value, connection))

    fun remove(id: String) = persist(Connections.remove(_state.value, id))

    fun markUsed(id: String, at: Long = System.currentTimeMillis()) =
        persist(Connections.markUsed(_state.value, id, at))

    fun touch(id: String, status: String?) =
        persist(Connections.touch(_state.value, id, status))

    fun find(url: String, username: String?): SavedConnection? =
        Connections.find(_state.value, url, username)

    fun findById(id: String): SavedConnection? = Connections.findById(_state.value, id)

    private fun persist(next: List<SavedConnection>) {
        prefs.edit().putString(KEY_CONNECTIONS, Connections.encode(next)).apply()
        _state.value = next
    }

    private companion object {
        const val KEY_CONNECTIONS = "connections"
    }
}
