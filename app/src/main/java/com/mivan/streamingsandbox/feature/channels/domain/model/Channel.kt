package com.mivan.streamingsandbox.feature.channels.domain.model

enum class StreamType { HLS, DASH }

data class Channel(
    val id: String,
    val name: String,
    val type: StreamType,
    val url: String
)
