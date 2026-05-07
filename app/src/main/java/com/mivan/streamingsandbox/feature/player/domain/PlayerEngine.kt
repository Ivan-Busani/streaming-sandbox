package com.mivan.streamingsandbox.feature.player.domain

import android.view.View
import com.mivan.streamingsandbox.feature.player.presentation.PlayableMedia
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
        media: PlayableMedia,
        playWhenReady: Boolean = true,
        seekToMs: Long = 0L,
    )
    fun play()
    fun pause()
    fun isCurrentLive(): Boolean
    fun isLiveDvrSeekable(): Boolean
    fun seekBack()
    fun seekForward()
    fun seekTo(positionMs: Long)
    fun seekToLiveEdge()
    fun liveOffsetMs(): Long?
    fun durationMs(): Long
    fun currentPositionMs(): Long
    fun release()
}
