package com.mivan.streamingsandbox.feature.player.presentation

import android.util.Log
import android.view.View
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModel
import com.mivan.streamingsandbox.feature.channels.domain.model.Channel
import com.mivan.streamingsandbox.feature.channels.domain.model.EpgEntry
import com.mivan.streamingsandbox.feature.channels.domain.usecase.GetChannelsUseCase
import com.mivan.streamingsandbox.feature.channels.domain.usecase.GetProgramsForChannelUseCase
import com.mivan.streamingsandbox.feature.player.domain.PlaybackMetrics
import com.mivan.streamingsandbox.feature.player.domain.PlayerEngineFactory
import com.mivan.streamingsandbox.feature.player.domain.PlayerEngineState
import com.mivan.streamingsandbox.feature.player.domain.PlayerVendorProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    getChannelsUseCase: GetChannelsUseCase,
    private val getProgramsForChannelUseCase: GetProgramsForChannelUseCase,
    playerEngineFactory: PlayerEngineFactory,
    playerVendorProvider: PlayerVendorProvider
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val playerEngine = playerEngineFactory.create(playerVendorProvider.currentVendor())

    private companion object {
        private const val TAG = "*|PlayerViewModel"
    }
    private var lastLoggedMetrics: PlaybackMetrics? = null

    init {
        viewModelScope.launch {
            val channels = getChannelsUseCase()
            _uiState.value = PlayerUiState(
                channels = channels,
                playbackState = PlaybackUiState.Idle
            )
        }

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

        viewModelScope.launch {
            while (true) {
                updateState()
                delay(1_000L)
            }
        }

        viewModelScope.launch {
            while (true) {
                val selected = _uiState.value.selectedChannel
                if (selected != null) {
                    val programs = getProgramsForChannelUseCase(selected.id)
                    updateState(programs = programs)
                }
                delay(30_000L)
            }
        }
    }

    fun openChannelSelector(open: Boolean = true) {
        _uiState.update { current ->
            current.copy(isChannelSelectorOpen = open)
        }
    }

    fun selectChannel(channel: Channel) {
        val current = _uiState.value.selectedChannel
        if (current?.id == channel.id) return

        updateState(
            selectedChannel = channel,
            programs = emptyList(),
            isProgramsLoading = true
        )

        loadChannel(channel, 0L)

        viewModelScope.launch {
            runCatching { getProgramsForChannelUseCase(channel.id) }
                .onSuccess { programs ->
                    if (_uiState.value.selectedChannel?.id != channel.id) return@onSuccess
                    updateState(programs = programs, isProgramsLoading = false)
                }
                .onFailure { error ->
                    Log.w(TAG, "EPG background load failed for ${channel.id}", error)
                    updateState(programs = emptyList(), isProgramsLoading = false)
                }
        }
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

    fun togglePlayPause() {
        val state = _uiState.value.playbackState
        val active = state == PlaybackUiState.Playing || state == PlaybackUiState.Buffering

        if (active) {
            playerEngine.pause()
        } else if (_uiState.value.selectedChannel != null) {
            playerEngine.play()
        }
    }

    fun goToLive() {
        if (_uiState.value.selectedChannel == null) return
        playerEngine.seekToLiveEdge()
        playerEngine.play()
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

    private fun currentProgram(programs: List<EpgEntry>, nowEpochMs: Long): EpgEntry? {
        return programs.firstOrNull {
            nowEpochMs >= it.startEpochMs && nowEpochMs < it.endEpochMs
        }
    }

    private fun updateState(selectedChannel: Channel? = null, programs: List<EpgEntry>? = null, isProgramsLoading: Boolean? = null) {
        val current = _uiState.value
        val now = System.currentTimeMillis()
        val live = currentProgram(current.programs, now)
        val total = live?.let {
            (it.endEpochMs - it.startEpochMs).coerceAtLeast(1L)
        }
        val elapsed = live?.let {
            (now - it.startEpochMs).coerceIn(0L, total ?: 1L)
        }
        val percent = if (elapsed != null && total != null) {
            ((elapsed * 100) / total).toInt()
        } else {
            null
        }

        _uiState.update { current ->
            current.copy(
                selectedChannel = selectedChannel ?: current.selectedChannel,
                programs = programs ?: current.programs,
                epgNowEpochMs = now,
                currentProgramProgressPercent = percent,
                currentProgramElapsedMs = elapsed,
                currentProgramTotalMs = total,
                isProgramsLoading = isProgramsLoading ?: current.isProgramsLoading
            )
        }
    }

    private fun logMetricsIfChanged(metrics: PlaybackMetrics) {
        if (lastLoggedMetrics == metrics) return
        lastLoggedMetrics = metrics
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
