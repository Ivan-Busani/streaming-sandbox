package com.mivan.streamingsandbox.feature.player.presentation

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
    private companion object {
        private const val TAG = "*|PlayerViewModel"
        private const val LIVE_ENTER_BEHIND_MS = 15_000L
        private const val LIVE_EXIT_BEHIND_MS = 8_000L
        private const val GO_LIVE_GRACE_MS = 12_000L
        private const val GO_LIVE_GRACE_BUFFER_EXTEND_MS = 1_500L
        private const val GO_LIVE_GRACE_MAX_MS = 28_000L
    }

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val playerEngine = playerEngineFactory.create(playerVendorProvider.currentVendor())
    private val allEpg = MutableStateFlow<List<EpgEntry>>(emptyList())
    private var goLiveGraceUntilMs: Long = 0L
    private var goLiveGraceStartedAtMs: Long = 0L
    private var refreshEpgJob: Job? = null
    private var lastLoggedMetrics: PlaybackMetrics? = null

    /** Avoid clearing tuning on stale Playing/Ready from the previous channel after prepare(). */
    private var sawIdleOrBufferingSinceTuneStarted = false

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
                val current = _uiState.value
                if (current.isTuningChannel) {
                    when (engineState) {
                        PlayerEngineState.Idle,
                        PlayerEngineState.Buffering -> sawIdleOrBufferingSinceTuneStarted = true
                        else -> Unit
                    }
                }
                val tuningFinished = current.isTuningChannel && when (engineState) {
                    is PlayerEngineState.Error -> true
                    PlayerEngineState.Ready,
                    PlayerEngineState.Playing,
                    PlayerEngineState.Ended -> sawIdleOrBufferingSinceTuneStarted
                    else -> false
                }
                if (tuningFinished) {
                    sawIdleOrBufferingSinceTuneStarted = false
                }
                _uiState.update { cur ->
                    cur.copy(
                        playbackState = engineState.toUiState(),
                        isTuningChannel = if (tuningFinished) false else cur.isTuningChannel
                    )
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

        goLiveGraceUntilMs = 0L
        goLiveGraceStartedAtMs = 0L
        sawIdleOrBufferingSinceTuneStarted = false
        _uiState.update { state ->
            state.copy(isBehindLive = false, isTuningChannel = true)
        }

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
        sawIdleOrBufferingSinceTuneStarted = false
        _uiState.update { it.copy(isTuningChannel = true) }
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

    fun detachPlayerView(view: View) {
        playerEngine.detachView(view)
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
        val now = System.currentTimeMillis()
        goLiveGraceStartedAtMs = now
        goLiveGraceUntilMs = now + GO_LIVE_GRACE_MS
        playerEngine.seekToLiveEdge()
        playerEngine.play()
    }

    fun seekBack() {
        if (_uiState.value.selectedChannel == null) return
        playerEngine.seekBack()
    }

    fun seekForward() {
        if (_uiState.value.selectedChannel == null) return
        playerEngine.seekForward()
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

    private fun updateState(selectedChannel: Channel? = null, programs: List<EpgEntry>? = null, isProgramsLoading: Boolean? = null) {
        val current = _uiState.value
        val now = System.currentTimeMillis()

        val nextPrograms = programs ?: current.programs

        // Do not read old player/timeline values while tuning — avoids stale UI from previous channel.
        if (current.isTuningChannel) {
            _uiState.update { cur ->
                cur.copy(
                    selectedChannel = selectedChannel ?: cur.selectedChannel,
                    programs = nextPrograms,
                    epgNowEpochMs = now,
                    playbackPositionMs = null,
                    playbackDurationMs = null,
                    liveOffsetMs = null,
                    isBehindLive = false,
                    currentProgramProgressPercent = null,
                    currentProgramElapsedMs = null,
                    currentProgramTotalMs = null,
                    canSeekLiveDvr = false,
                    isProgramsLoading = isProgramsLoading ?: cur.isProgramsLoading
                )
            }
            return
        }

        val liveOffsetMs = playerEngine.liveOffsetMs()
        val previousBehind = current.isBehindLive
        val isLiveStream = playerEngine.isCurrentLive()

        val isBehindLive = when {
            !isLiveStream -> false
            liveOffsetMs == null -> previousBehind
            previousBehind -> liveOffsetMs > LIVE_EXIT_BEHIND_MS
            else -> liveOffsetMs > LIVE_ENTER_BEHIND_MS
        }
        val inGoLiveGrace = now < goLiveGraceUntilMs
        if (inGoLiveGrace && current.playbackState == PlaybackUiState.Buffering && goLiveGraceStartedAtMs > 0L) {
            val graceCap = goLiveGraceStartedAtMs + GO_LIVE_GRACE_MAX_MS
            val extended = goLiveGraceUntilMs + GO_LIVE_GRACE_BUFFER_EXTEND_MS
            goLiveGraceUntilMs = minOf(extended, graceCap)
        }

        val effectiveIsBehindLive = if (now < goLiveGraceUntilMs) false else isBehindLive
        val live = currentProgram(nextPrograms, now)

        val hasEpgProgram = live != null

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

        val playbackPos = playerEngine.currentPositionMs().takeIf { it >= 0L }
        val playbackDur = if (!hasEpgProgram && isLiveStream) {
            null
        } else {
            playerEngine.durationMs()
        }

        val canSeekLiveDvr = playerEngine.isLiveDvrSeekable()

        _uiState.update { current ->
            current.copy(
                selectedChannel = selectedChannel ?: current.selectedChannel,
                playbackPositionMs = playbackPos,
                playbackDurationMs = playbackDur,
                programs = nextPrograms,
                epgNowEpochMs = now,
                currentProgramProgressPercent = percent,
                currentProgramElapsedMs = elapsed,
                currentProgramTotalMs = total,
                liveOffsetMs = liveOffsetMs,
                isBehindLive = effectiveIsBehindLive,
                isProgramsLoading = isProgramsLoading ?: current.isProgramsLoading,
                canSeekLiveDvr = canSeekLiveDvr
            )
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
