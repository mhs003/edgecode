package dev.edgecode.data.net

import dev.edgecode.core.model.AgentInfo
import dev.edgecode.core.model.Attachment
import dev.edgecode.core.model.AttachmentKind
import dev.edgecode.core.model.ChatMessage
import dev.edgecode.core.model.DiffFile
import dev.edgecode.core.model.ModelCapabilities
import dev.edgecode.core.model.ModelInfo
import dev.edgecode.core.model.ModelRef
import dev.edgecode.core.model.Part
import dev.edgecode.core.model.PermissionKind
import dev.edgecode.core.model.PermissionRequest
import dev.edgecode.core.model.ProjectRef
import dev.edgecode.core.model.ProviderInfo
import dev.edgecode.core.model.ServerEvent
import dev.edgecode.core.model.SessionStatus
import dev.edgecode.core.model.SessionSummary
import dev.edgecode.core.model.SlashCommand
import dev.edgecode.core.model.TokenUsage
import dev.edgecode.core.model.ToolState
import dev.edgecode.core.model.ToolStatus
import dev.edgecode.core.util.JsonValue
import dev.edgecode.core.util.arr
import dev.edgecode.core.util.asArray
import dev.edgecode.core.util.asObject
import dev.edgecode.core.util.boolOr
import dev.edgecode.core.util.doubleOr
import dev.edgecode.core.util.encode
import dev.edgecode.core.util.intOr
import dev.edgecode.core.util.long
import dev.edgecode.core.util.longOr
import dev.edgecode.core.util.obj
import dev.edgecode.core.util.string
import dev.edgecode.core.util.stringOr
import dev.edgecode.core.util.strings

/**
 * Maps OpenCode v1 JSON onto [dev.edgecode.core.model] types.
 *
 * Deliberately pure and free of any transport: every rule here was read off a
 * live v1.19.3 server's own `/doc`, and the fixtures these functions are tested
 * against are captured responses from that server. Both directions of the
 * mapping are covered by `OpenCodeWireTest` on the JVM.
 *
 * The guiding rule is that an unknown or missing field degrades to a sensible
 * default rather than throwing. The server adds union variants between minor
 * versions, and a client that crashes on an unrecognised part is worse than one
 * that renders it as a plain note.
 */
object OpenCodeWire {

    // ---- sessions -------------------------------------------------------

    fun session(json: JsonValue): SessionSummary {
        val time = json.obj("time")
        val tokens = json.obj("tokens")
        val summary = json.obj("summary")
        val model = json.obj("model")

        return SessionSummary(
            id = json.stringOr("id", ""),
            title = json.stringOr("title", "Untitled"),
            directory = json.stringOr("directory", ""),
            createdAt = time?.longOr("created", 0L) ?: 0L,
            updatedAt = time?.longOr("updated", 0L) ?: 0L,
            status = statusOf(json),
            cost = json.doubleOr("cost", 0.0),
            tokens = tokenUsage(tokens),
            parentId = json.string("parentID"),
            additions = summary?.intOr("additions", 0) ?: 0,
            deletions = summary?.intOr("deletions", 0) ?: 0,
            changedFiles = summary?.intOr("files", 0) ?: 0,
            modelLabel = model?.let { modelLabel(it) },
        )
    }

    /**
     * A session carries no status field. `time.compacting` is the only working
     * signal in the payload itself; the authoritative source is `session.status`
     * over the event stream, which the repository applies on top of this.
     */
    private fun statusOf(json: JsonValue): SessionStatus {
        val compacting = json.obj("time")?.long("compacting")
        return if (compacting != null && compacting > 0) SessionStatus.Working else SessionStatus.Idle
    }

    private fun modelLabel(model: JsonValue): String? {
        val provider = model.string("providerID")
        val id = model.string("id") ?: model.string("modelID")
        return when {
            provider != null && id != null -> "$provider/$id"
            else -> id
        }
    }

