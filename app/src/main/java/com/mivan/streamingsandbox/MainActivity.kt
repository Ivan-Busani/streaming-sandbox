package com.mivan.streamingsandbox

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Menu
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.mivan.streamingsandbox.feature.channels.domain.model.Channel
import com.mivan.streamingsandbox.feature.channels.domain.model.EpgEntry
import com.mivan.streamingsandbox.feature.player.domain.PlaybackMetrics
import com.mivan.streamingsandbox.feature.player.presentation.PlaybackUiState
import com.mivan.streamingsandbox.feature.player.presentation.PlayerUiState
import com.mivan.streamingsandbox.feature.player.presentation.PlayerViewModel
import com.mivan.streamingsandbox.ui.theme.StreamingSandboxTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val TAG = "*|MainActivity"
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
                    openChannelSelector = vm::openChannelSelector,
                    onSelectChannel = vm::selectChannel,
                    onRetry = vm::retryCurrentChannel,
                    onTogglePlayPause = vm::togglePlayPause,
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

    @Composable
    private fun PlayerScreen(
        uiState: PlayerUiState,
        onAttachPlayerView: (View) -> Unit,
        openChannelSelector: (open: Boolean) -> Unit,
        onSelectChannel: (Channel) -> Unit,
        onRetry: () -> Unit,
        onTogglePlayPause: () -> Unit,
        onGoToLive: () -> Unit
    ) {
        var isPlayerControlsVisible by remember { mutableStateOf(false) }
        val drawerState = rememberDrawerState(
            initialValue = DrawerValue.Closed
        )
        val scope = rememberCoroutineScope()

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
                            onRetry = onRetry
                        )
                    }
                }
            ) {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    VideoPlayerLayer(
                        onAttachPlayerView = onAttachPlayerView,
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

                    BottomControlsOverlay(
                        visible = isPlayerControlsVisible,
                        progressPercent = uiState.currentProgramProgressPercent,
                        elapsedMs = uiState.currentProgramElapsedMs,
                        totalMs = uiState.currentProgramTotalMs,
                        showPauseIcon = uiState.playbackState == PlaybackUiState.Playing ||
                                uiState.playbackState == PlaybackUiState.Buffering,
                        playPauseEnabled = uiState.selectedChannel != null,
                        onPlayPauseClick = onTogglePlayPause,
                        goToLiveEnabled = uiState.selectedChannel != null,
                        onGoToLiveClick = onGoToLive,
                        isProgramLoading = uiState.isProgramsLoading,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )

                    NoChannelSelectedOverlay(
                        modifier = Modifier.align(Alignment.Center),
                        visible = !uiState.isChannelSelectorOpen && uiState.selectedChannel == null
                    )
                }
            }
        }
    }

    @Composable
    private fun VideoPlayerLayer(
        onAttachPlayerView: (View) -> Unit,
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
                onAttachPlayerView(playerView)
                playerView
            },
            update = { view ->
                onAttachPlayerView(view)
                view.setControllerVisibilityListener(controllerVisibilityListener)
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
                        AsyncImage(
                            model = logoUrl,
                            contentDescription = "Logo del canal",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(64.dp)
                        )
                    }
                    Text(
                        text = "Sintonizando canal...",
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
        progressPercent: Int?,
        elapsedMs: Long?,
        totalMs: Long?,
        showPauseIcon: Boolean,
        playPauseEnabled: Boolean,
        onPlayPauseClick: () -> Unit,
        goToLiveEnabled: Boolean,
        onGoToLiveClick: () -> Unit,
        isProgramLoading: Boolean,
        modifier: Modifier = Modifier
    ) {
        AnimatedVisibility(
            visible = visible,
            modifier = modifier,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            val progress = ((progressPercent ?: 0).coerceIn(0, 100)) / 100f

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
                    enabled = playPauseEnabled,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (showPauseIcon) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (showPauseIcon) "Pausar" else "Reproducir",
                        tint = if (playPauseEnabled) Color.White else Color.Gray,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isProgramLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Text(
                            text = "${formatDuration(elapsedMs)} / ${formatDuration(totalMs)}",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.weight(1f)
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                        .clickable(enabled = goToLiveEnabled) {
                            onGoToLiveClick()
                        }
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
                                .background(if (goToLiveEnabled) Color.Red else Color.Black)
                        )
                        Text(
                            text = "LIVE",
                            color = if (goToLiveEnabled) Color.White else Color.Black,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
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
                                    .background(Color.White)
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
    private fun SidebarDrawerContent(uiState: PlayerUiState, onRetry: () -> Unit)  {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.90f))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (uiState.selectedChannel != null) {
                ChannelInfoSection(uiState.selectedChannel, uiState.playbackState)
                ProgramTimelineSection(uiState.programs, uiState.isProgramsLoading, uiState.currentProgramProgressPercent)
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
            text = "Canal actual: ${channel.name}",
            color = Color.White
        )
        AsyncImage(
            model = channel.urlLogo,
            contentDescription = "Logo del canal",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(64.dp)
        )
        Text(
            text = "Estado: $playbackText",
            color = Color.White
        )

        HorizontalDivider()
    }

    @Composable
    private fun ProgramTimelineSection(programs: List<EpgEntry>, isProgramLoading: Boolean = false, currentProgramProgressPercent: Int?) {
        Text(
            text = "Programación",
            color = Color.White,
            style = MaterialTheme.typography.titleSmall
        )

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
                text = "Sin programación disponible",
                color = Color.White.copy(alpha = 0.8f)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(programs, key = { it.title }) { program ->
                    Text(
                        text = program.title,
                        color = Color.White
                    )

                    Text(
                        text = formatHourRange(program.startEpochMs, program.endEpochMs),
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            /*
            programs.forEachIndexed { index, program ->
                Text(
                    text = program.title,
                    color = Color.White
                )

                if (index == 0) {
                    Text(
                        text = "Progreso: ${currentProgramProgressPercent?.let { "$it%" } ?: "N/A"}",
                        color = Color.White
                    )
                }

                Text(
                    text = formatHourRange(program.startEpochMs, program.endEpochMs),
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
             */
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
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
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