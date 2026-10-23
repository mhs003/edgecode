package dev.edgecode.core.markdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit

/** Tag used for clickable links inside rendered markdown. */
const val MD_URL_TAG = "md-url"

data class InlineColors(
    val text: Color,
    val code: Color,
    val codeBackground: Color,
    val link: Color,
)

/**
 * Inline markdown to an [AnnotatedString].
 *
 * Handles the spans that appear in real assistant output — bold, italic,
 * inline code, strikethrough and links — and, importantly, leaves unmatched
 * delimiters as literal text. Half-typed `**bold` during streaming should read
 * as `**bold`, not vanish.
 */
fun renderInline(text: String, colors: InlineColors): AnnotatedString =
    buildAnnotatedString {
        appendInline(text, SpanStyle(color = colors.text), colors, 0)
    }

private const val MAX_DEPTH = 6

private fun AnnotatedString.Builder.appendInline(
    text: String,
    base: SpanStyle,
    colors: InlineColors,
    depth: Int,
) {
    if (depth > MAX_DEPTH) {
        withStyle(base) { append(text) }
        return
    }

    val plain = StringBuilder()
    var i = 0

    fun flush() {
        if (plain.isNotEmpty()) {
            withStyle(base) { append(plain.toString()) }
            plain.setLength(0)
        }
    }

    while (i < text.length) {
        val c = text[i]
        when {
            c == '\\' && i + 1 < text.length -> {
                // Backslash escape: emit the next character literally.
                plain.append(text[i + 1])
                i += 2
            }

            c == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end > i + 1) {
                    flush()
                    withStyle(inlineCodeStyle(base, colors)) { append(text.substring(i + 1, end)) }
                    i = end + 1
                } else {
                    plain.append(c)
                    i++
                }
            }

            text.startsWith("**", i) || text.startsWith("__", i) -> {
                val delimiter = text.substring(i, i + 2)
                val end = text.indexOf(delimiter, i + 2)
                if (end > i + 2) {
                    flush()
                    val style = base.merge(SpanStyle(fontWeight = FontWeight.SemiBold))
                    withStyle(style) {
                        appendInline(text.substring(i + 2, end), style, colors, depth + 1)
                    }
                    i = end + 2
                } else {
                    plain.append(c)
                    i++
                }
            }

            text.startsWith("~~", i) -> {
                val end = text.indexOf("~~", i + 2)
                if (end > i + 2) {
                    flush()
                    val style = base.merge(
                        SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough),
                    )
                    withStyle(style) {
                        appendInline(text.substring(i + 2, end), style, colors, depth + 1)
                    }
                    i = end + 2
                } else {
                    plain.append(c)
                    i++
                }
            }

            c == '*' || c == '_' -> {
                val end = text.indexOf(c, i + 1)
                if (end > i + 1) {
                    flush()
                    val style = base.merge(SpanStyle(fontStyle = FontStyle.Italic))
                    withStyle(style) {
                        appendInline(text.substring(i + 1, end), style, colors, depth + 1)
                    }
                    i = end + 1
                } else {
                    plain.append(c)
                    i++
                }
            }

            c == '[' -> {
                val close = text.indexOf(']', i + 1)
                val paren = if (close > 0 && close + 1 < text.length && text[close + 1] == '(') close + 1 else -1
                val urlEnd = if (paren > 0) text.indexOf(')', paren + 1) else -1
                if (close > i + 1 && urlEnd > paren) {
                    flush()
                    val label = text.substring(i + 1, close)
                    val url = text.substring(paren + 1, urlEnd)
                    val style = base.merge(
                        SpanStyle(
                            color = colors.link,
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                        ),
                    )
                    val start = length
                    withStyle(style) { appendInline(label, style, colors, depth + 1) }
                    addLink(
                        LinkAnnotation.Url(
                            url = url,
                            styles = TextLinkStyles(style = style),
                        ),
                        start = start,
                        end = length,
                    )
                    i = urlEnd + 1
                } else {
                    plain.append(c)
                    i++
                }
            }

            else -> {
                plain.append(c)
                i++
            }
        }
    }

    flush()
}

/**
 * Inline code sits slightly below body size so it does not disturb line rhythm.
 *
 * The family is inherited rather than forced to `Monospace`: the app's paragraph
 * typeface is already monospaced, and inheriting keeps inline code on the same
 * ligature path as the text around it.
 */
private fun inlineCodeStyle(base: SpanStyle, colors: InlineColors): SpanStyle {
    val size = base.fontSize
    return base.merge(
        SpanStyle(
            color = colors.code,
            background = colors.codeBackground,
            // An unspecified size cannot be scaled; leave it to inherit instead.
            fontSize = if (size == TextUnit.Unspecified) TextUnit.Unspecified else size * 0.92f,
        ),
    )
}