    fun tokenUsage(json: JsonValue?): TokenUsage {
        if (json == null) return TokenUsage()
        val cache = json.obj("cache")
        return TokenUsage(
            input = json.intOr("input", 0),
            output = json.intOr("output", 0),
            reasoning = json.intOr("reasoning", 0),
            cacheRead = cache?.intOr("read", 0) ?: 0,
            cacheWrite = cache?.intOr("write", 0) ?: 0,
        )
    }

    // ---- messages -------------------------------------------------------

    /**
     * `GET /session/{id}/message` returns `[{info, parts}]` — the message and its
     * parts are siblings, and the message is discriminated by `info.role`.
     */
    fun message(json: JsonValue): ChatMessage? {
        val info = json.obj("info") ?: return null
        val id = info.stringOr("id", "")
        if (id.isEmpty()) return null
        val parts = json.arr("parts")

        return when (info.string("role")) {
            "user" -> ChatMessage.User(
                id = id,
                createdAt = info.obj("time")?.longOr("created", 0L) ?: 0L,
                text = parts.mapNotNull { textPartText(it) }.joinToString("\n").trim(),
                attachments = parts.mapNotNull { attachmentPart(it) },
            )

            "assistant" -> {
                val rendered = parts.mapNotNull { part(it) }
                val created = info.obj("time")?.longOr("created", 0L) ?: 0L
                val completed = info.obj("time")?.long("completed")
                ChatMessage.Assistant(
                    id = id,
                    createdAt = created,
                    parts = rendered,
                    agent = info.string("agent") ?: info.string("mode"),
                    model = modelRef(info),
                    // A turn is live if the server has not stamped `completed`.
                    // This keeps REST authoritative when the event stream drops.
                    streaming = completed == null && info.obj("error") == null,
                    error = errorMessage(info.obj("error")),
                    cost = info.doubleOr("cost", 0.0),
                    tokens = tokenUsage(info.obj("tokens")),
                    completedAt = completed,
                )
            }

            else -> null
        }
    }

    /**
     * A model reference appears in two shapes: a user message carries
     * `model: {providerID, modelID, variant}`, while an assistant message carries
     * flat `providerID` and `modelID` on the message itself. Both must map, or
     * the model chip silently disappears for one side of the transcript.
     */
    private fun modelRef(json: JsonValue): ModelRef? {
        val nested = json.obj("model")
        val provider = nested?.string("providerID") ?: json.string("providerID")
        val id = nested?.string("modelID") ?: json.string("modelID")
        if (provider == null || id == null) return null
        return ModelRef(provider, id, nested?.string("variant"))
    }

    private fun textPartText(json: JsonValue): String? =
        if (json.string("type") == "text") json.string("text") else null

    private fun attachmentPart(json: JsonValue): Attachment? =
        if (json.string("type") == "file") attachment(json) else null

    fun attachment(json: JsonValue): Attachment {
        val mime = json.stringOr("mime", "application/octet-stream")
        val name = json.stringOr("filename", "attachment")
        return Attachment(
            id = json.stringOr("id", name),
            name = name,
            mime = mime,
            sizeBytes = 0L,
            kind = attachmentKind(mime),
            uri = json.string("url"),
        )
    }

    fun attachmentKind(mime: String): AttachmentKind = when {
        mime.startsWith("image/") -> AttachmentKind.Image
        mime.startsWith("text/") -> AttachmentKind.Text
        mime.contains("pdf") || mime.contains("word") || mime.contains("document") ->
            AttachmentKind.Document
        else -> AttachmentKind.Other
    }

    // ---- parts ----------------------------------------------------------

