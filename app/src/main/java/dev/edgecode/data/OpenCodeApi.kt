package dev.edgecode.data

import dev.edgecode.core.model.AgentInfo
import dev.edgecode.core.model.ApiVersion
import dev.edgecode.core.model.Attachment
import dev.edgecode.core.model.ChatMessage
import dev.edgecode.core.model.ModelInfo
import dev.edgecode.core.model.ModelRef
import dev.edgecode.core.model.Part
import dev.edgecode.core.model.PermissionDecision
import dev.edgecode.core.model.PermissionRequest
import dev.edgecode.core.model.ProjectRef
import dev.edgecode.core.model.ProviderInfo
import dev.edgecode.core.model.QuestionRequest
import dev.edgecode.core.model.ServerEvent
import dev.edgecode.core.model.ServerInfo
import dev.edgecode.core.model.SessionSummary
import dev.edgecode.core.model.SessionStatus
import dev.edgecode.core.model.SlashCommand
import kotlinx.coroutines.flow.Flow

/**
 * The models and providers a server is configured to use.
 *
 * Kept as one value because they come from one registry read. The full
 * `/provider` document also lists every provider the server knows about,
 * connected or not, and is measured in megabytes; `/config/providers` is the
 * configured subset the picker actually needs.
 */
data class ProviderRegistry(
    val models: List<ModelInfo> = emptyList(),
    val providers: List<ProviderInfo> = emptyList(),
)

/**
 * The one seam between the app and an OpenCode server.
 *
 * Everything above this interface speaks [dev.edgecode.core.model] types, so a
 * real v1 client and, later, a v2 client can both satisfy it without any UI
 * change. [dev.edgecode.data.demo.DemoOpenCodeApi] is the current
 * implementation.
 */
interface OpenCodeApi {

    val version: ApiVersion

    /** `GET /global/health` on v1. Throws on an unreachable or unauthorised host. */
    suspend fun health(): ServerInfo

    suspend fun projects(): List<ProjectRef>

    suspend fun sessions(): List<SessionSummary>

    /**
     * Which sessions the server is currently working on, by id.
     *
     * A session's own payload carries no status, so this is the REST half of the
     * status picture; `session.status` events are the live half.
     */
    suspend fun sessionStatuses(): Map<String, SessionStatus>

    suspend fun createSession(directory: String?, title: String?): SessionSummary

    suspend fun renameSession(sessionId: String, title: String)

    suspend fun deleteSession(sessionId: String)

    suspend fun messages(sessionId: String): List<ChatMessage>

    /** Returns the id of the user message that was queued. */
    suspend fun sendPrompt(
        sessionId: String,
        text: String,
        attachments: List<Attachment> = emptyList(),
        agent: String? = null,
        model: ModelRef? = null,
        messageId: String? = null,
    ): String

    suspend fun runCommand(sessionId: String, command: String, arguments: String)

    suspend fun runShell(sessionId: String, command: String)

    suspend fun abort(sessionId: String)

    suspend fun respondPermission(
        sessionId: String,
        requestId: String,
        decision: PermissionDecision,
        remember: Boolean,
    )

    /** Pending questions across every session, in the server's order. */
    suspend fun questions(): List<QuestionRequest>

    /**
     * Answers one question request. [answers] must hold one array per question,
     * in the order the questions were asked; each array contains selected labels
     * (or one free-text label).
     */
    suspend fun replyQuestion(requestId: String, answers: List<List<String>>)

    suspend fun rejectQuestion(requestId: String)

    suspend fun commands(): List<SlashCommand>

    suspend fun agents(): List<AgentInfo>

    suspend fun providers(): List<ProviderInfo>

    suspend fun models(): List<ModelInfo>

    /**
     * Reads the configured providers and their models in one request.
     *
     * The default implementation composes [models] and [providers]; the HTTP
     * client overrides it so a real server is read once.
     */
    suspend fun providerRegistry(): ProviderRegistry =
        ProviderRegistry(models = models(), providers = providers())

    /**
     * Event stream. Always collect this together with a REST reconcile: the v1
     * server documents the stream as lossy, so it can never be the only source
     * of truth for what a session contains.
     */
    fun events(): Flow<ServerEvent>

    suspend fun close()
}

/**
 * Reduces server events into UI state.
 *
 * Pure functions on purpose: this is where most of the real complexity lives,
 * and keeping it free of coroutines and Android types means it is fully
 * testable on the JVM.
 */
object ChatReducer {

    fun reduceMessages(current: List<ChatMessage>, event: ServerEvent): List<ChatMessage> =
        when (event) {
            is ServerEvent.MessageUpserted -> upsertMessage(current, event.message)
            is ServerEvent.PartUpserted -> upsertPart(current, event.sessionId, event.messageId, event.part)
            is ServerEvent.PartDelta -> appendDelta(current, event)
            is ServerEvent.MessageRemoved -> current.filterNot { it.id == event.messageId }
            is ServerEvent.SessionFailed -> current.map { message ->
                if (message is ChatMessage.Assistant && message.streaming) {
                    message.copy(streaming = false, error = event.message)
                } else {
                    message
                }
            }
            is ServerEvent.SessionIdle -> current.map { message ->
                if (message is ChatMessage.Assistant && message.streaming) {
                    message.copy(streaming = false, completedAt = System.currentTimeMillis())
                } else {
                    message
                }
            }
            else -> current
        }

