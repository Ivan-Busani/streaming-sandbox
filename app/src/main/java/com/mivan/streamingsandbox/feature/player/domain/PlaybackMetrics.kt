package com.mivan.streamingsandbox.feature.player.domain

data class PlaybackMetrics(
    val startupTimeMs: Long? = null,
    val rebufferCount: Int = 0,
    val totalRebufferMs: Long = 0L,
    val fatalErrorCount: Int = 0
)