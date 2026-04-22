package com.mivan.streamingsandbox.feature.channels.domain.usecase

import com.mivan.streamingsandbox.feature.channels.domain.model.EpgEntry
import com.mivan.streamingsandbox.feature.channels.domain.repository.ChannelRepository
import javax.inject.Inject

class GetProgramsForChannelUseCase @Inject constructor(
    private val channelRepository: ChannelRepository
) {
    suspend operator fun invoke(
        channelId: String,
        nowEpochMs: Long = System.currentTimeMillis(),
        limit: Int = 5
    ): List<EpgEntry> {
        return channelRepository
            .getEpgEntries()
            .asSequence()
            .filter { it.channelId == channelId && it.endEpochMs > nowEpochMs }
            .sortedBy { it.startEpochMs }
            .take(limit)
            .toList()
    }
}