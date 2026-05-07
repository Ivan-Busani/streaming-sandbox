package com.mivan.streamingsandbox.feature.vod.domain.model

import com.mivan.streamingsandbox.feature.channels.domain.model.StreamType
import com.mivan.streamingsandbox.feature.player.domain.DrmConfig

data class VodItem(
    val id: String,
    val name: String,
    val type: StreamType,
    val url: String,
    val urlPortrait: String? = null,
    val drm: DrmConfig? = null
)
