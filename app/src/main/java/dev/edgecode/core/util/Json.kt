package dev.edgecode.core.util

sealed interface JsonValue {
    data class Obj(val fields: Map<String, JsonValue>) : JsonValue
    data class Arr(val items: List<JsonValue>) : JsonValue
    data class Str(val value: String) : JsonValue
    data class Num(val value: Double) : JsonValue
    data class Bool(val value: Boolean) : JsonValue
    data object Null : JsonValue
}

/** Raised for malformed input; the message carries the byte offset. */
class JsonException(message: String) : Exception(message)

private const val MAX_DEPTH = 64

/** Throws [JsonException] on malformed input. */
fun parseJson(text: String): JsonValue = JsonParser(text).parse()

/** Compact serialisation. */
fun JsonValue.encode(): String {
    val out = StringBuilder()
    writeTo(out)
    return out.toString()
}

operator fun JsonValue.get(key: String): JsonValue? =
    (this as? JsonValue.Obj)?.fields?.get(key)

operator fun JsonValue.get(index: Int): JsonValue? {
    val items = (this as? JsonValue.Arr)?.items ?: return null
    return items.getOrNull(index)
}

fun JsonValue.string(key: String): String? = (get(key) as? JsonValue.Str)?.value

fun JsonValue.stringOr(key: String, fallback: String): String = string(key) ?: fallback

fun JsonValue.int(key: String): Int? = (get(key) as? JsonValue.Num)?.value?.toInt()

fun JsonValue.intOr(key: String, fallback: Int): Int = int(key) ?: fallback

fun JsonValue.long(key: String): Long? = (get(key) as? JsonValue.Num)?.value?.toLong()

fun JsonValue.longOr(key: String, fallback: Long): Long = long(key) ?: fallback

fun JsonValue.double(key: String): Double? = (get(key) as? JsonValue.Num)?.value

fun JsonValue.doubleOr(key: String, fallback: Double): Double = double(key) ?: fallback

fun JsonValue.bool(key: String): Boolean? = (get(key) as? JsonValue.Bool)?.value

fun JsonValue.boolOr(key: String, fallback: Boolean): Boolean = bool(key) ?: fallback

fun JsonValue.obj(key: String): JsonValue? = get(key) as? JsonValue.Obj

fun JsonValue.arr(key: String): List<JsonValue> =
    (get(key) as? JsonValue.Arr)?.items ?: emptyList()

fun JsonValue.strings(key: String): List<String> =
    arr(key).mapNotNull { (it as? JsonValue.Str)?.value }

val JsonValue.asString: String? get() = (this as? JsonValue.Str)?.value

val JsonValue.asObject: Map<String, JsonValue>? get() = (this as? JsonValue.Obj)?.fields

val JsonValue.asArray: List<JsonValue>? get() = (this as? JsonValue.Arr)?.items

val JsonValue.asStringList: List<String>
    get() = asArray.orEmpty().mapNotNull { (it as? JsonValue.Str)?.value }

private fun JsonValue.writeTo(out: StringBuilder) {
    when (this) {
        is JsonValue.Obj -> {
            out.append('{')
            var first = true
            for ((key, value) in fields) {
                if (!first) out.append(',')
                first = false
                writeString(out, key)
                out.append(':')
                value.writeTo(out)
            }
            out.append('}')
        }
        is JsonValue.Arr -> {
            out.append('[')
            var first = true
            for (item in items) {
                if (!first) out.append(',')
                first = false
                item.writeTo(out)
            }
            out.append(']')
        }
        is JsonValue.Str -> writeString(out, value)
        is JsonValue.Num -> out.append(encodeNumber(value))
        is JsonValue.Bool -> out.append(if (value) "true" else "false")
        JsonValue.Null -> out.append("null")
    }
}

private fun writeString(out: StringBuilder, value: String) {
    out.append('"')
    for (c in value) {
        when (c) {
            '"' -> out.append("\\\"")
            '\\' -> out.append("\\\\")
            '\n' -> out.append("\\n")
            '\r' -> out.append("\\r")
            '\t' -> out.append("\\t")
            '\b' -> out.append("\\b")
            '\u000C' -> out.append("\\f")
            else ->
                if (c < ' ') {
                    out.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                } else {
                    out.append(c)
                }
        }
    }
    out.append('"')
}

/** Whole numbers are emitted as integers so epoch millis never become `1.79E12`. */
private fun encodeNumber(value: Double): String {
    if (value.isNaN() || value.isInfinite()) {
        throw JsonException("cannot encode $value as a JSON number")
    }
    val asLong = value.toLong()
    return if (asLong.toDouble() == value) asLong.toString() else value.toString()
}

private class JsonParser(private val text: String) {

    private var index = 0

    fun parse(): JsonValue {
        skipWhitespace()
        if (atEnd()) throw JsonException("unexpected end of input at offset $index")
        val value = parseValue(0)
        skipWhitespace()
        if (!atEnd()) throw JsonException("trailing garbage at offset $index")
        return value
    }

    private fun parseValue(depth: Int): JsonValue {
        if (atEnd()) throw JsonException("unexpected end of input at offset $index")
        return when (val c = text[index]) {
            '{' -> parseObject(depth)
            '[' -> parseArray(depth)
            '"' -> JsonValue.Str(parseString())
            't' -> {
                expect("true")
                JsonValue.Bool(true)
            }
            'f' -> {
                expect("false")
                JsonValue.Bool(false)
            }
            'n' -> {
                expect("null")
                JsonValue.Null
            }
            '-', in '0'..'9' -> JsonValue.Num(parseNumber())
            else -> throw JsonException("unexpected character '$c' at offset $index")
        }
    }

