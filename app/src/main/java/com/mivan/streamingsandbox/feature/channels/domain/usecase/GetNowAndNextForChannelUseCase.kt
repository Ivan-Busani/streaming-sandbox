package com.mivan.streamingsandbox.feature.channels.domain.usecase

import com.mivan.streamingsandbox.feature.channels.domain.model.EpgEntry
import com.mivan.streamingsandbox.feature.channels.domain.repository.ChannelRepository
import javax.inject.Inject

data class NowAndNext(
    val now: EpgEntry?,
    val next: EpgEntry?
)

class GetNowAndNextForChannelUseCase @Inject constructor(
    private val channelRepository: ChannelRepository
) {
    operator fun invoke(channelId: String, nowEpochMs: Long = System.currentTimeMillis()): NowAndNext {
        val channelEntries = channelRepository
            .getEpgEntries()
            .filter { it.channelId == channelId }
            .sortedBy { it.startEpochMs }

        val nowEntry = channelEntries.firstOrNull { entry ->
            nowEpochMs in entry.startEpochMs until entry.endEpochMs
        }

        val nextEntry = when (nowEntry) {
            null -> channelEntries.firstOrNull { it.startEpochMs > nowEpochMs }
            else -> channelEntries.firstOrNull { it.startEpochMs >= nowEntry.endEpochMs }
        }

        return NowAndNext(
            now = nowEntry,
            next = nextEntry
        )
    }
}