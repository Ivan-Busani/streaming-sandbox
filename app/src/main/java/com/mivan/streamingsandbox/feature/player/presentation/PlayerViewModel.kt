package com.mivan.streamingsandbox.feature.player.presentation

import android.util.Log
import android.view.View
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModel
import com.mivan.streamingsandbox.feature.channels.domain.model.Channel
import com.mivan.streamingsandbox.feature.channels.domain.usecase.GetChannelsUseCase
import com.mivan.streamingsandbox.feature.player.domain.PlaybackMetrics
import com.mivan.streamingsandbox.feature.player.domain.PlayerEngineFactory
import com.mivan.streamingsandbox.feature.player.domain.PlayerEngineState
import com.mivan.streamingsandbox.feature.player.domain.PlayerVendorProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    getChannelsUseCase: GetChannelsUseCase,
    playerEngineFactory: PlayerEngineFactory,
    playerVendorProvider: PlayerVendorProvider
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val playerEngine = playerEngineFactory.create(playerVendorProvider.currentVendor())

    private companion object {
        private const val TAG = "PlayerViewModel"
    }
    private var lastLoggedMetrics: PlaybackMetrics? = null

    init {
        val channels = getChannelsUseCase()

        _uiState.value = PlayerUiState(
            channels = channels,
            playbackState = PlaybackUiState.Idle
        )

        viewModelScope.launch {
            playerEngine.state.collect { engineState ->
                _uiState.update { current ->
                    current.copy(playbackState = engineState.toUiState())
                }
            }
        }

        viewModelScope.launch {
            playerEngine.metrics.collect { metrics ->
                _uiState.update { current ->
                    current.copy(metrics = metrics)
                }
                logMetricsIfChanged(metrics)
            }
        }
    }

    fun selectChannel(channel: Channel) {
        val current = _uiState.value.selectedChannel
        if (current?.id == channel.id) return

        _uiState.update { it.copy(selectedChannel = channel) }

        loadChannel(
            channel = channel,
            seekToMs = 0L
        )
    }

    fun retryCurrentChannel() {
        val current = _uiState.value.selectedChannel ?: return
        loadChannel(
            channel = current,
            seekToMs = playerEngine.currentPositionMs()
        )
    }

    fun onHostStop() {
        playerEngine.pause()
    }

    fun onHostStart() {
        if (_uiState.value.selectedChannel != null) {
            playerEngine.play()
        }
    }

    fun attachPlayerView(view: View) {
        playerEngine.attachView(view)
    }

    private fun loadChannel(channel: Channel, seekToMs: Long) {
        playerEngine.prepare(
            channelId = channel.id,
            url = channel.url,
            playWhenReady = true,
            seekToMs = seekToMs,
            drm = channel.drm
        )
    }

    private fun logMetricsIfChanged(metrics: PlaybackMetrics) {
        if (lastLoggedMetrics == metrics) return
        lastLoggedMetrics = metrics
        Log.d(
            TAG,
            "QoE metrics -> startupMs=${metrics.startupTimeMs}, " +
                "rebuffers=${metrics.rebufferCount}, " +
                "rebufferTotalMs=${metrics.totalRebufferMs}," +
                "fatalErrors=${metrics.fatalErrorCount}"
        )
    }

    override fun onCleared() {
        playerEngine.release()
        super.onCleared()
    }
}

private fun PlayerEngineState.toUiState(): PlaybackUiState = when (this) {
    PlayerEngineState.Idle -> PlaybackUiState.Idle
    PlayerEngineState.Buffering -> PlaybackUiState.Buffering
    PlayerEngineState.Ready -> PlaybackUiState.Ready
    PlayerEngineState.Playing -> PlaybackUiState.Playing
    PlayerEngineState.Ended -> PlaybackUiState.Ended
    is PlayerEngineState.Error -> PlaybackUiState.Error(message)
}