    private fun parseObject(depth: Int): JsonValue {
        checkDepth(depth)
        index++
        skipWhitespace()
        val fields = LinkedHashMap<String, JsonValue>()
        if (peek() == '}') {
            index++
            return JsonValue.Obj(fields)
        }
        while (true) {
            skipWhitespace()
            if (peek() != '"') throw JsonException("expected object key at offset $index")
            val key = parseString()
            skipWhitespace()
            if (peek() != ':') throw JsonException("expected ':' at offset $index")
            index++
            skipWhitespace()
            fields[key] = parseValue(depth + 1)
            skipWhitespace()
            when (peek()) {
                ',' -> {
                    index++
                    skipWhitespace()
                    if (peek() == '}') throw JsonException("trailing comma at offset $index")
                }
                '}' -> {
                    index++
                    return JsonValue.Obj(fields)
                }
                else -> throw JsonException("expected ',' or '}' at offset $index")
            }
        }
    }

    private fun parseArray(depth: Int): JsonValue {
        checkDepth(depth)
        index++
        skipWhitespace()
        val items = mutableListOf<JsonValue>()
        if (peek() == ']') {
            index++
            return JsonValue.Arr(items)
        }
        while (true) {
            skipWhitespace()
            items += parseValue(depth + 1)
            skipWhitespace()
            when (peek()) {
                ',' -> {
                    index++
                    skipWhitespace()
                    if (peek() == ']') throw JsonException("trailing comma at offset $index")
                }
                ']' -> {
                    index++
                    return JsonValue.Arr(items)
                }
                else -> throw JsonException("expected ',' or ']' at offset $index")
            }
        }
    }

    private fun parseString(): String {
        index++
        val out = StringBuilder()
        while (true) {
            if (atEnd()) throw JsonException("unterminated string at offset $index")
            when (val c = text[index++]) {
                '"' -> return out.toString()
                '\\' -> parseEscape(out)
                else -> out.append(c)
            }
        }
    }

    private fun parseEscape(out: StringBuilder) {
        if (atEnd()) throw JsonException("unterminated escape at offset $index")
        when (val e = text[index++]) {
            '"' -> out.append('"')
            '\\' -> out.append('\\')
            '/' -> out.append('/')
            'b' -> out.append('\b')
            'f' -> out.append('\u000C')
            'n' -> out.append('\n')
            'r' -> out.append('\r')
            't' -> out.append('\t')
            'u' -> out.append(readUnicodeEscape())
            else -> throw JsonException("invalid escape '\\$e' at offset ${index - 1}")
        }
    }

    /** Joins a `\uD83D\uDE00`-style surrogate pair into one code point. */
    private fun readUnicodeEscape(): String {
        val first = readHex4()
        if (first !in 0xD800..0xDBFF) return first.toChar().toString()
        if (index + 1 >= text.length || text[index] != '\\' || text[index + 1] != 'u') {
            return first.toChar().toString()
        }
        index += 2
        val second = readHex4()
        if (second !in 0xDC00..0xDFFF) return first.toChar().toString() + second.toChar()
        val codePoint = 0x10000 + ((first - 0xD800) shl 10) + (second - 0xDC00)
        return String(Character.toChars(codePoint))
    }

    private fun readHex4(): Int {
        if (index + 4 > text.length) throw JsonException("truncated \\u escape at offset $index")
        var value = 0
        for (i in 0 until 4) {
            val digit = Character.digit(text[index + i], 16)
            if (digit < 0) throw JsonException("invalid \\u escape at offset ${index + i}")
            value = value * 16 + digit
        }
        index += 4
        return value
    }

    private fun parseNumber(): Double {
        val start = index
        if (peek() == '-') index++
        if (index >= text.length) throw JsonException("truncated number at offset $start")
        when {
            text[index] == '0' -> index++
            text[index] in '1'..'9' -> while (index < text.length && text[index] in '0'..'9') index++
            else -> throw JsonException("invalid number at offset $start")
        }
        if (index < text.length && text[index] == '.') {
            index++
            if (index >= text.length || text[index] !in '0'..'9') {
                throw JsonException("invalid fraction at offset $index")
            }
            while (index < text.length && text[index] in '0'..'9') index++
        }
        if (index < text.length && (text[index] == 'e' || text[index] == 'E')) {
            index++
            if (index < text.length && (text[index] == '+' || text[index] == '-')) index++
            if (index >= text.length || text[index] !in '0'..'9') {
                throw JsonException("invalid exponent at offset $index")
            }
            while (index < text.length && text[index] in '0'..'9') index++
        }
        return text.substring(start, index).toDouble()
    }

    private fun checkDepth(depth: Int) {
        if (depth >= MAX_DEPTH) throw JsonException("nesting too deep at offset $index")
    }

    private fun expect(word: String) {
        if (!text.startsWith(word, index)) throw JsonException("invalid token at offset $index")
        index += word.length
    }

    private fun peek(): Char? = if (index < text.length) text[index] else null

    private fun atEnd(): Boolean = index >= text.length

    private fun skipWhitespace() {
        while (index < text.length) {
            when (text[index]) {
                ' ', '\t', '\n', '\r' -> index++
                else -> return
            }
        }
    }
}
