package dev.edgecode.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionDraftTest {

    private fun single(
        options: List<String> = listOf("Phone", "Tablet", "Both"),
        custom: Boolean = true,
    ) = QuestionInfo(
        question = "Which target?",
        header = "Target",
        options = options.map { QuestionOption(it, "") },
        multiple = false,
        custom = custom,
    )

    private fun multi(
        options: List<String> = listOf("Unit tests", "UI test", "Contrast"),
        custom: Boolean = true,
    ) = QuestionInfo(
        question = "Which checks?",
        header = "Checks",
        options = options.map { QuestionOption(it, "") },
        multiple = true,
        custom = custom,
    )

    @Test
    fun `a single select keeps exactly one option`() {
        val q = single()
        var draft = QuestionDraft.forQuestions(listOf(q))
        draft = draft.select(0, q, "Phone")
        assertEquals(listOf("Phone"), draft.toAnswers(listOf(q)).single())

        draft = draft.select(0, q, "Tablet")
        assertEquals(listOf("Tablet"), draft.toAnswers(listOf(q)).single())
    }

    @Test
    fun `typing a custom answer replaces a single selection`() {
        val q = single()
        var draft = QuestionDraft.forQuestions(listOf(q))
            .select(0, q, "Phone")
            .type(0, q, "  Both, but smaller  ")

        assertEquals(listOf("Both, but smaller"), draft.toAnswers(listOf(q)).single())
    }

    @Test
    fun `a multi select combines options and custom text in option order`() {
        val q = multi()
        var draft = QuestionDraft.forQuestions(listOf(q))
            .select(0, q, "Contrast")
            .select(0, q, "Unit tests")
            .type(0, q, "Accessibility")

        assertEquals(
            listOf("Unit tests", "Contrast", "Accessibility"),
            draft.toAnswers(listOf(q)).single(),
        )
    }

    @Test
    fun `a custom answer identical to an option is not duplicated`() {
        val q = multi()
        val draft = QuestionDraft.forQuestions(listOf(q))
            .select(0, q, "Unit tests")
            .type(0, q, "Unit tests")

        assertEquals(listOf("Unit tests"), draft.toAnswers(listOf(q)).single())
    }

    @Test
    fun `a custom-only question is answered by typing`() {
        val q = QuestionInfo(question = "Name it", header = "Name", custom = true)
        val empty = QuestionDraft.forQuestions(listOf(q))
        assertFalse(empty.isComplete(listOf(q)))

        val typed = empty.type(0, q, "EdgeCode")
        assertTrue(typed.isComplete(listOf(q)))
        assertEquals(listOf(listOf("EdgeCode")), typed.toAnswers(listOf(q)))
    }

    @Test
    fun `a question with no options and no custom is answerable empty`() {
        val q = QuestionInfo(question = "Acknowledge", header = "Notice")
        val draft = QuestionDraft.forQuestions(listOf(q))

        assertTrue(draft.isComplete(listOf(q)))
        assertEquals(listOf(emptyList<String>()), draft.toAnswers(listOf(q)))
    }

    @Test
    fun `an unanswered choice question blocks completion`() {
        val q = single()
        assertFalse(QuestionDraft.forQuestions(listOf(q)).isComplete(listOf(q)))
    }

    @Test
    fun `answers preserve the request's question order`() {
        val first = single()
        val second = multi()
        val draft = QuestionDraft.forQuestions(listOf(first, second))
            .select(0, first, "Both")
            .select(1, second, "UI test")

        assertEquals(
            listOf(listOf("Both"), listOf("UI test")),
            draft.toAnswers(listOf(first, second)),
        )
    }
}
