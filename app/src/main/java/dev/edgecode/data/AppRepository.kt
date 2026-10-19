package dev.edgecode.data

import dev.edgecode.core.model.AgentInfo
import dev.edgecode.core.model.Attachment
import dev.edgecode.core.model.ChatMessage
import dev.edgecode.core.model.ConnectionState
import dev.edgecode.core.model.ModelInfo
import dev.edgecode.core.model.ModelRef
import dev.edgecode.core.model.PermissionDecision
import dev.edgecode.core.model.PermissionRequest
import dev.edgecode.core.model.ProjectRef
import dev.edgecode.core.model.ProviderInfo
import dev.edgecode.core.model.ServerProfile
import dev.edgecode.core.model.ServerEvent
import dev.edgecode.core.model.SessionSummary
import dev.edgecode.core.model.SlashCommand
import dev.edgecode.data.demo.DemoOpenCodeApi
import dev.edgecode.data.net.HttpOpenCodeApi
import dev.edgecode.data.net.HttpTransport
import dev.edgecode.data.net.OpenCodeWire
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Per-session transcript state. */
data class SessionChatState(
    val sessionId: String,
    val messages: List<ChatMessage> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
) {
    val isStreaming: Boolean
        get() = (messages.lastOrNull() as? ChatMessage.Assistant)?.streaming == true

    val lastAssistant: ChatMessage.Assistant?
        get() = messages.lastOrNull { it is ChatMessage.Assistant } as? ChatMessage.Assistant
}

/** The catalog of things the user can pick. Loaded once per connection. */
data class Catalog(
    val models: List<ModelInfo> = emptyList(),
    val agents: List<AgentInfo> = emptyList(),
    val commands: List<SlashCommand> = emptyList(),
    val providers: List<ProviderInfo> = emptyList(),
    val projects: List<ProjectRef> = emptyList(),
) {
    /**
     * Indexed rather than scanned: the server advertises its whole model
     * registry — thousands of entries — and this is called during composition.
     */
    private val byKey: Map<String, ModelInfo> by lazy {
        models.associateBy { it.providerId + "/" + it.id }
    }

    fun model(ref: ModelRef?): ModelInfo? = ref?.let { byKey[it.key] }

    /**
     * The models that can actually be used.
     *
     * The registry lists every provider the server knows about, connected or
     * not, and only a handful are. Offering the rest is dead weight: picking one
     * simply fails on send. When the server reports no connection state at all,
     * or reports everything connected, every model is usable.
     */
    val usableModels: List<ModelInfo> by lazy {
        val connected = providers.filter { it.connected }.map { it.id }.toSet()
        if (providers.isEmpty() || connected.size == providers.size) {
            models
        } else {
            models.filter { it.providerId in connected }
        }
    }

    val modelsByProvider: Map<String, List<ModelInfo>>
        get() = models.groupBy { it.providerName }

    val defaultModel: ModelInfo?
        get() = models.firstOrNull { it.isDefault } ?: models.firstOrNull()
}

/**
 * Single source of truth for everything the UI shows.
 *
 * It owns the API, subscribes to the server's event stream, and reduces those
 * events into [StateFlow]s. Because the v1 stream is documented as lossy, every
 * (re)connection is followed by a REST reconcile — the stream accelerates the
 * UI, it never defines the truth.
 */
