package dev.edgecode.core.markdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

data class CodeColors(
    val plain: Color,
    val keyword: Color,
    val string: Color,
    val comment: Color,
    val number: Color,
)

/**
 * A deliberately conservative highlighter.
 *
 * It only colours things that are unambiguous across languages — comments,
 * string literals, numeric literals, and a small keyword set. Anything more
 * aggressive starts guessing at language semantics and produces confidently
 * wrong colouring, which reads worse than no highlighting at all.
 */
object SyntaxHighlighter {

    private val KEYWORDS = setOf(
        "abstract", "as", "async", "await", "break", "case", "catch", "class", "const",
        "constructor", "continue", "declare", "default", "def", "delete", "do", "else",
        "enum", "export", "extends", "false", "finally", "fn", "for", "from", "fun",
        "function", "if", "implements", "import", "in", "instanceof", "interface", "is",
        "lambda", "let", "match", "new", "none", "null", "object", "of", "package",
        "private", "protected", "public", "raise", "readonly", "return", "self", "static",
        "struct", "super", "switch", "this", "throw", "trait", "true", "try", "type",
        "typeof", "val", "var", "void", "when", "while", "with", "yield", "suspend",
    )

    fun highlight(code: String, colors: CodeColors): AnnotatedString = buildAnnotatedString {
        var i = 0
        while (i < code.length) {
            val c = code[i]

            when {
                c == '/' && i + 1 < code.length && code[i + 1] == '/' -> {
                    val end = code.indexOf('\n', i).let { if (it < 0) code.length else it }
                    withStyle(SpanStyle(color = colors.comment)) { append(code.substring(i, end)) }
                    i = end
                }

                c == '/' && i + 1 < code.length && code[i + 1] == '*' -> {
                    val close = code.indexOf("*/", i + 2)
                    val stop = if (close < 0) code.length else close + 2
                    withStyle(SpanStyle(color = colors.comment)) { append(code.substring(i, stop)) }
                    i = stop
                }

                c == '#' && isLineLeading(code, i) -> {
                    val end = code.indexOf('\n', i).let { if (it < 0) code.length else it }
                    withStyle(SpanStyle(color = colors.comment)) { append(code.substring(i, end)) }
                    i = end
                }

                c == '"' || c == '\'' || c == '`' -> {
                    val stop = scanString(code, i)
                    withStyle(SpanStyle(color = colors.string)) { append(code.substring(i, stop)) }
                    i = stop
                }

                c.isDigit() -> {
                    val stop = scanNumber(code, i)
                    withStyle(SpanStyle(color = colors.number)) { append(code.substring(i, stop)) }
                    i = stop
                }

                c.isLetter() || c == '_' -> {
                    val stop = scanWord(code, i)
                    val word = code.substring(i, stop)
                    if (word in KEYWORDS) {
                        withStyle(SpanStyle(color = colors.keyword)) { append(word) }
                    } else {
                        withStyle(SpanStyle(color = colors.plain)) { append(word) }
                    }
                    i = stop
                }

                else -> {
                    withStyle(SpanStyle(color = colors.plain)) { append(c) }
                    i++
                }
            }
        }
    }

    private fun isLineLeading(code: String, index: Int): Boolean {
        if (index == 0) return true
        val previous = code[index - 1]
        return previous == '\n' || previous == ' ' || previous == '\t'
    }

    private fun scanString(code: String, start: Int): Int {
        val quote = code[start]
        var i = start + 1
        while (i < code.length) {
            val c = code[i]
            if (c == '\\') {
                i += 2
                continue
            }
            if (c == quote) return i + 1
            if (c == '\n' && quote != '`') return i
            i++
        }
        return code.length
    }

    private fun scanNumber(code: String, start: Int): Int {
        var i = start
        while (i < code.length && (code[i].isLetterOrDigit() || code[i] == '.' || code[i] == '_')) i++
        return i
    }

    private fun scanWord(code: String, start: Int): Int {
        var i = start
        while (i < code.length && (code[i].isLetterOrDigit() || code[i] == '_')) i++
        return i
    }
}
