package com.mivan.streamingsandbox.feature.player.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mivan.streamingsandbox.feature.channels.domain.model.EpgEntry
import com.mivan.streamingsandbox.feature.player.domain.PlaybackMetrics
import com.mivan.streamingsandbox.feature.player.presentation.PlaybackUiState
import com.mivan.streamingsandbox.feature.player.presentation.PlayerUiState
import com.mivan.streamingsandbox.feature.player.presentation.PlayableMedia
import com.mivan.streamingsandbox.ui.utils.getImageBgColorFromUrl
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SidebarDrawerContent(
    uiState: PlayerUiState,
    onRetry: () -> Unit,
    onProgramRefresh: (force: Boolean?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.90f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val selectedMedia = uiState.selectedPlayableMedia
        if (selectedMedia != null) {
            MediaInfoSection(selectedMedia, uiState.playbackState)
            if (uiState.selectedPlayableMedia is PlayableMedia.Live) {
                ProgramTimelineSection(
                    programs = uiState.programs,
                    onProgramRefreshClick = onProgramRefresh,
                    isProgramLoading = uiState.isProgramsLoading,
                    currentProgramProgressPercent = uiState.currentProgramProgressPercent
                )
            }

            // MetricsSection(metrics = uiState.metrics)

            if (uiState.playbackState is PlaybackUiState.Error) {
                MediaErrorsSection(
                    errorMessage = uiState.playbackState.message,
                    onRetry = onRetry
                )
            }
        }
    }
}

@Composable
private fun MediaInfoSection(media: PlayableMedia, playbackState: PlaybackUiState) {
    val playbackText = when (playbackState) {
        PlaybackUiState.Idle -> "En espera"
        PlaybackUiState.Buffering -> "Almacenando búfer..."
        PlaybackUiState.Ready -> "En Pausa"
        PlaybackUiState.Playing -> "Reproduciendo"
        PlaybackUiState.Ended -> "Finalizado"
        is PlaybackUiState.Error -> "Error de reproducción"
    }
    val mediaName = media.name()
    val mediaImageUrl = media.imageUrl()

    Text(text = "Seleccionado: $mediaName", color = Color.White)

    val context = LocalContext.current
    val fallback = MaterialTheme.colorScheme.surfaceVariant
    var bgColor by remember(mediaImageUrl) { mutableStateOf(fallback) }

    if (mediaImageUrl != null) {
        LaunchedEffect(mediaImageUrl) {
            bgColor = getImageBgColorFromUrl(
                context = context,
                imageUrl = mediaImageUrl,
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
            model = mediaImageUrl,
            contentDescription = "Imagen del contenido",
            contentScale = ContentScale.Fit
        )
    }
    Text(text = "Estado: $playbackText", color = Color.White)
    HorizontalDivider()
}

private fun PlayableMedia.name(): String = when (this) {
    is PlayableMedia.Live -> channel.name
    is PlayableMedia.Vod -> item.name
}

private fun PlayableMedia.imageUrl(): String? = when (this) {
    is PlayableMedia.Live -> channel.urlLogo
    is PlayableMedia.Vod -> item.urlPortrait
}

@Composable
private fun ProgramTimelineSection(
    programs: List<EpgEntry>,
    onProgramRefreshClick: (force: Boolean?) -> Unit,
    isProgramLoading: Boolean = false,
    currentProgramProgressPercent: Int?
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
                tint = if (isProgramLoading) Color.Gray else Color.White
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
            Text(text = "Obteniendo programación...", color = Color.White)
        }
    } else if (programs.isEmpty()) {
        Text(text = "Programación no disponible", color = Color.White.copy(alpha = 0.8f))
    } else {
        LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val now = System.currentTimeMillis()
            items(programs, key = { "${it.channelId}-${it.startEpochMs}-${it.endEpochMs}-${it.title}" }) { program ->
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
                        drawStopIndicator = {},
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
    Text(text = "QoE", color = Color.White, style = MaterialTheme.typography.titleSmall)
    Text(text = "Startup: $startupText", color = Color.White)
    Text(text = "Rebuffers: ${metrics.rebufferCount}", color = Color.White)
    Text(text = "Rebuffer total: ${metrics.totalRebufferMs}ms", color = Color.White)
    Text(text = "Fatal errors: ${metrics.fatalErrorCount}", color = Color.White)
    HorizontalDivider()
}

@Composable
private fun MediaErrorsSection(errorMessage: String, onRetry: () -> Unit) {
    if (errorMessage.contains("DRM", ignoreCase = true)) {
        Text(
            text = "Tip: verifica licenseUrl, headers y autorización del contenido.",
            color = Color(0xFFFFC107)
        )
    } else {
        Text(text = errorMessage, color = Color.Red)
    }

    Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
        Text("Reintentar")
    }
    HorizontalDivider()
}

private fun formatHourRange(startEpochMs: Long, endEpochMs: Long): String {
    val formatter = SimpleDateFormat("hh:mm a", Locale.getDefault())
    val start = formatter.format(Date(startEpochMs))
    val end = formatter.format(Date(endEpochMs))
    return "$start - $end"
}
