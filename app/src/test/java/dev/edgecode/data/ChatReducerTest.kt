package dev.edgecode.data

import dev.edgecode.core.model.ChatMessage
import dev.edgecode.core.model.Part
import dev.edgecode.core.model.QuestionInfo
import dev.edgecode.core.model.QuestionRequest
import dev.edgecode.core.model.ServerEvent
import dev.edgecode.core.model.ToolState
import dev.edgecode.core.model.ToolStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatReducerTest {

    private val session = "ses_1"

    private fun user(id: String) = ChatMessage.User(
        id = id,
        createdAt = 1L,
        text = "hello",
    )

    private fun assistant(id: String, streaming: Boolean = true) = ChatMessage.Assistant(
        id = id,
        createdAt = 2L,
        streaming = streaming,
    )

    private fun question(id: String = "que_1") = QuestionRequest(
        id = id,
        sessionId = session,
        questions = listOf(QuestionInfo(question = "Which one?", header = "Choice")),
    )

    @Test
    fun `a question is added and replaced by id`() {
        val original = question()
        val added = ChatReducer.reduceQuestions(
            emptyList(),
            ServerEvent.QuestionAsked(original),
        )
        assertEquals(listOf(original), added)

        val updated = original.copy(
            questions = listOf(QuestionInfo(question = "Which one now?", header = "Choice")),
        )
        val replaced = ChatReducer.reduceQuestions(
            added,
            ServerEvent.QuestionAsked(updated),
        )
        assertEquals(1, replaced.size)
        assertEquals(updated, replaced.single())
    }

    @Test
    fun `replied and rejected questions are removed`() {
        val first = question("que_1")
        val second = question("que_2")
        val current = listOf(first, second)

        val replied = ChatReducer.reduceQuestions(
            current,
            ServerEvent.QuestionReplied(session, first.id, listOf(listOf("A"))),
        )
        assertEquals(listOf(second), replied)

        val rejected = ChatReducer.reduceQuestions(
            current,
            ServerEvent.QuestionRejected(session, second.id),
        )
        assertEquals(listOf(first), rejected)
    }

    @Test
    fun `appends a new message`() {
        val result = ChatReducer.reduceMessages(
            emptyList(),
            ServerEvent.MessageUpserted(session, user("m1")),
        )
        assertEquals(1, result.size)
        assertEquals("m1", result.single().id)
    }

    @Test
    fun `replaces an existing message by id rather than duplicating it`() {
        val start = listOf(user("m1"))
        val result = ChatReducer.reduceMessages(
            start,
            ServerEvent.MessageUpserted(session, user("m1")),
        )
        assertEquals(1, result.size)
    }

    /**
     * A part can arrive before its message on a lossy stream; the transcript must
     * still show it rather than dropping content.
     */
    @Test
    fun `creates an assistant shell when a part arrives first`() {
        val result = ChatReducer.reduceMessages(
            emptyList(),
            ServerEvent.PartUpserted(session, "m1", Part.Text("p1", "partial")),
        )
        val message = result.single() as ChatMessage.Assistant
        assertEquals("m1", message.id)
        assertTrue(message.streaming)
        assertEquals(1, message.parts.size)
    }

    @Test
    fun `updates a streaming part in place`() {
        var messages = ChatReducer.reduceMessages(
            emptyList(),
            ServerEvent.MessageUpserted(session, assistant("m1")),
        )
        messages = ChatReducer.reduceMessages(
            messages,
            ServerEvent.PartUpserted(session, "m1", Part.Text("p1", "He")),
        )
        messages = ChatReducer.reduceMessages(
            messages,
            ServerEvent.PartUpserted(session, "m1", Part.Text("p1", "Hello")),
        )

        val parts = (messages.single() as ChatMessage.Assistant).parts
        assertEquals(1, parts.size)
        assertEquals("Hello", (parts.single() as Part.Text).text)
    }

    @Test
    fun `keeps part order while streaming`() {
        var messages = ChatReducer.reduceMessages(
            emptyList(),
            ServerEvent.MessageUpserted(session, assistant("m1")),
        )
        listOf("p1", "p2", "p3").forEach { id ->
            messages = ChatReducer.reduceMessages(
                messages,
                ServerEvent.PartUpserted(session, "m1", Part.Text(id, id)),
            )
        }
        val ids = (messages.single() as ChatMessage.Assistant).parts.map { it.id }
        assertEquals(listOf("p1", "p2", "p3"), ids)
    }

    @Test
    fun `does not let a stale frame wipe richer local parts`() {
        var messages = ChatReducer.reduceMessages(
            emptyList(),
            ServerEvent.MessageUpserted(session, assistant("m1")),
        )
        messages = ChatReducer.reduceMessages(
            messages,
            ServerEvent.PartUpserted(session, "m1", Part.Text("p1", "streamed")),
        )
        // A message snapshot with no parts should not erase what we already have.
        messages = ChatReducer.reduceMessages(
            messages,
            ServerEvent.MessageUpserted(session, assistant("m1").copy(parts = emptyList())),
        )
        assertEquals(1, (messages.single() as ChatMessage.Assistant).parts.size)
    }

    @Test
    fun `settles streaming state on session idle`() {
        val messages = ChatReducer.reduceMessages(
            listOf(assistant("m1", streaming = true)),
            ServerEvent.SessionIdle(session),
        )
        assertFalse((messages.single() as ChatMessage.Assistant).streaming)
    }

    @Test
    fun `records an error on the streaming message`() {
        val messages = ChatReducer.reduceMessages(
            listOf(assistant("m1", streaming = true)),
            ServerEvent.SessionFailed(session, "boom"),
        )
        val message = messages.single() as ChatMessage.Assistant
        assertEquals("boom", message.error)
        assertFalse(message.streaming)
    }

    @Test
    fun `tool state transitions replace the previous state`() {
        var messages = ChatReducer.reduceMessages(
            emptyList(),
            ServerEvent.MessageUpserted(session, assistant("m1")),
        )
        messages = ChatReducer.reduceMessages(
            messages,
            ServerEvent.PartUpserted(
                session, "m1",
                Part.Tool("t1", "grep", "Grep", ToolState(status = ToolStatus.Running, title = "grep x")),
            ),
        )
        messages = ChatReducer.reduceMessages(
            messages,
            ServerEvent.PartUpserted(
                session, "m1",
                Part.Tool(
                    "t1", "grep", "Grep",
                    ToolState(status = ToolStatus.Completed, title = "grep x", output = "3 matches"),
                ),
            ),
        )
        val tool = (messages.single() as ChatMessage.Assistant).parts.single() as Part.Tool
        assertEquals(ToolStatus.Completed, tool.state.status)
        assertEquals("3 matches", tool.state.output)
    }

    // A user message behaves nothing like an assistant one on the wire. Its
    // `message.updated` frame carries no parts at all — the body arrives later
    // over `message.part.updated` — and the frame is re-sent on every update.
    // Treating it like an assistant message dropped the text entirely, which is
    // what a transcript of only the agent's replies looked like.

    @Test
    fun `a user message body arrives as a part and is folded into the message`() {
        var messages = ChatReducer.reduceMessages(
            emptyList(),
            ServerEvent.MessageUpserted(session, ChatMessage.User("m1", 1L, text = "")),
        )
        messages = ChatReducer.reduceMessages(
            messages,
            ServerEvent.PartUpserted(session, "m1", Part.Text("p1", "what changed?")),
        )

        assertEquals(1, messages.size)
        assertEquals("what changed?", (messages.single() as ChatMessage.User).text)
    }

    @Test
    fun `a repeated message frame does not blank the user body`() {
        var messages = ChatReducer.reduceMessages(
            emptyList(),
            ServerEvent.MessageUpserted(session, ChatMessage.User("m1", 1L, text = "keep me")),
        )
        // The server re-sends the frame with no body after each part update.
        messages = ChatReducer.reduceMessages(
            messages,
            ServerEvent.MessageUpserted(session, ChatMessage.User("m1", 1L, text = "")),
        )

        assertEquals("keep me", (messages.single() as ChatMessage.User).text)
    }

    @Test
    fun `an echo of an optimistic message merges instead of duplicating`() {
        // This is the whole send path: the repository inserts the bubble under
        // an id it chose, the server keeps that id, and the echo must land on
        // the same row.
        var messages: List<ChatMessage> = listOf(ChatMessage.User("m1", 1L, text = "sent from the app"))
        messages = ChatReducer.reduceMessages(
            messages,
            ServerEvent.MessageUpserted(session, ChatMessage.User("m1", 1L, text = "")),
        )
        messages = ChatReducer.reduceMessages(
            messages,
            ServerEvent.PartUpserted(session, "m1", Part.Text("p1", "sent from the app")),
        )

        assertEquals(1, messages.size)
        assertEquals("sent from the app", (messages.single() as ChatMessage.User).text)
    }

    @Test
    fun `a re-emitted identical part does not double the user body`() {
        var messages = ChatReducer.reduceMessages(
            emptyList(),
            ServerEvent.MessageUpserted(session, ChatMessage.User("m1", 1L, text = "")),
        )
        repeat(3) {
            messages = ChatReducer.reduceMessages(
                messages,
                ServerEvent.PartUpserted(session, "m1", Part.Text("p1", "hi")),
            )
        }
        assertEquals("hi", (messages.single() as ChatMessage.User).text)
    }

    @Test
    fun `a user message attachment is kept`() {
        var messages = ChatReducer.reduceMessages(
            emptyList(),
            ServerEvent.MessageUpserted(session, ChatMessage.User("m1", 1L, text = "")),
        )
        messages = ChatReducer.reduceMessages(
            messages,
            ServerEvent.PartUpserted(
                session, "m1",
                Part.Attachment(
                    "p2",
                    dev.edgecode.core.model.Attachment(
                        id = "a1",
                        name = "shot.png",
                        mime = "image/png",
                        sizeBytes = 10,
                        kind = dev.edgecode.core.model.AttachmentKind.Image,
                    ),
                ),
            ),
        )

        val user = messages.single() as ChatMessage.User
        assertEquals(1, user.attachments.size)
        assertEquals("shot.png", user.attachments.single().name)
    }

    @Test
    fun `an attachment part arriving twice is not duplicated`() {
        val attachment = dev.edgecode.core.model.Attachment(
            id = "a1",
            name = "shot.png",
            mime = "image/png",
            sizeBytes = 10,
            kind = dev.edgecode.core.model.AttachmentKind.Image,
        )
        var messages = ChatReducer.reduceMessages(
            emptyList(),
            ServerEvent.MessageUpserted(session, ChatMessage.User("m1", 1L, text = "")),
        )
        repeat(2) {
            messages = ChatReducer.reduceMessages(
                messages,
                ServerEvent.PartUpserted(session, "m1", Part.Attachment("p2", attachment)),
            )
        }
        assertEquals(1, (messages.single() as ChatMessage.User).attachments.size)
    }

    // A reply is not sent as a series of whole parts. The server sends the part
    // once, empty, and then every character as a delta; the whole part is only
    // re-sent at the end. Ignoring deltas meant the transcript updated a handful
    // of times per turn instead of continuously, which reads as no streaming.

    @Test
    fun `deltas accumulate into the part being written`() {
        var messages = ChatReducer.reduceMessages(
            emptyList(),
            ServerEvent.MessageUpserted(session, assistant("m1")),
        )
        messages = ChatReducer.reduceMessages(
            messages,
            ServerEvent.PartUpserted(session, "m1", Part.Text("p1", "", streaming = true)),
        )
        listOf("Hel", "lo ", "there").forEach { chunk ->
            messages = ChatReducer.reduceMessages(
                messages,
                ServerEvent.PartDelta(session, "m1", "p1", "text", chunk),
            )
        }

        val part = (messages.single() as ChatMessage.Assistant).parts.single() as Part.Text
        assertEquals("Hello there", part.text)
        assertTrue(part.streaming)
    }

    @Test
    fun `a delta arriving before its part still starts it`() {
        var messages = ChatReducer.reduceMessages(
            emptyList(),
            ServerEvent.MessageUpserted(session, assistant("m1")),
        )
        messages = ChatReducer.reduceMessages(
            messages,
            ServerEvent.PartDelta(session, "m1", "p1", "text", "early"),
        )

        val part = (messages.single() as ChatMessage.Assistant).parts.single() as Part.Text
        assertEquals("early", part.text)
    }

    @Test
    fun `the final whole part replaces the accumulated result`() {
        // This is what makes delta accumulation safe: the server's own
        // accumulation always arrives last and wins outright.
        var messages = ChatReducer.reduceMessages(
            emptyList(),
            ServerEvent.MessageUpserted(session, assistant("m1")),
        )
        messages = ChatReducer.reduceMessages(
            messages,
            ServerEvent.PartUpserted(session, "m1", Part.Text("p1", "", streaming = true)),
        )
        messages = ChatReducer.reduceMessages(
            messages,
            ServerEvent.PartDelta(session, "m1", "p1", "text", "Hello"),
        )
        messages = ChatReducer.reduceMessages(
            messages,
            ServerEvent.PartUpserted(session, "m1", Part.Text("p1", "Hello")),
        )

        val part = (messages.single() as ChatMessage.Assistant).parts.single() as Part.Text
        assertEquals("Hello", part.text)
        assertFalse(part.streaming)
    }

    @Test
    fun `deltas accumulate into a reasoning part too`() {
        var messages = ChatReducer.reduceMessages(
            emptyList(),
            ServerEvent.MessageUpserted(session, assistant("m1")),
        )
        messages = ChatReducer.reduceMessages(
            messages,
            ServerEvent.PartUpserted(session, "m1", Part.Reasoning("p2", "")),
        )
        messages = ChatReducer.reduceMessages(
            messages,
            ServerEvent.PartDelta(session, "m1", "p2", "text", "thinking"),
        )

        val part = (messages.single() as ChatMessage.Assistant).parts.single() as Part.Reasoning
        assertEquals("thinking", part.text)
    }

    @Test
    fun `a delta for an unknown message is ignored rather than inventing one`() {
        val messages = ChatReducer.reduceMessages(
            emptyList(),
            ServerEvent.PartDelta(session, "nope", "p1", "text", "x"),
        )
        assertTrue(messages.isEmpty())
    }

    @Test
    fun `a session status event moves only the session it names`() {
        val sessions = listOf(
            dev.edgecode.core.model.SessionSummary("a", "A", "/", 0L, 0L),
            dev.edgecode.core.model.SessionSummary("b", "B", "/", 0L, 0L),
        )

        val result = ChatReducer.reduceSessions(
            sessions,
            ServerEvent.SessionStatusChanged(
                "b",
                dev.edgecode.core.model.SessionStatus.Working,
            ),
        )

        assertEquals(dev.edgecode.core.model.SessionStatus.Idle, result.first().status)
        assertEquals(dev.edgecode.core.model.SessionStatus.Working, result.last().status)
    }

    @Test
    fun `a non-text delta field is ignored`() {
        var messages = ChatReducer.reduceMessages(
            emptyList(),
            ServerEvent.MessageUpserted(session, assistant("m1")),
        )
        messages = ChatReducer.reduceMessages(
            messages,
            ServerEvent.PartUpserted(session, "m1", Part.Tool("t1", "grep", "Grep", ToolState())),
        )
        messages = ChatReducer.reduceMessages(
            messages,
            ServerEvent.PartDelta(session, "m1", "t1", "input", "{\"x\":1}"),
        )

        val part = (messages.single() as ChatMessage.Assistant).parts.single()
        assertTrue(part is Part.Tool)
    }
}
