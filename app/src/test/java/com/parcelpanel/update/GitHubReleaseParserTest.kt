package com.parcelpanel.update

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Test

class GitHubReleaseParserTest {
    @Test
    fun parseLatestRelease_returnsApkAndChecksumAssets() {
        val release = GitHubReleaseParser.parseLatestRelease(
            """
            {
              "tag_name": "v1.2.3",
              "html_url": "https://github.com/praj107/ParcelPanel/releases/tag/v1.2.3",
              "published_at": "2026-03-27T12:12:01Z",
              "body": "Bug fixes and OTA validation.",
              "assets": [
                {
                  "name": "ParcelPanel-v1.2.3.apk",
                  "browser_download_url": "https://github.com/praj107/ParcelPanel/releases/download/v1.2.3/ParcelPanel-v1.2.3.apk",
                  "size": 12345678
                },
                {
                  "name": "SHA256SUMS.txt",
                  "browser_download_url": "https://github.com/praj107/ParcelPanel/releases/download/v1.2.3/SHA256SUMS.txt",
                  "size": 96
                }
              ]
            }
            """.trimIndent()
        )

        assertThat(release.tagName).isEqualTo("v1.2.3")
        assertThat(release.versionName).isEqualTo("1.2.3")
        assertThat(release.apkAsset.name).isEqualTo("ParcelPanel-v1.2.3.apk")
        assertThat(release.checksumAsset.name).isEqualTo("SHA256SUMS.txt")
        assertThat(release.publishedAt).isEqualTo(Instant.parse("2026-03-27T12:12:01Z").toEpochMilli())
    }

    @Test
    fun checksumForArtifact_returnsMatchingLine() {
        val checksum = GitHubReleaseParser.checksumForArtifact(
            sha256FileContents = """
                aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa  ParcelPanel-v1.2.3.apk
                bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb  Other.apk
            """.trimIndent(),
            assetName = "ParcelPanel-v1.2.3.apk",
        )

        assertThat(checksum).isEqualTo("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
    }
}
