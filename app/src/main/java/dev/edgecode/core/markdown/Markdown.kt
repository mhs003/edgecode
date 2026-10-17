package dev.edgecode.core.markdown

/**
 * A small CommonMark subset, chosen for what agent output actually contains:
 * headings, paragraphs, fenced code, lists, task lists, quotes, tables, rules.
 *
 * Written by hand rather than pulled from a library for two reasons. Rendering
 * has to survive *incomplete* input, because assistant text arrives a few
 * characters at a time — an unterminated fence must still render as a code
 * block that fills in. And the transcript needs to control styling block by
 * block rather than hand a whole document to a converter.
 */
sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Code(val language: String?, val code: String, val closed: Boolean) : MdBlock
    data class Bullets(val items: List<MdItem>) : MdBlock
    data class Ordered(val items: List<MdItem>, val start: Int) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MdBlock
    data object Rule : MdBlock
}

data class MdItem(val text: String, val checked: Boolean? = null)

object Markdown {

    fun parse(source: String): List<MdBlock> {
        val lines = source.replace("\r\n", "\n").split("\n")
        val blocks = mutableListOf<MdBlock>()
        val paragraph = mutableListOf<String>()
        var i = 0

        fun flushParagraph() {
            if (paragraph.isNotEmpty()) {
                blocks += MdBlock.Paragraph(paragraph.joinToString("\n").trim())
                paragraph.clear()
            }
        }

        while (i < lines.size) {
            val trimmed = lines[i].trimStart()

            val fence = fenceMarker(trimmed)
            if (fence != null) {
                flushParagraph()
                val language = trimmed.removePrefix(fence).trim().ifBlank { null }
                val body = mutableListOf<String>()
                i++
                var closed = false
                while (i < lines.size) {
                    if (lines[i].trimStart().startsWith(fence)) {
                        closed = true
                        i++
                        break
                    }
                    body += lines[i]
                    i++
                }
                blocks += MdBlock.Code(language, body.joinToString("\n"), closed)
                continue
            }

            when {
                trimmed.isBlank() -> {
                    flushParagraph()
                    i++
                }

                isRule(trimmed) -> {
                    flushParagraph()
                    blocks += MdBlock.Rule
                    i++
                }

                headingLevel(trimmed) > 0 -> {
                    flushParagraph()
                    val level = headingLevel(trimmed)
                    blocks += MdBlock.Heading(level, trimmed.drop(level).trim())
                    i++
                }

                trimmed.startsWith(">") -> {
                    flushParagraph()
                    val quote = mutableListOf<String>()
                    while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                        quote += lines[i].trimStart().removePrefix(">").removePrefix(" ").trimEnd()
                        i++
                    }
                    blocks += MdBlock.Quote(quote.joinToString("\n").trim())
                }

                isBullet(trimmed) -> {
                    flushParagraph()
                    val items = mutableListOf<MdItem>()
                    while (i < lines.size && isBullet(lines[i].trimStart())) {
                        items += parseBullet(lines[i].trimStart())
                        i++
                    }
                    blocks += MdBlock.Bullets(items)
                }

                isOrdered(trimmed) -> {
                    flushParagraph()
                    val start = orderedNumber(trimmed)
                    val items = mutableListOf<MdItem>()
                    while (i < lines.size && isOrdered(lines[i].trimStart())) {
                        val rest = lines[i].trimStart().substringAfter(". ").trim()
                        items += MdItem(rest)
                        i++
                    }
                    blocks += MdBlock.Ordered(items, start)
                }

                isTableRow(trimmed) &&
                    i + 1 < lines.size &&
                    isTableSeparator(lines[i + 1].trim()) -> {
                    flushParagraph()
                    val headers = splitRow(trimmed)
                    i += 2
                    val rows = mutableListOf<List<String>>()
                    while (i < lines.size && isTableRow(lines[i].trim())) {
                        rows += splitRow(lines[i].trim())
                        i++
                    }
                    blocks += MdBlock.Table(headers, rows)
                }

                else -> {
                    paragraph += lines[i]
                    i++
                }
            }
        }

        flushParagraph()
        return blocks
    }

    // --- line helpers ------------------------------------------------------

    private fun fenceMarker(line: String): String? = when {
        line.startsWith("```") -> "```"
        line.startsWith("~~~") -> "~~~"
        else -> null
    }

    private fun isRule(line: String): Boolean {
        val stripped = line.replace(" ", "")
        if (stripped.length < 3) return false
        val first = stripped.first()
        return (first == '-' || first == '*' || first == '_') && stripped.all { it == first }
    }

    private fun headingLevel(line: String): Int {
        var count = 0
        while (count < line.length && line[count] == '#') count++
        if (count == 0 || count > 6) return 0
        return if (count < line.length && line[count] == ' ') count else 0
    }

    private fun isBullet(line: String): Boolean =
        (line.startsWith("- ") || line.startsWith("* ") || line.startsWith("+ "))

    private fun parseBullet(line: String): MdItem {
        val body = line.substring(2).trim()
        val checkbox = when {
            body.startsWith("[ ] ") -> false
            body.startsWith("[x] ") || body.startsWith("[X] ") -> true
            body == "[ ]" -> false
            body == "[x]" || body == "[X]" -> true
            else -> null
        }
        return if (checkbox != null) {
            MdItem(body.removePrefix("[ ] ").removePrefix("[x] ").removePrefix("[X] ").trim(), checkbox)
        } else {
            MdItem(body)
        }
    }

    private fun isOrdered(line: String): Boolean {
        var index = 0
        while (index < line.length && line[index].isDigit()) index++
        return index > 0 && index + 1 < line.length &&
            line[index] == '.' && line[index + 1] == ' '
    }

    private fun orderedNumber(line: String): Int =
        line.takeWhile { it.isDigit() }.toIntOrNull() ?: 1

    private fun isTableRow(line: String): Boolean = line.contains('|')

    private fun isTableSeparator(line: String): Boolean {
        if (!line.contains('-')) return false
        return line.all { it == '|' || it == '-' || it == ':' || it == ' ' }
    }

    private fun splitRow(line: String): List<String> {
        val parts = line.split('|').map { it.trim() }
        val start = if (parts.firstOrNull().isNullOrEmpty()) 1 else 0
        val end = if (parts.size - start > 0 && parts.last().isEmpty()) parts.size - 1 else parts.size
        return parts.subList(start, end.coerceAtLeast(start))
    }
}
