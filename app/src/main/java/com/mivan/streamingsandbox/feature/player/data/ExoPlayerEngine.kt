package com.mivan.streamingsandbox.feature.player.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.mivan.streamingsandbox.feature.player.domain.DrmConfig
import com.mivan.streamingsandbox.feature.player.domain.DrmScheme
import com.mivan.streamingsandbox.feature.player.domain.PlaybackMetrics
import com.mivan.streamingsandbox.feature.player.domain.PlayerEngine
import com.mivan.streamingsandbox.feature.player.domain.PlayerEngineState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@UnstableApi
@Singleton
class ExoPlayerEngine @Inject constructor(
    @ApplicationContext context: Context
) : PlayerEngine {
    private val appContext: Context = context

    private companion object {
        private const val TAG = "*|ExoPlayerEngine"
    }

    private val _state = MutableStateFlow<PlayerEngineState>(PlayerEngineState.Idle)
    override val state: StateFlow<PlayerEngineState> = _state.asStateFlow()

    private val _metrics = MutableStateFlow(PlaybackMetrics())
    override val metrics: StateFlow<PlaybackMetrics> = _metrics.asStateFlow()

    private var loadStartMs: Long = 0L
    private var rebufferStartMs: Long? = null
    private var lastChannelId: String? = null

    private var player: ExoPlayer? = null

    private fun ensurePlayer(): ExoPlayer {
        val existing = player
        if (existing != null) return existing

        val created = ExoPlayer.Builder(appContext)
            .setLooper(Looper.getMainLooper())
            .build()
            .apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (_state.value is PlayerEngineState.Error) return

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
                        if (_state.value is PlayerEngineState.Error) return

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

                        val userMessage = when {
                            error.errorCodeName.contains("DRM", ignoreCase = true) ->
                                "Error DRM: licencia invalida o no disponible"

                            error.message?.contains("license", ignoreCase = true) == true ->
                                "Error DRM: no se pudo obtener la licencia"

                            error.message?.contains("denied", ignoreCase = true) == true ->
                                "Error DRM: contenido no autorizado"

                            else -> error.message ?: "Error de reproducción desconocido"
                        }
                        Log.e(
                            TAG,
                            "Playback error: code=${error.errorCodeName}, message=${error.message}",
                            error
                        )

                        _state.value = PlayerEngineState.Error(userMessage)
                    }
                })
            }
        player = created
        return created
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block()
        else mainHandler.post(block)
    }

    override fun attachView(view: View) = runOnMain {
        val playerView = view as? PlayerView ?: return@runOnMain
        playerView.player = ensurePlayer()
    }

    override fun prepare(channelId: String, url: String, playWhenReady: Boolean, seekToMs: Long, drm: DrmConfig?) = runOnMain {
        val p = ensurePlayer()

        loadStartMs = System.currentTimeMillis()
        rebufferStartMs = null
        val currentFatalErrors = _metrics.value.fatalErrorCount
        _metrics.value = PlaybackMetrics()

        if (channelId == lastChannelId) {
            _metrics.value = _metrics.value.copy(
                fatalErrorCount = currentFatalErrors
            )
        } else {
            lastChannelId = channelId
        }

        _state.value = PlayerEngineState.Idle
        p.stop()
        p.clearMediaItems()

        val mediaItemBuilder = MediaItem.Builder()
            .setUri(url)

        if (drm != null) {
            val schemeUuid = when (drm.scheme) {
                DrmScheme.WIDEVINE -> C.WIDEVINE_UUID
            }

            val drmConfig = MediaItem.DrmConfiguration.Builder(schemeUuid)
                .setLicenseUri(drm.licenseUrl)
                .setMultiSession(drm.multiSession)
                .apply {
                    setLicenseRequestHeaders(drm.headers)
                }
                .build()

            mediaItemBuilder.setDrmConfiguration(drmConfig)
        }

        val mediaItem = mediaItemBuilder.build()

        p.setMediaItem(mediaItem)
        p.prepare()
        if (seekToMs > 0L) {
            p.seekTo(seekToMs)
        }
        p.playWhenReady = playWhenReady
    }

    override fun play() = runOnMain { ensurePlayer().play() }

    override fun pause() = runOnMain { player?.pause() }

    override fun seekToLiveEdge() = runOnMain { player?.seekToDefaultPosition() }

    override fun currentPositionMs(): Long {
        val p = player ?: return 0L
        return if (Looper.myLooper() == Looper.getMainLooper()) {
            p.currentPosition
        } else {
            0L
        }
    }

    override fun release() = runOnMain {
        player?.release()
        player = null
    }
}
