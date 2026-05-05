package com.mivan.streamingsandbox

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.mivan.streamingsandbox.feature.channels.domain.model.Channel
import com.mivan.streamingsandbox.feature.channels.domain.model.EpgEntry
import com.mivan.streamingsandbox.feature.player.domain.PlaybackMetrics
import com.mivan.streamingsandbox.feature.player.presentation.BottomControlsUiModel
import com.mivan.streamingsandbox.feature.player.presentation.PlaybackUiState
import com.mivan.streamingsandbox.feature.player.presentation.PlayerUiState
import com.mivan.streamingsandbox.feature.player.presentation.PlayerViewModel
import com.mivan.streamingsandbox.feature.player.presentation.toBottomControlsUiModel
import com.mivan.streamingsandbox.ui.theme.StreamingSandboxTheme
import com.mivan.streamingsandbox.ui.utils.getImageBgColorFromUrl
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val vm: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            StreamingSandboxTheme {
                val uiState by vm.uiState.collectAsState()
                PlayerScreen(
                    uiState = uiState,
                    onAttachPlayerView = vm::attachPlayerView,
                    onDetachPlayerView = vm::detachPlayerView,
                    openChannelSelector = vm::openChannelSelector,
                    onSelectChannel = vm::selectChannel,
                    onRetry = vm::retryCurrentChannel,
                    onProgramRefresh = vm::refreshEpg,
                    onTogglePlayPause = vm::togglePlayPause,
                    onSeekBack = vm::seekBack,
                    onSeekForward = vm::seekForward,
                    onGoToLive = vm::goToLive
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.onHostStart()
    }

    override fun onStop() {
        vm.onHostStop()
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    @Composable
    private fun PlayerScreen(
        uiState: PlayerUiState,
        onAttachPlayerView: (View) -> Unit,
        onDetachPlayerView: (View) -> Unit,
        openChannelSelector: (open: Boolean) -> Unit,
        onSelectChannel: (Channel) -> Unit,
        onRetry: () -> Unit,
        onProgramRefresh: (force: Boolean?) -> Unit,
        onTogglePlayPause: () -> Unit,
        onSeekBack: () -> Unit,
        onSeekForward: () -> Unit,
        onGoToLive: () -> Unit
    ) {
        var isPlayerControlsVisible by remember { mutableStateOf(false) }
        val drawerState = rememberDrawerState(
            initialValue = DrawerValue.Closed
        )
        val scope = rememberCoroutineScope()
        val controlsModel = uiState.toBottomControlsUiModel(::formatDuration)
        val showBottomBarWhileTuning =
            uiState.selectedChannel != null && uiState.isTuningChannel

        Scaffold { innerPadding ->
            ModalNavigationDrawer(
                modifier = Modifier.padding(innerPadding),
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        modifier = Modifier.width(320.dp)
                    ) {
                        SidebarDrawerContent(
                            uiState = uiState,
                            onRetry = onRetry,
                            onProgramRefresh = onProgramRefresh
                        )
                    }
                }
            ) {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    VideoPlayerLayer(
                        onAttachPlayerView = onAttachPlayerView,
                        onDetachPlayerView = onDetachPlayerView,
                        onControllerVisibilityChanged = { visible ->
                            isPlayerControlsVisible = visible
                        }
                    )

                    ChannelSelectorOverlay(
                        visible = uiState.isChannelSelectorOpen,
                        channels = uiState.channels,
                        selectedChannel = uiState.selectedChannel,
                        onSelectChannel = { channel ->
                            onSelectChannel(channel)
                            openChannelSelector(false)
                        },
                        onTapOut = {
                            openChannelSelector(false)
                        }
                    )

                    ChannelTitleOverlay(
                        selectedChannel = uiState.selectedChannel,
                        visible = isPlayerControlsVisible,
                        modifier = Modifier
                            .align(Alignment.TopCenter),
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
                            openChannelSelector(true)
                        }
                    )

                    ChannelLoadingOverlay(
                        modifier = Modifier.align(Alignment.Center),
                        visible = uiState.selectedChannel != null &&
                                uiState.playbackState == PlaybackUiState.Buffering,
                        logoUrl = uiState.selectedChannel?.urlLogo
                    )

                    NoChannelSelectedOverlay(
                        modifier = Modifier.align(Alignment.Center),
                        visible = !uiState.isChannelSelectorOpen && uiState.selectedChannel == null
                    )

                    BottomControlsOverlay(
                        visible = isPlayerControlsVisible || showBottomBarWhileTuning,
                        model = controlsModel,
                        onPlayPauseClick = onTogglePlayPause,
                        onSeekBackClick = onSeekBack,
                        onSeekForwardClick = onSeekForward,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )

                    LiveBadgeOverlay(
                        visible = uiState.selectedChannel != null &&
                            !uiState.isTuningChannel &&
                            uiState.playbackState != PlaybackUiState.Buffering,
                        liveButtonEnabled = controlsModel.liveButtonEnabled,
                        liveBadgeText = controlsModel.liveBadgeText,
                        liveBadgeColor = controlsModel.progressColor,
                        onGoToLiveClick = onGoToLive,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(
                                end = 12.dp,
                                bottom = if (isPlayerControlsVisible) 88.dp else 12.dp
                            )
                    )
                }
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
        val controllerVisibilityListener = PlayerView.ControllerVisibilityListener {
            visibility -> onControllerVisibilityChanged(visibility == View.VISIBLE)
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

    @Composable
    private fun ChannelTitleOverlay(selectedChannel: Channel?, visible: Boolean, modifier: Modifier = Modifier, onInfoClick: () -> Unit, onChannelsMenuClick: () -> Unit) {
        AnimatedVisibility(
            visible = visible,
            modifier = modifier,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectedChannel != null) {
                    IconButton(onClick = onInfoClick) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "Abrir información del canal",
                            tint = Color.White
                        )
                    }
                }
                Text(
                    text = selectedChannel?.name ?: "Sin canal seleccionado",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onChannelsMenuClick) {
                    Icon(
                        imageVector = Icons.Outlined.Tv,
                        contentDescription = "Abrir selector de canales",
                        tint = Color.White
                    )
                }
            }
        }
    }

    @Composable
    private fun ChannelLoadingOverlay(
        modifier: Modifier = Modifier,
        visible: Boolean,
        logoUrl: String? = null
    ) {
        AnimatedVisibility(
            visible = visible,
            modifier = modifier,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (logoUrl != null) {
                        val context = LocalContext.current
                        val fallback = MaterialTheme.colorScheme.surfaceVariant
                        var bgColor by remember(logoUrl) { mutableStateOf(fallback) }
                        LaunchedEffect(logoUrl) {
                            bgColor = getImageBgColorFromUrl(
                                context = context,
                                imageUrl = logoUrl,
                                fallbackColor = fallback
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(bgColor)
                                .padding(6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                modifier = Modifier.fillMaxSize(),
                                model = logoUrl,
                                contentDescription = "Logo del canal",
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                    Text(
                        text = "Almacenando Búfer...",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }

    @Composable
    private fun BottomControlsOverlay(
        visible: Boolean,
        model: BottomControlsUiModel,
        onPlayPauseClick: () -> Unit,
        onSeekBackClick: () -> Unit,
        onSeekForwardClick: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        AnimatedVisibility(
            visible = visible,
            modifier = modifier,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onPlayPauseClick,
                    enabled = model.controlsEnabled,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (model.showPlayPauseAsPause) {
                            Icons.Filled.Pause
                        } else {
                            Icons.Filled.PlayArrow
                        },
                        contentDescription = if (model.showPlayPauseAsPause) "Pausar" else "Reproducir",
                        tint = if (model.controlsEnabled) Color.White else Color.Gray,
                        modifier = Modifier.size(28.dp)
                    )
                }

                IconButton(
                    onClick = onSeekBackClick,
                    enabled = model.controlsEnabled && model.canSeekDvr,
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.FastRewind,
                        contentDescription = "Retroceder 10 segundos",
                        tint = if (model.controlsEnabled && model.canSeekDvr) Color.White else Color.Gray,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (model.showProgramLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Text(
                            text = model.timeLabel,
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    LinearProgressIndicator(
                        progress = { model.progressValue },
                        color = model.progressColor,
                        trackColor = Color.Gray,
                        modifier = Modifier.weight(1f)
                    )
                }

                IconButton(
                    onClick = onSeekForwardClick,
                    enabled = model.controlsEnabled && model.canSeekDvr,
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.FastForward,
                        contentDescription = "Adelantar 10 segundos",
                        tint = if (model.controlsEnabled && model.canSeekDvr) Color.White else Color.Gray,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }

    @Composable
    private fun LiveBadgeOverlay(
        visible: Boolean,
        liveButtonEnabled: Boolean,
        liveBadgeText: String,
        liveBadgeColor: Color,
        onGoToLiveClick: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        AnimatedVisibility(
            visible = visible,
            modifier = modifier,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(enabled = liveButtonEnabled) { onGoToLiveClick() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (liveButtonEnabled) liveBadgeColor else Color.DarkGray)
                    )
                    Text(
                        text = liveBadgeText,
                        color = if (liveButtonEnabled) Color.White else Color.Gray,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ChannelSelectorOverlay(
        modifier: Modifier = Modifier,
        visible: Boolean,
        channels: List<Channel>,
        selectedChannel: Channel?,
        onSelectChannel: (Channel) -> Unit,
        onTapOut: (() -> Unit)? = null,
    ) {
        AnimatedVisibility(
            modifier = modifier.fillMaxSize(),
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            onTapOut?.invoke()
                        }
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.80f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val initialIndex = channels.indexOfFirst { it.id == selectedChannel?.id }
                        .takeIf { it >= 0 } ?: 0
                    val carouselState = rememberCarouselState(
                        initialItem = initialIndex,
                        itemCount = { channels.size }
                    )

                    HorizontalMultiBrowseCarousel(
                        state = carouselState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .padding(top = 16.dp, bottom = 16.dp),
                        preferredItemWidth = 186.dp,
                        itemSpacing = 8.dp,
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) { i ->
                        val channel = channels[i]
                        val isSelected = channel.id == selectedChannel?.id

                        val context = LocalContext.current
                        val fallback = MaterialTheme.colorScheme.surfaceVariant
                        var bgColor by remember(channel.urlLogo) { mutableStateOf(fallback) }

                        if (channel.urlLogo != null) {
                            LaunchedEffect(channel.urlLogo) {
                                bgColor = getImageBgColorFromUrl(
                                    context = context,
                                    imageUrl = channel.urlLogo,
                                    fallbackColor = fallback
                                )
                            }
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.clickable { onSelectChannel(channel) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .height(150.dp)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(
                                        width = if (isSelected) 2.dp else 0.dp,
                                        color = if (isSelected) Color(0xFF00E5FF) else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .background(bgColor)
                            ) {
                                AsyncImage(
                                    modifier = Modifier.fillMaxSize(),
                                    model = channel.urlLogo,
                                    contentDescription = "Logo del canal",
                                    contentScale = ContentScale.Fit
                                )
                            }

                            Text(
                                text = channel.name,
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun NoChannelSelectedOverlay(modifier: Modifier = Modifier, visible: Boolean) {
        AnimatedVisibility(
            visible = visible,
            modifier = modifier,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = modifier
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "No hay canal seleccionado",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }

    @Composable
    private fun SidebarDrawerContent(uiState: PlayerUiState, onRetry: () -> Unit, onProgramRefresh: (force: Boolean?) -> Unit)  {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.90f))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (uiState.selectedChannel != null) {
                ChannelInfoSection(uiState.selectedChannel, uiState.playbackState)
                ProgramTimelineSection(
                    programs = uiState.programs,
                    onProgramRefreshClick = onProgramRefresh,
                    isProgramLoading = uiState.isProgramsLoading,
                    currentProgramProgressPercent = uiState.currentProgramProgressPercent
                )
                // MetricsSection(metrics = uiState.metrics)

                if (uiState.playbackState is PlaybackUiState.Error) {
                    ChannelErrorsSection(
                        errorMessage = (uiState.playbackState).message,
                        onRetry = onRetry
                    )
                }
            }
        }
    }

    @Composable
    private fun ChannelsListSection(channels: List<Channel>, selectedChannel: Channel?, onSelectChannel: (Channel) -> Unit) {
        Text(
            text = "Total canales: ${channels.size}",
            color = Color.White
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(channels, key = { it.id }) { channel ->
                val isSelected = selectedChannel?.id == channel.id
                Button(
                    onClick = { onSelectChannel(channel) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isSelected) "✓ ${channel.name}" else channel.name)
                }
            }
        }
        HorizontalDivider()
    }

    @Composable
    private fun ChannelInfoSection(channel: Channel, playbackState: PlaybackUiState) {
        val playbackText = when (playbackState) {
            PlaybackUiState.Idle -> "En espera"
            PlaybackUiState.Buffering -> "Almacenando búfer..."
            PlaybackUiState.Ready -> "En Pausa"
            PlaybackUiState.Playing -> "Reproduciendo"
            PlaybackUiState.Ended -> "Finalizado"
            is PlaybackUiState.Error -> "Error de reproducción"
        }

        Text(
            text = "Sintonizado: ${channel.name}",
            color = Color.White
        )

        val context = LocalContext.current
        val fallback = MaterialTheme.colorScheme.surfaceVariant
        var bgColor by remember(channel.urlLogo) { mutableStateOf(fallback) }

        if (channel.urlLogo != null) {
            LaunchedEffect(channel.urlLogo) {
                bgColor = getImageBgColorFromUrl(
                    context = context,
                    imageUrl = channel.urlLogo,
                    fallbackColor = fallback
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(bgColor)
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                modifier = Modifier.fillMaxSize(),
                model = channel.urlLogo,
                contentDescription = "Logo del canal",
                contentScale = ContentScale.Fit
            )
        }
        Text(
            text = "Estado: $playbackText",
            color = Color.White
        )

        HorizontalDivider()
    }

    @Composable
    private fun ProgramTimelineSection(
        programs: List<EpgEntry>,
        onProgramRefreshClick: (force: Boolean?) -> Unit,
        isProgramLoading: Boolean = false,
        currentProgramProgressPercent: Int?
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Programación",
                color = Color.White,
                style = MaterialTheme.typography.titleSmall
            )
            IconButton(
                onClick = { onProgramRefreshClick(true) },
                enabled = !isProgramLoading,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = "Actualizar programación",
                )
            }
        }

        if (isProgramLoading) {
            Row(
                modifier = Modifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
                Text(
                    text = "Obteniendo programación...",
                    color = Color.White
                )
            }
        }
        else if (programs.isEmpty()) {
            Text(
                text = "Programación no disponible",
                color = Color.White.copy(alpha = 0.8f)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val now = System.currentTimeMillis()
                items(
                    programs,
                    key = { "${it.channelId}-${it.startEpochMs}-${it.endEpochMs}-${it.title}" }
                ) { program ->
                    val isLive = now >= program.startEpochMs && now < program.endEpochMs
                    Text(
                        text = program.title,
                        color = if (isLive) Color(0xFF00E5FF) else Color.White
                    )
                    if (isLive) {
                        val progressPercent = (currentProgramProgressPercent ?: 0).coerceIn(0, 100)
                        val progress = progressPercent / 100f

                        Text(
                            text = "Progreso: $progressPercent%",
                            color = Color(0xFF00E5FF),
                            style = MaterialTheme.typography.bodySmall
                        )
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Text(
                        text = formatHourRange(program.startEpochMs, program.endEpochMs),
                        color = if (isLive) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    @Composable
    private fun MetricsSection(metrics: PlaybackMetrics) {
        val startupText = metrics.startupTimeMs?.let { "${it}ms" } ?: "N/A"

        Text(
            text = "QoE",
            color = Color.White,
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text = "Startup: $startupText",
            color = Color.White
        )
        Text(
            text = "Rebuffers: ${metrics.rebufferCount}",
            color = Color.White
        )
        Text(
            text = "Rebuffer total: ${metrics.totalRebufferMs}ms",
            color = Color.White
        )
        Text(
            text = "Fatal errors: ${metrics.fatalErrorCount}",
            color = Color.White
        )
        HorizontalDivider()
    }

    @Composable
    private fun ChannelErrorsSection(errorMessage: String, onRetry: () -> Unit) {
        if (errorMessage.contains("DRM", ignoreCase = true)) {
            Text(
                text = "Tip: verifica licenseUrl, headers y autorización del contenido.",
                color = Color(0xFFFFC107)
            )
        } else {
            Text(
                text = errorMessage,
                color = Color.Red
            )
        }

        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Reintentar")
        }

        HorizontalDivider()
    }
}

private fun formatHourRange(startEpochMs: Long, endEpochMs: Long): String {
    val formatter = SimpleDateFormat("hh:mm a", Locale.getDefault())
    val start = formatter.format(Date(startEpochMs))
    val end = formatter.format(Date(endEpochMs))
    return "$start - $end"
}

private fun formatDuration(ms: Long?): String {
    if (ms == null || ms < 0) return "--:--"
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%02d:%02d".format(min, sec)
}