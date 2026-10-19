package dev.edgecode.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzyTest {

    private data class Model(val name: String, val provider: String, val id: String = "")

    private fun Model.fields() = listOf(name, provider, id)

    @Test
    fun `a contiguous prefix matches`() {
        assertNotNull(Fuzzy.score("sonnet", "Claude Sonnet 4.5"))
    }

    @Test
    fun `matching is case insensitive`() {
        assertNotNull(Fuzzy.score("SONNET", "claude sonnet"))
        assertNotNull(Fuzzy.score("Sonnet", "CLAUDE SONNET"))
    }

    @Test
    fun `a query that is not a subsequence does not match`() {
        assertNull(Fuzzy.score("zzz", "claude sonnet"))
        // Right characters, wrong order.
        assertNull(Fuzzy.score("netclair", "clairenet"))
    }

    @Test
    fun `an empty query matches everything`() {
        assertEquals(0, Fuzzy.score("", "anything"))
        assertEquals(0, Fuzzy.score("   ", "anything"))
    }

    // The reason this exists: a user types the model and the provider together,
    // in whichever order comes to mind.

    @Test
    fun `model then provider matches`() {
        assertNotNull(Fuzzy.score("glm commandcode", listOf("GLM-5.3-Flash", "commandcode")))
    }

    @Test
    fun `provider then model matches`() {
        assertNotNull(Fuzzy.score("commandcode glm", listOf("GLM-5.3-Flash", "commandcode")))
    }

    @Test
    fun `a partially typed model name still matches`() {
        assertNotNull(Fuzzy.score("gpt 5 openai", listOf("gpt-5.2-codex", "openai")))
    }

    // The bug that made every search return everything: fields were joined into
    // one string, so a token could be assembled from the tail of one field and
    // the head of the next.

    @Test
    fun `a token cannot be assembled across two fields`() {
        // "glm" exists here only as g from "GPT", l from "Sol", m from
        // "CommandCode" — one character from each word of a joined string.
        assertNull(Fuzzy.score("glm", listOf("GPT-5.6 Sol", "CommandCode", "gpt-5.6-sol")))
    }

    @Test
    fun `glm commandcode finds only the glm models from that provider`() {
        val models = listOf(
            Model("GPT-5.6 Sol", "commandcode", "gpt-5.6-sol"),
            Model("GPT-6.1 Sol", "commandcode", "gpt-6.1-sol"),
            Model("Gemini 4 Argon", "commandcode", "gemini-4-argon"),
            Model("GLM-5.2", "commandcode", "zai-org/GLM-5.2"),
            Model("GLM-5.3-Flash", "commandcode", "z-ai/glm-5.3-flash"),
            Model("GLM-5.3", "agentrouter", "glm-5.3"),
            Model("GLM-5.3-Flash Free", "opencode", "glm-5.3-flash-free"),
        )
        val ranked = Fuzzy.rank("glm commandcode", models) { it.fields() }

        assertEquals(
            listOf("GLM-5.2", "GLM-5.3-Flash"),
            ranked.map { it.name },
        )
    }

    @Test
    fun `a model identifier must not be used as a search field`() {
        // "google/gemini-3.6-flash" contains g, l and m within ten characters,
        // so including the id made every Gemini match a search for "glm".
        assertNull(Fuzzy.score("glm", listOf("Gemini 3.6 Flash", "CommandCode")))

        val gemini = Model("Gemini 3.6 Flash", "CommandCode", "google/gemini-3.6-flash")
        assertEquals(
            emptyList<String>(),
            Fuzzy.rank("glm commandcode", listOf(gemini)) { listOf(it.name, it.provider) }
                .map { it.name },
        )
    }

    @Test
    fun `a scattered match must still be densely packed`() {
        // Three characters spread over an 18-character word are not a match.
        assertNull(Fuzzy.score("glm", "gpt-5.6 sol commandcode"))
        // The same characters close together are.
        assertNotNull(Fuzzy.score("glm", "glm-5.3"))
    }

    @Test
    fun `a contiguous match outranks a scattered one`() {
        val contiguous = Fuzzy.score("flash", "GLM Flash")!!
        val scattered = Fuzzy.score("flash", "flxxash")!!
        assertTrue(contiguous > scattered)
    }

    @Test
    fun `a word start outranks a match in the middle of a word`() {
        val atWordStart = Fuzzy.score("net", "sonnet net")!!
        val inside = Fuzzy.score("net", "sonnet")!!
        assertTrue(atWordStart > inside)
    }

    @Test
    fun `dashes and dots are ordinary characters in the haystack`() {
        assertNotNull(Fuzzy.score("5.3", "GLM-5.3-Flash"))
        assertNotNull(Fuzzy.score("glm-5.3", "GLM-5.3-Flash"))
    }

    @Test
    fun `rank returns matches best first and drops the rest`() {
        val models = listOf(
            Model("OpenAI GPT-5", "openai"),
            Model("Anthropic Claude Sonnet 4.5", "anthropic"),
            Model("OpenCode Zen", "opencode"),
        )
        val ranked = Fuzzy.rank("sonnet claude", models) { it.fields() }
        assertEquals(listOf("Anthropic Claude Sonnet 4.5"), ranked.map { it.name })
    }

    @Test
    fun `rank prefers the better match`() {
        // Same token in three places: as a prefix, after a separator, and
        // buried in a word. Order must follow that quality, not the input.
        val models = listOf(
            Model("something gptish", "p"),
            Model("reply-gpt-model", "p"),
            Model("gpt-5", "p"),
        )
        val ranked = Fuzzy.rank("gpt", models) { it.fields() }
        assertEquals("gpt-5", ranked.first().name)
        assertEquals("reply-gpt-model", ranked[1].name)
        assertEquals(3, ranked.size)
    }

    @Test
    fun `rank keeps the original order for equal scores`() {
        val models = listOf(
            Model("alpha one", "p"),
            Model("alpha two", "p"),
            Model("alpha three", "p"),
        )
        assertEquals(models, Fuzzy.rank("alpha", models) { it.fields() })
    }
}
