package com.mivan.streamingsandbox.feature.channels.data.repository

import android.util.Log
import com.mivan.streamingsandbox.feature.channels.domain.model.Channel
import com.mivan.streamingsandbox.feature.channels.domain.model.StreamType
import com.mivan.streamingsandbox.feature.channels.domain.repository.ChannelRepository
import com.mivan.streamingsandbox.feature.player.domain.DrmConfig
import com.mivan.streamingsandbox.feature.player.domain.DrmScheme
import javax.inject.Inject
import com.mivan.streamingsandbox.BuildConfig
import com.mivan.streamingsandbox.feature.channels.data.epg.EpgRemoteDataSource
import com.mivan.streamingsandbox.feature.channels.data.m3u.M3uParser
import com.mivan.streamingsandbox.feature.channels.domain.model.EpgEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class ChannelRepositoryImpl @Inject constructor(
    private val epgRemoteDataSource: EpgRemoteDataSource
) : ChannelRepository {
    @Volatile
    private var cachedChannels: List<Channel>? = null
    private var cachedChannelsAtEpochMs: Long = 0L

    @Volatile
    private var cachedTvgIdToChannelId: Map<String, String> = emptyMap()

    @Volatile
    private var cachedMappedEpgEntries: List<EpgEntry>? = null
    private var cachedMappedEpgAtEpochMs: Long = 0L
    private var cachedMappedEpgForChannelsEpochMs: Long = Long.MIN_VALUE

    private val epgBaseEpochMs = System.currentTimeMillis()
    private val httpClient = OkHttpClient()

    private companion object {
        private const val TAG = "ChannelRepository"
        private const val IPTV_SPA_M3U_URL = "https://iptv-org.github.io/iptv/languages/spa.m3u"
        private const val REMOTE_LIMIT = 200
        private const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L
        private const val MAPPED_EPG_CACHE_TTL_MS = 6 * 60 * 60 * 1000L
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

    private fun buildEpgForChannels(channels: List<Channel>): List<EpgEntry> {
        val minuteMs = 60 * 1000L
        return channels.flatMap { channel ->
            val jitterMin = kotlin.math.abs(channel.id.hashCode()) % 10
            val offsetMs = jitterMin * minuteMs

            val currentStart = epgBaseEpochMs - 10 * minuteMs + offsetMs
            val currentEnd = epgBaseEpochMs + 20 * minuteMs + offsetMs

            val nextEnd = currentEnd + 30 * minuteMs

            val laterEnd = nextEnd + 30 * minuteMs

            listOf(
                EpgEntry(
                    channelId = channel.id,
                    title = "En directo · ${channel.name}",
                    startEpochMs = currentStart,
                    endEpochMs = currentEnd,
                    description = "Guía simulada para sandbox"
                ),
                EpgEntry(
                    channelId = channel.id,
                    title = "A continuación · ${channel.name}",
                    startEpochMs = currentEnd,
                    endEpochMs = nextEnd
                ),
                EpgEntry(
                    channelId = channel.id,
                    title = "Más tarde · ${channel.name}",
                    startEpochMs = nextEnd,
                    endEpochMs = laterEnd
                )
            )
        }
    }

    private fun mapRemoteEpgToKnownChannels(
        remotePrograms: List<EpgEntry>,
        channels: List<Channel>
    ): List<EpgEntry> {
        val byNormalizeName = channels.associateBy { normalizeChannelName(it.name) }

        return remotePrograms.mapNotNull { entry ->
            val xmlChannelKey = entry.channelId.trim().lowercase()
            val byTvg = cachedTvgIdToChannelId[xmlChannelKey]
            if (byTvg != null) {
                return@mapNotNull entry.copy(channelId = byTvg)
            }

            val normalizedName = normalizeChannelName(entry.channelId)
            val byName = byNormalizeName[normalizedName] ?: return@mapNotNull null
            entry.copy(channelId = byName.id)
        }
    }

    private fun normalizeChannelName(raw: String): String {
        return raw.lowercase()
            .replace(Regex("[^a-z0-9]"), "")
    }

    override suspend fun getChannels(): List<Channel> {
        val now = System.currentTimeMillis()
        val cached = cachedChannels
        if (cached != null && (now - cachedChannelsAtEpochMs) < CACHE_TTL_MS) {
            return cached
        }

        return runCatching {
            withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url(IPTV_SPA_M3U_URL)
                    .build()

                var remoteChannels: List<Channel> = emptyList()
                val builtChannels = mutableListOf<Channel>()
                val tvgIdToChannelId = mutableMapOf<String, String>()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        error("M3U request failed: ${response.code}")
                    }

                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) {
                        error("M3U body is empty")
                    }

                    val parsed = M3uParser.parse(body)
                    for (m3u in parsed) {
                        val streamType = inferStreamType(m3u.url) ?: continue
                        val id = "live-m3u-${m3u.name}-${m3u.url.hashCode()}"
                        val channel = Channel(
                            id = id,
                            name = m3u.name,
                            type = streamType,
                            url = m3u.url
                        )
                        builtChannels.add(channel)

                        m3u.tvgId
                            ?.trim()
                            ?.lowercase()
                            ?.takeIf { it.isNotEmpty() }
                            ?.let { tvgIdToChannelId[it] = id }
                    }

                    remoteChannels = builtChannels.take(REMOTE_LIMIT)
                    cachedTvgIdToChannelId = tvgIdToChannelId
                }

                if (remoteChannels.isEmpty()) error("No playable channels parsed from M3U")

                cachedChannels = remoteChannels
                cachedChannelsAtEpochMs = now
                remoteChannels

            }
        }.getOrElse {
            cachedTvgIdToChannelId = emptyMap()
            val fallback = fallbackChannels()
            cachedChannels = fallback
            cachedChannelsAtEpochMs = now
            return fallback
        }
    }

    override suspend fun getEpgEntries(): List<EpgEntry> {
        val channels = cachedChannels ?: fallbackChannels()
        val now = System.currentTimeMillis()

        val cachedEntries = cachedMappedEpgEntries
        if (cachedEntries != null &&
            (now - cachedMappedEpgAtEpochMs) < MAPPED_EPG_CACHE_TTL_MS &&
            cachedMappedEpgForChannelsEpochMs == cachedChannelsAtEpochMs
            ) {
            return cachedEntries
        }

        return runCatching {
            val remotePrograms = epgRemoteDataSource.fetchPrograms()
            val mapped = mapRemoteEpgToKnownChannels(remotePrograms, channels)

            val total = remotePrograms.size
            val matched = mapped.size
            val percent = if (total == 0) 0 else (matched * 100) / total
            Log.d(TAG, "Epg match: $matched/$total ($percent%)")

            val matchedChannelIds = mapped.map { it.channelId }.toSet()
            channels
                .filter { it.id !in matchedChannelIds }
                .forEach { channel ->
                    Log.d(TAG, "No EPG match for channelId=${channel.id}, name=${channel.name}")
                }

            val result = mapped.ifEmpty {
                Log.d(TAG, "Mapped EPG is empty, using simulated fallback")
                buildEpgForChannels(channels)
            }

            cachedMappedEpgEntries = result
            cachedMappedEpgAtEpochMs = now
            cachedMappedEpgForChannelsEpochMs = cachedChannelsAtEpochMs

            result
        }.getOrElse {
            cachedMappedEpgEntries = null
            cachedMappedEpgAtEpochMs = 0L
            cachedMappedEpgForChannelsEpochMs = Long.MIN_VALUE
            buildEpgForChannels(channels)
        }
    }
}
