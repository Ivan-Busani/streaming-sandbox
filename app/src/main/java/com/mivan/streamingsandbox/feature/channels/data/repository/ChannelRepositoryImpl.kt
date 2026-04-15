package com.mivan.streamingsandbox.feature.channels.data.repository

import com.mivan.streamingsandbox.feature.channels.domain.model.Channel
import com.mivan.streamingsandbox.feature.channels.domain.model.StreamType
import com.mivan.streamingsandbox.feature.channels.domain.repository.ChannelRepository
import javax.inject.Inject

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
        )
    )
}
