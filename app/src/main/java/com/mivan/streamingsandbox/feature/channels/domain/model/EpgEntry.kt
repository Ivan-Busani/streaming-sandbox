package com.mivan.streamingsandbox.feature.channels.domain.model

data class EpgEntry(
    val channelId: String,
    val title: String,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val description: String? = null,
    val sourceDisplayName: String? = null
)
