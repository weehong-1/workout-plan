package com.vitrack.app.data.backend

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val body: ByteArray?,
)

class HttpResponse(val status: Int, val body: ByteArray) {
    val text: String get() = body.decodeToString()
}

/** One HTTPS call. The app talks to Supabase over plain HTTPS with no SDK, like the iOS app. */
fun interface HttpTransport {
    /** Throws [IOException] when the server can't be reached. */
    suspend fun send(request: HttpRequest): HttpResponse
}

class UrlConnectionTransport @Inject constructor() : HttpTransport {
    override suspend fun send(request: HttpRequest): HttpResponse = withContext(Dispatchers.IO) {
        val connection = URL(request.url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = request.method
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            connection.useCaches = false
            request.headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            request.body?.let { body ->
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            HttpResponse(status, stream?.use { it.readBytes() } ?: ByteArray(0))
        } finally {
            connection.disconnect()
        }
    }
}
