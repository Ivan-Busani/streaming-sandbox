package com.mivan.streamingsandbox.feature.vod.domain.usecase

import com.mivan.streamingsandbox.feature.vod.domain.model.VodItem
import com.mivan.streamingsandbox.feature.vod.domain.repository.VodRepository
import javax.inject.Inject

class GetVodItemsUseCase @Inject constructor(
    private val repository: VodRepository
) {
    suspend operator fun invoke(): List<VodItem> = repository.getVodItems()
}
