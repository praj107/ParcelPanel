package com.parcelpanel.update

import android.content.Context
import com.parcelpanel.BuildConfig
import com.parcelpanel.data.SettingsRepository
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OtaUpdateRepository(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val endpointConfig: UpdateEndpointConfig = UpdateEndpointConfig.fromBuildConfig(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val updatesDir = File(context.cacheDir, "updates")

    private val _state = MutableStateFlow(AppUpdateState(currentVersionName = BuildConfig.VERSION_NAME))
    val state: StateFlow<AppUpdateState> = _state.asStateFlow()

    init {
        updatesDir.mkdirs()
        scope.launch {
            settingsRepository.preferences.collectLatest { prefs ->
                _state.value = _state.value.copy(
                    autoCheckEnabled = prefs.autoCheckUpdates,
                    lastCheckedAt = prefs.lastUpdateCheckAt,
                )
            }
        }
    }

    suspend fun setAutoCheckUpdates(enabled: Boolean) {
        settingsRepository.setAutoCheckUpdates(enabled)
    }

    suspend fun checkForUpdates(manual: Boolean) {
        val prefs = settingsRepository.preferences.first()
        val now = System.currentTimeMillis()
        if (!manual) {
            if (!prefs.autoCheckUpdates) return
            val lastCheckedAt = prefs.lastUpdateCheckAt
            if (lastCheckedAt != null && now - lastCheckedAt < AUTO_CHECK_INTERVAL_MS) {
                return
            }
        }

        _state.value = _state.value.copy(
            status = UpdateStatus.CHECKING,
            errorMessage = null,
            downloadProgressPercent = null,
        )
        settingsRepository.setLastUpdateCheckAt(now)

        runCatching {
            val release = fetchLatestRelease()
            if (!ReleaseVersionComparator.isNewer(release.versionName, BuildConfig.VERSION_NAME)) {
                release to null
            } else {
                release to resolvePreparedUpdatePath(release)
            }
        }.onSuccess { releaseWithPreparedApk ->
            val (release, preparedApk) = releaseWithPreparedApk
            val hasNewerRelease = ReleaseVersionComparator.isNewer(release.versionName, BuildConfig.VERSION_NAME)
            _state.value = _state.value.copy(
                status = when {
                    !hasNewerRelease -> UpdateStatus.UP_TO_DATE
                    preparedApk != null -> UpdateStatus.READY_TO_INSTALL
                    else -> UpdateStatus.AVAILABLE
                },
                release = release,
                downloadedApkPath = preparedApk?.absolutePath,
                downloadProgressPercent = if (preparedApk != null) 100 else null,
                errorMessage = null,
            )
        }.onFailure { throwable ->
            _state.value = _state.value.copy(
                status = UpdateStatus.ERROR,
                errorMessage = throwable.message ?: "Unable to check the latest ParcelPanel release.",
            )
        }
    }

    suspend fun downloadLatestUpdate() {
        val release = _state.value.release
            ?: return
        _state.value = _state.value.copy(
            status = UpdateStatus.DOWNLOADING,
            downloadProgressPercent = 0,
            errorMessage = null,
        )

        runCatching {
            val checksumContents = httpGetString(release.checksumAsset.downloadUrl)
            val expectedChecksum = GitHubReleaseParser.checksumForArtifact(
                sha256FileContents = checksumContents,
                assetName = release.apkAsset.name,
            ) ?: error("No checksum found for ${release.apkAsset.name}")
            val tempFile = File(updatesDir, "${release.apkAsset.name}.part")
            val targetFile = File(updatesDir, release.apkAsset.name)

            downloadBinary(release.apkAsset.downloadUrl, tempFile) { progress ->
                _state.value = _state.value.copy(
                    status = UpdateStatus.DOWNLOADING,
                    downloadProgressPercent = progress,
                )
            }

            val actualChecksum = sha256Hex(tempFile)
            if (actualChecksum != expectedChecksum) {
                tempFile.delete()
                error("Downloaded update checksum mismatch. The package was discarded.")
            }
            if (!ApkSignatureVerifier.matchesInstalledSigning(context, tempFile)) {
                tempFile.delete()
                error("Downloaded update is not signed with the installed ParcelPanel certificate.")
            }

            if (targetFile.exists()) {
                targetFile.delete()
            }
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
            targetFile
        }.onSuccess { apkFile ->
            _state.value = _state.value.copy(
                status = UpdateStatus.READY_TO_INSTALL,
                downloadedApkPath = apkFile.absolutePath,
                downloadProgressPercent = 100,
                errorMessage = null,
            )
        }.onFailure { throwable ->
            _state.value = _state.value.copy(
                status = UpdateStatus.ERROR,
                downloadedApkPath = null,
                downloadProgressPercent = null,
                errorMessage = throwable.message ?: "Update download failed.",
            )
        }
    }

    private suspend fun fetchLatestRelease(): ReleaseDescriptor = withContext(Dispatchers.IO) {
        val releaseJson = httpGetString(endpointConfig.latestReleaseUrl)
        val release = GitHubReleaseParser.parseLatestRelease(releaseJson)
        ensureGithubUrl(release.htmlUrl)
        ensureGithubUrl(release.apkAsset.downloadUrl)
        ensureGithubUrl(release.checksumAsset.downloadUrl)
        release
    }

    private suspend fun resolvePreparedUpdatePath(release: ReleaseDescriptor): File? = withContext(Dispatchers.IO) {
        val preparedFile = File(updatesDir, release.apkAsset.name)
        if (!preparedFile.exists()) return@withContext null

        val checksumContents = runCatching { httpGetString(release.checksumAsset.downloadUrl) }.getOrNull()
            ?: return@withContext null
        val expectedChecksum = GitHubReleaseParser.checksumForArtifact(checksumContents, release.apkAsset.name)
            ?: return@withContext null
        if (sha256Hex(preparedFile) != expectedChecksum) {
            preparedFile.delete()
            return@withContext null
        }
        if (!ApkSignatureVerifier.matchesInstalledSigning(context, preparedFile)) {
            preparedFile.delete()
            return@withContext null
        }
        preparedFile
    }

    private fun httpGetString(url: String): String {
        val connection = openConnection(url)
        connection.inputStream.bufferedReader().use { reader ->
            return reader.readText()
        }
    }

    private fun downloadBinary(
        url: String,
        targetFile: File,
        onProgress: (Int?) -> Unit,
    ) {
        val connection = openConnection(url)
        val totalBytes = connection.contentLengthLong.takeIf { it > 0L }
        targetFile.parentFile?.mkdirs()
        connection.inputStream.use { input ->
            targetFile.outputStream().buffered().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var downloadedBytes = 0L
                while (true) {
                    val bytesRead = input.read(buffer)
                    if (bytesRead <= 0) break
                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    onProgress(totalBytes?.let { total ->
                        ((downloadedBytes.toDouble() / total.toDouble()) * 100.0).toInt().coerceIn(0, 100)
                    })
                }
            }
        }
    }

    private fun openConnection(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        if (url.contains("/releases/latest")) {
            connection.setRequestProperty("Accept", "application/vnd.github+json")
        }
        connection.setRequestProperty("User-Agent", endpointConfig.userAgent)
        connection.connect()
        ensureGithubUrl(connection.url.toString())
        if (connection.responseCode !in 200..299) {
            val message = connection.errorStream?.bufferedReader()?.use { reader -> reader.readText() }
            throw IllegalStateException(
                "Release server returned HTTP ${connection.responseCode}${message?.let { ": $it" } ?: ""}"
            )
        }
        return connection
    }

    private fun ensureGithubUrl(url: String) {
        val host = URI(url).host?.lowercase().orEmpty()
        val trusted = host in endpointConfig.trustedHosts ||
            endpointConfig.trustedHostSuffixes.any { suffix -> host.endsWith(suffix) }
        check(trusted) { "Unexpected update host: $host" }
    }

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val bytesRead = input.read(buffer)
                if (bytesRead <= 0) break
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private companion object {
        const val AUTO_CHECK_INTERVAL_MS = 12L * 60L * 60L * 1000L
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
    }
}
