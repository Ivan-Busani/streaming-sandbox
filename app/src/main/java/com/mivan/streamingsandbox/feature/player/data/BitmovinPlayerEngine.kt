package com.mivan.streamingsandbox.feature.player.data

import android.view.View
import com.mivan.streamingsandbox.feature.player.domain.PlaybackMetrics
import com.mivan.streamingsandbox.feature.player.domain.PlayerEngine
import com.mivan.streamingsandbox.feature.player.domain.PlayerEngineState
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
        TODO("Not yet implemented")
    }

    override fun prepare(url: String, playWhenReady: Boolean, seekToMs: Long) {
        _state.value = PlayerEngineState.Error("Bitmovin no integrado aun.")
    }

    override fun play() {
        TODO("Not yet implemented")
    }

    override fun pause() {
        TODO("Not yet implemented")
    }

    override fun currentPositionMs(): Long {
        TODO("Not yet implemented")
    }

    override fun release() {
        TODO("Not yet implemented")
    }
}