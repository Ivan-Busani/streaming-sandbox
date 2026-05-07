package com.mivan.streamingsandbox.feature.player.presentation

import androidx.compose.ui.graphics.Color

private const val LIVE_EDGE_PROGRESS = 1f

data class BottomControlsUiModel(
    val hasChannel: Boolean,
    /** Play/pause/seek enabled (false when playback error). */
    val controlsEnabled: Boolean,
    val showPlayPauseAsPause: Boolean,
    val showSeekControls: Boolean,
    val canSeekDvr: Boolean,
    val showTimeLabel: Boolean,
    val timeLabel: String,
    val seekPositionMs: Long?,
    val seekDurationMs: Long?,
    val progressValue: Float,
    val progressColor: Color,
    val liveButtonEnabled: Boolean,
    val liveBadgeText: String
)

fun PlayerUiState.toBottomControlsUiModel(
    formatDuration: (Long?) -> String
): BottomControlsUiModel {
    val hasChannel = selectedPlayableMedia != null
    val isError = playbackState is PlaybackUiState.Error
    val isBuffering = playbackState == PlaybackUiState.Buffering
    val isBufferingLiveMedia = isBuffering && isLiveStream
    val neutralLikeNoChannel =
        (!hasChannel || isTuningMedia || isBufferingLiveMedia) && !isError

    val elapsed = currentProgramElapsedMs ?: playbackPositionMs
    val total = currentProgramTotalMs ?: playbackDurationMs

    val hasKnownDuration = hasChannel && elapsed != null && total != null && total > 0L
    val isActiveLiveMedia = hasChannel && isLiveStream && !isError
    val isVodTimeline = hasKnownDuration && !isLiveStream && !isError && !isTuningMedia

    val progressFromEpg = currentProgramProgressPercent
        ?.coerceIn(0, 100)
        ?.div(100f)

    val progressValue = when {
        neutralLikeNoChannel -> 0f
        isError -> 0f
        isActiveLiveMedia -> LIVE_EDGE_PROGRESS
        progressFromEpg != null -> progressFromEpg
        hasKnownDuration -> {
            (elapsed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        }
        else -> 0f
    }

    val progressColor = when {
        neutralLikeNoChannel -> Color.DarkGray
        isError -> Color.Gray          // playback error
        else -> Color.Red              // at live edge / normal live indicator
    }

    val timeLabel = if (isVodTimeline) {
        "${formatDuration(elapsed)} / ${formatDuration(total)}"
    } else {
        "--:--"
    }

    val liveBadgeText = when {
        isError -> "ERROR"
        isTuningMedia -> "--"
        isBuffering -> "--"
        else -> "LIVE"
    }

    val controlsEnabled = hasChannel && !isError && !isTuningMedia

    return BottomControlsUiModel(
        hasChannel = hasChannel,
        controlsEnabled = controlsEnabled,
        showPlayPauseAsPause = !isTuningMedia && (
            playbackState == PlaybackUiState.Playing ||
                playbackState == PlaybackUiState.Buffering
            ),
        showSeekControls = isVodTimeline,
        canSeekDvr = isVodTimeline && canSeekLiveDvr,
        showTimeLabel = isVodTimeline,
        timeLabel = timeLabel,
        seekPositionMs = if (isVodTimeline) elapsed else null,
        seekDurationMs = if (isVodTimeline) total else null,
        progressValue = progressValue,
        progressColor = progressColor,
        liveButtonEnabled = controlsEnabled,
        liveBadgeText = liveBadgeText
    )
}