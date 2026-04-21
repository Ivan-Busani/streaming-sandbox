package com.mivan.streamingsandbox.feature.channels.domain.repository

import com.mivan.streamingsandbox.feature.channels.domain.model.Channel
import com.mivan.streamingsandbox.feature.channels.domain.model.EpgEntry

interface ChannelRepository {
    suspend fun getChannels(): List<Channel>
    suspend fun getEpgEntries(): List<EpgEntry>
}
