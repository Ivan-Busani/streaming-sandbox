package com.mivan.streamingsandbox.feature.player.presentation.ui

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.view.View
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.mivan.streamingsandbox.R
import com.mivan.streamingsandbox.feature.channels.domain.model.Channel
import com.mivan.streamingsandbox.feature.player.presentation.PlayableMedia
import com.mivan.streamingsandbox.feature.player.presentation.PlaybackUiState
import com.mivan.streamingsandbox.feature.player.presentation.PlayerUiState
import com.mivan.streamingsandbox.feature.player.presentation.toBottomControlsUiModel
import com.mivan.streamingsandbox.feature.vod.domain.model.VodItem
import kotlinx.coroutines.launch

@Composable
fun PlayerScreen(
    uiState: PlayerUiState,
    onAttachPlayerView: (View) -> Unit,
    onDetachPlayerView: (View) -> Unit,
    openMediaSelector: (open: Boolean) -> Unit,
    openFavoritesSelector: (open: Boolean) -> Unit,
    onSelectChannel: (Channel) -> Unit,
    onSelectVod: (VodItem) -> Unit,
    onToggleChannelFavorite: (Channel) -> Unit,
    onToggleVodFavorite: (VodItem) -> Unit,
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
    val favoriteChannels = uiState.channels.filter { it.id in uiState.favoriteChannelIds }
    val favoriteVodItems = uiState.vodItems.filter { it.id in uiState.favoriteVodIds }
    val isAnySelectorOpen = uiState.isMediaSelectorOpen || uiState.isFavoritesSelectorOpen
    val showBottomBarWhileTuning = uiState.selectedPlayableMedia != null && uiState.isTuningMedia
    val shouldShowBottomControls =
        uiState.selectedPlayableMedia != null &&
            !isAnySelectorOpen &&
            (isPlayerControlsVisible || showBottomBarWhileTuning || isSeekOverlayLocked)
    val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    val cutoutTopPadding = if (isPortrait) {
        WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
    } else {
        0.dp
    }
    BackHandler {
        when {
            uiState.isMediaSelectorOpen -> openMediaSelector(false)
            uiState.isFavoritesSelectorOpen -> openFavoritesSelector(false)
            drawerState.isOpen -> scope.launch { drawerState.close() }
            else -> Unit
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = uiState.selectedPlayableMedia != null,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier
                    .width(320.dp),
                drawerContainerColor = Color(0xFF555555)
            ) {
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
                selectedMedia = uiState.selectedPlayableMedia,
                favoriteChannelIds = uiState.favoriteChannelIds,
                favoriteVodIds = uiState.favoriteVodIds,
                onSelectChannel = { channel ->
                    onSelectChannel(channel)
                    openMediaSelector(false)
                },
                onSelectVod = { vodItem ->
                    onSelectVod(vodItem)
                    openMediaSelector(false)
                },
                onToggleChannelFavorite = onToggleChannelFavorite,
                onToggleVodFavorite = onToggleVodFavorite,
                onTapOut = {
                    openMediaSelector(false)
                }
            )

            MediaSelectorOverlay(
                visible = uiState.isFavoritesSelectorOpen,
                channels = favoriteChannels,
                vodItems = favoriteVodItems,
                selectedMedia = uiState.selectedPlayableMedia,
                favoriteChannelIds = uiState.favoriteChannelIds,
                favoriteVodIds = uiState.favoriteVodIds,
                onSelectChannel = { channel ->
                    onSelectChannel(channel)
                    openFavoritesSelector(false)
                },
                onSelectVod = { vodItem ->
                    onSelectVod(vodItem)
                    openFavoritesSelector(false)
                },
                onToggleChannelFavorite = onToggleChannelFavorite,
                onToggleVodFavorite = onToggleVodFavorite,
                onTapOut = {
                    openFavoritesSelector(false)
                }
            )

            MediaTitleOverlay(
                selectedMedia = uiState.selectedPlayableMedia,
                visible = !isAnySelectorOpen &&
                    (isPlayerControlsVisible || uiState.selectedPlayableMedia == null),
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
                onFavoritesMenuClick = {
                    openFavoritesSelector(true)
                },
                onChannelsMenuClick = {
                    openMediaSelector(true)
                }
            )

                MediaLoadingOverlay(
                    modifier = Modifier.align(Alignment.Center),
                    visible = uiState.selectedPlayableMedia != null &&
                    !isAnySelectorOpen &&
                        uiState.playbackState == PlaybackUiState.Buffering,
                    channelLogoUrl = if (uiState.selectedPlayableMedia is PlayableMedia.Live) {
                        uiState.selectedChannel?.urlLogo
                    } else {
                        null
                    }
                )

                NoMediaSelectedOverlay(
                    modifier = Modifier.align(Alignment.Center),
                    visible = !isAnySelectorOpen && uiState.selectedPlayableMedia == null
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
                visible = uiState.selectedPlayableMedia != null &&
                    !isAnySelectorOpen &&
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
