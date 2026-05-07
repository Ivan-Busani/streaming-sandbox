package com.mivan.streamingsandbox.feature.player.presentation.ui

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mivan.streamingsandbox.feature.channels.domain.model.Channel
import com.mivan.streamingsandbox.feature.player.presentation.BottomControlsUiModel
import com.mivan.streamingsandbox.ui.utils.getImageBgColorFromUrl
import com.mivan.streamingsandbox.feature.vod.domain.model.VodItem
import kotlin.math.roundToInt

private enum class SelectorSection {
    LIVE,
    VOD
}

@Composable
fun MediaTitleOverlay(
    selectedMedia: Channel?,
    visible: Boolean,
    modifier: Modifier = Modifier,
    onInfoClick: () -> Unit,
    onChannelsMenuClick: () -> Unit
) {
    AnimatedVisibility(visible = visible, modifier = modifier, enter = fadeIn(), exit = fadeOut()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.8f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectedMedia != null) {
                IconButton(onClick = onInfoClick) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "Abrir información del medio",
                        tint = Color.White
                    )
                }
            }
            Text(
                text = selectedMedia?.name ?: "",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onChannelsMenuClick) {
                Icon(
                    imageVector = Icons.Outlined.Menu,
                    contentDescription = "Abrir selector de medio",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun MediaLoadingOverlay(
    modifier: Modifier = Modifier,
    visible: Boolean,
    channelLogoUrl: String? = null
) {
    AnimatedVisibility(visible = visible, modifier = modifier, enter = fadeIn(), exit = fadeOut()) {
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
                if (channelLogoUrl != null) {
                    val context = LocalContext.current
                    val fallback = MaterialTheme.colorScheme.surfaceVariant
                    var bgColor by remember(channelLogoUrl) { mutableStateOf(fallback) }
                    LaunchedEffect(channelLogoUrl) {
                        bgColor = getImageBgColorFromUrl(
                            context = context,
                            imageUrl = channelLogoUrl,
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
                            model = channelLogoUrl,
                            contentDescription = "Logo del canal",
                            contentScale = ContentScale.Fit
                        )
                    }
                }
                Text(
                    text = "Cargando...",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomControlsOverlay(
    visible: Boolean,
    model: BottomControlsUiModel,
    onPlayPauseClick: () -> Unit,
    onSeekBackClick: () -> Unit,
    onSeekForwardClick: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekDragStateChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var isUserSeeking by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(model.progressValue.coerceIn(0f, 1f)) }

    LaunchedEffect(model.progressValue, model.showSeekControls) {
        if (!isUserSeeking) {
            sliderValue = model.progressValue.coerceIn(0f, 1f)
        }
    }

    AnimatedVisibility(visible = visible, modifier = modifier, enter = fadeIn(), exit = fadeOut()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.8f))
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onPlayPauseClick,
                    enabled = model.controlsEnabled,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (model.showPlayPauseAsPause) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (model.showPlayPauseAsPause) "Pausar" else "Reproducir",
                        tint = if (model.controlsEnabled) Color.White else Color.Gray,
                        modifier = Modifier.size(28.dp)
                    )
                }

                if (model.showSeekControls) {
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
                }

                if (model.showSeekControls) {
                    val durationMs = model.seekDurationMs ?: 0L
                    val sliderInteraction = remember { MutableInteractionSource() }
                    val sliderColors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.Red,
                        inactiveTrackColor = Color.Gray.copy(alpha = 0.55f)
                    )
                    var sliderWidthPx by remember { mutableFloatStateOf(0f) }
                    val density = LocalDensity.current
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                            .onSizeChanged { sliderWidthPx = it.width.toFloat() }
                    ) {
                        val previewMs = (sliderValue * durationMs).toLong().coerceAtLeast(0L)
                        val bubbleHalfWidthPx = with(density) { 28.dp.toPx() }.roundToInt()
                        val bubbleX = (sliderWidthPx * sliderValue).roundToInt() - bubbleHalfWidthPx

                        if (isUserSeeking && durationMs > 0L) {
                            Box(
                                modifier = Modifier
                                    .offset {
                                        IntOffset(
                                            x = bubbleX,
                                            y = -with(density) { 28.dp.toPx() }.roundToInt()
                                        )
                                    }
                                    .background(
                                        color = Color.Black.copy(alpha = 0.85f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = formatPreviewDuration(previewMs),
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }

                        Slider(
                            value = sliderValue,
                            onValueChange = { value ->
                                isUserSeeking = true
                                onSeekDragStateChanged(true)
                                sliderValue = value.coerceIn(0f, 1f)
                            },
                            onValueChangeFinished = {
                                if (durationMs > 0L) {
                                    val targetMs = (sliderValue * durationMs).toLong().coerceIn(0L, durationMs)
                                    onSeekTo(targetMs)
                                }
                                isUserSeeking = false
                                onSeekDragStateChanged(false)
                            },
                            enabled = model.controlsEnabled && durationMs > 0L,
                            interactionSource = sliderInteraction,
                            colors = sliderColors,
                            thumb = {
                                Box(
                                    modifier = Modifier
                                        .offset(y = 3.dp)
                                        .size(9.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.95f))
                                        .border(
                                            width = 0.5.dp,
                                            color = Color.White.copy(alpha = 0.35f),
                                            shape = CircleShape
                                        )
                                )
                            },
                            track = { sliderState ->
                                SliderDefaults.Track(
                                    sliderState = sliderState,
                                    colors = sliderColors,
                                    modifier = Modifier.height(3.dp),
                                    drawStopIndicator = {}
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    LinearProgressIndicator(
                        progress = { model.progressValue },
                        color = model.progressColor,
                        trackColor = Color.Gray,
                        drawStopIndicator = {},
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                }

                if (model.showSeekControls) {
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (model.showTimeLabel) {
                    Text(
                        text = model.timeLabel,
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 0.dp)
                    )
                }
            }
        }
    }
}

private fun formatPreviewDuration(ms: Long): String {
    if (ms < 0L) return "00:00"
    val totalSec = ms / 1000
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val seconds = totalSec % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

@Composable
fun LiveBadgeOverlay(
    visible: Boolean,
    liveButtonEnabled: Boolean,
    liveBadgeText: String,
    liveBadgeColor: Color,
    onGoToLiveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(visible = visible, modifier = modifier, enter = fadeIn(), exit = fadeOut()) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(enabled = liveButtonEnabled) { onGoToLiveClick() }
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .alpha(0.8f),
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
fun MediaSelectorOverlay(
    modifier: Modifier = Modifier,
    visible: Boolean,
    channels: List<Channel>,
    vodItems: List<VodItem>,
    selectedMedia: Channel?,
    onSelectChannel: (Channel) -> Unit,
    onSelectVod: (VodItem) -> Unit,
    onTapOut: (() -> Unit)? = null
) {
    val hasLiveItems = channels.isNotEmpty()
    val hasVodItems = vodItems.isNotEmpty()
    val selectedId = selectedMedia?.id
    val selectedIsVod = selectedId != null && vodItems.any { it.id == selectedId }
    val initialSection = when {
        selectedIsVod && hasVodItems -> SelectorSection.VOD
        hasLiveItems -> SelectorSection.LIVE
        hasVodItems -> SelectorSection.VOD
        else -> SelectorSection.LIVE
    }
    var activeSection by rememberSaveable(visible, selectedId) { mutableStateOf(initialSection) }

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
                    .background(Color.Black.copy(alpha = 0.45f))
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
                    .fillMaxWidth(
                        if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT) 0.96f else 0.94f
                    )
                    .fillMaxHeight(
                        if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT) 0.90f else 0.86f
                    )
                    .background(Color.Black.copy(alpha = 0.80f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .padding(top = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (hasLiveItems && hasVodItems) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AnimatedSelectorTabs(
                                activeSection = activeSection,
                                onSelectSection = { activeSection = it }
                            )
                        }
                    }

                    when (activeSection) {
                        SelectorSection.LIVE -> if (hasLiveItems) {
                            ChannelSectionCarousel(
                                modifier = Modifier.weight(1f),
                                channels = channels,
                                selectedChannel = selectedMedia,
                                onSelectChannel = onSelectChannel
                            )
                        } else if (hasVodItems) {
                            VodSectionCarousel(
                                modifier = Modifier.weight(1f),
                                vodItems = vodItems,
                                selectedChannel = selectedMedia,
                                onSelectVod = onSelectVod
                            )
                        }
                        SelectorSection.VOD -> if (hasVodItems) {
                            VodSectionCarousel(
                                modifier = Modifier.weight(1f),
                                vodItems = vodItems,
                                selectedChannel = selectedMedia,
                                onSelectVod = onSelectVod
                            )
                        } else if (hasLiveItems) {
                            ChannelSectionCarousel(
                                modifier = Modifier.weight(1f),
                                channels = channels,
                                selectedChannel = selectedMedia,
                                onSelectChannel = onSelectChannel
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimatedSelectorTabs(
    activeSection: SelectorSection,
    onSelectSection: (SelectorSection) -> Unit
) {
    var rowWidthPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val targetProgress = if (activeSection == SelectorSection.LIVE) 0f else 1f
    val indicatorProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 220),
        label = "selectorTabIndicator"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { rowWidthPx = it.width.toFloat() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SelectorTabButton(
                label = "Canales",
                isSelected = activeSection == SelectorSection.LIVE,
                onClick = { onSelectSection(SelectorSection.LIVE) },
                modifier = Modifier.weight(1f)
            )
            SelectorTabButton(
                label = "Video en demanda",
                isSelected = activeSection == SelectorSection.VOD,
                onClick = { onSelectSection(SelectorSection.VOD) },
                modifier = Modifier.weight(1f)
            )
        }

        if (rowWidthPx > 0f) {
            val indicatorWidthDp = with(density) { (rowWidthPx / 2f).toDp() }
            val indicatorOffsetDp = with(density) { ((rowWidthPx / 2f) * indicatorProgress).toDp() }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = indicatorOffsetDp)
                    .width(indicatorWidthDp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color.Red)
            )
        }
    }
}

@Composable
private fun SelectorTabButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.65f),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelSectionCarousel(
    modifier: Modifier = Modifier,
    channels: List<Channel>,
    selectedChannel: Channel?,
    onSelectChannel: (Channel) -> Unit
) {
    val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT

    if (isPortrait) {
        val selectedIndex = channels.indexOfFirst { it.id == selectedChannel?.id }
            .takeIf { it >= 0 } ?: 0
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
        LaunchedEffect(selectedChannel?.id, channels.size) {
            if (channels.isNotEmpty()) {
                listState.scrollToItem(selectedIndex)
            }
        }
        LazyColumn(
            state = listState,
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(channels, key = { it.id }) { channel ->
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

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.04f))
                        .border(
                            width = if (isSelected) 2.dp else 0.dp,
                            color = if (isSelected) Color(0xFF00E5FF) else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { onSelectChannel(channel) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(10.dp))
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
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2
                    )
                }
            }
        }
    } else {
        val initialIndex = channels.indexOfFirst { it.id == selectedChannel?.id }
            .takeIf { it >= 0 } ?: 0
        val carouselState = rememberCarouselState(
            initialItem = initialIndex,
            itemCount = { channels.size }
        )

        HorizontalMultiBrowseCarousel(
            state = carouselState,
            modifier = modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(bottom = 8.dp),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VodSectionCarousel(
    modifier: Modifier = Modifier,
    vodItems: List<VodItem>,
    selectedChannel: Channel?,
    onSelectVod: (VodItem) -> Unit
) {
    val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT

    if (isPortrait) {
        val selectedIndex = vodItems.indexOfFirst { it.id == selectedChannel?.id }
            .takeIf { it >= 0 } ?: 0
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
        LaunchedEffect(selectedChannel?.id, vodItems.size) {
            if (vodItems.isNotEmpty()) {
                listState.scrollToItem(selectedIndex)
            }
        }
        LazyColumn(
            state = listState,
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(vodItems, key = { it.id }) { vodItem ->
                val isSelected = vodItem.id == selectedChannel?.id
                val context = LocalContext.current
                val fallback = MaterialTheme.colorScheme.surfaceVariant
                var bgColor by remember(vodItem.urlPortrait) { mutableStateOf(fallback) }

                if (vodItem.urlPortrait != null) {
                    LaunchedEffect(vodItem.urlPortrait) {
                        bgColor = getImageBgColorFromUrl(
                            context = context,
                            imageUrl = vodItem.urlPortrait,
                            fallbackColor = fallback
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.04f))
                        .border(
                            width = if (isSelected) 2.dp else 0.dp,
                            color = if (isSelected) Color(0xFF00E5FF) else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { onSelectVod(vodItem) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(bgColor)
                    ) {
                        AsyncImage(
                            modifier = Modifier.fillMaxSize(),
                            model = vodItem.urlPortrait,
                            contentDescription = "Poster del VOD",
                            contentScale = ContentScale.Fit
                        )
                    }
                    Text(
                        text = vodItem.name,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2
                    )
                }
            }
        }
    } else {
        val initialIndex = vodItems.indexOfFirst { it.id == selectedChannel?.id }
            .takeIf { it >= 0 } ?: 0
        val carouselState = rememberCarouselState(
            initialItem = initialIndex,
            itemCount = { vodItems.size }
        )

        HorizontalMultiBrowseCarousel(
            state = carouselState,
            modifier = modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(bottom = 8.dp),
            preferredItemWidth = 186.dp,
            itemSpacing = 8.dp,
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) { i ->
            val vodItem = vodItems[i]
            val isSelected = vodItem.id == selectedChannel?.id
            val context = LocalContext.current
            val fallback = MaterialTheme.colorScheme.surfaceVariant
            var bgColor by remember(vodItem.urlPortrait) { mutableStateOf(fallback) }

            if (vodItem.urlPortrait != null) {
                LaunchedEffect(vodItem.urlPortrait) {
                    bgColor = getImageBgColorFromUrl(
                        context = context,
                        imageUrl = vodItem.urlPortrait,
                        fallbackColor = fallback
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.clickable { onSelectVod(vodItem) }
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
                        model = vodItem.urlPortrait,
                        contentDescription = "Poster del VOD",
                        contentScale = ContentScale.Fit
                    )
                }

                Text(
                    text = vodItem.name,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun NoMediaSelectedOverlay(modifier: Modifier = Modifier, visible: Boolean) {
    AnimatedVisibility(visible = visible, modifier = modifier, enter = fadeIn(), exit = fadeOut()) {
        Box(
            modifier = modifier
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                text = "No hay contenido seleccionado",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
