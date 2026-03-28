package com.parcelpanel.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

enum class UpdateStatus {
    IDLE,
    CHECKING,
    UP_TO_DATE,
    AVAILABLE,
    DOWNLOADING,
    READY_TO_INSTALL,
    ERROR,
}

data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
)

data class ReleaseDescriptor(
    val tagName: String,
    val versionName: String,
    val publishedAt: Long?,
    val body: String?,
    val htmlUrl: String,
    val apkAsset: ReleaseAsset,
    val checksumAsset: ReleaseAsset,
)

data class AppUpdateState(
    val currentVersionName: String,
    val autoCheckEnabled: Boolean = true,
    val lastCheckedAt: Long? = null,
    val status: UpdateStatus = UpdateStatus.IDLE,
    val release: ReleaseDescriptor? = null,
    val downloadProgressPercent: Int? = null,
    val downloadedApkPath: String? = null,
    val errorMessage: String? = null,
)

object ReleaseVersionComparator {
    fun isNewer(candidate: String, current: String): Boolean = compare(candidate, current) > 0

    fun compare(left: String, right: String): Int {
        val leftSegments = normalize(left)
        val rightSegments = normalize(right)
        val maxSize = maxOf(leftSegments.size, rightSegments.size)
        for (index in 0 until maxSize) {
            val leftPart = leftSegments.getOrElse(index) { 0 }
            val rightPart = rightSegments.getOrElse(index) { 0 }
            if (leftPart != rightPart) {
                return leftPart.compareTo(rightPart)
            }
        }
        return 0
    }

    private fun normalize(version: String): List<Int> {
        val cleaned = version
            .trim()
            .removePrefix("v")
            .substringBefore('-')
            .substringBefore('+')

        return cleaned
            .split('.')
            .mapNotNull { segment -> segment.toIntOrNull() }
            .ifEmpty { listOf(0) }
    }
}

object GitHubReleaseParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseLatestRelease(rawJson: String): ReleaseDescriptor {
        val root = json.parseToJsonElement(rawJson).jsonObject
        val tagName = root.getValue("tag_name").jsonPrimitive.content
        val htmlUrl = root.getValue("html_url").jsonPrimitive.content
        val publishedAt = root["published_at"]?.jsonPrimitive?.contentOrNull?.let { instant ->
            java.time.Instant.parse(instant).toEpochMilli()
        }
        val body = root["body"]?.jsonPrimitive?.contentOrNull
        val assets = root.getValue("assets").jsonArray.map { element ->
            val asset = element.jsonObject
            ReleaseAsset(
                name = asset.getValue("name").jsonPrimitive.content,
                downloadUrl = asset.getValue("browser_download_url").jsonPrimitive.content,
                sizeBytes = asset["size"]?.jsonPrimitive?.longOrNull ?: 0L,
            )
        }

        val apkAsset = assets.firstOrNull { asset -> asset.name.endsWith(".apk") }
            ?: error("No APK asset found in the latest GitHub release")
        val checksumAsset = assets.firstOrNull { asset -> asset.name == "SHA256SUMS.txt" }
            ?: error("No SHA256SUMS.txt asset found in the latest GitHub release")

        return ReleaseDescriptor(
            tagName = tagName,
            versionName = tagName.removePrefix("v"),
            publishedAt = publishedAt,
            body = body,
            htmlUrl = htmlUrl,
            apkAsset = apkAsset,
            checksumAsset = checksumAsset,
        )
    }

    fun checksumForArtifact(sha256FileContents: String, assetName: String): String? {
        return sha256FileContents
            .lineSequence()
            .map { line -> line.trim() }
            .firstNotNullOfOrNull { line ->
                if (!line.endsWith(assetName)) return@firstNotNullOfOrNull null
                line.substringBefore(' ').trim().takeIf { checksum ->
                    checksum.matches(Regex("^[0-9a-fA-F]{64}$"))
                }?.lowercase()
            }
    }
}
