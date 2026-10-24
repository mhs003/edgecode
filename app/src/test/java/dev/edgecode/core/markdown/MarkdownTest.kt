package dev.edgecode.core.markdown

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTest {

    @Test
    fun `parses headings at each level`() {
        val blocks = Markdown.parse("# One\n\n### Three")
        assertEquals(2, blocks.size)
        assertEquals(MdBlock.Heading(1, "One"), blocks[0])
        assertEquals(MdBlock.Heading(3, "Three"), blocks[1])
    }

    @Test
    fun `parses a fenced code block with its language`() {
        val blocks = Markdown.parse("```kotlin\nval x = 1\n```")
        val code = blocks.single() as MdBlock.Code
        assertEquals("kotlin", code.language)
        assertEquals("val x = 1", code.code)
        assertTrue(code.closed)
    }

    /**
     * The streaming case: assistant text arrives mid-fence, so the block must
     * render as open rather than being discarded as malformed.
     */
    @Test
    fun `renders an unterminated fence as an open code block`() {
        val blocks = Markdown.parse("```ts\nconst a = 1")
        val code = blocks.single() as MdBlock.Code
        assertEquals("ts", code.language)
        assertEquals("const a = 1", code.code)
        assertFalse(code.closed)
    }

    @Test
    fun `parses bullets and task items`() {
        val blocks = Markdown.parse("- plain\n- [x] done\n- [ ] todo")
        val list = blocks.single() as MdBlock.Bullets
        assertEquals(3, list.items.size)
        assertEquals(null, list.items[0].checked)
        assertEquals(true, list.items[1].checked)
        assertEquals(false, list.items[2].checked)
        assertEquals("done", list.items[1].text)
    }

    @Test
    fun `parses ordered lists preserving the start number`() {
        val blocks = Markdown.parse("3. three\n4. four")
        val list = blocks.single() as MdBlock.Ordered
        assertEquals(3, list.start)
        assertEquals(listOf("three", "four"), list.items.map { it.text })
    }

    @Test
    fun `parses a table with headers and rows`() {
        val blocks = Markdown.parse("| Step | Command |\n| --- | --- |\n| Cut | make release |")
        val table = blocks.single() as MdBlock.Table
        assertEquals(listOf("Step", "Command"), table.headers)
        assertEquals(1, table.rows.size)
        assertEquals(listOf("Cut", "make release"), table.rows.first())
    }

    @Test
    fun `parses quotes and rules`() {
        val blocks = Markdown.parse("> note\n\n---")
        assertEquals(MdBlock.Quote("note"), blocks[0])
        assertEquals(MdBlock.Rule, blocks[1])
    }

    @Test
    fun `keeps consecutive lines in one paragraph`() {
        val blocks = Markdown.parse("line one\nline two\n\nsecond")
        assertEquals(2, blocks.size)
        assertEquals("line one\nline two", (blocks[0] as MdBlock.Paragraph).text)
    }

    @Test
    fun `marks inline code ranges for the chip background`() {
        val colors = InlineColors(
            text = Color.Black,
            code = Color.Black,
            codeBackground = Color.LightGray,
            link = Color.Blue,
        )
        val rendered = renderInline("use `val x = 1` here", colors)
        val ranges = rendered.getStringAnnotations(MD_CODE_TAG, 0, rendered.length)
        assertEquals(1, ranges.size)
        assertEquals("val x = 1", rendered.text.substring(ranges.single().start, ranges.single().end))
    }
}
