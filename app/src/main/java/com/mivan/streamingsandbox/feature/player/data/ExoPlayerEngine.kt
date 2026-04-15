package com.mivan.streamingsandbox.feature.player.data

import android.content.Context
import android.view.View
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.mivan.streamingsandbox.feature.player.domain.PlaybackMetrics
import com.mivan.streamingsandbox.feature.player.domain.PlayerEngine
import com.mivan.streamingsandbox.feature.player.domain.PlayerEngineState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class ExoPlayerEngine @Inject constructor(
    @ApplicationContext context: Context
) : PlayerEngine {

    private val _state = MutableStateFlow<PlayerEngineState>(PlayerEngineState.Idle)
    override val state: StateFlow<PlayerEngineState> = _state.asStateFlow()

    private val _metrics = MutableStateFlow(PlaybackMetrics())
    override val metrics: StateFlow<PlaybackMetrics> = _metrics.asStateFlow()

    private var loadStartMs: Long = 0L
    private var rebufferStartMs: Long? = null

    private val player = ExoPlayer.Builder(context).build().apply {
        addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                _state.value = when (playbackState) {
                    Player.STATE_IDLE -> PlayerEngineState.Idle
                    Player.STATE_BUFFERING -> {
                        if (rebufferStartMs == null) {
                            rebufferStartMs = System.currentTimeMillis()
                        }
                        PlayerEngineState.Buffering
                    }
                    Player.STATE_READY -> {
                        val now = System.currentTimeMillis()

                        if (_metrics.value.startupTimeMs == null && loadStartMs > 0L) {
                            _metrics.value = _metrics.value.copy(
                                startupTimeMs = now - loadStartMs
                            )
                        }

                        rebufferStartMs?.let { startedAt ->
                            val delta = now - startedAt
                            _metrics.value = _metrics.value.copy(
                                rebufferCount = _metrics.value.rebufferCount + 1,
                                totalRebufferMs = _metrics.value.totalRebufferMs + delta
                            )
                            rebufferStartMs = null
                        }

                        if (isPlaying) PlayerEngineState.Playing else PlayerEngineState.Ready
                    }
                    Player.STATE_ENDED -> PlayerEngineState.Ended
                    else -> PlayerEngineState.Idle
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.value = if (isPlaying) {
                    PlayerEngineState.Playing
                } else {
                    PlayerEngineState.Ready
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                _metrics.value = _metrics.value.copy(
                    fatalErrorCount = _metrics.value.fatalErrorCount + 1
                )
                _state.value = PlayerEngineState.Error(error.message ?: "Playback error")
            }
        })
    }

    override fun attachView(view: View) {
        (view as? PlayerView)?.player = player
    }

    override fun prepare(url: String, playWhenReady: Boolean, seekToMs: Long) {
        loadStartMs = System.currentTimeMillis()
        rebufferStartMs = null
        _metrics.value = PlaybackMetrics()

        player.stop()
        player.clearMediaItems()
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        if (seekToMs > 0L) {
            player.seekTo(seekToMs)
        }
        player.playWhenReady = playWhenReady
    }

    override fun play() = player.play()

    override fun pause() = player.pause()

    override fun currentPositionMs(): Long = player.currentPosition

    override fun release() = player.release()
}
