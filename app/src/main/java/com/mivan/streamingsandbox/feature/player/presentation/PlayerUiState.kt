package com.mivan.streamingsandbox.feature.player.presentation

import com.mivan.streamingsandbox.feature.channels.domain.model.Channel
import com.mivan.streamingsandbox.feature.channels.domain.model.EpgEntry
import com.mivan.streamingsandbox.feature.player.domain.PlaybackMetrics
import com.mivan.streamingsandbox.feature.vod.domain.model.VodItem

data class PlayerUiState(
    val isMediaSelectorOpen: Boolean = false,
    val isFavoritesSelectorOpen: Boolean = false,
    val channels: List<Channel> = emptyList(),
    val vodItems: List<VodItem> = emptyList(),
    val favoriteChannelIds: Set<String> = emptySet(),
    val favoriteVodIds: Set<String> = emptySet(),
    val selectedChannel: Channel? = null,
    val selectedVodItem: VodItem? = null,
    val playbackState: PlaybackUiState = PlaybackUiState.Idle,
    val playbackPositionMs: Long? = null,
    val playbackDurationMs: Long? = null,
    val epgNowEpochMs: Long = System.currentTimeMillis(),
    val programs: List<EpgEntry> = emptyList(),
    val currentProgramProgressPercent: Int? = null,
    val currentProgramElapsedMs: Long? = null,
    val currentProgramTotalMs: Long? = null,
    val liveOffsetMs: Long? = null,
    val isBehindLive: Boolean = false,
    val isLiveStream: Boolean = false,
    var isProgramsLoading: Boolean = false,
    val canSeekLiveDvr: Boolean = false,
    /** True after channel change until first Ready/Playing/Error (initial tune). */
    val isTuningMedia: Boolean = false,
    val metrics: PlaybackMetrics = PlaybackMetrics()
) {
    val selectedPlayableMedia: PlayableMedia?
        get() = when {
            selectedChannel != null -> PlayableMedia.Live(selectedChannel)
            selectedVodItem != null -> PlayableMedia.Vod(selectedVodItem)
            else -> null
        }
}

sealed interface PlayableMedia {
    data class Live(val channel: Channel) : PlayableMedia
    data class Vod(val item: VodItem) : PlayableMedia
}

sealed class PlaybackUiState {
    data object Idle : PlaybackUiState()
    data object Buffering : PlaybackUiState()
    data object Ready : PlaybackUiState()
    data object Playing : PlaybackUiState()
    data object Ended : PlaybackUiState()
    data class Error(val message: String) : PlaybackUiState()
}