    private fun upsertMessage(current: List<ChatMessage>, message: ChatMessage): List<ChatMessage> {
        val index = current.indexOfFirst { it.id == message.id }
        return if (index >= 0) {
            // Never let a stale frame replace richer local streaming state.
            val existing = current[index]
            val merged = when {
                existing is ChatMessage.Assistant && message is ChatMessage.Assistant ->
                    message.copy(
                        parts = if (message.parts.isEmpty()) existing.parts else message.parts,
                        streaming = existing.streaming || message.streaming,
                    )
                // A `message.updated` frame for a user message carries no parts
                // at all — the body arrives separately over
                // `message.part.updated` — so the server sends several frames
                // for one message and each one would otherwise blank the text.
                existing is ChatMessage.User && message is ChatMessage.User ->
                    message.copy(
                        text = message.text.ifBlank { existing.text },
                        attachments = message.attachments.ifEmpty { existing.attachments },
                    )
                else -> message
            }
            current.toMutableList().also { it[index] = merged }
        } else {
            current + message
        }
    }

    private fun upsertPart(
        current: List<ChatMessage>,
        sessionId: String,
        messageId: String,
        part: dev.edgecode.core.model.Part,
    ): List<ChatMessage> {
        val index = current.indexOfFirst { it.id == messageId }
        if (index < 0) {
            // A part can arrive before we have seen its message; create a shell.
            val shell = ChatMessage.Assistant(
                id = messageId,
                createdAt = System.currentTimeMillis(),
                parts = listOf(part),
                streaming = true,
            )
            return current + shell
        }
        val message = current[index]
        return when (message) {
            is ChatMessage.Assistant -> {
                val parts = message.parts.toMutableList()
                val partIndex = parts.indexOfFirst { it.id == part.id }
                if (partIndex >= 0) parts[partIndex] = part else parts.add(part)
                current.toMutableList().also { it[index] = message.copy(parts = parts) }
            }

            // A user message has no part list in the model, so its body is
            // folded into `text` and `attachments` as the parts arrive.
            is ChatMessage.User -> {
                val updated = when (part) {
                    is Part.Text -> message.copy(text = mergeUserText(message.text, part.text))
                    is Part.Attachment -> message.copy(
                        attachments = if (message.attachments.any { it.id == part.attachment.id }) {
                            message.attachments
                        } else {
                            message.attachments + part.attachment
                        },
                    )
                    else -> message
                }
                current.toMutableList().also { it[index] = updated }
            }
        }
    }

    /**
     * Appends a slice of text to the part being written.
     *
     * The part may not have arrived yet — a delta can precede the frame that
     * creates it — in which case the part is started from the delta alone. A
     * later whole-part frame replaces the part outright, so any drift between
     * the deltas and the server's own accumulation corrects itself.
     */
    private fun appendDelta(
        current: List<ChatMessage>,
        event: ServerEvent.PartDelta,
    ): List<ChatMessage> {
        if (event.field != "text") return current

        val index = current.indexOfFirst { it.id == event.messageId }
        if (index < 0) return current
        val message = current[index]
        if (message !is ChatMessage.Assistant) return current

        val parts = message.parts.toMutableList()
        val partIndex = parts.indexOfFirst { it.id == event.partId }

        if (partIndex < 0) {
            parts.add(Part.Text(id = event.partId, text = event.delta, streaming = true))
        } else {
            parts[partIndex] = when (val part = parts[partIndex]) {
                is Part.Text -> part.copy(text = part.text + event.delta, streaming = true)
                is Part.Reasoning -> part.copy(text = part.text + event.delta, streaming = true)
                else -> return current
            }
        }

        return current.toMutableList().also {
            it[index] = message.copy(parts = parts, streaming = true)
        }
    }

    /**
     * The server sends a user message's body as its own part, and re-sends the
     * frame on every update. Identical text is a re-emit and must not double up.
     */
    private fun mergeUserText(existing: String, incoming: String): String = when {
        incoming.isBlank() -> existing
        existing.isBlank() -> incoming
        existing == incoming -> existing
        existing.contains(incoming) -> existing
        else -> "$existing\n$incoming"
    }

    fun reduceSessions(
        current: List<SessionSummary>,
        event: ServerEvent,
    ): List<SessionSummary> = when (event) {
        is ServerEvent.SessionUpserted -> {
            val index = current.indexOfFirst { it.id == event.session.id }
            if (index >= 0) {
                current.toMutableList().also { it[index] = event.session }
            } else {
                listOf(event.session) + current
            }
        }
        is ServerEvent.SessionRemoved -> current.filterNot { it.id == event.sessionId }
        is ServerEvent.SessionStatusChanged -> current.map { session ->
            if (session.id == event.sessionId) session.copy(status = event.status) else session
        }
        else -> current
    }

    fun reducePermissions(
        current: List<PermissionRequest>,
        event: ServerEvent,
    ): List<PermissionRequest> = when (event) {
        is ServerEvent.PermissionRequested -> current + event.request
        is ServerEvent.PermissionResolved -> current.filterNot { it.id == event.requestId }
        else -> current
    }

    fun reduceQuestions(
        current: List<QuestionRequest>,
        event: ServerEvent,
    ): List<QuestionRequest> = when (event) {
        is ServerEvent.QuestionAsked -> {
            val index = current.indexOfFirst { it.id == event.request.id }
            if (index >= 0) {
                current.toMutableList().also { it[index] = event.request }
            } else {
                current + event.request
            }
        }
        is ServerEvent.QuestionReplied ->
            current.filterNot { it.id == event.requestId }
        is ServerEvent.QuestionRejected ->
            current.filterNot { it.id == event.requestId }
        else -> current
    }
}
