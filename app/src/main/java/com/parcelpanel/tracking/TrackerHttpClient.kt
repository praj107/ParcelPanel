package com.parcelpanel.tracking

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class HttpTextResponse(
    val statusCode: Int,
    val body: String,
    val finalUrl: String,
)

class TrackerHttpClient {
    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): HttpTextResponse = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", DEFAULT_USER_AGENT)
            setRequestProperty("Accept", "application/json, text/plain, */*")
            setRequestProperty("Accept-Language", "en-AU,en;q=0.9")
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
        }

        try {
            val statusCode = connection.responseCode
            val body = (if (statusCode in 200..399) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            HttpTextResponse(
                statusCode = statusCode,
                body = body,
                finalUrl = connection.url?.toString() ?: url,
            )
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 20_000
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Mobile Safari/537.36"
    }
}
