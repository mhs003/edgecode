package dev.edgecode.core.model

/**
 * Domain model.
 *
 * These types are deliberately close to the OpenCode v1 wire format but not
 * identical to it: the API layer maps into them, and the UI reads only these.
 * That is the seam that lets a v2 API implementation arrive later without the
 * interface changing.
 */

// --- Connection ------------------------------------------------------------

enum class ApiVersion { V1, V2, Demo }

data class ServerProfile(
    val id: String,
    val name: String,
    val url: String,
    val username: String? = null,
    /**
     * Held in memory only. The real implementation must move this to
     * encrypted storage rather than plain preferences.
     */
    val password: String? = null,
)

data class ServerInfo(
    val version: String,
    val healthy: Boolean,
    val api: ApiVersion,
) {
    val label: String get() = if (api == ApiVersion.Demo) "Demo" else "v$version"
}

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data class Connecting(val profile: ServerProfile) : ConnectionState
    data class Connected(
        val profile: ServerProfile,
        val info: ServerInfo,
    ) : ConnectionState
    data class Failed(val profile: ServerProfile, val message: String) : ConnectionState

    val isConnected: Boolean get() = this is Connected
}

// --- Project / session -----------------------------------------------------

data class ProjectRef(
    val id: String,
    val directory: String,
    val name: String,
    val isCurrent: Boolean = false,
)

enum class SessionStatus { Idle, Working, Error }

data class TokenUsage(
    val input: Int = 0,
    val output: Int = 0,
    val reasoning: Int = 0,
    val cacheRead: Int = 0,
    val cacheWrite: Int = 0,
) {
    val total: Int get() = input + output
    val contextUsed: Int get() = input + output + cacheRead + cacheWrite

    operator fun plus(other: TokenUsage) = TokenUsage(
        input = input + other.input,
        output = output + other.output,
        reasoning = reasoning + other.reasoning,
        cacheRead = cacheRead + other.cacheRead,
        cacheWrite = cacheWrite + other.cacheWrite,
    )
}

data class SessionSummary(
    val id: String,
    val title: String,
    val directory: String,
    val createdAt: Long,
    val updatedAt: Long,
    val status: SessionStatus = SessionStatus.Idle,
    val cost: Double = 0.0,
    val tokens: TokenUsage = TokenUsage(),
    val parentId: String? = null,
    val additions: Int = 0,
    val deletions: Int = 0,
    val changedFiles: Int = 0,
    /** Model the session last ran with, shown as context in the list. */
    val modelLabel: String? = null,
)

// --- Attachments -----------------------------------------------------------

enum class AttachmentKind { Image, Document, Text, Other }

data class Attachment(
    val id: String,
    val name: String,
    val mime: String,
    val sizeBytes: Long,
    val kind: AttachmentKind,
    /**
     * Local content URI while the file is still just picked. The API layer is
     * responsible for resolving this into the base64 data URI the prompt
     * payload requires.
     */
    val uri: String? = null,
)

// --- Tools -----------------------------------------------------------------

enum class ToolStatus { Pending, Running, Completed, Failed }

/**
 * Tool call state, flattened to what the transcript actually renders: a
 * one-line summary that is always visible, plus detail revealed on expansion.
 */
data class ToolState(
    val status: ToolStatus = ToolStatus.Pending,
    /** Always-visible summary: `Grep "home.*button"`. */
    val title: String? = null,
    /** Secondary hint: `18 matches`, `142 lines`, `exit 0`. */
    val subtitle: String? = null,
    val input: String? = null,
    val output: String? = null,
    val meta: List<Pair<String, String>> = emptyList(),
    val durationMs: Long? = null,
    /**
     * Session this tool is bound to. A `task` call spawns a child session and
     * names it in its metadata; that id is the only way back to the subagent's
     * transcript once the call settles.
     */
    val subagentId: String? = null,
    /**
     * A `question` call carries the questions it asked in its input and the
     * chosen labels in its metadata. Keeping them structured lets the transcript
     * show the exchange where it happened instead of a raw JSON dump.
     */
    val questions: List<QuestionInfo> = emptyList(),
    val answers: List<List<String>> = emptyList(),
)

// --- Diffs -----------------------------------------------------------------

data class DiffFile(
    val path: String,
    val additions: Int,
    val deletions: Int,
    val hunks: List<DiffLine> = emptyList(),
)

