package dev.edgecode.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelSelectionTest {

    private val sonnet = ModelRef("anthropic", "claude-sonnet-4.5")
    private val gpt = ModelRef("openai", "gpt-5.2-codex")

    @Test
    fun `a chat with no history inherits the last model used anywhere`() {
        val selection = ModelSelection(recent = listOf(gpt, sonnet))
        assertEquals(gpt, selection.forSession("ses_new"))
    }

    @Test
    fun `a chat shows its own model, not another chat's`() {
        // The bug this exists to prevent: picking gpt in one chat must not make
        // it appear in a chat that has been using sonnet.
        val selection = ModelSelection(
            committed = mapOf("ses_a" to gpt, "ses_b" to sonnet),
            recent = listOf(gpt),
        )
        assertEquals(sonnet, selection.forSession("ses_b"))
        assertEquals(gpt, selection.forSession("ses_a"))
    }

    @Test
    fun `choosing a model does not commit it to the chat`() {
        val selection = ModelSelection(recent = listOf(gpt))
            .choosing("ses_a", sonnet)

        assertEquals(sonnet, selection.forSession("ses_a"))
        assertEquals(emptyMap<String, ModelRef>(), selection.committed)
        assertEquals(listOf(gpt), selection.recent)
    }

    @Test
    fun `sending commits the choice to the chat and to the recent list`() {
        val selection = ModelSelection(recent = listOf(gpt))
            .choosing("ses_a", sonnet)
            .sent("ses_a", sonnet)

        assertEquals(sonnet, selection.committed["ses_a"])
        assertEquals(listOf(sonnet, gpt), selection.recent)
        assertNull(selection.pending["ses_a"])
    }

    @Test
    fun `a chat keeps using its committed model after another chat changes`() {
        val selection = ModelSelection(recent = listOf(gpt))
            .sent("ses_a", sonnet)
            .choosing("ses_b", gpt)
            .sent("ses_b", gpt)

        assertEquals(sonnet, selection.forSession("ses_a"))
        assertEquals(gpt, selection.forSession("ses_b"))
        // The most recent send is what a brand new chat inherits.
        assertEquals(gpt, selection.forSession("ses_new"))
    }

    @Test
    fun `re-sending the same model moves it to the front without duplicating`() {
        val selection = ModelSelection(recent = listOf(gpt, sonnet))
            .sent("ses_a", sonnet)

        assertEquals(listOf(sonnet, gpt), selection.recent)
    }

    @Test
    fun `the recent list is capped`() {
        var selection = ModelSelection()
        repeat(10) { i ->
            selection = selection.sent("ses_$i", ModelRef("p", "m$i"))
        }
        assertEquals(ModelSelection.RecentLimit, selection.recent.size)
        assertEquals("m9", selection.recent.first().modelId)
    }

    @Test
    fun `a key round-trips and keeps slashes in the model id`() {
        val ref = ModelRef("openrouter", "anthropic/claude-sonnet")
        assertEquals(ref, ModelRef.parse(ref.key))
        assertEquals("anthropic/claude-sonnet", ModelRef.parse(ref.key)?.modelId)
    }

    @Test
    fun `a malformed key does not parse`() {
        assertNull(ModelRef.parse(""))
        assertNull(ModelRef.parse("/noprovider"))
        assertNull(ModelRef.parse("noprovider/"))
        assertNull(ModelRef.parse("noseparator"))
    }
}
