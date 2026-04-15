package com.mivan.streamingsandbox.feature.channels.domain.repository

import com.mivan.streamingsandbox.feature.channels.domain.model.Channel

interface ChannelRepository {
    fun getChannels(): List<Channel>
}