    /**
     * Unknown part types become [Part.Note] rather than being dropped, so a
     * variant this build predates is visible as a line of text instead of an
     * unexplained gap.
     */
    fun part(json: JsonValue): Part? {
        val id = json.stringOr("id", "")
        if (id.isEmpty()) return null

        return when (val type = json.string("type")) {
            "text" -> Part.Text(
                id = id,
                text = json.stringOr("text", ""),
                // `time.end` absent means the server is still writing this part.
                streaming = json.obj("time")?.long("end") == null,
            )

            "reasoning" -> {
                val time = json.obj("time")
                val start = time?.long("start")
                val end = time?.long("end")
                Part.Reasoning(
                    id = id,
                    text = json.stringOr("text", ""),
                    seconds = if (start != null && end != null) {
                        ((end - start) / 1000L).toInt().coerceAtLeast(0)
                    } else {
                        null
                    },
                    streaming = end == null,
                )
            }

            "tool" -> Part.Tool(
                id = id,
                toolId = json.stringOr("callID", id),
                name = json.stringOr("tool", "tool"),
                state = toolState(json.obj("state")),
            )

            "file" -> Part.Attachment(id = id, attachment = attachment(json))

            // Step boundaries are structure, not content. The server emits a
            // `step-start`/`step-finish` pair around every model iteration —
            // including the reasoning pass — so rendering them put a hairline
            // rule above and below every ordinary reply.
            "step-start", "step-finish" -> null

            "patch", "snapshot" -> Part.Diff(id = id, files = emptyList())

            "retry" -> Part.Note(
                id = id,
                text = "Retrying after " + (errorMessage(json.obj("error")) ?: "an error"),
            )

            "compaction" -> Part.Note(
                id = id,
                text = if (json.boolOr("auto", false)) {
                    "Context compacted automatically"
                } else {
                    "Context compacted"
                },
            )

            "subtask" -> Part.Note(
                id = id,
                text = "Subtask: " + json.stringOr("description", json.stringOr("prompt", "…")),
            )

            "agent" -> Part.Note(id = id, text = "Agent: " + json.stringOr("name", ""))

            null -> null

            else -> Part.Note(id = id, text = "Unsupported part: $type")
        }
    }

    fun toolState(json: JsonValue?): ToolState {
        if (json == null) return ToolState()

        val time = json.obj("time")
        val start = time?.long("start")
        val end = time?.long("end")
        val duration = if (start != null && end != null) end - start else null

        val input = json.obj("input")?.encode()
        val title = json.string("title")

        return when (json.string("status")) {
            "pending" -> ToolState(status = ToolStatus.Pending)

            "running" -> ToolState(
                status = ToolStatus.Running,
                title = title,
                input = input,
            )

            "completed" -> ToolState(
                status = ToolStatus.Completed,
                title = title,
                input = input,
                output = json.string("output"),
                durationMs = duration,
            )

            "error" -> ToolState(
                status = ToolStatus.Failed,
                title = title,
                input = input,
                output = json.string("error"),
                durationMs = duration,
            )

            else -> ToolState(status = ToolStatus.Pending)
        }
    }

    // ---- permissions ----------------------------------------------------

    fun permission(json: JsonValue): PermissionRequest {
        val permission = json.stringOr("permission", "")
        val patterns = json.strings("patterns")
        val metadata = json.obj("metadata")

        return PermissionRequest(
            id = json.stringOr("id", ""),
            sessionId = json.stringOr("sessionID", ""),
            kind = permissionKind(permission),
            title = permissionTitle(permission, patterns.firstOrNull()),
            detail = metadata?.string("description") ?: metadata?.string("command"),
            resources = patterns,
        )
    }

    fun permissionKind(permission: String): PermissionKind = when {
        permission.startsWith("edit") || permission == "write" -> PermissionKind.Edit
        permission.startsWith("bash") || permission == "shell" -> PermissionKind.Bash
        permission.startsWith("read") -> PermissionKind.Read
        permission.contains("fetch") || permission.contains("network") || permission.contains("web") ->
            PermissionKind.Network
        else -> PermissionKind.Other
    }

    private fun permissionTitle(permission: String, pattern: String?): String = when {
        permission.isEmpty() -> "Permission requested"
        pattern == null -> permission.replaceFirstChar { it.uppercase() }
        else -> permission.replaceFirstChar { it.uppercase() } + " " + pattern
    }

    // ---- errors ---------------------------------------------------------

    /** The error union carries its text at `data.message` for every variant. */
    fun errorMessage(json: JsonValue?): String? {
        if (json == null) return null
        val data = json.obj("data")
        return data?.string("message")
            ?: json.string("message")
            ?: json.string("name")
    }

    // ---- catalogue ------------------------------------------------------

