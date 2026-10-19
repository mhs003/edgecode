package dev.edgecode.data.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SseTest {

    private val parser = SseParser()

    /** A real `server.connected` frame as the OpenCode stream emits it. */
    private val connectedFrame =
        "data: {\"id\":\"evt_1509cec9b001xg0NncSaS8Mxqz\",\"type\":\"server.connected\"," +
            "\"properties\":{}}\n\n"

    private val connectedJson =
        "{\"id\":\"evt_1509cec9b001xg0NncSaS8Mxqz\",\"type\":\"server.connected\",\"properties\":{}}"

    @Test
    fun singleCompleteFrameInOneChunk() {
        val frames = parser.accept("data: hello\n\n")

        assertEquals(1, frames.size)
        assertEquals("hello", frames.single().data)
        assertNull(frames.single().event)
        assertNull(frames.single().id)
    }

    @Test
    fun realOpenCodeFrameShape() {
        val frames = parser.accept(connectedFrame)

        assertEquals(1, frames.size)
        val frame = frames.single()
        assertNull(frame.event)
        assertNull(frame.id)
        assertEquals(connectedJson, frame.data)
    }

    @Test
    fun twoFramesInOneChunk() {
        val frames = parser.accept("data: one\n\ndata: two\n\n")

        assertEquals(2, frames.size)
        assertEquals(listOf("one", "two"), frames.map { it.data })
    }

    @Test
    fun frameSplitMidData() {
        assertEquals(emptyList<SseFrame>(), parser.accept("data: he"))
        assertEquals(listOf("hello"), parser.accept("llo\n\n").map { it.data })
    }

    @Test
    fun frameSplitMidFieldName() {
        assertEquals(emptyList<SseFrame>(), parser.accept("da"))
        assertEquals(listOf("hello"), parser.accept("ta: hello\n\n").map { it.data })
    }

    @Test
    fun frameSplitBetweenCrAndLf() {
        assertEquals(emptyList<SseFrame>(), parser.accept("data: a\r"))
        assertEquals(listOf("a"), parser.accept("\n\r\n").map { it.data })
    }

    @Test
    fun frameSplitRightAfterTheColon() {
        assertEquals(emptyList<SseFrame>(), parser.accept("data:"))
        assertEquals(listOf("value"), parser.accept(" value\n\n").map { it.data })
    }

    @Test
    fun crlfProducesTheSameResultAsLf() {
        val lf = SseParser().accept("event: x\nid: 7\ndata: a\ndata: b\n\n")
        val crlf = SseParser().accept("event: x\r\nid: 7\r\ndata: a\r\ndata: b\r\n\r\n")

        assertEquals(lf, crlf)
        assertEquals(listOf("a\nb"), crlf.map { it.data })
    }

    @Test
    fun loneCrLineEndings() {
        assertEquals(emptyList<SseFrame>(), parser.accept("event: x\rdata: a\rdata: b\r"))
        assertEquals(emptyList<SseFrame>(), parser.accept("\r"))

        // The trailing lone `\r` only counts as a line break once the next
        // character proves it is not the first half of a CRLF.
        val frames = parser.accept("\n")

        assertEquals(1, frames.size)
        assertEquals("x", frames.single().event)
        assertEquals("a\nb", frames.single().data)
    }

    @Test
    fun commentLineAloneDispatchesNothing() {
        assertEquals(emptyList<SseFrame>(), parser.accept(": keep-alive\n\n"))
    }

    @Test
    fun commentLineFollowedByRealFrameYieldsOnlyTheRealFrame() {
        val frames = parser.accept(": keep-alive\ndata: real\n\n")

        assertEquals(1, frames.size)
        assertEquals("real", frames.single().data)
    }

    @Test
    fun commentInsideAFrameIsIgnored() {
        val frames = parser.accept("data: a\n: ignored\ndata: b\n\n")

        assertEquals(listOf("a\nb"), frames.map { it.data })
    }

    @Test
    fun multiLineDataJoinsWithNewlineAndStripsExactlyOne() {
        assertEquals(listOf("a\nb"), SseParser().accept("data: a\ndata: b\n\n").map { it.data })
        assertEquals(listOf("a\n"), SseParser().accept("data: a\ndata:\n\n").map { it.data })
    }

    @Test
    fun eventAndIdAreCaptured() {
        val frames = parser.accept("event: session.message\nid: 42\ndata: {}\n\n")

        assertEquals(1, frames.size)
        assertEquals("session.message", frames.single().event)
        assertEquals("42", frames.single().id)
        assertEquals("{}", frames.single().data)
    }

    @Test
    fun dataValueWithoutSpaceKeepsEverything() {
        assertEquals(listOf("value"), parser.accept("data:value\n\n").map { it.data })
    }

    @Test
    fun dataValueWithTwoSpacesStripsExactlyOne() {
        assertEquals(listOf(" value"), parser.accept("data:  value\n\n").map { it.data })
    }

    @Test
    fun frameWithEventButNoDataIsNotDispatched() {
        assertEquals(emptyList<SseFrame>(), parser.accept("event: ping\n\n"))
    }

    @Test
    fun unknownFieldsAreIgnored() {
        val frames = parser.accept("retry: 3000\nfoo: bar\ndata: x\n\n")

        assertEquals(listOf("x"), frames.map { it.data })
    }

    @Test
    fun frameIsOnlyCompletedByTheBlankLine() {
        assertEquals(emptyList<SseFrame>(), parser.accept("data: x\n"))
        assertEquals(listOf("x"), parser.accept("\n").map { it.data })
    }

    @Test
    fun oneCharacterAtATimeYieldsTheSameFrames() {
        val text = "event: session.message\nid: 42\ndata: {\"a\":1}\n\ndata: second\r\n\r\n"
        val expected = SseParser().accept(text)
        assertEquals(2, expected.size)

        val oneChar = SseParser()
        val actual = mutableListOf<SseFrame>()
        for (c in text) {
            actual += oneChar.accept(c.toString())
        }

        assertEquals(expected, actual)
    }

    @Test
    fun emptyChunksAreNoOps() {
        assertEquals(emptyList<SseFrame>(), parser.accept(""))
        assertEquals(emptyList<SseFrame>(), parser.accept("data: x\n"))
        assertEquals(emptyList<SseFrame>(), parser.accept(""))
        assertEquals(listOf("x"), parser.accept("\n").map { it.data })
    }

    @Test
    fun resetClearsBufferedPartialState() {
        assertEquals(emptyList<SseFrame>(), parser.accept("data: partial\r"))

        parser.reset()

        assertEquals(emptyList<SseFrame>(), parser.accept("\n"))
        val frames = parser.accept("data: fresh\n\n")
        assertEquals(listOf("fresh"), frames.map { it.data })
        assertTrue(frames.single().event == null)
    }

    @Test
    fun resetClearsAnAccumulatedFrame() {
        parser.accept("event: stale\ndata: a\n")
        parser.reset()

        val frames = parser.accept("data: b\n\n")
        assertEquals(1, frames.size)
        assertNull(frames.single().event)
        assertEquals("b", frames.single().data)
    }
}
