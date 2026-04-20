package com.mivan.streamingsandbox.feature.player.presentation

import com.mivan.streamingsandbox.feature.channels.domain.model.Channel
import com.mivan.streamingsandbox.feature.channels.domain.usecase.NowAndNext
import com.mivan.streamingsandbox.feature.player.domain.PlaybackMetrics

data class PlayerUiState(
    val channels: List<Channel> = emptyList(),
    val selectedChannel: Channel? = null,
    val playbackState: PlaybackUiState = PlaybackUiState.Idle,
    val metrics: PlaybackMetrics = PlaybackMetrics(),
    val nowAndNext: NowAndNext? = null,
    val epgNowEpochMs: Long = System.currentTimeMillis()
)

sealed class PlaybackUiState {
    data object Idle : PlaybackUiState()
    data object Buffering : PlaybackUiState()
    data object Ready : PlaybackUiState()
    data object Playing : PlaybackUiState()
    data object Ended : PlaybackUiState()
    data class Error(val message: String) : PlaybackUiState()
}