    fun agents(json: JsonValue): List<AgentInfo> =
        (json.asArray ?: emptyList())
            .filterNot { it.boolOr("hidden", false) }
            .map { agent(it) }
            .filter { it.id.isNotEmpty() }

    fun commands(json: JsonValue): List<SlashCommand> =
        (json.asArray ?: emptyList())
            .map { command(it) }
            .filter { it.name.isNotEmpty() }

    fun agent(json: JsonValue): AgentInfo {
        val name = json.stringOr("name", "")
        val model = json.obj("model")
        return AgentInfo(
            id = name,
            name = name,
            description = json.string("description"),
            builtIn = json.boolOr("native", false),
            isPrimary = json.string("mode") != "subagent",
        )
    }

    fun command(json: JsonValue): SlashCommand = SlashCommand(
        name = json.stringOr("name", ""),
        description = json.string("description"),
        source = json.stringOr("source", "server"),
    )

    /**
     * Providers arrive as `{all: [...], default: {...}, connected: [...]}` with
     * `models` a map keyed by model id, so the key is redundant with `id`.
     */
    fun providers(json: JsonValue): List<ProviderInfo> {
        val connected = json.strings("connected").toSet()
        return json.arr("all").mapNotNull { provider ->
            val id = provider.string("id") ?: return@mapNotNull null
            ProviderInfo(
                id = id,
                name = provider.stringOr("name", id),
                connected = connected.isEmpty() || id in connected,
            )
        }
    }

    fun models(json: JsonValue, default: Map<String, String> = emptyMap()): List<ModelInfo> {
        val out = mutableListOf<ModelInfo>()
        for (provider in json.arr("all")) {
            val providerId = provider.string("id") ?: continue
            val providerName = provider.stringOr("name", providerId)
            val models = provider.obj("models")?.asObject ?: continue
            val defaultId = default[providerId]

            for ((key, model) in models) {
                val id = model.string("id") ?: key
                val capabilities = model.obj("capabilities")
                val limit = model.obj("limit")
                val cost = model.obj("cost")
                val status = model.string("status")

                out += ModelInfo(
                    id = id,
                    providerId = providerId,
                    providerName = providerName,
                    name = model.stringOr("name", id),
                    contextLimit = limit?.intOr("context", 0) ?: 0,
                    outputLimit = limit?.intOr("output", 0) ?: 0,
                    inputCostPerM = cost?.doubleOr("input", 0.0) ?: 0.0,
                    outputCostPerM = cost?.doubleOr("output", 0.0) ?: 0.0,
                    variants = model.obj("variants")?.asObject?.keys?.toList() ?: emptyList(),
                    capabilities = ModelCapabilities(
                        tools = capabilities?.boolOr("toolcall", true) ?: true,
                        reasoning = capabilities?.boolOr("reasoning", false) ?: false,
                        vision = capabilities?.obj("input")?.boolOr("image", false) ?: false,
                        attachments = capabilities?.boolOr("attachment", false) ?: false,
                    ),
                    isDefault = defaultId != null && defaultId == id,
                    deprecated = status != null && status != "active",
                )
            }
        }
        return out
    }

    fun project(json: JsonValue): ProjectRef = ProjectRef(
        id = json.stringOr("id", ""),
        directory = json.stringOr("worktree", ""),
        name = json.stringOr("name", json.stringOr("worktree", "")),
        isCurrent = false,
    )

    // ---- events ---------------------------------------------------------

