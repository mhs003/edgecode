package dev.edgecode.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.edgecode.core.design.ThemeMode
import dev.edgecode.core.model.AgentInfo
import dev.edgecode.core.model.Attachment
import dev.edgecode.core.model.ConnectionState
import dev.edgecode.core.model.ModelRef
import dev.edgecode.core.model.PermissionDecision
import dev.edgecode.core.model.PermissionRequest
import dev.edgecode.core.model.ServerProfile
import dev.edgecode.core.model.SessionSummary
import dev.edgecode.data.AppRepository
import dev.edgecode.data.Catalog
import dev.edgecode.data.SessionChatState
import dev.edgecode.data.Settings
import dev.edgecode.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Destinations. Small enough that a hand-rolled stack beats a nav graph. */
sealed interface Screen {
    data object Connect : Screen
    data object Sessions : Screen
    data class Chat(val sessionId: String) : Screen
    data object Settings : Screen
}

class AppViewModel(
    private val settings: SettingsStore,
) : ViewModel() {

    /**
     * Owned here rather than using `viewModelScope` so the scope is explicit,
     * cancelled deterministically, and shared with the repository.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val repository = AppRepository(scope)

    val connection: StateFlow<ConnectionState> = repository.connection
    val sessions: StateFlow<List<SessionSummary>> = repository.sessions
    val permissions: StateFlow<List<PermissionRequest>> = repository.permissions
    val catalog: StateFlow<Catalog> = repository.catalog
    val settings_: StateFlow<Settings> = settings.state

    private val _stack = MutableStateFlow<List<Screen>>(listOf(Screen.Connect))
    val stack: StateFlow<List<Screen>> = _stack.asStateFlow()

    private val _selectedModel = MutableStateFlow<ModelRef?>(null)
    val selectedModel: StateFlow<ModelRef?> = _selectedModel.asStateFlow()

    private val _selectedAgent = MutableStateFlow("build")
    val selectedAgent: StateFlow<String> = _selectedAgent.asStateFlow()

    private val _reconnecting = MutableStateFlow(false)
    val reconnecting: StateFlow<Boolean> = _reconnecting.asStateFlow()

    val themeMode: ThemeMode get() = settings.state.value.themeMode

    init {
        val saved = settings.state.value
        if (saved.hasSavedServer) {
            scope.launch {
                repository.connect(
                    ServerProfile(
                        id = "saved",
                        name = saved.serverName.ifBlank { saved.serverUrl },
                        url = saved.serverUrl,
                        username = saved.serverUsername,
                    ),
                )
                if (repository.connection.value.isConnected) {
                    _stack.value = listOf(Screen.Sessions)
                }
            }
        }
    }

    // --- Connection --------------------------------------------------------

    fun connect(url: String, username: String?, password: String?, remember: Boolean) {
        val normalized = normalizeUrl(url)
        val profile = ServerProfile(
            id = "profile",
            name = normalized,
            url = normalized,
            username = username?.takeIf { it.isNotBlank() },
            password = password?.takeIf { it.isNotBlank() },
        )
        scope.launch {
            repository.connect(profile)
            if (repository.connection.value.isConnected) {
                if (remember) {
                    settings.saveServer(normalized, normalized, profile.username)
                }
                _stack.value = listOf(Screen.Sessions)
            }
        }
    }

    fun connectDemo(remember: Boolean = false) {
        scope.launch {
            repository.connectDemo()
            if (repository.connection.value.isConnected) {
                if (remember) settings.saveServer("demo://localhost", "Demo server", null)
                _stack.value = listOf(Screen.Sessions)
            }
        }
    }

    fun disconnect() {
        repository.disconnect()
        settings.clearServer()
        _stack.value = listOf(Screen.Connect)
    }

    fun retryConnect() {
        val saved = settings.state.value
        _reconnecting.value = true
        scope.launch {
            if (saved.hasSavedServer) {
                repository.connect(
                    ServerProfile("saved", saved.serverName, saved.serverUrl, saved.serverUsername),
                )
            }
            _reconnecting.value = false
        }
    }

    // --- Navigation --------------------------------------------------------

    fun openSessions() {
        _stack.value = listOf(Screen.Sessions)
    }

    fun openSettings() {
        _stack.update { it + Screen.Settings }
    }

    fun openSession(sessionId: String) {
        repository.openSession(sessionId)
        _stack.update { it + Screen.Chat(sessionId) }
    }

    fun toTop() {
        _stack.update { listOf(it.first()) }
    }

    /**
     * Handles system back. Returns true when the event was consumed, so the
     * activity knows whether to finish.
     */
    fun back(): Boolean {
        val current = _stack.value
        if (current.size <= 1) return false
        _stack.value = current.dropLast(1)
        return true
    }

    // --- Sessions ----------------------------------------------------------

    fun createSession(directory: String?, title: String?) {
        repository.createSession(directory, title) { session ->
            _stack.update { it + Screen.Chat(session.id) }
        }
    }

    fun renameSession(sessionId: String, title: String) = repository.renameSession(sessionId, title)

    fun deleteSession(sessionId: String) {
        repository.deleteSession(sessionId)
        if (_stack.value.lastOrNull() == Screen.Chat(sessionId)) {
            _stack.value = listOf(Screen.Sessions)
        }
    }

    fun refresh() {
        scope.launch { repository.refreshSessions() }
    }

    // --- Chat --------------------------------------------------------------

    fun chat(sessionId: String): StateFlow<SessionChatState> = repository.chat(sessionId)

    fun send(sessionId: String, text: String, attachments: List<Attachment>) {
        if (text.isBlank() && attachments.isEmpty()) return
        repository.sendPrompt(
            sessionId = sessionId,
            text = text,
            attachments = attachments,
            agent = _selectedAgent.value,
            model = _selectedModel.value,
        )
    }

    fun runCommand(sessionId: String, command: String, arguments: String) {
        repository.runCommand(sessionId, command, arguments)
    }

    fun abort(sessionId: String) = repository.abort(sessionId)

    fun respondPermission(request: PermissionRequest, decision: PermissionDecision, remember: Boolean) =
        repository.respondPermission(request, decision, remember)

    fun setAgent(agent: String) {
        _selectedAgent.value = agent
    }

    fun setModel(ref: ModelRef?) {
        _selectedModel.value = ref
    }

    fun agentInfo(id: String): AgentInfo? =
        repository.catalog.value.agents.firstOrNull { it.id == id }

    fun currentModelLabel(): String {
        val catalog = repository.catalog.value
        val ref = _selectedModel.value
        val info = catalog.model(ref) ?: catalog.defaultModel
            ?: return "No model"
        return info.name
    }

    // --- Settings ----------------------------------------------------------

    fun setTheme(mode: ThemeMode) = settings.setTheme(mode)

    fun setShowReasoning(value: Boolean) = settings.setShowReasoning(value)

    fun setExpandTools(value: Boolean) = settings.setExpandTools(value)

    fun setSendOnEnter(value: Boolean) = settings.setSendOnEnter(value)

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }

    private fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return trimmed
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("demo://")) {
            trimmed.trimEnd('/')
        } else {
            "http://${trimmed.trimEnd('/')}"
        }
    }
}

class AppViewModelFactory(
    private val settings: SettingsStore,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return AppViewModel(settings) as T
    }
}
