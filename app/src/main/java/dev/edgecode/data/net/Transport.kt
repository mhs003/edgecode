package dev.edgecode.data.net

import dev.edgecode.core.util.obj
import dev.edgecode.core.util.string
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class HttpResponse(val code: Int, val body: String)

/** Carries the server's own error text so the UI can show it verbatim. */
class OpenCodeHttpException(val code: Int, message: String) : Exception(message)

/**
 * The narrow HTTP seam.
 *
 * Deliberately an interface: it lets the API class be exercised against a fake
 * without a socket, and it keeps [OpenCodeWire] entirely free of networking.
 */
interface Transport {
    suspend fun request(
        method: String,
        path: String,
        query: Map<String, String> = emptyMap(),
        body: String? = null,
    ): HttpResponse

    /** One emission per SSE `data:` payload, decoded to a UTF-8 string. */
    fun stream(path: String): Flow<String>

    fun close()
}

/**
 * `HttpURLConnection` rather than OkHttp: one fewer artifact to fetch and dex on
 * a phone, and this client makes a handful of request shapes that are all
 * `setRequestProperty` calls away.
 *
 * HTTP Basic is the whole of v1's authentication. `OPENCODE_SERVER_PASSWORD`
 * enables it server-side with the username defaulting to `opencode`; when the
 * password is absent the header is simply not sent.
 */
class HttpTransport(
    private val baseUrl: String,
    private val username: String? = null,
    private val password: String? = null,
) : Transport {

    private val connections = mutableSetOf<HttpURLConnection>()

    private fun open(path: String, query: Map<String, String>): HttpURLConnection {
        val suffix = if (query.isEmpty()) {
            ""
        } else {
            "?" + query.entries.joinToString("&") { "${it.key}=${it.value.urlEncode()}" }
        }
        val url = URL(baseUrl.trimEnd('/') + path + suffix)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            useCaches = false
            if (!password.isNullOrEmpty()) {
                val user = username?.takeIf { it.isNotBlank() } ?: "opencode"
                setRequestProperty("Authorization", "Basic " + basic(user, password))
            }
        }
        synchronized(connections) { connections += connection }
        return connection
    }

    private fun release(connection: HttpURLConnection) {
        synchronized(connections) { connections -= connection }
    }

    override suspend fun request(
        method: String,
        path: String,
        query: Map<String, String>,
        body: String?,
    ): HttpResponse = withContext(Dispatchers.IO) {
        require(path.startsWith("/")) { "path must be absolute: $path" }
        val connection = open(path, query)
        try {
            connection.requestMethod = method
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.let {
                BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            }.orEmpty()

            if (code !in 200..299) {
                throw OpenCodeHttpException(code, describe(code, text))
            }
            HttpResponse(code, text)
        } finally {
            release(connection)
            connection.disconnect()
        }
    }

    /**
     * The event stream never completes on its own, so the only way out is the
     * collector being cancelled — which closes the connection via `close()` and
     * lets the blocking read unwind.
     */
    override fun stream(path: String): Flow<String> = flow {
        val connection = open(path, emptyMap())
        try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "text/event-stream")
            connection.setRequestProperty("Cache-Control", "no-cache")
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = 0

            val parser = SseParser()
            BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { reader ->
                val buffer = CharArray(2048)
                while (true) {
                    val read = reader.read(buffer)
                    if (read < 0) break
                    for (frame in parser.accept(String(buffer, 0, read))) {
                        if (frame.data.isNotEmpty()) emit(frame.data)
                    }
                }
            }
        } finally {
            release(connection)
            connection.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    override fun close() {
        val open = synchronized(connections) { connections.toList().also { connections.clear() } }
        for (connection in open) {
            runCatching { connection.disconnect() }
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 30_000

        fun basic(user: String, password: String): String =
            android.util.Base64.encodeToString(
                "$user:$password".toByteArray(Charsets.UTF_8),
                android.util.Base64.NO_WRAP,
            )

        fun String.urlEncode(): String =
            java.net.URLEncoder.encode(this, "UTF-8").replace("+", "%20")

        fun describe(code: Int, body: String): String {
            // The server reports its own errors as
            // `{name, data:{message}, kind}` — for example
            //   Expected a string starting with "prt", got "msg_abc"
            // which is far more useful than anything we could invent.
            val detail = runCatching {
                val json = dev.edgecode.core.util.parseJson(body)
                json.string("message")
                    ?: json.obj("data")?.string("message")
                    ?: json.obj("error")?.string("message")
            }.getOrNull()

            return when {
                !detail.isNullOrBlank() -> detail
                code == 401 -> "This server needs a password"
                code == 404 -> "No OpenCode server at that address"
                code >= 500 -> "The server failed ($code)"
                else -> "Request failed ($code)"
            }
        }
    }
}
