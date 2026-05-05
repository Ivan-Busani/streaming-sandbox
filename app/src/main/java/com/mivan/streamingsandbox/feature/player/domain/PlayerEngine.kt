package com.mivan.streamingsandbox.feature.player.domain

import android.view.View
import kotlinx.coroutines.flow.StateFlow

sealed class PlayerEngineState {
    data object Idle : PlayerEngineState()
    data object Buffering : PlayerEngineState()
    data object Ready : PlayerEngineState()
    data object Playing : PlayerEngineState()
    data object Ended : PlayerEngineState()
    data class Error(val message: String) : PlayerEngineState()
}

interface PlayerEngine {
    val state: StateFlow<PlayerEngineState>
    val metrics: StateFlow<PlaybackMetrics>

    fun attachView(view: View)
    fun detachView(view: View)
    fun prepare(
        channelId: String,
        url: String,
        playWhenReady: Boolean = true,
        seekToMs: Long = 0L,
        drm: DrmConfig? = null
    )
    fun play()
    fun pause()
    fun isCurrentLive(): Boolean
    fun isLiveDvrSeekable(): Boolean
    fun seekBack()
    fun seekForward()
    fun seekToLiveEdge()
    fun liveOffsetMs(): Long?
    fun durationMs(): Long
    fun currentPositionMs(): Long
    fun release()
}
