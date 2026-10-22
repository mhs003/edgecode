package dev.edgecode.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.edgecode.core.design.ThemeMode
import dev.edgecode.core.model.AgentInfo
import dev.edgecode.core.model.Attachment
import dev.edgecode.core.model.ConnectionState
import dev.edgecode.core.model.ModelRef
import dev.edgecode.core.model.ModelSelection
import dev.edgecode.core.model.PermissionDecision
import dev.edgecode.core.model.PermissionRequest
import dev.edgecode.core.model.QuestionRequest
import dev.edgecode.core.model.ServerProfile
import dev.edgecode.core.model.SessionSummary
import dev.edgecode.data.AppRepository
import dev.edgecode.data.Catalog
import dev.edgecode.data.ConnectionsStore
import dev.edgecode.data.SavedConnection
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
    data class Chat(val sessionId: String, val readOnly: Boolean = false) : Screen
    data object Settings : Screen
}

class AppViewModel(
    private val settings: SettingsStore,
    private val connections: ConnectionsStore,
) : ViewModel() {

    /**
     * Owned here rather than using `viewModelScope` so the scope is explicit,
     * cancelled deterministically, and shared with the repository.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val repository = AppRepository(scope)

    val connection: StateFlow<ConnectionState> = repository.connection
    val sessions: StateFlow<List<SessionSummary>> = repository.sessions
    val subagents: StateFlow<List<SessionSummary>> = repository.subagents
    val permissions: StateFlow<List<PermissionRequest>> = repository.permissions
    val questions: StateFlow<List<QuestionRequest>> = repository.questions
    val questionBusy: StateFlow<Boolean> = repository.questionBusy
    val questionError: StateFlow<String?> = repository.questionError
    val catalog: StateFlow<Catalog> = repository.catalog
    val settings_: StateFlow<Settings> = settings.state
    val savedConnections: StateFlow<List<SavedConnection>> = connections.state

    /**
     * In-memory passwords, keyed by (normalised) URL. A password is never written
     * to disk, so this is what lets a retry — or a tap on a saved server — reuse
     * the password the user already typed without prompting again.
     */
    private val passwords = mutableMapOf<String, String>()

    private val _stack = MutableStateFlow<List<Screen>>(listOf(Screen.Connect))
    val stack: StateFlow<List<Screen>> = _stack.asStateFlow()

    /** Set when a notification was tapped before the connection was ready. */
    private var pendingSessionId: String? = null

    private val _selection = MutableStateFlow(ModelSelection())
    val modelSelection: StateFlow<ModelSelection> = _selection.asStateFlow()

    private val _selectedAgent = MutableStateFlow("build")
    val selectedAgent: StateFlow<String> = _selectedAgent.asStateFlow()

    private val _reconnecting = MutableStateFlow(false)
    val reconnecting: StateFlow<Boolean> = _reconnecting.asStateFlow()

    val themeMode: ThemeMode get() = settings.state.value.themeMode

    init {
        val saved = settings.state.value
        _selection.value = ModelSelection(
            committed = saved.sessionModels.mapNotNull { (sessionId, key) ->
                ModelRef.parse(key)?.let { sessionId to it }
            }.toMap(),
            recent = saved.recentModels.mapNotNull(ModelRef::parse),
        )
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
                    maybeOpenPendingSession()
                }
            }
        }
    }

    // --- Connection --------------------------------------------------------

    fun connect(url: String, username: String?, password: String?, remember: Boolean) {
        // A second tap while the first attempt is still connecting used to
        // disconnect the attempt in flight, which surfaced as a failure on the
        // row that was tapped. There is nothing useful to do until it settles.
        if (connectionBusy()) return
        val normalized = normalizeUrl(url)
        val user = username?.takeIf { it.isNotBlank() }
        val secret = password?.takeIf { it.isNotBlank() }
        if (secret != null) passwords[normalized] = secret
        val profile = ServerProfile(
            id = "profile",
            name = normalized,
            url = normalized,
            username = user,
            password = secret,
        )
        val saved = if (remember) rememberServer(normalized, user) else null
        scope.launch {
            repository.connect(profile)
            val state = repository.connection.value
            if (state.isConnected) {
                saved?.let { connections.markUsed(it.id) }
                if (remember) settings.saveServer(normalized, normalized, user)
                _stack.value = listOf(Screen.Sessions)
                maybeOpenPendingSession()
            } else if (state is ConnectionState.Failed) {
                saved?.let { connections.touch(it.id, state.message) }
            }
        }
    }

    /**
     * Connect to a server the user picked from their saved list. [password] is
     * only supplied when the user was just asked for it; otherwise the password
     * cached for this URL (if the user typed one earlier this session) is reused.
     */
    fun connectSaved(connection: SavedConnection, password: String?) {
        if (connectionBusy()) return
        val url = connection.url
        val secret = password?.takeIf { it.isNotBlank() } ?: passwords[url]
        if (secret != null) passwords[url] = secret
        val profile = ServerProfile(
            id = connection.id,
            name = connection.name,
            url = url,
            username = connection.username,
            password = secret,
        )
        scope.launch {
            repository.connect(profile)
            val state = repository.connection.value
            if (state.isConnected) {
                connections.markUsed(connection.id)
                settings.saveServer(url, connection.name, connection.username)
                _stack.value = listOf(Screen.Sessions)
                maybeOpenPendingSession()
            } else if (state is ConnectionState.Failed) {
                connections.touch(connection.id, state.message)
            }
        }
    }

    /**
     * Drops the cached password for a saved connection so the next attempt asks
     * for it again. The connection itself stays in the list.
     */
    fun forgetSaved(id: String) {
        val saved = connections.findById(id) ?: return
        passwords.remove(saved.url)
    }

    fun removeSavedConnection(id: String) {
        forgetSaved(id)
        connections.remove(id)
    }

    private fun rememberServer(url: String, username: String?): SavedConnection {
        val existing = connections.find(url, username)
        val connection = SavedConnection(
            id = existing?.id ?: java.util.UUID.randomUUID().toString(),
            name = url,
            url = url,
            username = username,
            lastUsedAt = System.currentTimeMillis(),
        )
        connections.save(connection)
        return connection
    }

    fun connectDemo(remember: Boolean = false) {
        if (connectionBusy()) return
        scope.launch {
            repository.connectDemo()
            if (repository.connection.value.isConnected) {
                if (remember) settings.saveServer("demo://localhost", "Demo server", null)
                _stack.value = listOf(Screen.Sessions)
                maybeOpenPendingSession()
            }
        }
    }

    private fun connectionBusy(): Boolean =
        repository.connection.value.let { it is ConnectionState.Connecting || it.isConnected }

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
                    ServerProfile(
                        id = "saved",
                        name = saved.serverName,
                        url = saved.serverUrl,
                        username = saved.serverUsername,
                        password = passwords[saved.serverUrl.trimEnd('/')],
                    ),
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

    /**
     * Opens a subagent's transcript. It is read-only: the subagent is driven by
     * the agent that spawned it, and a prompt typed here would be outside that
     * conversation.
     */
    fun openSubagent(sessionId: String) {
        repository.openSession(sessionId)
        _stack.update { it + Screen.Chat(sessionId, readOnly = true) }
    }

    fun toTop() {
        _stack.update { listOf(it.first()) }
    }

    /**
     * Opens the session a notification pointed at. If the connection is still
     * being established, the id waits until it succeeds; a cold start from a
     * notification therefore lands on the blocked chat rather than the list.
     */
    fun openFromNotification(sessionId: String) {
        pendingSessionId = sessionId
        maybeOpenPendingSession()
    }

    private fun maybeOpenPendingSession() {
        val sessionId = pendingSessionId ?: return
        if (!repository.connection.value.isConnected) return
        pendingSessionId = null

        val current = _stack.value.lastOrNull()
        if (current is Screen.Chat && current.sessionId == sessionId) return

        if (repository.subagents.value.any { it.id == sessionId }) {
            openSubagent(sessionId)
        } else {
            openSession(sessionId)
        }
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
        scope.launch {
            repository.refreshSessions()
            repository.refreshQuestions()
        }
    }

    // --- Chat --------------------------------------------------------------

    fun chat(sessionId: String): StateFlow<SessionChatState> = repository.chat(sessionId)

    fun send(sessionId: String, text: String, attachments: List<Attachment>) {
        if (text.isBlank() && attachments.isEmpty()) return

        // This is the moment a model choice becomes the chat's model. Picking
        // one in the picker only stages it; sending is what proves the intent,
        // and it is also what makes the model the default for a new chat.
        val selection = _selection.value
        val model = selection.forSession(sessionId)
        if (model != null) {
            val next = selection.sent(sessionId, model)
            _selection.value = next
            settings.setRecentModels(next.recent.map { it.key })
            settings.setSessionModel(sessionId, model.key)
        }

        repository.sendPrompt(
            sessionId = sessionId,
            text = text,
            attachments = attachments,
            agent = _selectedAgent.value,
            model = model,
        )
    }

    fun runCommand(sessionId: String, command: String, arguments: String) {
        repository.runCommand(sessionId, command, arguments)
    }

    fun abort(sessionId: String) = repository.abort(sessionId)

    fun respondPermission(request: PermissionRequest, decision: PermissionDecision, remember: Boolean) =
        repository.respondPermission(request, decision, remember)

    fun answerQuestion(request: QuestionRequest, answers: List<List<String>>) =
        repository.answerQuestion(request, answers)

    fun rejectQuestion(request: QuestionRequest) =
        repository.rejectQuestion(request)

    fun clearQuestionError() = repository.clearQuestionError()

    fun setAgent(agent: String) {
        _selectedAgent.value = agent
    }

    /** Stages a model for the current chat. It is committed when a turn is sent. */
    fun setModel(sessionId: String, ref: ModelRef) {
        _selection.value = _selection.value.choosing(sessionId, ref)
    }

    fun agentInfo(id: String): AgentInfo? =
        repository.catalog.value.agents.firstOrNull { it.id == id }

    fun currentModelLabel(sessionId: String): String {
        val catalog = repository.catalog.value
        val info = catalog.model(_selection.value.forSession(sessionId))
            ?: catalog.defaultModel
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
    private val connections: ConnectionsStore,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return AppViewModel(settings, connections) as T
    }
}