class AppRepository(
    private val scope: CoroutineScope,
    /**
     * How the API client is built for a connection. Injectable so the timing
     * behaviour of the connect path can be tested on the JVM without a device,
     * and so the real v1 client can be dropped in without touching this class.
     */
    private val apiFactory: (CoroutineScope, ServerProfile) -> OpenCodeApi = { scope, profile ->
        if (profile.url.startsWith("demo://")) {
            DemoOpenCodeApi(scope)
        } else {
            HttpOpenCodeApi(
                HttpTransport(
                    baseUrl = profile.url,
                    username = profile.username,
                    password = profile.password,
                ),
            )
        }
    },
) {

    private var api: OpenCodeApi? = null
    private var eventJob: Job? = null

    private val _connection = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    private val _sessions = MutableStateFlow<List<SessionSummary>>(emptyList())
    val sessions: StateFlow<List<SessionSummary>> = _sessions.asStateFlow()

    private val _permissions = MutableStateFlow<List<PermissionRequest>>(emptyList())
    val permissions: StateFlow<List<PermissionRequest>> = _permissions.asStateFlow()

    private val _catalog = MutableStateFlow(Catalog())
    val catalog: StateFlow<Catalog> = _catalog.asStateFlow()

    private val chats = mutableMapOf<String, MutableStateFlow<SessionChatState>>()

    /**
     * Cached read-only views of [chats].
     *
     * `asStateFlow()` allocates a new wrapper on every call, so handing out a
     * fresh instance from composition restarted the collector coroutine on every
     * recomposition. Callers get one stable flow per session instead.
     */
    private val chatViews = mutableMapOf<String, StateFlow<SessionChatState>>()

    fun chat(sessionId: String): StateFlow<SessionChatState> =
        chatViews.getOrPut(sessionId) {
            chats.getOrPut(sessionId) { MutableStateFlow(SessionChatState(sessionId)) }
                .asStateFlow()
        }

    // --- Connection --------------------------------------------------------

    suspend fun connect(profile: ServerProfile) {
        disconnect()
        _connection.value = ConnectionState.Connecting(profile)
        val client = apiFactory(scope, profile)
        api = client
        try {
            val info = client.health()
            _connection.value = ConnectionState.Connected(profile, info)
            startEventStream(client)
            loadCatalog(client)
            refreshSessions()
        } catch (t: Throwable) {
            _connection.value = ConnectionState.Failed(
                profile,
                t.message ?: "Could not reach the server",
            )
            api = null
        }
    }

    /**
     * Demo connection: same path as a real one so the UI cannot tell the
     * difference, except that the top bar shows a "Demo" badge.
     */
    suspend fun connectDemo() {
        connect(
            ServerProfile(
                id = "demo",
                name = "Demo server",
                url = "demo://localhost",
            ),
        )
    }

    fun disconnect() {
        eventJob?.cancel()
        eventJob = null
        scope.launch { api?.close() }
        api = null
        _connection.value = ConnectionState.Disconnected
        _sessions.value = emptyList()
        _permissions.value = emptyList()
        _catalog.value = Catalog()
        chats.clear()
        chatViews.clear()
    }

    private fun startEventStream(client: OpenCodeApi) {
        eventJob?.cancel()
        eventJob = scope.launch {
            client.events().collect { event -> apply(event) }
        }
    }

    private fun apply(event: ServerEvent) {
        when (event) {
            is ServerEvent.SessionUpserted ->
                _sessions.update { ChatReducer.reduceSessions(it, event) }
            is ServerEvent.SessionRemoved ->
                _sessions.update { ChatReducer.reduceSessions(it, event) }
            is ServerEvent.PermissionRequested,
            is ServerEvent.PermissionResolved ->
                _permissions.update { ChatReducer.reducePermissions(it, event) }
            else -> Unit
        }

        val sessionId = event.sessionIdOrNull() ?: return
        chats[sessionId]?.update { current ->
            current.copy(messages = ChatReducer.reduceMessages(current.messages, event))
        }
    }

    /**
     * The catalogue fetches are independent, so they run concurrently. Doing
     * them one after another summed five round-trips into the time the user
     * waited before the session list appeared.
     */
    private suspend fun loadCatalog(client: OpenCodeApi) = coroutineScope {
        val models = async { runCatching { client.models() }.getOrDefault(emptyList()) }
        val agents = async { runCatching { client.agents() }.getOrDefault(emptyList()) }
        val commands = async { runCatching { client.commands() }.getOrDefault(emptyList()) }
        val providers = async { runCatching { client.providers() }.getOrDefault(emptyList()) }
        val projects = async { runCatching { client.projects() }.getOrDefault(emptyList()) }

        _catalog.value = Catalog(
            models = models.await(),
            agents = agents.await(),
            commands = commands.await(),
            providers = providers.await(),
            projects = projects.await(),
        )
    }

    // --- Sessions ----------------------------------------------------------

    suspend fun refreshSessions() {
        val client = api ?: return
        runCatching { client.sessions() }
            .onSuccess { _sessions.value = it }
    }

    fun openSession(sessionId: String) {
        val client = api ?: return
        val state = chats.getOrPut(sessionId) { MutableStateFlow(SessionChatState(sessionId)) }
        if (state.value.messages.isNotEmpty()) return
        state.update { it.copy(loading = true, error = null) }
        scope.launch {
            runCatching { client.messages(sessionId) }
                .onSuccess { messages -> state.update { it.copy(messages = messages, loading = false) } }
                .onFailure { error ->
                    state.update {
                        it.copy(loading = false, error = error.message ?: "Could not load messages")
                    }
                }
        }
    }

    fun createSession(directory: String?, title: String?, onCreated: (SessionSummary) -> Unit = {}) {
        val client = api ?: return
        scope.launch {
            runCatching { client.createSession(directory, title) }
                .onSuccess { session ->
                    _sessions.update { listOf(session) + it }
                    onCreated(session)
                }
        }
    }

    fun renameSession(sessionId: String, title: String) {
        val client = api ?: return
        scope.launch { runCatching { client.renameSession(sessionId, title) } }
    }

    fun deleteSession(sessionId: String) {
        val client = api ?: return
        scope.launch {
            runCatching { client.deleteSession(sessionId) }
            _sessions.update { list -> list.filterNot { it.id == sessionId } }
            chats.remove(sessionId)
            chatViews.remove(sessionId)
        }
    }

    // --- Conversation ------------------------------------------------------

    fun sendPrompt(
        sessionId: String,
        text: String,
        attachments: List<Attachment>,
        agent: String?,
        model: ModelRef?,
    ) {
        val client = api ?: return
        val body = text.trim()
        if (body.isEmpty() && attachments.isEmpty()) return

        val messageId = OpenCodeWire.newMessageId()

        // The turn appears immediately rather than waiting on a round trip. The
        // id is ours, and the server keeps it, so the echo over the event stream
        // merges into this bubble instead of duplicating it.
        val optimistic = ChatMessage.User(
            id = messageId,
            createdAt = System.currentTimeMillis(),
            text = body,
            attachments = attachments,
        )
        chats[sessionId]?.update { current ->
            current.copy(messages = current.messages + optimistic, error = null)
        }

        scope.launch {
            runCatching {
                client.sendPrompt(sessionId, body, attachments, agent, model, messageId)
            }.onFailure { error ->
                // It never reached the server, so the bubble is a lie.
                chats[sessionId]?.update { current ->
                    current.copy(
                        messages = current.messages.filterNot { it.id == messageId },
                        error = error.message ?: "Message failed to send",
                    )
                }
            }
        }
    }

    fun runCommand(sessionId: String, command: String, arguments: String) {
        val client = api ?: return
        scope.launch { runCatching { client.runCommand(sessionId, command, arguments) } }
    }

    fun abort(sessionId: String) {
        val client = api ?: return
        scope.launch { runCatching { client.abort(sessionId) } }
    }

    fun respondPermission(request: PermissionRequest, decision: PermissionDecision, remember: Boolean) {
        val client = api ?: return
        scope.launch {
            runCatching { client.respondPermission(request.sessionId, request.id, decision, remember) }
            _permissions.update { list -> list.filterNot { it.id == request.id } }
        }
    }
}

private fun ServerEvent.sessionIdOrNull(): String? = when (this) {
    is ServerEvent.MessageUpserted -> sessionId
    is ServerEvent.PartUpserted -> sessionId
    is ServerEvent.PartDelta -> sessionId
    is ServerEvent.MessageRemoved -> sessionId
    is ServerEvent.SessionIdle -> sessionId
    is ServerEvent.SessionFailed -> sessionId
    else -> null
}
