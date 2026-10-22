package dev.edgecode.core.model

/**
 * The answer being assembled for one question.
 *
 * [selected] holds option labels. [custom] holds the free-text answer when the
 * question allows one. For a single-select question the two are mutually
 * exclusive; for a multi-select question they are combined.
 */
data class QuestionAnswerDraft(
    val selected: Set<String> = emptySet(),
    val custom: String = "",
) {
    /**
     * The answer as the server expects it: an array of labels.
     *
     * Selected labels follow the order the server offered them, so the payload
     * is stable across recompositions. A custom answer is appended last for a
     * multi-select question, and replaces the selection for a single-select one.
     */
    fun answer(question: QuestionInfo): List<String> {
        val typed = custom.trim()
        return if (question.multiple) {
            buildList {
                question.options.map { it.label }
                    .filter { it in selected }
                    .forEach { add(it) }
                if (typed.isNotEmpty() && none { it == typed }) add(typed)
            }
        } else {
            if (typed.isNotEmpty()) listOf(typed) else question.options.map { it.label }.filter { it in selected }.take(1)
        }
    }

    /**
     * A question with no options and no custom field is satisfied by an empty
     * answer; requiring an unanswerable question would strand the agent.
     */
    fun isAnswered(question: QuestionInfo): Boolean =
        answer(question).isNotEmpty() || (question.options.isEmpty() && !question.custom)

    fun selecting(question: QuestionInfo, label: String): QuestionAnswerDraft =
        if (question.multiple) {
            copy(selected = if (label in selected) selected - label else selected + label)
        } else {
            copy(selected = setOf(label), custom = "")
        }

    fun typing(question: QuestionInfo, text: String): QuestionAnswerDraft =
        if (question.multiple) {
            copy(custom = text)
        } else {
            copy(custom = text, selected = if (text.isBlank()) selected else emptySet())
        }
}

/** A draft for every question in one request, in the request's order. */
data class QuestionDraft(
    val answers: List<QuestionAnswerDraft> = emptyList(),
) {
    companion object {
        fun forQuestions(questions: List<QuestionInfo>): QuestionDraft =
            QuestionDraft(List(questions.size) { QuestionAnswerDraft() })
    }

    fun at(index: Int): QuestionAnswerDraft = answers.getOrNull(index) ?: QuestionAnswerDraft()

    fun select(index: Int, question: QuestionInfo, label: String): QuestionDraft =
        replacing(index, at(index).selecting(question, label))

    fun type(index: Int, question: QuestionInfo, text: String): QuestionDraft =
        replacing(index, at(index).typing(question, text))

    fun isComplete(questions: List<QuestionInfo>): Boolean =
        questions.indices.all { at(it).isAnswered(questions[it]) }

    fun toAnswers(questions: List<QuestionInfo>): List<List<String>> =
        questions.indices.map { at(it).answer(questions[it]) }

    private fun replacing(index: Int, value: QuestionAnswerDraft): QuestionDraft {
        val next = answers.toMutableList()
        while (next.size <= index) next += QuestionAnswerDraft()
        next[index] = value
        return copy(answers = next)
    }
}
