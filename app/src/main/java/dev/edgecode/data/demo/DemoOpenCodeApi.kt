package dev.edgecode.data.demo

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
import dev.edgecode.core.model.SessionStatus
import dev.edgecode.core.model.SessionSummary
import dev.edgecode.core.model.SlashCommand
import dev.edgecode.core.model.TokenUsage
import dev.edgecode.core.model.ToolState
import dev.edgecode.core.model.ToolStatus
import dev.edgecode.data.OpenCodeApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * In-memory stand-in for a real server.
 *
 * This is not a mock that returns static values: it drives the same
 * [ServerEvent] stream the SSE client will, including incremental part updates,
 * tool state transitions, a permission round-trip that genuinely suspends until
 * answered, and abort handling. The UI therefore exercises the real streaming
 * code path, and swapping in the network implementation later leaves the
 * interface layer untouched.
 */
class DemoOpenCodeApi(
    private val scope: CoroutineScope,
    /** 1.0 is real time; higher is faster, which is useful in tests. */
    private val speed: Float = 1f,
) : OpenCodeApi {

    override val version: ApiVersion = ApiVersion.Demo

    private val events = MutableSharedFlow<ServerEvent>(
        replay = 0,
        extraBufferCapacity = 512,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val sessions = ConcurrentHashMap<String, SessionSummary>()
    private val transcripts = ConcurrentHashMap<String, MutableList<ChatMessage>>()
    private val permissions = ConcurrentHashMap<String, MutableList<PermissionRequest>>()
    private val pendingAnswers = ConcurrentHashMap<String, CompletableDeferred<PermissionDecision>>()
    private val questions = ConcurrentHashMap<String, MutableList<QuestionRequest>>()
    private val pendingQuestionAnswers = ConcurrentHashMap<String, CompletableDeferred<QuestionOutcome>>()
    private val running = ConcurrentHashMap<String, Job>()
    private val counter = AtomicInteger(0)

    init {
        val now = System.currentTimeMillis()
        DemoContent.sessions(now).forEach { sessions[it.id] = it }
    }

    override fun events(): Flow<ServerEvent> = events.asSharedFlow()

    private suspend fun emit(event: ServerEvent) {
        events.emit(event)
    }

    private fun nextId(prefix: String) = "${prefix}_${counter.incrementAndGet()}"

    private fun scaled(ms: Long) = (ms / speed).toLong().coerceAtLeast(1L)

    /**
     * Simulated round-trip times.
     *
     * Tuned to a server on the local network rather than to a WAN, because that
     * is how OpenCode is actually deployed. These values are the floor on how
     * long the demo takes to respond, so they are deliberately small: an earlier
     * set summed to well over a second before the session list could appear.
     */
    private object Latency {
        const val Health = 220L
        const val Sessions = 60L
        const val Projects = 35L
        const val Messages = 80L
        const val CreateSession = 90L
        const val RenameSession = 50L
        const val DeleteSession = 60L
        const val Models = 45L
        const val Questions = 30L
        const val Agents = 30L
        const val Commands = 25L
        const val Providers = 30L
        const val FirstToken = 260L
    }

    // --- Connection --------------------------------------------------------

    override suspend fun health(): ServerInfo {
        delay(scaled(Latency.Health))
        return ServerInfo(
            version = DemoContent.SERVER_VERSION,
            healthy = true,
            api = ApiVersion.Demo,
        )
    }

    // --- Projects and sessions --------------------------------------------

    override suspend fun projects(): List<ProjectRef> {
        delay(scaled(Latency.Projects))
        return DemoContent.projects
    }

    override suspend fun sessions(): List<SessionSummary> {
        delay(scaled(Latency.Sessions))
        return sessions.values.sortedByDescending { it.updatedAt }
    }

    override suspend fun sessionStatuses(): Map<String, SessionStatus> {
        delay(scaled(Latency.Sessions))
        return sessions.values
            .filter { it.status != SessionStatus.Idle }
            .associate { it.id to it.status }
    }

    override suspend fun createSession(directory: String?, title: String?): SessionSummary {
        delay(scaled(Latency.CreateSession))
        val now = System.currentTimeMillis()
        val session = SessionSummary(
            id = nextId("ses"),
            title = title?.takeIf { it.isNotBlank() }
                ?: directory?.trimEnd('/')?.substringAfterLast('/')?.let { "New session in $it" }
                ?: "New session",
            directory = directory ?: DemoContent.projects.first().directory,
            createdAt = now,
            updatedAt = now,
            modelLabel = "Opus 4.5",
        )
        sessions[session.id] = session
        transcripts[session.id] = mutableListOf()
        emit(ServerEvent.SessionUpserted(session))
        return session
    }

    override suspend fun renameSession(sessionId: String, title: String) {
        delay(scaled(Latency.RenameSession))
        val updated = sessions[sessionId]?.copy(title = title) ?: return
        sessions[sessionId] = updated
        emit(ServerEvent.SessionUpserted(updated))
    }

    override suspend fun deleteSession(sessionId: String) {
        delay(scaled(Latency.DeleteSession))
        sessions.remove(sessionId)
        transcripts.remove(sessionId)
        emit(ServerEvent.SessionRemoved(sessionId))
    }

    override suspend fun messages(sessionId: String): List<ChatMessage> {
        delay(scaled(Latency.Messages))
        return transcripts.getOrPut(sessionId) {
            DemoContent.transcript(sessionId, System.currentTimeMillis()).toMutableList()
        }.toList()
    }

    // --- Conversation ------------------------------------------------------

    override suspend fun sendPrompt(
        sessionId: String,
        text: String,
        attachments: List<Attachment>,
        agent: String?,
        model: ModelRef?,
        messageId: String?,
    ): String {
        val now = System.currentTimeMillis()
        val userMessage = ChatMessage.User(
            id = messageId ?: nextId("msg"),
            createdAt = now,
            text = text,
            attachments = attachments,
        )
        val list = transcripts.getOrPut(sessionId) { mutableListOf() }
        list.add(userMessage)
        emit(ServerEvent.MessageUpserted(sessionId, userMessage))

        setStatus(sessionId, SessionStatus.Working)

        val job = scope.launch {
            playScript(
                sessionId = sessionId,
                prompt = text,
                agentId = agent ?: "build",
                model = model,
            )
        }
        running[sessionId]?.cancel()
        running[sessionId] = job
        return userMessage.id
    }

    override suspend fun runCommand(sessionId: String, command: String, arguments: String) {
        val now = System.currentTimeMillis()
        val userMessage = ChatMessage.User(
            id = nextId("msg"),
            createdAt = now,
            text = "/$command${if (arguments.isBlank()) "" else " $arguments"}",
        )
        transcripts.getOrPut(sessionId) { mutableListOf() }.add(userMessage)
        emit(ServerEvent.MessageUpserted(sessionId, userMessage))
        setStatus(sessionId, SessionStatus.Working)

        running[sessionId]?.cancel()
        running[sessionId] = scope.launch {
            val assistantId = nextId("msg")
            val assistant = ChatMessage.Assistant(
                id = assistantId,
                createdAt = System.currentTimeMillis(),
                agent = "build",
                streaming = true,
            )
            transcripts.getOrPut(sessionId) { mutableListOf() }.add(assistant)
            emit(ServerEvent.MessageUpserted(sessionId, assistant))

            val partId = nextId("part")
            stream(
                sessionId, assistantId, partId, commandReply(command, arguments),
                chunk = 10, perChunk = 22L,
            ) { id, value -> Part.Text(id = id, text = value, streaming = true) }

            finishAssistant(sessionId, assistantId)
            setStatus(sessionId, SessionStatus.Idle)
            emit(ServerEvent.SessionIdle(sessionId))
        }
    }

    override suspend fun runShell(sessionId: String, command: String) {
        runCommand(sessionId, "sh", command)
    }

    override suspend fun abort(sessionId: String) {
        running.remove(sessionId)?.cancel()
        val list = transcripts[sessionId].orEmpty()
        val last = list.lastOrNull()
        if (last is ChatMessage.Assistant && last.streaming) {
            emit(
                ServerEvent.PartUpserted(
                    sessionId, last.id,
                    Part.Note(id = nextId("part"), text = "Interrupted by you"),
                ),
            )
        }
        finishAssistant(sessionId, (list.lastOrNull() as? ChatMessage.Assistant)?.id)
        setStatus(sessionId, SessionStatus.Idle)
        emit(ServerEvent.SessionIdle(sessionId))
    }

    // --- Permissions -------------------------------------------------------

    override suspend fun respondPermission(
        sessionId: String,
        requestId: String,
        decision: PermissionDecision,
        remember: Boolean,
    ) {
        pendingAnswers.remove(requestId)?.complete(decision)
        permissions[sessionId]?.removeAll { it.id == requestId }
        emit(ServerEvent.PermissionResolved(requestId))
    }

    // --- Questions ---------------------------------------------------------

    override suspend fun questions(): List<QuestionRequest> {
        delay(scaled(Latency.Questions))
        return questions.values.flatten()
    }

    override suspend fun replyQuestion(requestId: String, answers: List<List<String>>) {
        val request = takeQuestion(requestId) ?: return
        pendingQuestionAnswers.remove(requestId)?.complete(QuestionOutcome.Answers(answers))
        emit(ServerEvent.QuestionReplied(request.sessionId, requestId, answers))
    }

    override suspend fun rejectQuestion(requestId: String) {
        val request = takeQuestion(requestId) ?: return
        pendingQuestionAnswers.remove(requestId)?.complete(QuestionOutcome.Rejected)
        emit(ServerEvent.QuestionRejected(request.sessionId, requestId))
    }

    private fun takeQuestion(requestId: String): QuestionRequest? {
        val request = questions.values.flatten().firstOrNull { it.id == requestId }
        questions.values.forEach { it.removeAll { question -> question.id == requestId } }
        return request
    }

    // --- Catalogue ---------------------------------------------------------

    override suspend fun commands(): List<SlashCommand> {
        delay(scaled(Latency.Commands))
        return DemoContent.commands
    }

    override suspend fun agents(): List<AgentInfo> {
        delay(scaled(Latency.Agents))
        return DemoContent.agents
    }

    override suspend fun providers(): List<ProviderInfo> {
        delay(scaled(Latency.Providers))
        return DemoContent.providers
    }

    override suspend fun models(): List<ModelInfo> {
        delay(scaled(Latency.Models))
        return DemoContent.models
    }

    override suspend fun close() {
        running.values.forEach { it.cancel() }
        running.clear()
        pendingQuestionAnswers.values.forEach { it.cancel() }
        pendingQuestionAnswers.clear()
    }

    private sealed interface QuestionOutcome {
        data class Answers(val answers: List<List<String>>) : QuestionOutcome
        data object Rejected : QuestionOutcome
    }

    // --- Playback ----------------------------------------------------------

    private suspend fun playScript(
        sessionId: String,
        prompt: String,
        agentId: String,
        model: ModelRef?,
    ) {
        val assistantId = nextId("msg")
        val assistant = ChatMessage.Assistant(
            id = assistantId,
            createdAt = System.currentTimeMillis(),
            agent = agentId,
            model = model ?: ModelRef("anthropic", "claude-opus-4-5"),
            streaming = true,
        )
        transcripts.getOrPut(sessionId) { mutableListOf() }.add(assistant)
        emit(ServerEvent.MessageUpserted(sessionId, assistant))

        delay(scaled(Latency.FirstToken))

        val script = DemoContent.replyScript(prompt, sessionId, agentId)

        for (step in script) {
            when (step) {
                is DemoContent.ScriptStep.Reasoning -> {
                    val partId = nextId("part")
                    stream(sessionId, assistantId, partId, step.text, chunk = 14, perChunk = 16L) {
                            id, value ->
                        Part.Reasoning(id = id, text = value, streaming = true)
                    }
                }

                is DemoContent.ScriptStep.Text -> {
                    val partId = nextId("part")
                    stream(sessionId, assistantId, partId, step.text, chunk = 7, perChunk = 14L) {
                            id, value ->
                        Part.Text(id = id, text = value, streaming = true)
                    }
                }

                is DemoContent.ScriptStep.Tool -> {
                    val partId = nextId("part")
                    val runningState = ToolState(
                        status = ToolStatus.Running,
                        title = step.title,
                        input = step.input,
                    )
                    emit(
                        ServerEvent.PartUpserted(
                            sessionId, assistantId,
                            Part.Tool(partId, step.toolId, step.name, runningState),
                        ),
                    )
                    delay(scaled(step.durationMs))
                    val doneState = ToolState(
                        status = if (step.fail) ToolStatus.Failed else ToolStatus.Completed,
                        title = step.title,
                        subtitle = step.subtitle,
                        input = step.input,
                        output = step.output,
                        meta = step.meta,
                        durationMs = step.durationMs,
                    )
                    emit(
                        ServerEvent.PartUpserted(
                            sessionId, assistantId,
                            Part.Tool(partId, step.toolId, step.name, doneState),
                        ),
                    )
                }

                is DemoContent.ScriptStep.AskQuestion -> {
                    val callId = nextId("call")
                    val toolPartId = nextId("part")
                    val request = step.request.copy(
                        id = nextId("que"),
                        sessionId = sessionId,
                        messageId = assistantId,
                        callId = callId,
                    )
                    val title = if (request.questions.size == 1) {
                        "Asked 1 question"
                    } else {
                        "Asked ${request.questions.size} questions"
                    }
                    emit(
                        ServerEvent.PartUpserted(
                            sessionId, assistantId,
                            Part.Tool(
                                toolPartId, callId, "question",
                                ToolState(
                                    status = ToolStatus.Running,
                                    title = title,
                                    questions = request.questions,
                                ),
                            ),
                        ),
                    )

                    val answer = CompletableDeferred<QuestionOutcome>()
                    pendingQuestionAnswers[request.id] = answer
                    questions.getOrPut(sessionId) { mutableListOf() }.add(request)
                    emit(ServerEvent.QuestionAsked(request))

                    when (val outcome = answer.await()) {
                        is QuestionOutcome.Answers -> {
                            emit(
                                ServerEvent.PartUpserted(
                                    sessionId, assistantId,
                                    Part.Tool(
                                        toolPartId, callId, "question",
                                        ToolState(
                                            status = ToolStatus.Completed,
                                            title = title,
                                            questions = request.questions,
                                            answers = outcome.answers,
                                        ),
                                    ),
                                ),
                            )
                        }

                        QuestionOutcome.Rejected -> {
                            emit(
                                ServerEvent.PartUpserted(
                                    sessionId, assistantId,
                                    Part.Tool(
                                        toolPartId, callId, "question",
                                        ToolState(
                                            status = ToolStatus.Completed,
                                            title = title,
                                            questions = request.questions,
                                        ),
                                    ),
                                ),
                            )
                            emit(
                                ServerEvent.PartUpserted(
                                    sessionId, assistantId,
                                    Part.Note(nextId("part"), "Skipped the question."),
                                ),
                            )
                            finishAssistant(sessionId, assistantId)
                            setStatus(sessionId, SessionStatus.Idle)
                            emit(ServerEvent.SessionIdle(sessionId))
                            return
                        }
                    }
                }

                is DemoContent.ScriptStep.Diff -> {
                    emit(
                        ServerEvent.PartUpserted(
                            sessionId, assistantId,
                            Part.Diff(nextId("part"), step.files),
                        ),
                    )
                }

                is DemoContent.ScriptStep.Note -> {
                    emit(
                        ServerEvent.PartUpserted(
                            sessionId, assistantId,
                            Part.Note(nextId("part"), step.text),
                        ),
                    )
                }

                is DemoContent.ScriptStep.AskPermission -> {
                    val request = step.request.copy(
                        id = nextId("perm"),
                        sessionId = sessionId,
                    )
                    val answer = CompletableDeferred<PermissionDecision>()
                    pendingAnswers[request.id] = answer
                    permissions.getOrPut(sessionId) { mutableListOf() }.add(request)
                    emit(ServerEvent.PermissionRequested(request))

                    when (answer.await()) {
                        PermissionDecision.Reject -> {
                            emit(
                                ServerEvent.PartUpserted(
                                    sessionId, assistantId,
                                    Part.Note(
                                        nextId("part"),
                                        "You declined the edit. I'll leave the file alone.",
                                    ),
                                ),
                            )
                            finishAssistant(sessionId, assistantId)
                            setStatus(sessionId, SessionStatus.Idle)
                            emit(ServerEvent.SessionIdle(sessionId))
                            return
                        }

                        PermissionDecision.Always -> {
                            emit(
                                ServerEvent.PartUpserted(
                                    sessionId, assistantId,
                                    Part.Note(
                                        nextId("part"),
                                        "Approved, and remembered for this project.",
                                    ),
                                ),
                            )
                        }

                        PermissionDecision.Once -> Unit
                    }
                }
            }
        }

        finishAssistant(sessionId, assistantId)
        setStatus(sessionId, SessionStatus.Idle)
        emit(ServerEvent.SessionIdle(sessionId))
    }

    /** Streams [full] in chunks, re-emitting the growing part each time. */
    private suspend fun stream(
        sessionId: String,
        messageId: String,
        partId: String,
        full: String,
        chunk: Int,
        perChunk: Long,
        build: (String, String) -> Part,
    ) {
        var index = 0
        while (index < full.length) {
            index = (index + chunk).coerceAtMost(full.length)
            emit(ServerEvent.PartUpserted(sessionId, messageId, build(partId, full.substring(0, index))))
            delay(scaled(perChunk))
        }
        emit(
            ServerEvent.PartUpserted(
                sessionId, messageId,
                when (val settled = build(partId, full)) {
                    is Part.Text -> settled.copy(streaming = false)
                    is Part.Reasoning -> settled.copy(streaming = false)
                    else -> settled
                },
            ),
        )
    }

    private suspend fun finishAssistant(sessionId: String, messageId: String?) {
        if (messageId == null) return
        val list = transcripts[sessionId] ?: return
        val index = list.indexOfFirst { it.id == messageId }
        if (index < 0) return
        val message = list[index]
        if (message !is ChatMessage.Assistant) return

        val settled = message.copy(
            streaming = false,
            completedAt = System.currentTimeMillis(),
            cost = message.cost + TURN_COST,
            tokens = message.tokens + TURN_TOKENS,
        )
        list[index] = settled
        emit(ServerEvent.MessageUpserted(sessionId, settled))

        // Roll the turn's usage up into the session, so the transcript's meta bar
        // reflects the whole conversation rather than only its first turn.
        val session = sessions[sessionId]
        if (session != null) {
            val updated = session.copy(
                updatedAt = System.currentTimeMillis(),
                cost = session.cost + TURN_COST,
                tokens = session.tokens + TURN_TOKENS,
                additions = session.additions,
            )
            sessions[sessionId] = updated
            emit(ServerEvent.SessionUpserted(updated))
        }
    }

    private companion object {
        const val TURN_COST = 0.014
        val TURN_TOKENS = TokenUsage(input = 1_820, output = 240, cacheRead = 1_200)
    }

    private suspend fun setStatus(sessionId: String, status: SessionStatus) {
        val session = sessions[sessionId] ?: return
        val updated = session.copy(status = status, updatedAt = System.currentTimeMillis())
        sessions[sessionId] = updated
        // The real server reports status over its own event rather than by
        // re-sending the session, so the demo does the same and exercises the
        // path the UI actually uses.
        emit(ServerEvent.SessionStatusChanged(sessionId, status))
    }

    private fun commandReply(command: String, arguments: String): String = when (command) {
        "undo" -> "Reverted the last change and restored the original prompt. Send it again with edits to retry."
        "redo" -> "Reapplied the reverted change."
        "compact" -> "Summarised the session. Earlier turns are now a single context block; nothing was lost from the transcript."
        "share" -> "Created a share link for this session."
        "init" -> "Analysed the project and wrote `AGENTS.md` with the build commands, conventions and layout."
        "models" -> "This is a client-side command. It opens the model picker rather than sending anything to the server."
        "agents" -> "This is a client-side command. It opens the agent picker."
        else -> "Ran `/$command${if (arguments.isBlank()) "" else " $arguments"}`."
    }
}
