package dev.edgecode.data

import android.content.Context
import dev.edgecode.core.design.ThemeFamily
import dev.edgecode.core.design.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * User preferences.
 *
 * Passwords are deliberately absent: they are held in memory for the life of a
 * connection and never written to disk. The real implementation should keep
 * credentials in encrypted storage rather than here.
 */
data class Settings(
    val themeMode: ThemeMode = ThemeMode.System,
    val themeFamily: ThemeFamily = ThemeFamily.Edge,
    val serverUrl: String = "",
    val serverName: String = "",
    val serverUsername: String? = null,
    val showReasoning: Boolean = true,
    val expandTools: Boolean = false,
    val sendOnEnter: Boolean = true,
    /** Model keys (`provider/model`), most recently used first. */
    val recentModels: List<String> = emptyList(),
    /** The model each chat last sent a turn with, keyed by session id. */
    val sessionModels: Map<String, String> = emptyMap(),
) {
    val hasSavedServer: Boolean get() = serverUrl.isNotBlank()
}

class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("edgecode.settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<Settings> = _state.asStateFlow()

    private fun read() = Settings(
        themeMode = runCatching {
            ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: ThemeMode.System.name)
        }.getOrDefault(ThemeMode.System),
        themeFamily = ThemeFamily.fromStorage(prefs.getString(KEY_THEME_FAMILY, null)),
        serverUrl = prefs.getString(KEY_URL, "").orEmpty(),
        serverName = prefs.getString(KEY_NAME, "").orEmpty(),
        serverUsername = prefs.getString(KEY_USER, null),
        showReasoning = prefs.getBoolean(KEY_REASONING, true),
        expandTools = prefs.getBoolean(KEY_TOOLS, false),
        sendOnEnter = prefs.getBoolean(KEY_SEND_ON_ENTER, true),
        recentModels = prefs.getString(KEY_RECENT_MODELS, "").orEmpty()
            .lineSequence()
            .filter { it.isNotBlank() }
            .toList(),
        sessionModels = prefs.getString(KEY_SESSION_MODELS, "").orEmpty()
            .lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val cut = line.indexOf('\t')
                if (cut <= 0 || cut == line.lastIndex) null
                else line.substring(0, cut) to line.substring(cut + 1)
            }
            .toMap(),
    )

    fun setTheme(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _state.value = _state.value.copy(themeMode = mode)
    }

    fun setThemeFamily(family: ThemeFamily) {
        prefs.edit().putString(KEY_THEME_FAMILY, family.name).apply()
        _state.value = _state.value.copy(themeFamily = family)
    }

    fun saveServer(url: String, name: String, username: String?) {
        prefs.edit()
            .putString(KEY_URL, url)
            .putString(KEY_NAME, name)
            .putString(KEY_USER, username)
            .apply()
        _state.value = _state.value.copy(serverUrl = url, serverName = name, serverUsername = username)
    }

    fun clearServer() {
        prefs.edit().remove(KEY_URL).remove(KEY_NAME).remove(KEY_USER).apply()
        _state.value = _state.value.copy(serverUrl = "", serverName = "", serverUsername = null)
    }

    fun setShowReasoning(value: Boolean) {
        prefs.edit().putBoolean(KEY_REASONING, value).apply()
        _state.value = _state.value.copy(showReasoning = value)
    }

    fun setExpandTools(value: Boolean) {
        prefs.edit().putBoolean(KEY_TOOLS, value).apply()
        _state.value = _state.value.copy(expandTools = value)
    }

    fun setSendOnEnter(value: Boolean) {
        prefs.edit().putBoolean(KEY_SEND_ON_ENTER, value).apply()
        _state.value = _state.value.copy(sendOnEnter = value)
    }

    fun setRecentModels(keys: List<String>) {
        prefs.edit().putString(KEY_RECENT_MODELS, keys.joinToString("\n")).apply()
        _state.value = _state.value.copy(recentModels = keys)
    }

    /**
     * Remembers a chat's model. Older chats are dropped once the map is large
     * enough to be stale, so the preference cannot grow without bound.
     */
    fun setSessionModel(sessionId: String, modelKey: String) {
        val next = (_state.value.sessionModels - sessionId) + (sessionId to modelKey)
        val capped = next.entries.toList().takeLast(SESSION_MODEL_LIMIT)
            .associate { it.key to it.value }
        prefs.edit()
            .putString(KEY_SESSION_MODELS, capped.entries.joinToString("\n") { "${it.key}\t${it.value}" })
            .apply()
        _state.value = _state.value.copy(sessionModels = capped)
    }

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_THEME_FAMILY = "theme.family"
        const val KEY_URL = "server.url"
        const val KEY_NAME = "server.name"
        const val KEY_USER = "server.user"
        const val KEY_REASONING = "chat.reasoning"
        const val KEY_TOOLS = "chat.tools"
        const val KEY_SEND_ON_ENTER = "chat.sendOnEnter"
        const val KEY_RECENT_MODELS = "chat.recentModels"
        const val KEY_SESSION_MODELS = "chat.sessionModels"
        const val SESSION_MODEL_LIMIT = 60
    }
}
