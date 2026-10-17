package dev.edgecode.data

import dev.edgecode.core.model.ChatMessage
import dev.edgecode.core.model.Part
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
}
