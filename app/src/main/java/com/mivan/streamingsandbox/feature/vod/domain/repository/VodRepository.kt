package com.mivan.streamingsandbox.feature.vod.domain.repository

import com.mivan.streamingsandbox.feature.vod.domain.model.VodItem

interface VodRepository {
    suspend fun getVodItems(): List<VodItem>
}
