package com.mivan.streamingsandbox.feature.channels.domain.usecase

import com.mivan.streamingsandbox.feature.channels.domain.model.EpgEntry
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class GetProgramsForChannelUseCase @Inject constructor() {
    operator fun invoke(
        channelId: String,
        epg: List<EpgEntry>
    ): List<EpgEntry> {
        val nowEpochMs = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()

        val startOfDayMs = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val startOfNextDayMs = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        return epg
            .asSequence()
            .filter { it.channelId == channelId }
            .filter { it.endEpochMs > nowEpochMs }
            .filter { it.endEpochMs > startOfDayMs && it.startEpochMs < startOfNextDayMs }
            .sortedBy { it.startEpochMs }
            .toList()
    }
}