package com.mivan.streamingsandbox.feature.channels.domain.model

import com.mivan.streamingsandbox.feature.player.domain.DrmConfig

enum class StreamType { HLS, DASH }

data class Channel(
    val id: String,
    val name: String,
    val type: StreamType,
    val url: String,
    val urlLogo: String? = null,
    val drm: DrmConfig? = null
)
