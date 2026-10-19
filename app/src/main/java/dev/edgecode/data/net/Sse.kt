package dev.edgecode.data.net

/**
 * One server-sent event, as accumulated between two blank lines.
 *
 * [data] has the field's single trailing newline removed; multiple `data:`
 * lines are joined with `\n`.
 */
data class SseFrame(
    val event: String?,
    val data: String,
    val id: String?,
)

/**
 * Incremental SSE parser. Feed it arbitrary chunks — network reads split
 * wherever they like, including mid-line and mid-UTF8-character — and it
 * returns whatever complete frames that chunk completed.
 */
class SseParser {

    private val line = StringBuilder()
    private val data = StringBuilder()
    private var event: String? = null
    private var id: String? = null
    private var hasData = false

    // A `\r` at the very end of a chunk may still turn out to be the first half
    // of a CRLF, so the line it terminated is held back until we see one more
    // character. Dispatching it early would fabricate a blank line — and a blank
    // line dispatches.
    private var pendingCr = false

    fun accept(chunk: String): List<SseFrame> {
        if (chunk.isEmpty()) return emptyList()

        val frames = mutableListOf<SseFrame>()
        var i = 0

        if (pendingCr) {
            pendingCr = false
            if (chunk[0] == '\n') i = 1
            finishLine(frames)
        }

        while (i < chunk.length) {
            val c = chunk[i]
            when {
                c == '\n' -> {
                    finishLine(frames)
                    i++
                }

                c == '\r' && i == chunk.length - 1 -> {
                    pendingCr = true
                    i++
                }

                c == '\r' -> {
                    finishLine(frames)
                    i += if (chunk[i + 1] == '\n') 2 else 1
                }

                else -> {
                    line.append(c)
                    i++
                }
            }
        }

        return frames
    }

    fun reset() {
        line.setLength(0)
        data.setLength(0)
        event = null
        id = null
        hasData = false
        pendingCr = false
    }

    private fun finishLine(frames: MutableList<SseFrame>) {
        val text = line.toString()
        line.setLength(0)

        if (text.isEmpty()) {
            dispatch(frames)
            return
        }
        if (text[0] == ':') return

        val colon = text.indexOf(':')
        val field = if (colon < 0) text else text.substring(0, colon)
        val raw = if (colon < 0) "" else text.substring(colon + 1)
        val value = if (raw.startsWith(" ")) raw.substring(1) else raw

        when (field) {
            "data" -> {
                data.append(value).append('\n')
                hasData = true
            }

            "event" -> event = value
            "id" -> id = value
        }
    }

    private fun dispatch(frames: MutableList<SseFrame>) {
        if (hasData) {
            frames.add(
                SseFrame(
                    event = event,
                    data = data.toString().removeSuffix("\n"),
                    id = id,
                ),
            )
        }
        data.setLength(0)
        event = null
        id = null
        hasData = false
    }
}
