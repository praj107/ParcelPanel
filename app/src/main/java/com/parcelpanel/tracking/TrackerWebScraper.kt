package com.parcelpanel.tracking

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.resume

data class TrackerPageDocument(
    val pageTitle: String?,
    val bodyText: String,
    val finalUrl: String,
)

data class TrackerSessionDocument(
    val pageTitle: String?,
    val bodyText: String,
    val finalUrl: String,
    val scriptStatus: String,
    val scriptPayload: String?,
    val responseUrl: String?,
    val httpStatus: Int?,
    val errorMessage: String?,
) {
    fun asPageDocument(): TrackerPageDocument =
        TrackerPageDocument(
            pageTitle = pageTitle,
            bodyText = bodyText,
            finalUrl = finalUrl,
        )
}

class TrackerWebScraper(
    private val context: Context,
) {
    suspend fun fetch(url: String, trackingNumber: String): TrackerPageDocument = withContext(Dispatchers.Main) {
        val webView = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = false
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            settings.userAgentString = DEFAULT_USER_AGENT
        }
        CookieManager.getInstance().setAcceptCookie(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        }

        try {
            val loadState = webView.awaitPageLoad(url)
            val extractedBodyText = webView.awaitMeaningfulBodyText(trackingNumber)
            check(extractedBodyText.isNotBlank()) {
                loadState.errorMessage ?: "Tracker page loaded, but no readable shipment text was exposed."
            }
            TrackerPageDocument(
                pageTitle = webView.title,
                bodyText = extractedBodyText,
                finalUrl = webView.url ?: url,
            )
        } finally {
            webView.stopLoading()
            webView.destroy()
        }
    }

    suspend fun fetchWithSessionScript(
        url: String,
        trackingNumber: String,
        script: String,
    ): TrackerSessionDocument = withContext(Dispatchers.Main) {
        val webView = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = false
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            settings.userAgentString = DEFAULT_USER_AGENT
        }
        CookieManager.getInstance().setAcceptCookie(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        }

        try {
            webView.awaitPageLoad(url)
            delay(SESSION_BOOTSTRAP_DELAY_MS)
            CookieManager.getInstance().flush()

            val sessionState = webView.awaitSessionScriptState(script)
            CookieManager.getInstance().flush()

            TrackerSessionDocument(
                pageTitle = webView.title,
                bodyText = webView.awaitMeaningfulBodyText(
                    trackingNumber = trackingNumber,
                    maxAttempts = SESSION_BODY_TEXT_ATTEMPTS,
                    minBodyTextLength = SESSION_MIN_BODY_TEXT_LENGTH,
                ),
                finalUrl = webView.url ?: url,
                scriptStatus = sessionState.status,
                scriptPayload = sessionState.payload,
                responseUrl = sessionState.responseUrl,
                httpStatus = sessionState.httpStatus,
                errorMessage = sessionState.errorMessage,
            )
        } finally {
            webView.stopLoading()
            webView.destroy()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun WebView.awaitPageLoad(url: String): PageLoadState =
        suspendCancellableCoroutine { continuation ->
            val state = PageLoadState()
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    state.lastUrl = url
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    state.lastUrl = url
                    if (continuation.isActive) {
                        continuation.resume(state)
                    }
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError,
                ) {
                    if (request.isForMainFrame) {
                        state.errorMessage = error.description?.toString()
                    }
                }
            }
            loadUrl(url)
            continuation.invokeOnCancellation {
                stopLoading()
            }
        }

    private suspend fun WebView.awaitMeaningfulBodyText(
        trackingNumber: String,
        maxAttempts: Int = DEFAULT_BODY_TEXT_ATTEMPTS,
        minBodyTextLength: Int = MIN_BODY_TEXT_LENGTH,
    ): String {
        var bestText = ""
        var stableCount = 0
        repeat(maxAttempts) {
            val text = evaluateJavascriptString(
                """
                (function() {
                  if (!document || !document.body) { return ""; }
                  return document.body.innerText || "";
                })();
                """.trimIndent()
            ).trim()
            if (text.length > bestText.length) {
                bestText = text
                stableCount = 0
            }

            val looksUseful = text.contains(trackingNumber, ignoreCase = true) ||
                TRACKING_KEYWORDS.any { keyword -> text.contains(keyword, ignoreCase = true) }
            if (looksUseful && text.length >= minBodyTextLength) {
                stableCount += 1
                if (stableCount >= 2) {
                    return text
                }
            }
            delay(900)
        }
        return bestText
    }

    private suspend fun WebView.awaitSessionScriptState(script: String): SessionScriptState {
        repeat(SESSION_SCRIPT_ATTEMPTS) {
            val rawState = evaluateJavascriptString(script).trim()
            if (rawState.isNotBlank()) {
                val parsed = decodeSessionScriptState(rawState)
                if (parsed.status !in PENDING_SESSION_STATES) {
                    return parsed
                }
            }
            delay(SESSION_SCRIPT_POLL_DELAY_MS)
        }
        return SessionScriptState(
            status = "timeout",
            payload = null,
            responseUrl = null,
            httpStatus = null,
            errorMessage = "Session script did not finish before timeout.",
        )
    }

    private suspend fun WebView.evaluateJavascriptString(script: String): String =
        suspendCancellableCoroutine { continuation ->
            evaluateJavascript(script) { rawValue ->
                if (continuation.isActive) {
                    continuation.resume(decodeJavascriptString(rawValue))
                }
            }
        }

    private fun decodeJavascriptString(rawValue: String?): String {
        if (rawValue == null || rawValue == "null") return ""
        return runCatching {
            Json.parseToJsonElement(rawValue).jsonPrimitive.content
        }.getOrDefault(rawValue.removeSurrounding("\""))
    }

    private fun decodeSessionScriptState(rawState: String): SessionScriptState {
        val stateObject = runCatching {
            Json.parseToJsonElement(rawState).jsonObject
        }.getOrNull() ?: return SessionScriptState(
            status = "malformed",
            payload = null,
            responseUrl = null,
            httpStatus = null,
            errorMessage = "Session script returned malformed state.",
        )

        return SessionScriptState(
            status = stateObject["status"]?.jsonPrimitive?.content ?: "pending",
            payload = stateObject["payload"]?.jsonPrimitive?.contentOrNull,
            responseUrl = stateObject["responseUrl"]?.jsonPrimitive?.contentOrNull,
            httpStatus = stateObject["httpStatus"]?.jsonPrimitive?.intOrNull,
            errorMessage = stateObject["errorMessage"]?.jsonPrimitive?.contentOrNull,
        )
    }

    private data class PageLoadState(
        var lastUrl: String? = null,
        var errorMessage: String? = null,
    )

    private data class SessionScriptState(
        val status: String,
        val payload: String?,
        val responseUrl: String?,
        val httpStatus: Int?,
        val errorMessage: String?,
    )

    private companion object {
        const val MIN_BODY_TEXT_LENGTH = 80
        const val SESSION_MIN_BODY_TEXT_LENGTH = 24
        const val DEFAULT_BODY_TEXT_ATTEMPTS = 12
        const val SESSION_BODY_TEXT_ATTEMPTS = 6
        const val SESSION_BOOTSTRAP_DELAY_MS = 2_200L
        const val SESSION_SCRIPT_ATTEMPTS = 18
        const val SESSION_SCRIPT_POLL_DELAY_MS = 900L
        val PENDING_SESSION_STATES = setOf("", "pending", "starting")
        val TRACKING_KEYWORDS = listOf(
            "latest update",
            "shipment",
            "tracking",
            "out for delivery",
            "delivered",
            "in transit",
            "delivery estimate",
            "arriving today",
        )
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Mobile Safari/537.36"
    }
}
