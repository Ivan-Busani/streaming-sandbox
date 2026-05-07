package com.mivan.streamingsandbox.feature.player.presentation

import android.content.Context
import android.view.View
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModel
import com.mivan.streamingsandbox.feature.channels.domain.model.Channel
import com.mivan.streamingsandbox.feature.channels.domain.model.EpgEntry
import com.mivan.streamingsandbox.feature.channels.domain.usecase.GetChannelsUseCase
import com.mivan.streamingsandbox.feature.channels.domain.usecase.GetEpgEntriesUseCase
import com.mivan.streamingsandbox.feature.channels.domain.usecase.GetProgramsForChannelUseCase
import com.mivan.streamingsandbox.feature.vod.domain.model.VodItem
import com.mivan.streamingsandbox.feature.vod.domain.usecase.GetVodItemsUseCase
import com.mivan.streamingsandbox.feature.player.domain.PlaybackMetrics
import com.mivan.streamingsandbox.feature.player.domain.PlayerEngineFactory
import com.mivan.streamingsandbox.feature.player.domain.PlayerEngineState
import com.mivan.streamingsandbox.feature.player.domain.PlayerVendorProvider
import dagger.hilt.android.qualifiers.ApplicationContext
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
import androidx.core.content.edit

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext context: Context,
    getChannelsUseCase: GetChannelsUseCase,
    private val getEpgEntriesUseCase: GetEpgEntriesUseCase,
    getVodItemsUseCase: GetVodItemsUseCase,
    private val getProgramsForChannelUseCase: GetProgramsForChannelUseCase,
    playerEngineFactory: PlayerEngineFactory,
    playerVendorProvider: PlayerVendorProvider
) : ViewModel() {
    private val appContext = context.applicationContext

    private companion object {
        private const val TAG = "*|PlayerViewModel"
        private const val PREFS_NAME = "player_favorites_prefs"
        private const val KEY_FAVORITE_CHANNEL_IDS = "favorite_channel_ids"
        private const val KEY_FAVORITE_VOD_IDS = "favorite_vod_ids"
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

    /** Avoid clearing tuning on stale Playing/Ready from previous media after prepare(). */
    private var sawIdleOrBufferingSinceTuneStarted = false

    init {
        // Load channels and video on demand
        viewModelScope.launch {
            val channels = getChannelsUseCase()
            val vodItems = getVodItemsUseCase()
            _uiState.value = PlayerUiState(
                channels = channels,
                vodItems = vodItems,
                favoriteChannelIds = readFavoriteChannelIds(),
                favoriteVodIds = readFavoriteVodIds(),
                playbackState = PlaybackUiState.Idle
            )
        }

        // Load EPG
        refreshEpg()

        // Player state
        viewModelScope.launch {
            playerEngine.state.collect { engineState ->
                val current = _uiState.value
                if (current.isTuningMedia) {
                    when (engineState) {
                        PlayerEngineState.Idle,
                        PlayerEngineState.Buffering -> sawIdleOrBufferingSinceTuneStarted = true
                        else -> Unit
                    }
                }
                val tuningFinished = current.isTuningMedia && when (engineState) {
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
                        isTuningMedia = if (tuningFinished) false else cur.isTuningMedia
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

    fun openMediaSelector(open: Boolean = true) {
        _uiState.update { current ->
            current.copy(
                isMediaSelectorOpen = open,
                isFavoritesSelectorOpen = if (open) false else current.isFavoritesSelectorOpen
            )
        }
    }

    fun openFavoritesSelector(open: Boolean = true) {
        _uiState.update { current ->
            current.copy(
                isFavoritesSelectorOpen = open,
                isMediaSelectorOpen = if (open) false else current.isMediaSelectorOpen
            )
        }
    }

    fun toggleChannelFavorite(channel: Channel) {
        _uiState.update { current ->
            val next = current.favoriteChannelIds.toMutableSet()
            if (!next.add(channel.id)) {
                next.remove(channel.id)
            }
            persistFavoriteChannelIds(next)
            current.copy(favoriteChannelIds = next)
        }
    }

    fun toggleVodFavorite(vodItem: VodItem) {
        _uiState.update { current ->
            val next = current.favoriteVodIds.toMutableSet()
            if (!next.add(vodItem.id)) {
                next.remove(vodItem.id)
            }
            persistFavoriteVodIds(next)
            current.copy(favoriteVodIds = next)
        }
    }

    fun selectChannel(channel: Channel) {
        val current = _uiState.value.selectedChannel
        if (current?.id == channel.id) return

        goLiveGraceUntilMs = 0L
        goLiveGraceStartedAtMs = 0L
        sawIdleOrBufferingSinceTuneStarted = false
        _uiState.update { state ->
            state.copy(
                isBehindLive = false,
                isTuningMedia = true,
                selectedChannel = channel,
                selectedVodItem = null
            )
        }

        updateState(
            selectedChannel = channel,
            programs = emptyList()
        )

        loadMedia(PlayableMedia.Live(channel), 0L)

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

    fun selectVod(vodItem: VodItem) {
        val current = _uiState.value.selectedVodItem
        if (current?.id == vodItem.id) return

        goLiveGraceUntilMs = 0L
        goLiveGraceStartedAtMs = 0L
        sawIdleOrBufferingSinceTuneStarted = false

        _uiState.update { state ->
            state.copy(
                isBehindLive = false,
                isTuningMedia = true,
                selectedChannel = null,
                selectedVodItem = vodItem
            )
        }

        updateState(
            selectedVodItem = vodItem,
            programs = emptyList()
        )

        loadMedia(PlayableMedia.Vod(vodItem), 0L)
    }

    fun retryCurrentMedia() {
        val current = _uiState.value.selectedPlayableMedia ?: return
        sawIdleOrBufferingSinceTuneStarted = false
        _uiState.update { it.copy(isTuningMedia = true) }
        loadMedia(
            media = current,
            seekToMs = playerEngine.currentPositionMs()
        )
    }

    fun onHostStop() {
        playerEngine.pause()
    }

    fun onHostStart() {
        if (_uiState.value.selectedPlayableMedia != null) {
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
        val playbackState = _uiState.value.playbackState
        val active = playbackState == PlaybackUiState.Playing || playbackState == PlaybackUiState.Buffering

        if (active) {
            playerEngine.pause()
        } else if (_uiState.value.selectedPlayableMedia != null) {
            playerEngine.play()
        }
    }

    fun seekBack() {
        val state = _uiState.value
        if (state.selectedPlayableMedia == null) return
        if (state.isLiveStream) return
        playerEngine.seekBack()
    }

    fun seekForward() {
        val state = _uiState.value
        if (state.selectedPlayableMedia == null) return
        if (state.isLiveStream) return
        playerEngine.seekForward()
    }

    fun seekTo(positionMs: Long) {
        val state = _uiState.value
        if (state.selectedPlayableMedia == null) return
        if (state.isLiveStream) return
        playerEngine.seekTo(positionMs)
    }

    fun goToLive() {
        val state = _uiState.value
        if (state.selectedPlayableMedia == null) return
        val now = System.currentTimeMillis()
        goLiveGraceStartedAtMs = now
        goLiveGraceUntilMs = now + GO_LIVE_GRACE_MS
        playerEngine.seekToLiveEdge()
        playerEngine.play()
    }

    fun refreshEpg(force: Boolean? = false) {
        refreshEpgJob?.cancel()
        refreshEpgJob = viewModelScope.launch {
            updateState(isProgramsLoading = true)
            try {
                val epg = getEpgEntriesUseCase(force)
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
                    if (current.selectedPlayableMedia != null) {
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

    private fun updateState(
        selectedChannel: Channel? = null,
        selectedVodItem: VodItem? = null,
        programs: List<EpgEntry>? = null,
        isProgramsLoading: Boolean? = null
    ) {
        val current = _uiState.value
        val now = System.currentTimeMillis()
        val nextPrograms = programs ?: current.programs

        val nextSelectedLive = selectedChannel ?: current.selectedChannel
        val nextSelectedVod = selectedVodItem ?: current.selectedVodItem
        if (nextSelectedLive == null && nextSelectedVod == null) {
            _uiState.update { cur ->
                cur.copy (
                    selectedChannel = null,
                    selectedVodItem = null,
                    programs = emptyList(),
                    epgNowEpochMs = now,
                    playbackPositionMs = null,
                    playbackDurationMs = null,
                    liveOffsetMs = null,
                    isBehindLive = false,
                    isLiveStream = false,
                    currentProgramProgressPercent = null,
                    currentProgramElapsedMs = null,
                    currentProgramTotalMs = null,
                    canSeekLiveDvr = false,
                    isProgramsLoading = false
                )
            }
            return
        }

        // Do not read old player/timeline values while tuning — avoids stale UI from previous channel.
        if (current.isTuningMedia) {
            _uiState.update { cur ->
                cur.copy(
                    selectedChannel = selectedChannel ?: cur.selectedChannel,
                    selectedVodItem = selectedVodItem ?: cur.selectedVodItem,
                    programs = nextPrograms,
                    epgNowEpochMs = now,
                    playbackPositionMs = null,
                    playbackDurationMs = null,
                    liveOffsetMs = null,
                    isBehindLive = false,
                    isLiveStream = false,
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
                selectedVodItem = selectedVodItem ?: current.selectedVodItem,
                playbackPositionMs = playbackPos,
                playbackDurationMs = playbackDur,
                programs = nextPrograms,
                epgNowEpochMs = now,
                currentProgramProgressPercent = percent,
                currentProgramElapsedMs = elapsed,
                currentProgramTotalMs = total,
                liveOffsetMs = liveOffsetMs,
                isBehindLive = effectiveIsBehindLive,
                isLiveStream = isLiveStream,
                isProgramsLoading = isProgramsLoading ?: current.isProgramsLoading,
                canSeekLiveDvr = canSeekLiveDvr
            )
        }
    }

    private fun loadMedia(media: PlayableMedia, seekToMs: Long) {
        playerEngine.prepare(
            media = media,
            playWhenReady = true,
            seekToMs = seekToMs
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

    // Keep favorites across app restarts.
    private fun persistFavoriteChannelIds(ids: Set<String>) {
        appContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putStringSet(KEY_FAVORITE_CHANNEL_IDS, ids)
            }
    }

    // Keep favorites across app restarts.
    private fun persistFavoriteVodIds(ids: Set<String>) {
        appContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putStringSet(KEY_FAVORITE_VOD_IDS, ids)
            }
    }

    private fun readFavoriteChannelIds(): Set<String> {
        val persisted = appContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_FAVORITE_CHANNEL_IDS, emptySet())
            ?: emptySet()
        return persisted.toSet()
    }

    private fun readFavoriteVodIds(): Set<String> {
        val persisted = appContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_FAVORITE_VOD_IDS, emptySet())
            ?: emptySet()
        return persisted.toSet()
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
