package com.parcelpanel.update

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.parcelpanel.data.SettingsRepository
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.Closeable
import java.io.File
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OtaUpdateRepositoryInstrumentedTest {
    @Test
    fun checkAndDownloadUpdate_fromLocalReleaseFeed_reachesReadyToInstall() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val installedApk = File(context.packageCodePath)
        assertThat(installedApk.exists()).isTrue()

        File(context.cacheDir, "updates").deleteRecursively()

        val apkName = "ParcelPanel-ota-smoke.apk"
        val apkBytes = installedApk.readBytes()
        val checksumContents = "${sha256Hex(apkBytes)}  $apkName\n"

        LocalTestHttpServer(
            routes = mapOf(
                "/releases/latest" to TestHttpResponse(
                    body = latestReleaseJson(
                        baseAssetUrl = "http://127.0.0.1:%PORT%/assets",
                        apkName = apkName,
                        apkSize = apkBytes.size.toLong(),
                    ).toByteArray(),
                    contentType = "application/vnd.github+json",
                    substitutePort = true,
                ),
                "/assets/$apkName" to TestHttpResponse(
                    body = apkBytes,
                    contentType = "application/vnd.android.package-archive",
                ),
                "/assets/SHA256SUMS.txt" to TestHttpResponse(
                    body = checksumContents.toByteArray(),
                    contentType = "text/plain; charset=utf-8",
                ),
            )
        ).use { server ->
            val repository = OtaUpdateRepository(
                context = context,
                settingsRepository = SettingsRepository(context),
                endpointConfig = UpdateEndpointConfig(
                    latestReleaseUrl = "${server.baseUrl}/releases/latest",
                    userAgent = "ParcelPanel-Test/1.0",
                    trustedHosts = setOf("127.0.0.1"),
                ),
            )

            repository.checkForUpdates(manual = true)
            val availableState = repository.state.value
            assertThat(availableState.status).isEqualTo(UpdateStatus.AVAILABLE)
            assertThat(availableState.release?.versionName).isEqualTo("9.9.9")

            repository.downloadLatestUpdate()
            val readyState = repository.state.value
            assertThat(readyState.status).isEqualTo(UpdateStatus.READY_TO_INSTALL)
            assertThat(readyState.downloadedApkPath).isNotNull()

            val downloadedApk = File(readyState.downloadedApkPath!!)
            assertThat(downloadedApk.exists()).isTrue()
            assertThat(ApkSignatureVerifier.matchesInstalledSigning(context, downloadedApk)).isTrue()
        }
    }
}

private data class TestHttpResponse(
    val statusCode: Int = 200,
    val contentType: String = "application/json; charset=utf-8",
    val body: ByteArray,
    val substitutePort: Boolean = false,
)

private class LocalTestHttpServer(
    private val routes: Map<String, TestHttpResponse>,
) : Closeable {
    private val serverSocket = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))
    private val running = AtomicBoolean(true)
    private val executor = Executors.newCachedThreadPool()

    val baseUrl: String = "http://127.0.0.1:${serverSocket.localPort}"

    init {
        executor.execute {
            while (running.get()) {
                try {
                    val socket = serverSocket.accept()
                    executor.execute {
                        handle(socket)
                    }
                } catch (_: SocketException) {
                    if (running.get()) {
                        throw IllegalStateException("Local OTA test server socket closed unexpectedly.")
                    }
                }
            }
        }
    }

    override fun close() {
        running.set(false)
        serverSocket.close()
        executor.shutdownNow()
        executor.awaitTermination(5, TimeUnit.SECONDS)
    }

    private fun handle(socket: java.net.Socket) {
        socket.use { client ->
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val requestLine = reader.readLine() ?: return
            while (true) {
                val header = reader.readLine() ?: break
                if (header.isEmpty()) break
            }

            val path = requestLine.split(' ').getOrNull(1)?.substringBefore('?') ?: "/"
            val templateResponse = routes[path]
            val response = when (templateResponse) {
                null -> TestHttpResponse(statusCode = 404, contentType = "text/plain; charset=utf-8", body = "Not found".toByteArray())
                else -> if (templateResponse.substitutePort) {
                    templateResponse.copy(body = templateResponse.body.replacePort(serverSocket.localPort))
                } else {
                    templateResponse
                }
            }

            val output = BufferedOutputStream(client.getOutputStream())
            output.write("HTTP/1.1 ${response.statusCode} ${reasonPhrase(response.statusCode)}\r\n".toByteArray())
            output.write("Content-Type: ${response.contentType}\r\n".toByteArray())
            output.write("Content-Length: ${response.body.size}\r\n".toByteArray())
            output.write("Connection: close\r\n\r\n".toByteArray())
            output.write(response.body)
            output.flush()
        }
    }
}

private fun latestReleaseJson(
    baseAssetUrl: String,
    apkName: String,
    apkSize: Long,
): String = """
    {
      "tag_name": "v9.9.9",
      "html_url": "$baseAssetUrl/release-page",
      "published_at": "2026-03-28T00:00:00Z",
      "body": "Local OTA smoke release",
      "assets": [
        {
          "name": "$apkName",
          "browser_download_url": "$baseAssetUrl/$apkName",
          "size": $apkSize
        },
        {
          "name": "SHA256SUMS.txt",
          "browser_download_url": "$baseAssetUrl/SHA256SUMS.txt",
          "size": 80
        }
      ]
    }
""".trimIndent()

private fun ByteArray.replacePort(port: Int): ByteArray =
    String(this).replace("%PORT%", port.toString()).toByteArray()

private fun reasonPhrase(statusCode: Int): String = when (statusCode) {
    200 -> "OK"
    404 -> "Not Found"
    else -> "Status"
}

private fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
