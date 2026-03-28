package com.parcelpanel.update

import com.parcelpanel.BuildConfig

data class UpdateEndpointConfig(
    val latestReleaseUrl: String,
    val userAgent: String,
    val trustedHosts: Set<String>,
    val trustedHostSuffixes: Set<String> = emptySet(),
) {
    companion object {
        fun fromBuildConfig(): UpdateEndpointConfig =
            UpdateEndpointConfig(
                latestReleaseUrl = BuildConfig.UPDATE_RELEASES_LATEST_URL,
                userAgent = BuildConfig.UPDATE_USER_AGENT,
                trustedHosts = setOf(
                    "github.com",
                    "api.github.com",
                    "objects.githubusercontent.com",
                    "release-assets.githubusercontent.com",
                ),
                trustedHostSuffixes = setOf(".githubusercontent.com"),
            )
    }
}
