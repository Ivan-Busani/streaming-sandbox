package com.mivan.streamingsandbox.feature.channels.domain.usecase

import com.mivan.streamingsandbox.feature.channels.domain.model.EpgEntry
import com.mivan.streamingsandbox.feature.channels.domain.repository.ChannelRepository
import javax.inject.Inject

class GetEpgEntriesUseCase @Inject constructor(
    private val repository: ChannelRepository
) {
    suspend operator fun invoke(force: Boolean? = false): List<EpgEntry> {
        return repository.getEpgEntries(force)
    }
}
