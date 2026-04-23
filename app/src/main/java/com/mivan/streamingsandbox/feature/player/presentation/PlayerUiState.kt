package com.mivan.streamingsandbox.feature.player.presentation

import com.mivan.streamingsandbox.feature.channels.domain.model.Channel
import com.mivan.streamingsandbox.feature.channels.domain.model.EpgEntry
import com.mivan.streamingsandbox.feature.player.domain.PlaybackMetrics

data class PlayerUiState(
    val isChannelSelectorOpen: Boolean = false,
    val channels: List<Channel> = emptyList(),
    val selectedChannel: Channel? = null,
    val playbackState: PlaybackUiState = PlaybackUiState.Idle,
    val metrics: PlaybackMetrics = PlaybackMetrics(),
    val epgNowEpochMs: Long = System.currentTimeMillis(),
    val programs: List<EpgEntry> = emptyList(),
    val currentProgramProgressPercent: Int? = null,
    val currentProgramElapsedMs: Long? = null,
    val currentProgramTotalMs: Long? = null,
    var isProgramsLoading: Boolean = false
)

sealed class PlaybackUiState {
    data object Idle : PlaybackUiState()
    data object Buffering : PlaybackUiState()
    data object Ready : PlaybackUiState()
    data object Playing : PlaybackUiState()
    data object Ended : PlaybackUiState()
    data class Error(val message: String) : PlaybackUiState()
}
