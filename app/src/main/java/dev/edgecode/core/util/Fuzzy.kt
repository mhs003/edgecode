package dev.edgecode.core.util

/**
 * Subsequence matching, in the style of a command line palette.
 *
 * A query is split on whitespace and every token must appear in the target. The
 * tokens are matched independently, so their order does not matter: "sonnet
 * anthropic" and "anthropic sonnet" both find the same model. That is what lets
 * a model name and its provider be typed together without remembering which
 * side of the slash comes first.
 *
 * Each token is matched against each field on its own, never against the fields
 * concatenated. Gluing them together lets a token be assembled from the tail of
 * one field and the head of the next: "glm" found "GPT-5.6 Sol" + "CommandCode"
 * by picking up g, l and m from three different words, which made every search
 * return everything.
 *
 * A scattered match is only allowed when the characters are packed roughly as
 * tightly as the token is long. Without that, short tokens match almost any
 * string, and a search for a model nobody has still returns a full page.
 *
 * A contiguous occurrence outranks a scattered one, and a match at the start of
 * a word outranks a match in the middle, so typing a prefix puts the obvious
 * answer first.
 */
object Fuzzy {

    private const val CONTIGUOUS = 1000
    private const val PER_CONTIGUOUS_CHAR = 4
    private const val WORD_START = 40
    private const val CONSECUTIVE = 12
    private const val GAP = 1
    private const val LEADING = 1

    /**
     * How much wider than the token a scattered match may be. A token of three
     * characters may not be assembled from an 18-character stretch of text.
     */
    private const val SCATTER_SLACK = 4
    private const val SCATTER_FACTOR = 3

    /** Null when [query] does not match [target] at all. Higher is better. */
    fun score(query: String, target: String): Int? = score(query, listOf(target))

    /**
     * Matches against a set of fields. A token must match at least one field;
     * its best field wins. [fields] are usually a model's name and its provider.
     *
     * Do not pass a machine identifier alongside the name. Ids are slugs of the
     * name and add only noise: "glm" matched "google/gemini-3.6-flash" through
     * g, l and m picked out of the provider prefix and "gemini", which returned
     * every Gemini for a GLM search.
     */
    fun score(query: String, fields: List<String>): Int? {
        val tokens = query.lowercase().split(' ', '\t', '\n').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return 0

        val haystacks = fields.map { it.lowercase() }.filter { it.isNotEmpty() }
        if (haystacks.isEmpty()) return null

        var total = 0
        for (token in tokens) {
            var best: Int? = null
            for (haystack in haystacks) {
                val score = tokenScore(token, haystack) ?: continue
                if (best == null || score > best) best = score
            }
            total += best ?: return null
        }
        return total
    }

    /** Every item that matches, best first. Order is otherwise preserved. */
    fun <T> rank(query: String, items: List<T>, fields: (T) -> List<String>): List<T> =
        items.asSequence()
            .mapNotNull { item -> score(query, fields(item))?.let { it to item } }
            .sortedByDescending { it.first }
            .map { it.second }
            .toList()

    private fun tokenScore(token: String, haystack: String): Int? {
        // Every contiguous occurrence is scored, not just the first: "net" in
        // "sonnet net" should be found at the word boundary, not inside
        // "sonnet". Taking indexOf alone made those two rank identically.
        var best = Int.MIN_VALUE
        var from = 0
        while (true) {
            val at = haystack.indexOf(token, from)
            if (at < 0) break
            var score = CONTIGUOUS + token.length * PER_CONTIGUOUS_CHAR
            if (at == 0 || isBoundary(haystack, at)) score += WORD_START
            score -= minOf(at, 60) * LEADING
            if (score > best) best = score
            from = at + 1
        }
        if (best != Int.MIN_VALUE) return best

        var matched = 0
        var score = 0
        var previous = -2
        var first = -1
        for (i in haystack.indices) {
            if (haystack[i] != token[matched]) continue
            if (first < 0) first = i
            score += 1
            if (i == previous + 1) {
                score += CONSECUTIVE
            } else {
                score -= minOf(i - previous - 1, 10) * GAP
            }
            if (i == 0 || isBoundary(haystack, i)) score += WORD_START
            previous = i
            matched++
            if (matched == token.length) {
                val span = i - first + 1
                val ceiling = token.length * SCATTER_FACTOR + SCATTER_SLACK
                return if (span <= ceiling) score else null
            }
        }
        return null
    }

    private fun isBoundary(text: String, index: Int): Boolean = !text[index - 1].isLetterOrDigit()
}
