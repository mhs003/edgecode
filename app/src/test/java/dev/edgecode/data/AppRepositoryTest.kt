package dev.edgecode.data

import dev.edgecode.core.model.AgentInfo
import dev.edgecode.core.model.ApiVersion
import dev.edgecode.core.model.Attachment
import dev.edgecode.core.model.ChatMessage
import dev.edgecode.core.model.ModelInfo
import dev.edgecode.core.model.ModelRef
import dev.edgecode.core.model.PermissionDecision
import dev.edgecode.core.model.ProjectRef
import dev.edgecode.core.model.ProviderInfo
import dev.edgecode.core.model.QuestionInfo
import dev.edgecode.core.model.QuestionOption
import dev.edgecode.core.model.QuestionRequest
import dev.edgecode.core.model.ServerEvent
import dev.edgecode.core.model.ServerInfo
import dev.edgecode.core.model.ServerProfile
import dev.edgecode.core.model.SessionSummary
import dev.edgecode.core.model.SessionStatus
import dev.edgecode.core.model.SlashCommand
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * The connect path used to wait on its catalogue fetches one after another, so
 * five independent round-trips were added together before the session list could
 * appear. These tests pin the two properties that fix depends on.
 */
class AppRepositoryTest {

    /**
     * Records how many calls were in flight at once. This is deterministic:
     * with concurrent fetches all of them suspend before any resumes, so the
     * peak is the number of calls; run sequentially the peak is always one.
     */
    private class FakeApi(
        private val latencyMs: Long = 100,
        private val sessionList: List<SessionSummary> = emptyList(),
        private val statuses: Map<String, SessionStatus> = emptyMap(),
        private val questionList: List<QuestionRequest> = emptyList(),
    ) : OpenCodeApi {

        private val inFlight = AtomicInteger(0)
        private val peak = AtomicInteger(0)

        val peakConcurrency: Int get() = peak.get()

        private suspend fun <T> call(value: T): T {
            val now = inFlight.incrementAndGet()
            peak.updateAndGet { current -> maxOf(current, now) }
            try {
                delay(latencyMs)
            } finally {
                inFlight.decrementAndGet()
            }
            return value
        }

        override val version: ApiVersion = ApiVersion.V1

        override suspend fun health(): ServerInfo =
            call(ServerInfo(version = "0.0.0-test", healthy = true, api = ApiVersion.V1))

        override suspend fun projects(): List<ProjectRef> = call(emptyList())
        override suspend fun sessions(): List<SessionSummary> = call(sessionList)

        override suspend fun sessionStatuses(): Map<String, SessionStatus> = call(statuses)

        override suspend fun createSession(directory: String?, title: String?): SessionSummary =
            call(
                SessionSummary(
                    id = "s",
                    title = "s",
                    directory = "/",
                    createdAt = 0L,
                    updatedAt = 0L,
                ),
            )

        override suspend fun renameSession(sessionId: String, title: String) =
            call(Unit)

        override suspend fun deleteSession(sessionId: String) = call(Unit)

        override suspend fun messages(sessionId: String): List<ChatMessage> = call(emptyList())

        override suspend fun sendPrompt(
            sessionId: String,
            text: String,
            attachments: List<Attachment>,
            agent: String?,
            model: ModelRef?,
            messageId: String?,
        ): String = call(messageId ?: "m")

        override suspend fun runCommand(sessionId: String, command: String, arguments: String) =
            call(Unit)

        override suspend fun runShell(sessionId: String, command: String) = call(Unit)

        override suspend fun abort(sessionId: String) = call(Unit)

        override suspend fun respondPermission(
            sessionId: String,
            requestId: String,
            decision: PermissionDecision,
            remember: Boolean,
        ) = call(Unit)

        override suspend fun questions(): List<QuestionRequest> = call(questionList)

        override suspend fun replyQuestion(requestId: String, answers: List<List<String>>) =
            call(Unit)

        override suspend fun rejectQuestion(requestId: String) = call(Unit)

        override suspend fun commands(): List<SlashCommand> = call(emptyList())
        override suspend fun agents(): List<AgentInfo> = call(emptyList())
        override suspend fun providers(): List<ProviderInfo> = call(emptyList())
        override suspend fun models(): List<ModelInfo> = call(emptyList())

        override fun events(): Flow<ServerEvent> = emptyFlow()

        override suspend fun close() = Unit
    }

