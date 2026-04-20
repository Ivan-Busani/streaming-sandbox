package com.mivan.streamingsandbox.feature.channels.data.repository

import com.mivan.streamingsandbox.feature.channels.domain.model.Channel
import com.mivan.streamingsandbox.feature.channels.domain.model.StreamType
import com.mivan.streamingsandbox.feature.channels.domain.repository.ChannelRepository
import com.mivan.streamingsandbox.feature.player.domain.DrmConfig
import com.mivan.streamingsandbox.feature.player.domain.DrmScheme
import javax.inject.Inject
import com.mivan.streamingsandbox.BuildConfig
import com.mivan.streamingsandbox.feature.channels.data.m3u.M3uParser
import com.mivan.streamingsandbox.feature.channels.domain.model.EpgEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class ChannelRepositoryImpl @Inject constructor() : ChannelRepository {
    @Volatile private var cachedChannels: List<Channel>? = null
    private var cachedAtEpochMs: Long = 0L

    private val httpClient = OkHttpClient()

    private companion object {
        private const val IPTV_SPA_M3U_URL = "https://iptv-org.github.io/iptv/languages/spa.m3u"
        private const val REMOTE_LIMIT = 20
        private const val CACHE_TTL_MS =
            (6 /* hours */) *
            (60 /* minutes per hour */) *
            (60 /* seconds per minute */) *
            (1000L /* ms per second */)
    }

    private fun fallbackChannels(): List<Channel> = listOf(
        Channel(
            id = "1",
            name = "Tears of Steel HLS",
            type = StreamType.HLS,
            url = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
        ),
        Channel(
            id = "2",
            name = "Tears of Steel DASH",
            type = StreamType.DASH,
            url = "https://storage.googleapis.com/wvmedia/clear/h264/tears/tears.mpd"
        ),
        Channel(
            id = "3",
            name = "Demo DRM Widevine",
            type = StreamType.DASH,
            url = "https://storage.googleapis.com/wvmedia/cenc/h264/tears/tears.mpd",
            drm = DrmConfig(
                scheme = DrmScheme.WIDEVINE,
                licenseUrl = BuildConfig.WIDEVINE_LICENSE_URL,
                headers = emptyMap(),
                multiSession = false
            )
        )
    )

    private fun inferStreamType(url: String): StreamType? {
        val normalized = url.lowercase()
        return when {
            ".m3u8" in normalized -> StreamType.HLS
            ".mpd" in normalized -> StreamType.DASH
            else -> null
        }
    }

    override suspend fun getChannels(): List<Channel> {
        val now = System.currentTimeMillis()
        val cached = cachedChannels
        if (cached != null && (now - cachedAtEpochMs) < CACHE_TTL_MS) {
            return cached
        }

        return runCatching {
            withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url(IPTV_SPA_M3U_URL)
                    .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    error("M3U request failed: ${response.code}")
                }

                val body = response.body?.string().orEmpty()
                if (body.isBlank()) {
                    error("M3U body is empty")
                }

                val parsed = M3uParser.parse(body)

                val remoteChannels = parsed
                    .asSequence()
                    .mapNotNull { entry ->
                        val streamType = inferStreamType(entry.url) ?: return@mapNotNull null
                        Channel(
                            id = "live-m3u-${entry.name}-${entry.url.hashCode()}",
                            name = entry.name,
                            type = streamType,
                            url = entry.url
                        )
                    }
                    .take(REMOTE_LIMIT)
                    .toList()

                if (remoteChannels.isEmpty()) error("No playable channels parsed from M3U")

                cachedChannels = remoteChannels
                cachedAtEpochMs = now
                remoteChannels
            }
        }.getOrElse {
            val fallback = fallbackChannels()
            cachedChannels = fallback
            cachedAtEpochMs = now
            return fallback
        }
    }

    private val epgBaseEpochMs = System.currentTimeMillis()
    private val epgEntries: List<EpgEntry> = listOf(
        // Canal 1: ahora + siguiente
        EpgEntry(
            channelId = "1",
            title = "Noticias - Edición central",
            startEpochMs = epgBaseEpochMs - 10 * 60 * 1000L,
            endEpochMs = epgBaseEpochMs + 20 * 60 * 1000L,
            description = "Resumen del día"
        ),
        EpgEntry(
            channelId = "1",
            title = "Entrevistas de actualidad",
            startEpochMs = epgBaseEpochMs + 20 * 60 * 1000L,
            endEpochMs = epgBaseEpochMs + 50 * 60 * 1000L
        ),
        // Canal 2: ahora + siguiente
        EpgEntry(
            channelId = "2",
            title = "Magazine de la tarde",
            startEpochMs = epgBaseEpochMs - 5 * 60 * 1000L,
            endEpochMs = epgBaseEpochMs + 25 * 60 * 1000L
        ),
        EpgEntry(
            channelId = "2",
            title = "Deportes en vivo",
            startEpochMs = epgBaseEpochMs + 25 * 60 * 1000L,
            endEpochMs = epgBaseEpochMs + 55 * 60 * 1000L
        ),
        // Canal 3 (DRM): ahora + siguiente
        EpgEntry(
            channelId = "3",
            title = "Película prime time",
            startEpochMs = epgBaseEpochMs - 15 * 60 * 1000L,
            endEpochMs = epgBaseEpochMs + 15 * 60 * 1000L
        ),
        EpgEntry(
            channelId = "3",
            title = "Behind the scenes",
            startEpochMs = epgBaseEpochMs + 15 * 60 * 1000L,
            endEpochMs = epgBaseEpochMs + 45 * 60 * 1000L
        )
    )

    override fun getEpgEntries() = epgEntries
}
