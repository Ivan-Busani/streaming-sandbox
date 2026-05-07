package com.mivan.streamingsandbox.feature.vod.data.repository

import com.mivan.streamingsandbox.feature.vod.data.json.VodCatalogParser
import com.mivan.streamingsandbox.feature.vod.data.local.VodLocalDataSource
import com.mivan.streamingsandbox.feature.vod.domain.model.VodItem
import com.mivan.streamingsandbox.feature.vod.domain.repository.VodRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VodRepositoryImpl @Inject constructor(
    private val localDataSource: VodLocalDataSource
) : VodRepository {

    @Volatile
    private var cachedVodItems: List<VodItem>? = null
    private var cachedAtEpochMs: Long = 0L

    private companion object {
        private const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L
    }

    override suspend fun getVodItems(): List<VodItem> {
        val now = System.currentTimeMillis()
        val cached = cachedVodItems
        if (cached != null && (now - cachedAtEpochMs) < CACHE_TTL_MS) {
            return cached
        }

        return runCatching {
            withContext(Dispatchers.IO) {
                val json = localDataSource.loadCatalogJson()
                if (json.isBlank()) error("VOD catalog is empty")

                val items = VodCatalogParser.parse(json)
                if (items.isEmpty()) error("No valid VOD items found")

                cachedVodItems = items
                cachedAtEpochMs = now
                items
            }
        }.getOrElse { throwable ->
            if (throwable is CancellationException) throw throwable
            emptyList()
        }
    }
}