    @Test
    fun catalogueFetchesOverlapRatherThanSerialising() = runBlocking {
        val api = FakeApi(latencyMs = 100)
        var built: OpenCodeApi? = null
        val repository = AppRepository(this) { _, _ -> built = api; api }

        val started = System.nanoTime()
        repository.connect(ServerProfile("t", "test", "http://localhost:4096"))
        val elapsedMs = (System.nanoTime() - started) / 1_000_000

        assertNotNull(built)
        // Five 100ms catalogue fetches. Overlapped, connect costs
        // health(100) + catalogue(100) + sessions(100) ≈ 300ms; serialised it
        // would cost 100 + 500 + 100 = 700ms.
        assertEquals(
            "catalogue fetches should all be in flight at once",
            5,
            api.peakConcurrency,
        )
        assertTrue(
            "connect took ${elapsedMs}ms; serialised catalogue fetches would take ~700ms",
            elapsedMs < 500,
        )
    }

    @Test
    fun connectSucceedsAndPublishesTheSessionList() = runBlocking {
        val api = FakeApi(latencyMs = 1)
        val repository = AppRepository(this) { _, _ -> api }

        repository.connect(ServerProfile("t", "test", "http://localhost:4096"))

        assertTrue(repository.connection.value.isConnected)
        assertTrue(repository.sessions.value.isEmpty())
    }

    /**
     * A `task` call creates an ordinary session with a `parentID`. It belongs to
     * the chat that spawned it, so listing it as a chat of its own is wrong.
     */
    @Test
    fun subagentSessionsAreSeparatedFromTheSessionList() = runBlocking {
        val root = SessionSummary("root", "Root", "/", 0L, 0L)
        val child = SessionSummary("child", "Child", "/", 0L, 0L, parentId = "root")
        val api = FakeApi(latencyMs = 1, sessionList = listOf(root, child))
        val repository = AppRepository(this) { _, _ -> api }

        repository.connect(ServerProfile("t", "test", "http://localhost:4096"))

        assertEquals(listOf("root"), repository.sessions.value.map { it.id })
        assertEquals(listOf("child"), repository.subagents.value.map { it.id })
    }

    /** Status is not in a session payload, so the list has to merge it in. */
    @Test
    fun refreshMergesStatusFromTheStatusEndpoint() = runBlocking {
        val root = SessionSummary("root", "Root", "/", 0L, 0L)
        val api = FakeApi(
            latencyMs = 1,
            sessionList = listOf(root),
            statuses = mapOf("root" to SessionStatus.Working),
        )
        val repository = AppRepository(this) { _, _ -> api }

        repository.connect(ServerProfile("t", "test", "http://localhost:4096"))

        assertEquals(SessionStatus.Working, repository.sessions.value.single().status)
    }

    @Test
    fun connectPublishesPendingQuestions() = runBlocking {
        val request = QuestionRequest(
            id = "que_1",
            sessionId = "root",
            questions = listOf(
                QuestionInfo(
                    question = "Which target?",
                    header = "Target",
                    options = listOf(QuestionOption("Phone", "Fast")),
                ),
            ),
        )
        val api = FakeApi(latencyMs = 1, questionList = listOf(request))
        val repository = AppRepository(this) { _, _ -> api }

        repository.connect(ServerProfile("t", "test", "http://localhost:4096"))

        assertEquals(listOf(request), repository.questions.value)
    }
}
