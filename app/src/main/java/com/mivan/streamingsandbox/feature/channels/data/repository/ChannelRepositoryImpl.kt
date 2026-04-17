package com.mivan.streamingsandbox.feature.channels.data.repository

import com.mivan.streamingsandbox.feature.channels.domain.model.Channel
import com.mivan.streamingsandbox.feature.channels.domain.model.StreamType
import com.mivan.streamingsandbox.feature.channels.domain.repository.ChannelRepository
import com.mivan.streamingsandbox.feature.player.domain.DrmConfig
import com.mivan.streamingsandbox.feature.player.domain.DrmScheme
import javax.inject.Inject
import com.mivan.streamingsandbox.BuildConfig

class ChannelRepositoryImpl @Inject constructor() : ChannelRepository {
    override fun getChannels(): List<Channel> = listOf(
        Channel(
            id = "1",
            name = "Tears of Steel HLS",
            type = StreamType.HLS,
            url = "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8"
        ),
        Channel(
            id = "2",
            name = "Tears of Steel DASH",
            type = StreamType.DASH,
            url = "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.mpd"
        ),
        Channel(
            id = "drm-demo-1",
            name = "Demo DRM Widevine",
            type = StreamType.DASH,
            url = "https://storage.googleapis.com/wvmedia/cenc/h264/tears/tears.mpd",
            drm = DrmConfig(
                scheme = DrmScheme.WIDEVINE,
                licenseUrl = BuildConfig.WIDEVINE_LICENSE_URL,
                headers = emptyMap(),
                multiSession = false
            )
        )
    )
}