data class DiffLine(val kind: DiffLineKind, val text: String)

enum class DiffLineKind { Context, Added, Removed, Hunk }

// --- Parts -----------------------------------------------------------------

sealed interface Part {
    val id: String

    data class Text(
        override val id: String,
        val text: String,
        val streaming: Boolean = false,
    ) : Part

    data class Reasoning(
        override val id: String,
        val text: String,
        val seconds: Int? = null,
        val streaming: Boolean = false,
    ) : Part

    data class Tool(
        override val id: String,
        val toolId: String,
        val name: String,
        val state: ToolState,
    ) : Part

    data class Attachment(
        override val id: String,
        val attachment: dev.edgecode.core.model.Attachment,
    ) : Part

    data class Diff(
        override val id: String,
        val files: List<DiffFile>,
    ) : Part

    /** System-level notice: compaction, interruption, retry. */
    data class Note(
        override val id: String,
        val text: String,
    ) : Part
}

// --- Messages --------------------------------------------------------------

sealed interface ChatMessage {
    val id: String
    val createdAt: Long

    data class User(
        override val id: String,
        override val createdAt: Long,
        val text: String,
        val attachments: List<Attachment> = emptyList(),
    ) : ChatMessage

    data class Assistant(
        override val id: String,
        override val createdAt: Long,
        val parts: List<Part> = emptyList(),
        val agent: String? = null,
        val model: ModelRef? = null,
        val streaming: Boolean = false,
        val error: String? = null,
        val cost: Double = 0.0,
        val tokens: TokenUsage = TokenUsage(),
        val completedAt: Long? = null,
    ) : ChatMessage
}

// --- Permissions -----------------------------------------------------------

enum class PermissionKind { Edit, Bash, Read, Network, Other }

data class PermissionRequest(
    val id: String,
    val sessionId: String,
    val kind: PermissionKind,
    /** `Edit src/main/…/Theme.kt` — the primary line. */
    val title: String,
    val detail: String? = null,
    val resources: List<String> = emptyList(),
    val diff: List<DiffFile> = emptyList(),
)

enum class PermissionDecision { Once, Always, Reject }

// --- Questions -------------------------------------------------------------

data class QuestionOption(
    val label: String,
    val description: String = "",
)

/**
 * One question the agent wants answered.
 *
 * [header] is the agent's own short label for the question; [question] is the
 * full text. [multiple] switches the choice rows between radio and checkbox
 * behaviour. [custom] adds a free-text answer alongside (or instead of) the
 * offered options.
 */
data class QuestionInfo(
    val question: String,
    val header: String,
    val options: List<QuestionOption> = emptyList(),
    val multiple: Boolean = false,
    val custom: Boolean = false,
)

/**
 * A blocking request for one or more answers.
 *
 * [messageId] and [callId] link the request to the tool call that asked it,
 * when the server supplies them.
 */
data class QuestionRequest(
    val id: String,
    val sessionId: String,
    val questions: List<QuestionInfo>,
    val messageId: String? = null,
    val callId: String? = null,
)

// --- Providers, models, agents --------------------------------------------

data class ProviderInfo(
    val id: String,
    val name: String,
    val connected: Boolean = true,
)

data class ModelCapabilities(
    val tools: Boolean = true,
    val reasoning: Boolean = false,
    val vision: Boolean = false,
    val attachments: Boolean = false,
)

data class ModelInfo(
    val id: String,
    val providerId: String,
    val providerName: String,
    val name: String,
    val contextLimit: Int = 0,
    val outputLimit: Int = 0,
    val inputCostPerM: Double = 0.0,
    val outputCostPerM: Double = 0.0,
    val variants: List<String> = emptyList(),
    val capabilities: ModelCapabilities = ModelCapabilities(),
    val isDefault: Boolean = false,
    val deprecated: Boolean = false,
)

data class ModelRef(
    val providerId: String,
    val modelId: String,
    val variant: String? = null,
) {
    val key: String get() = "$providerId/$modelId"

    companion object {
        /** Parses a stored `provider/model` key. Model ids may contain slashes. */
        fun parse(key: String): ModelRef? {
            val cut = key.indexOf('/')
            if (cut <= 0 || cut == key.lastIndex) return null
            return ModelRef(key.substring(0, cut), key.substring(cut + 1))
        }
    }
}

