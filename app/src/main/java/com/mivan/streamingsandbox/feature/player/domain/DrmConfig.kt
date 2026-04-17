package com.mivan.streamingsandbox.feature.player.domain

data class DrmConfig(
    val scheme: DrmScheme,
    val licenseUrl: String,
    val headers: Map<String, String> = emptyMap(),
    val multiSession: Boolean = false
)

enum class DrmScheme {
    WIDEVINE
}
