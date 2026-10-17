package dev.edgecode.data

import android.content.Context
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
    val themeMode: ThemeMode = ThemeMode.Dark,
    val serverUrl: String = "",
    val serverName: String = "",
    val serverUsername: String? = null,
    val showReasoning: Boolean = true,
    val expandTools: Boolean = false,
    val sendOnEnter: Boolean = true,
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
            ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: ThemeMode.Dark.name)
        }.getOrDefault(ThemeMode.Dark),
        serverUrl = prefs.getString(KEY_URL, "").orEmpty(),
        serverName = prefs.getString(KEY_NAME, "").orEmpty(),
        serverUsername = prefs.getString(KEY_USER, null),
        showReasoning = prefs.getBoolean(KEY_REASONING, true),
        expandTools = prefs.getBoolean(KEY_TOOLS, false),
        sendOnEnter = prefs.getBoolean(KEY_SEND_ON_ENTER, true),
    )

    fun setTheme(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _state.value = _state.value.copy(themeMode = mode)
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

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_URL = "server.url"
        const val KEY_NAME = "server.name"
        const val KEY_USER = "server.user"
        const val KEY_REASONING = "chat.reasoning"
        const val KEY_TOOLS = "chat.tools"
        const val KEY_SEND_ON_ENTER = "chat.sendOnEnter"
    }
}
