package dev.edgecode.data.net

import dev.edgecode.core.model.AgentInfo
import dev.edgecode.core.model.ApiVersion
import dev.edgecode.core.model.Attachment
import dev.edgecode.core.model.ChatMessage
import dev.edgecode.core.model.ModelInfo
import dev.edgecode.core.model.ModelRef
import dev.edgecode.core.model.PermissionDecision
import dev.edgecode.core.model.ProjectRef
import dev.edgecode.core.model.ProviderInfo
import dev.edgecode.core.model.QuestionRequest
import dev.edgecode.core.model.ServerEvent
import dev.edgecode.core.model.ServerInfo
import dev.edgecode.core.model.SessionSummary
import dev.edgecode.core.model.SessionStatus
import dev.edgecode.core.model.SlashCommand
import dev.edgecode.core.util.asArray
import dev.edgecode.core.util.asObject
import dev.edgecode.core.util.asString
import dev.edgecode.core.util.boolOr
import dev.edgecode.core.util.encode
import dev.edgecode.core.util.obj
import dev.edgecode.core.util.parseJson
import dev.edgecode.core.util.string
import dev.edgecode.core.util.stringOr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

/**
 * An [dev.edgecode.data.OpenCodeApi] backed by a real OpenCode v1 server.
 *
 * All the shape knowledge lives in [OpenCodeWire]; this class is only routing.
 * The endpoints and bodies were read from the server's own `/doc`.
 */
class HttpOpenCodeApi(
    private val transport: Transport,
) : dev.edgecode.data.OpenCodeApi {

    override val version: ApiVersion = ApiVersion.V1

    private suspend fun get(path: String, query: Map<String, String> = emptyMap()) =
        parseJson(transport.request("GET", path, query).body)

    private suspend fun send(method: String, path: String, body: String? = null) =
        parseJson(transport.request(method, path, body = body).body)

    override suspend fun health(): ServerInfo {
        val json = get("/global/health")
        return ServerInfo(
            version = json.stringOr("version", ""),
            healthy = json.boolOr("healthy", false),
            api = ApiVersion.V1,
        )
    }

    override suspend fun projects(): List<ProjectRef> =
        get("/project").let { json ->
            (json.asArray ?: emptyList()).map { OpenCodeWire.project(it) }
        }

    override suspend fun sessions(): List<SessionSummary> =
        get("/session").let { json ->
            (json.asArray ?: emptyList())
                .map { OpenCodeWire.session(it) }
                .sortedByDescending { it.updatedAt }
        }

    override suspend fun sessionStatuses(): Map<String, SessionStatus> =
        OpenCodeWire.statuses(get("/session/status"))

    override suspend fun createSession(directory: String?, title: String?): SessionSummary {
        val body = dev.edgecode.core.util.JsonValue.Obj(
            buildMap {
                directory?.let { put("directory", dev.edgecode.core.util.JsonValue.Str(it)) }
                title?.let { put("title", dev.edgecode.core.util.JsonValue.Str(it)) }
            },
        ).encode()
        return OpenCodeWire.session(send("POST", "/session", body))
    }

    override suspend fun renameSession(sessionId: String, title: String) {
        val body = dev.edgecode.core.util.JsonValue.Obj(
            mapOf("title" to dev.edgecode.core.util.JsonValue.Str(title)),
        ).encode()
        transport.request("PATCH", "/session/$sessionId", body = body)
    }

    override suspend fun deleteSession(sessionId: String) {
        transport.request("DELETE", "/session/$sessionId")
    }

    override suspend fun messages(sessionId: String): List<ChatMessage> =
        get("/session/$sessionId/message").let { json ->
            (json.asArray ?: emptyList()).mapNotNull { OpenCodeWire.message(it) }
        }

    /**
     * `prompt_async` returns as soon as the prompt is accepted; the reply arrives
     * over the event stream. The blocking `POST /message` would make the UI wait
     * out an entire turn before rendering anything.
     */
    override suspend fun sendPrompt(
        sessionId: String,
        text: String,
        attachments: List<Attachment>,
        agent: String?,
        model: ModelRef?,
        messageId: String?,
    ): String {
        val id = messageId ?: OpenCodeWire.newMessageId()
        val body = OpenCodeWire.promptBody(text, id, model, agent, attachments)
        transport.request("POST", "/session/$sessionId/prompt_async", body = body)
        return id
    }

    override suspend fun runCommand(sessionId: String, command: String, arguments: String) {
        val body = OpenCodeWire.commandBody(command, arguments, null, null)
        transport.request("POST", "/session/$sessionId/command", body = body)
    }

    override suspend fun runShell(sessionId: String, command: String) {
        val body = OpenCodeWire.shellBody(command, null, null)
        transport.request("POST", "/session/$sessionId/shell", body = body)
    }

    override suspend fun abort(sessionId: String) {
        transport.request("POST", "/session/$sessionId/abort")
    }

    override suspend fun respondPermission(
        sessionId: String,
        requestId: String,
        decision: PermissionDecision,
        remember: Boolean,
    ) {
        val reply = when (decision) {
            PermissionDecision.Once -> "once"
            PermissionDecision.Always -> "always"
            PermissionDecision.Reject -> "reject"
        }
        transport.request("POST", "/permission/$requestId/reply", body = OpenCodeWire.replyBody(reply))
    }

    override suspend fun questions(): List<QuestionRequest> =
        (get("/question").asArray ?: emptyList()).map { OpenCodeWire.questionRequest(it) }

    override suspend fun replyQuestion(requestId: String, answers: List<List<String>>) {
        transport.request(
            "POST",
            "/question/$requestId/reply",
            body = OpenCodeWire.questionReplyBody(answers),
        )
    }

    override suspend fun rejectQuestion(requestId: String) {
        transport.request("POST", "/question/$requestId/reject")
    }

    override suspend fun commands(): List<SlashCommand> = OpenCodeWire.commands(get("/command"))

    override suspend fun agents(): List<AgentInfo> = OpenCodeWire.agents(get("/agent"))

    override suspend fun providers(): List<ProviderInfo> = OpenCodeWire.providers(get("/provider"))

    override suspend fun models(): List<ModelInfo> {
        val json = get("/provider")
        val defaults = json.obj("default")?.asObject.orEmpty()
            .mapNotNull { (provider, value) ->
                value.asString?.let { provider to it }
            }
            .toMap()
        return OpenCodeWire.models(json, defaults)
    }

    /**
     * `GET /event` is `/api/event`'s v1 counterpart and never completes. The
     * stream is documented as lossy, so a drop is reported as
     * [ServerEvent.Disconnected] and then reconnected with backoff rather than
     * ending the session; the repository reconciles over REST on every
     * reconnect.
     */
    override fun events(): Flow<ServerEvent> = flow {
        var attempt = 0
        while (currentCoroutineContext().isActive) {
            try {
                transport.stream("/event").collect { payload ->
                    attempt = 0
                    val json = runCatching { parseJson(payload) }.getOrNull() ?: return@collect
                    OpenCodeWire.event(json)?.let { emit(it) }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (t: Throwable) {
                emit(ServerEvent.Disconnected(t.message ?: "Event stream closed"))
            }
            delay(backoffMillis(attempt))
            attempt += 1
        }
    }

    private fun backoffMillis(attempt: Int): Long {
        val steps = longArrayOf(500, 1_000, 2_000, 4_000, 8_000)
        return steps[attempt.coerceIn(0, steps.size - 1)]
    }

    override suspend fun close() {
        transport.close()
    }
}
