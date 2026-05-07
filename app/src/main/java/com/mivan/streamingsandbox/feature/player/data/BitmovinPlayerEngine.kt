package com.mivan.streamingsandbox.feature.player.data

import android.view.View
import com.mivan.streamingsandbox.feature.player.domain.PlaybackMetrics
import com.mivan.streamingsandbox.feature.player.domain.PlayerEngine
import com.mivan.streamingsandbox.feature.player.domain.PlayerEngineState
import com.mivan.streamingsandbox.feature.player.presentation.PlayableMedia
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BitmovinPlayerEngine @Inject constructor() : PlayerEngine {
    private val _state = MutableStateFlow<PlayerEngineState>(PlayerEngineState.Idle)
    override val state: StateFlow<PlayerEngineState> = _state.asStateFlow()

    private val _metrics = MutableStateFlow(PlaybackMetrics())
    override val metrics: StateFlow<PlaybackMetrics> = _metrics.asStateFlow()

    private var loadStartMs: Long = 0L
    private var rebufferStartMs: Long? = null

    override fun attachView(view: View) {
        _state.value = PlayerEngineState.Error("Vendor no integrado aun.")
    }

    override fun detachView(view: View) {
        _state.value = PlayerEngineState.Error("Vendor no integrado aun.")
    }

    override fun prepare(media: PlayableMedia, playWhenReady: Boolean, seekToMs: Long) {
        _state.value = PlayerEngineState.Error("Vendor no integrado aun.")
    }

    override fun play() {
        _state.value = PlayerEngineState.Error("Vendor no integrado aun.")
    }

    override fun pause() {
        _state.value = PlayerEngineState.Error("Vendor no integrado aun.")
    }

    override fun isCurrentLive(): Boolean = false

    override fun isLiveDvrSeekable(): Boolean {
        return false
    }

    override fun seekBack() {
        _state.value = PlayerEngineState.Error("Vendor no integrado aun.")
    }

    override fun seekForward() {
        _state.value = PlayerEngineState.Error("Vendor no integrado aun.")
    }

    override fun seekTo(positionMs: Long) {
        _state.value = PlayerEngineState.Error("Vendor no integrado aun.")
    }

    override fun seekToLiveEdge() {
        _state.value = PlayerEngineState.Error("Vendor no integrado aun.")
    }

    override fun liveOffsetMs(): Long? = null

    override fun durationMs(): Long = 0L

    override fun currentPositionMs(): Long = 0L

    override fun release() {
        _state.value = PlayerEngineState.Error("Vendor no integrado aun.")
    }
}