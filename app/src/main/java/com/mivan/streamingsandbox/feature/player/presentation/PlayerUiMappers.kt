package com.mivan.streamingsandbox.feature.player.presentation

import androidx.compose.ui.graphics.Color

private const val LIVE_FALLBACK_BEHIND_PROGRESS = 0.35f
private const val LIVE_EDGE_PROGRESS = 1f

data class BottomControlsUiModel(
    val hasChannel: Boolean,
    /** Play/pause/seek enabled (false when playback error). */
    val controlsEnabled: Boolean,
    val showPlayPauseAsPause: Boolean,
    val canSeekDvr: Boolean,
    val showProgramLoading: Boolean,
    val timeLabel: String,
    val progressValue: Float,
    val progressColor: Color,
    val liveButtonEnabled: Boolean,
    val liveBadgeText: String
)

fun PlayerUiState.toBottomControlsUiModel(
    formatDuration: (Long?) -> String
): BottomControlsUiModel {
    val hasChannel = selectedChannel != null
    val isError = playbackState is PlaybackUiState.Error
    val isBuffering = playbackState == PlaybackUiState.Buffering
    // Include any buffering (tuning or mid-playback rebuffer) so UI stays neutral, not LIVE.
    val neutralLikeNoChannel =
        (!hasChannel || isTuningChannel || isBuffering) && !isError

    val elapsed = currentProgramElapsedMs ?: playbackPositionMs
    val total = currentProgramTotalMs ?: playbackDurationMs

    val hasKnownDuration = hasChannel && elapsed != null && total != null && total > 0L

    val progressFromEpg = currentProgramProgressPercent
        ?.coerceIn(0, 100)
        ?.div(100f)

    val progressValue = when {
        neutralLikeNoChannel -> 0f
        isError -> 0f
        progressFromEpg != null -> progressFromEpg
        hasKnownDuration -> {
            (elapsed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        }
        isBehindLive -> LIVE_FALLBACK_BEHIND_PROGRESS
        else -> LIVE_EDGE_PROGRESS
    }

    val progressColor = when {
        neutralLikeNoChannel -> Color.DarkGray
        isError -> Color.Gray          // playback error
        isBehindLive -> Color.Cyan     // delayed vs live edge
        else -> Color.Red              // at live edge / normal live indicator
    }

    val timeLabel = when {
        neutralLikeNoChannel -> "--:--"
        isError -> "--:--"
        hasKnownDuration -> "${formatDuration(elapsed)} / ${formatDuration(total)}"
        isBehindLive -> when (liveOffsetMs) {
            null -> "LIVE -"
            else -> "-${formatDuration(liveOffsetMs)}"
        }
        else -> "LIVE"
    }

    val liveBadgeText = when {
        isError -> "ERROR"
        isTuningChannel -> "--"
        isBuffering -> "--"
        isBehindLive -> when (liveOffsetMs) {
            null -> "LIVE -"
            else -> "-${formatDuration(liveOffsetMs)}"
        }
        else -> "LIVE"
    }

    val controlsEnabled = hasChannel && !isError && !isTuningChannel

    return BottomControlsUiModel(
        hasChannel = hasChannel,
        controlsEnabled = controlsEnabled,
        showPlayPauseAsPause = !isTuningChannel && (
            playbackState == PlaybackUiState.Playing ||
                playbackState == PlaybackUiState.Buffering
            ),
        canSeekDvr = canSeekLiveDvr && !isError && !isTuningChannel,
        showProgramLoading = isProgramsLoading || (hasChannel && isBuffering),
        timeLabel = timeLabel,
        progressValue = progressValue,
        progressColor = progressColor,
        liveButtonEnabled = controlsEnabled,
        liveBadgeText = liveBadgeText
    )
}