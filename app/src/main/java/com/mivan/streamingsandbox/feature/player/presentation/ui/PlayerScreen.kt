package com.mivan.streamingsandbox.feature.player.presentation.ui

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.mivan.streamingsandbox.R
import com.mivan.streamingsandbox.feature.channels.domain.model.Channel
import com.mivan.streamingsandbox.feature.player.presentation.PlayableMedia
import com.mivan.streamingsandbox.feature.player.presentation.PlaybackUiState
import com.mivan.streamingsandbox.feature.player.presentation.PlayerUiState
import com.mivan.streamingsandbox.feature.player.presentation.toPlayableChannel
import com.mivan.streamingsandbox.feature.player.presentation.toBottomControlsUiModel
import com.mivan.streamingsandbox.feature.vod.domain.model.VodItem
import kotlinx.coroutines.launch

@Composable
fun PlayerScreen(
    uiState: PlayerUiState,
    onAttachPlayerView: (View) -> Unit,
    onDetachPlayerView: (View) -> Unit,
    openMediaSelector: (open: Boolean) -> Unit,
    onSelectChannel: (Channel) -> Unit,
    onSelectVod: (VodItem) -> Unit,
    onRetry: () -> Unit,
    onProgramRefresh: (force: Boolean?) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekTo: (positionMs: Long) -> Unit,
    onGoToLive: () -> Unit
) {
    var isPlayerControlsVisible by remember { mutableStateOf(false) }
    var isSeekOverlayLocked by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val controlsModel = uiState.toBottomControlsUiModel(::formatDuration)
    val selectedPlayableChannel = uiState.selectedPlayableMedia?.toPlayableChannel()
    val showBottomBarWhileTuning = selectedPlayableChannel != null && uiState.isTuningMedia
    val shouldShowBottomControls =
        !uiState.isMediaSelectorOpen &&
            (isPlayerControlsVisible || showBottomBarWhileTuning || isSeekOverlayLocked)
    val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    val cutoutTopPadding = if (isPortrait) {
        WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
    } else {
        0.dp
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(320.dp)) {
                SidebarDrawerContent(
                    uiState = uiState,
                    onRetry = onRetry,
                    onProgramRefresh = onProgramRefresh
                )
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            VideoPlayerLayer(
                onAttachPlayerView = onAttachPlayerView,
                onDetachPlayerView = onDetachPlayerView,
                onControllerVisibilityChanged = { visible ->
                    isPlayerControlsVisible = visible
                }
            )

                MediaSelectorOverlay(
                    visible = uiState.isMediaSelectorOpen,
                    channels = uiState.channels,
                    vodItems = uiState.vodItems,
                    selectedMedia = selectedPlayableChannel,
                    onSelectChannel = { channel ->
                        onSelectChannel(channel)
                        openMediaSelector(false)
                    },
                    onSelectVod = { vodItem ->
                        onSelectVod(vodItem)
                        openMediaSelector(false)
                    },
                    onTapOut = {
                        openMediaSelector(false)
                    }
                )

                MediaTitleOverlay(
                    selectedMedia = selectedPlayableChannel,
                    visible = isPlayerControlsVisible && !uiState.isMediaSelectorOpen,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = cutoutTopPadding),
                    onInfoClick = {
                        scope.launch {
                            if (drawerState.currentValue == DrawerValue.Open) {
                                drawerState.close()
                            } else {
                                drawerState.open()
                            }
                        }
                    },
                    onChannelsMenuClick = {
                        openMediaSelector(true)
                    }
                )

                MediaLoadingOverlay(
                    modifier = Modifier.align(Alignment.Center),
                    visible = selectedPlayableChannel != null &&
                        uiState.playbackState == PlaybackUiState.Buffering,
                    channelLogoUrl = if (uiState.selectedPlayableMedia is PlayableMedia.Live) {
                        selectedPlayableChannel?.urlLogo
                    } else {
                        null
                    }
                )

                NoMediaSelectedOverlay(
                    modifier = Modifier.align(Alignment.Center),
                    visible = !uiState.isMediaSelectorOpen && selectedPlayableChannel == null
                )

                BottomControlsOverlay(
                    visible = shouldShowBottomControls,
                    model = controlsModel,
                    onPlayPauseClick = onTogglePlayPause,
                    onSeekBackClick = onSeekBack,
                    onSeekForwardClick = onSeekForward,
                    onSeekTo = onSeekTo,
                    onSeekDragStateChanged = { dragging -> isSeekOverlayLocked = dragging },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )

            LiveBadgeOverlay(
                visible = selectedPlayableChannel != null &&
                    !uiState.isMediaSelectorOpen &&
                    uiState.selectedPlayableMedia is PlayableMedia.Live &&
                    !uiState.isTuningMedia &&
                    uiState.playbackState != PlaybackUiState.Buffering,
                liveButtonEnabled = controlsModel.liveButtonEnabled,
                liveBadgeText = controlsModel.liveBadgeText,
                liveBadgeColor = controlsModel.progressColor,
                onGoToLiveClick = onGoToLive,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 12.dp,
                            bottom = if (shouldShowBottomControls) 88.dp else 12.dp
                    )
            )
        }
    }
}

@SuppressLint("InflateParams")
@Composable
private fun VideoPlayerLayer(
    onAttachPlayerView: (View) -> Unit,
    onDetachPlayerView: (View) -> Unit,
    onControllerVisibilityChanged: (Boolean) -> Unit
) {
    val controllerVisibilityListener = PlayerView.ControllerVisibilityListener { visibility ->
        onControllerVisibilityChanged(visibility == View.VISIBLE)
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            val playerView = android.view.LayoutInflater.from(context)
                .inflate(R.layout.player_view_live, null, false) as PlayerView
            playerView.useController = true
            playerView.setControllerVisibilityListener(controllerVisibilityListener)
            playerView
        },
        update = { view ->
            onAttachPlayerView(view)
        },
        onRelease = { view ->
            onDetachPlayerView(view)
        }
    )
}

private fun formatDuration(ms: Long?): String {
    if (ms == null || ms < 0) return "--:--"
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%02d:%02d".format(min, sec)
}
