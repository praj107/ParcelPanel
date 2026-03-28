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
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.resume

data class TrackerPageDocument(
    val pageTitle: String?,
    val bodyText: String,
    val finalUrl: String,
)

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

    private suspend fun WebView.awaitMeaningfulBodyText(trackingNumber: String): String {
        var bestText = ""
        var stableCount = 0
        repeat(12) {
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
            if (looksUseful && text.length >= MIN_BODY_TEXT_LENGTH) {
                stableCount += 1
                if (stableCount >= 2) {
                    return text
                }
            }
            delay(900)
        }
        return bestText
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

    private data class PageLoadState(
        var lastUrl: String? = null,
        var errorMessage: String? = null,
    )

    private companion object {
        const val MIN_BODY_TEXT_LENGTH = 80
        val TRACKING_KEYWORDS = listOf(
            "latest update",
            "shipment",
            "tracking",
            "out for delivery",
            "delivered",
            "in transit",
        )
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Mobile Safari/537.36"
    }
}
