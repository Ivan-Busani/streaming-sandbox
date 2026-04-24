package com.mivan.streamingsandbox.feature.player.presentation

import android.util.Log
import android.view.View
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModel
import com.mivan.streamingsandbox.feature.channels.domain.model.Channel
import com.mivan.streamingsandbox.feature.channels.domain.model.EpgEntry
import com.mivan.streamingsandbox.feature.channels.domain.repository.ChannelRepository
import com.mivan.streamingsandbox.feature.channels.domain.usecase.GetChannelsUseCase
import com.mivan.streamingsandbox.feature.channels.domain.usecase.GetProgramsForChannelUseCase
import com.mivan.streamingsandbox.feature.player.domain.PlaybackMetrics
import com.mivan.streamingsandbox.feature.player.domain.PlayerEngineFactory
import com.mivan.streamingsandbox.feature.player.domain.PlayerEngineState
import com.mivan.streamingsandbox.feature.player.domain.PlayerVendorProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
    getChannelsUseCase: GetChannelsUseCase,
    private val getProgramsForChannelUseCase: GetProgramsForChannelUseCase,
    playerEngineFactory: PlayerEngineFactory,
    playerVendorProvider: PlayerVendorProvider
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val playerEngine = playerEngineFactory.create(playerVendorProvider.currentVendor())
    private val allEpg = MutableStateFlow<List<EpgEntry>>(emptyList())
    private var refreshEpgJob: Job? = null

    private companion object {
        private const val TAG = "*|PlayerViewModel"
    }
    private var lastLoggedMetrics: PlaybackMetrics? = null

    init {
        // Load channels
        viewModelScope.launch {
            val channels = getChannelsUseCase()
            _uiState.value = PlayerUiState(
                channels = channels,
                playbackState = PlaybackUiState.Idle
            )
        }

        // Load EPG
        refreshEpg()

        // Player state
        viewModelScope.launch {
            playerEngine.state.collect { engineState ->
                _uiState.update { current ->
                    current.copy(playbackState = engineState.toUiState())
                }
            }
        }

        // Refresh EPG
        viewModelScope.launch {
            while (true) {
                delay(6 * 60 * 60 * 1000L)
                refreshEpg()
            }
        }

        // Update metrics
        viewModelScope.launch {
            playerEngine.metrics.collect { metrics ->
                _uiState.update { current ->
                    current.copy(metrics = metrics)
                }
                logMetricsIfChanged(metrics)
            }
        }

        // UI visual update (time/progress)
        viewModelScope.launch {
            while (true) {
                updateState()
                delay(1_000L)
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
            programs = emptyList()
        )

        loadChannel(channel, 0L)

        val epgSnapshot = allEpg.value
        if (epgSnapshot.isNotEmpty()) {
            updateState(isProgramsLoading = true)
            val programs = getProgramsForChannelUseCase(
                channelId = channel.id,
                epg = epgSnapshot
            )
            updateState(programs = programs, isProgramsLoading = false)
        } else {
            refreshEpg()
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

    fun refreshEpg(force: Boolean? = false) {
        refreshEpgJob?.cancel()
        refreshEpgJob = viewModelScope.launch {
            updateState(isProgramsLoading = true)
            try {
                val epg = channelRepository.getEpgEntries(force)
                allEpg.value = epg

                val selected = _uiState.value.selectedChannel
                if (selected != null) {
                    val programs = getProgramsForChannelUseCase(
                        channelId = selected.id,
                        epg = epg
                    )
                    updateState(programs = programs)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                _uiState.update { current ->
                    if (current.selectedChannel != null) {
                        current.copy(isProgramsLoading = false)
                    } else {
                        current
                    }
                }
            } finally {
                updateState(isProgramsLoading = false)
            }
        }
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
        val nextPrograms = programs ?: current.programs
        val live = currentProgram(nextPrograms, now)
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
                programs = nextPrograms,
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
