package com.mivan.streamingsandbox

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.mivan.streamingsandbox.feature.channels.domain.model.Channel
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
                    onRetry = vm::retryCurrentChannel,
                    onSelectChannel = vm::selectChannel
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
        onRetry: () -> Unit,
        onSelectChannel: (Channel) -> Unit
    ) {
        val drawerState = rememberDrawerState(
            initialValue = DrawerValue.Closed
        )
        val scope = rememberCoroutineScope()
        var isPlayerControlsVisible by remember { mutableStateOf(false) }

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
                            onSelectChannel = { channel ->
                                onSelectChannel(channel)
                                scope.launch { drawerState.close() }
                            }
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

                    ChannelTitleOverlay(
                        title = uiState.selectedChannel?.name ?: "Sin canal seleccionado",
                        visible = isPlayerControlsVisible,
                        modifier = Modifier
                            .align(Alignment.TopCenter),
                        onMenuClick = {
                            scope.launch {
                                if (drawerState.currentValue == DrawerValue.Open) {
                                    drawerState.close()
                                } else {
                                    drawerState.open()
                                }
                            }
                        }
                    )
                    NoChannelSelectedOverlay(
                        modifier = Modifier.align(Alignment.Center),
                        visible = !isPlayerControlsVisible && uiState.selectedChannel == null
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
                PlayerView(context).apply {
                    useController = true
                    setControllerVisibilityListener(controllerVisibilityListener)
                    onAttachPlayerView(this)
                }
            },
            update = { view ->
                onAttachPlayerView(view)
                view.setControllerVisibilityListener(controllerVisibilityListener)
            }
        )
    }

    @Composable
    private fun ChannelTitleOverlay(title: String, visible: Boolean, modifier: Modifier = Modifier, onMenuClick: () -> Unit) {
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
                IconButton(onClick = onMenuClick) {
                    Icon(
                        imageVector = Icons.Outlined.Menu,
                        contentDescription = "Abrir canales",
                        tint = Color.White
                    )
                }
                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
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
    private fun SidebarDrawerContent(uiState: PlayerUiState, onRetry: () -> Unit, onSelectChannel: (Channel) -> Unit) {
        val playbackText = when (uiState.playbackState) {
            PlaybackUiState.Idle -> "Idle"
            PlaybackUiState.Buffering -> "Buffering..."
            PlaybackUiState.Ready -> "Ready"
            PlaybackUiState.Playing -> "Playing"
            PlaybackUiState.Ended -> "Ended"
            is PlaybackUiState.Error -> "Error de reproducción"
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.90f))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Total canales: ${uiState.channels.size}",
                color = Color.White
            )

            Text(
                text = "Canal actual: ${uiState.selectedChannel?.name ?: "N/A"}",
                color = Color.White
            )
            Text(
                text = "Estado: $playbackText",
                color = Color.White
            )

            HorizontalDivider()

            Text(
                text = "Timeline",
                color = Color.White,
                style = MaterialTheme.typography.titleSmall
            )

            if (uiState.programs.isEmpty()) {
                Text(
                    text = "Sin programación disponible",
                    color = Color.White.copy(alpha = 0.8f)
                )
            } else {
                uiState.programs.forEachIndexed { index, program ->
                    Text(
                        text = program.title,
                        color = Color.White
                    )

                    if (index == 0) {
                        Text(
                            text = "Progreso: ${uiState.currentProgramProgressPercent?.let { "$it%" } ?: "N/A"}",
                            color = Color.White
                        )
                    }

                    Text(
                        text = formatHourRange(program.startEpochMs, program.endEpochMs),
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            HorizontalDivider()

            MetricsSection(metrics = uiState.metrics)

            if (uiState.playbackState is PlaybackUiState.Error) {
                val errorMessage = (uiState.playbackState).message
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
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.channels, key = { it.id }) { channel ->
                    val isSelected = uiState.selectedChannel?.id == channel.id
                    Button(
                        onClick = { onSelectChannel(channel) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isSelected) "✓ ${channel.name}" else channel.name)
                    }
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
    }
}

private fun formatHourRange(startEpochMs: Long, endEpochMs: Long): String {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    val start = formatter.format(Date(startEpochMs))
    val end = formatter.format(Date(endEpochMs))
    return "$start - $end"
}