/**
 * Which model each chat is using, and where that choice came from.
 *
 * Three layers, because they answer three different questions:
 * - [pending] is what the user picked in the picker but has not sent with. It is
 *   what the composer shows, and it is deliberately *not* yet "this chat's
 *   model" — picking a model and then closing the screen should leave no trace.
 * - [committed] is the model each chat last actually sent a turn with.
 * - [recent] is the models last used anywhere, most recent first. A chat with no
 *   history of its own starts here, which is what makes a new chat open on the
 *   model the user has been working with.
 *
 * This also keeps chats from leaking into one another: the previous global
 * selection meant picking a model in one chat showed it in every other.
 */
data class ModelSelection(
    val pending: Map<String, ModelRef> = emptyMap(),
    val committed: Map<String, ModelRef> = emptyMap(),
    val recent: List<ModelRef> = emptyList(),
) {
    fun forSession(sessionId: String): ModelRef? =
        pending[sessionId] ?: committed[sessionId] ?: recent.firstOrNull()

    /** Records a choice for display, without making it the chat's model yet. */
    fun choosing(sessionId: String, ref: ModelRef): ModelSelection =
        copy(pending = pending + (sessionId to ref))

    /**
     * Commits the model the chat is showing, because a turn was just sent with
     * it. This is the only place a chat's model or the recent list changes.
     */
    fun sent(sessionId: String, ref: ModelRef): ModelSelection = copy(
        pending = pending - sessionId,
        committed = committed + (sessionId to ref),
        recent = (listOf(ref) + recent.filterNot { it.key == ref.key }).take(RecentLimit),
    )

    companion object {
        /** How many models the picker offers under "Recently used". */
        const val RecentLimit = 6
    }
}

data class AgentInfo(
    val id: String,
    val name: String,
    val description: String? = null,
    val builtIn: Boolean = false,
    val isPrimary: Boolean = true,
)

data class SlashCommand(
    val name: String,
    val description: String? = null,
    /** "server" for commands reported by the server, "client" for built-ins. */
    val source: String = "server",
)

// --- Events ----------------------------------------------------------------

/**
 * The server pushes these over SSE. The UI reduces them into state through
 * [dev.edgecode.data.ChatReducer]; because the v1 stream is best-effort, every
 * reconnect also triggers a REST reconcile rather than trusting the stream.
 */
sealed interface ServerEvent {
    data class Connected(val version: String) : ServerEvent
    data class SessionUpserted(val session: SessionSummary) : ServerEvent
    data class SessionRemoved(val sessionId: String) : ServerEvent
    data class MessageUpserted(val sessionId: String, val message: ChatMessage) : ServerEvent
    data class PartUpserted(val sessionId: String, val messageId: String, val part: Part) : ServerEvent

    /**
     * A slice of a part that is still being written.
     *
     * Replies do not arrive as a series of whole parts: the server sends the
     * part once, empty, and then every subsequent character as a delta. Without
     * this the transcript only updates when the part is finally sent whole,
     * which reads as no streaming at all.
     */
    data class PartDelta(
        val sessionId: String,
        val messageId: String,
        val partId: String,
        val field: String,
        val delta: String,
    ) : ServerEvent

    data class MessageRemoved(val sessionId: String, val messageId: String) : ServerEvent
    data class PermissionRequested(val request: PermissionRequest) : ServerEvent
    data class PermissionResolved(val requestId: String) : ServerEvent

    /** The agent is blocked until this question is answered or rejected. */
    data class QuestionAsked(val request: QuestionRequest) : ServerEvent

    /** One answer per question, in the order the questions were asked. */
    data class QuestionReplied(
        val sessionId: String,
        val requestId: String,
        val answers: List<List<String>> = emptyList(),
    ) : ServerEvent

    data class QuestionRejected(val sessionId: String, val requestId: String) : ServerEvent

    data class SessionIdle(val sessionId: String) : ServerEvent
    data class SessionFailed(val sessionId: String, val message: String) : ServerEvent

    /**
     * `session.status` from the server. A session carries no status field in its
     * own payload, so this is the only live signal that a session is busy —
     * including one the user is not looking at.
     */
    data class SessionStatusChanged(
        val sessionId: String,
        val status: SessionStatus,
    ) : ServerEvent
    data class Disconnected(val reason: String) : ServerEvent
}