    /**
     * The SSE envelope is `{id, type, properties}` — the payload sits under
     * `properties`, not `data`. Unmapped types return null and are dropped: the
     * stream is documented as lossy and carries a great deal the app never
     * renders.
     */
    fun event(json: JsonValue): ServerEvent? {
        val properties = json.obj("properties") ?: return null
        return when (val type = json.string("type")) {
            "server.connected" -> ServerEvent.Connected(
                properties.stringOr("version", ""),
            )

            "session.created", "session.updated" -> properties.obj("info")?.let {
                ServerEvent.SessionUpserted(session(it))
            }

            "session.deleted" -> properties.string("sessionID")?.let {
                ServerEvent.SessionRemoved(it)
            }

            "message.updated" -> {
                val sessionId = properties.stringOr("sessionID", "")
                properties.obj("info")?.let { info ->
                    val parts = properties.arr("parts").ifEmpty { emptyList() }
                    message(JsonValue.Obj(mapOf("info" to info, "parts" to JsonValue.Arr(parts))))
                        ?.let { ServerEvent.MessageUpserted(sessionId, it) }
                }
            }

            "message.part.updated" -> {
                val sessionId = properties.stringOr("sessionID", "")
                val part = properties.obj("part")
                val messageId = part?.stringOr("messageID", "") ?: ""
                part?.let { part(it) }?.let {
                    ServerEvent.PartUpserted(sessionId, messageId, it)
                }
            }

            "message.removed" -> {
                val sessionId = properties.stringOr("sessionID", "")
                properties.string("messageID")?.let {
                    ServerEvent.MessageRemoved(sessionId, it)
                }
            }

            "permission.asked" -> ServerEvent.PermissionRequested(permission(properties))

            "permission.replied" -> properties.string("requestID")?.let {
                ServerEvent.PermissionResolved(it)
            }

            "session.idle" -> properties.string("sessionID")?.let {
                ServerEvent.SessionIdle(it)
            }

            "session.error" -> {
                val sessionId = properties.stringOr("sessionID", "")
                ServerEvent.SessionFailed(
                    sessionId,
                    errorMessage(properties.obj("error")) ?: "The session failed",
                )
            }

            else -> null
        }
    }

    // ---- request bodies -------------------------------------------------

    /**
     * A client-supplied message id lets the optimistic local bubble be
     * reconciled with the server's copy instead of duplicating it. The server
     * keeps an id we supply, provided it carries the `msg_` prefix.
     */
    fun newMessageId(): String =
        "msg_" + java.util.UUID.randomUUID().toString().replace("-", "").take(20)

    fun promptBody(
        text: String,
        messageId: String?,
        model: ModelRef?,
        agent: String?,
        attachments: List<Attachment>,
    ): String {
        val parts = mutableListOf<JsonValue>()

        if (text.isNotBlank()) {
            // The part deliberately carries no id. A part id is a `prt_…`
            // server-side identity, not the message id, and echoing the message
            // id here is rejected with a 400.
            parts += JsonValue.Obj(
                mapOf(
                    "type" to JsonValue.Str("text"),
                    "text" to JsonValue.Str(text),
                ),
            )
        }

        for (attachment in attachments) {
            val url = attachment.uri ?: continue
            parts += JsonValue.Obj(
                buildMap {
                    put("type", JsonValue.Str("file"))
                    put("mime", JsonValue.Str(attachment.mime))
                    put("filename", JsonValue.Str(attachment.name))
                    put("url", JsonValue.Str(url))
                },
            )
        }

        return JsonValue.Obj(
            buildMap {
                messageId?.let { put("messageID", JsonValue.Str(it)) }
                put("parts", JsonValue.Arr(parts))
                model?.let {
                    put(
                        "model",
                        JsonValue.Obj(
                            mapOf(
                                "providerID" to JsonValue.Str(it.providerId),
                                "modelID" to JsonValue.Str(it.modelId),
                            ),
                        ),
                    )
                }
                agent?.let { put("agent", JsonValue.Str(it)) }
            },
        ).encode()
    }

    fun replyBody(reply: String): String =
        JsonValue.Obj(mapOf("reply" to JsonValue.Str(reply))).encode()

    fun commandBody(command: String, arguments: String, agent: String?, model: String?): String =
        JsonValue.Obj(
            buildMap {
                put("command", JsonValue.Str(command))
                put("arguments", JsonValue.Str(arguments))
                agent?.let { put("agent", JsonValue.Str(it)) }
                model?.let { put("model", JsonValue.Str(it)) }
            },
        ).encode()

    fun shellBody(command: String, agent: String?, model: String?): String =
        JsonValue.Obj(
            buildMap {
                put("command", JsonValue.Str(command))
                agent?.let { put("agent", JsonValue.Str(it)) }
                model?.let { put("model", JsonValue.Str(it)) }
            },
        ).encode()
}
