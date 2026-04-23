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
import java.text.Normalizer

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

    @Volatile
    private var cachedSourceEpgUrl: String? = null

    private val epgBaseEpochMs = System.currentTimeMillis()
    private val httpClient = OkHttpClient()

    private companion object {
        private const val TAG = "*|ChannelRepository"
        private const val REMOTE_LIMIT = 200
        private const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L
        private const val MAPPED_EPG_CACHE_TTL_MS = 6 * 60 * 60 * 1000L
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
                    .url(BuildConfig.IPTV_SOURCE_M3U_URL)
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
                    val playlist = M3uParser.parse(body)
                    val parsedChannels = playlist.channels

                    for (m3u in parsedChannels) {
                        val streamType = inferStreamType(m3u.url) ?: continue
                        val id = "live-m3u-${m3u.name}-${m3u.url.hashCode()}"
                        val channel = Channel(
                            id = id,
                            name = m3u.name,
                            type = streamType,
                            url = m3u.url,
                            urlLogo = m3u.tvgLogo
                        )
                        builtChannels.add(channel)

                        m3u.tvgId
                            ?.trim()
                            ?.lowercase()
                            ?.takeIf { it.isNotEmpty() }
                            ?.let { tvgIdToChannelId[it] = id }
                    }

                    // remoteChannels = builtChannels.take(REMOTE_LIMIT)
                    remoteChannels = builtChannels

                    cachedTvgIdToChannelId = tvgIdToChannelId
                    cachedSourceEpgUrl = playlist.epgUrl
                }

                if (remoteChannels.isEmpty()) error("No playable channels parsed from M3U")

                cachedChannels = remoteChannels
                cachedChannelsAtEpochMs = now

                // Invalidate mapped EPG cache when channel set changes
                cachedMappedEpgEntries = null
                cachedMappedEpgAtEpochMs = 0L
                cachedMappedEpgForChannelsEpochMs = Long.MIN_VALUE

                remoteChannels

            }
        }.getOrElse {
            cachedTvgIdToChannelId = emptyMap()
            val fallback = fallbackChannels()
            cachedChannels = fallback
            cachedChannelsAtEpochMs = now

            // Invalidate mapped EPG cache when channel set changes
            cachedMappedEpgEntries = null
            cachedMappedEpgAtEpochMs = 0L
            cachedMappedEpgForChannelsEpochMs = Long.MIN_VALUE
            cachedSourceEpgUrl = null

            return fallback
        }
    }

    override suspend fun getEpgEntries(): List<EpgEntry> {
        val channels = cachedChannels ?: fallbackChannels()
        val now = System.currentTimeMillis()

        val cachedEntries = cachedMappedEpgEntries
        val ageMs = now - cachedMappedEpgAtEpochMs
        val ttlOk = ageMs < MAPPED_EPG_CACHE_TTL_MS
        val channelsEpochOk = cachedMappedEpgForChannelsEpochMs == cachedChannelsAtEpochMs
        if (cachedEntries != null && ttlOk && channelsEpochOk) {
            Log.d(
                TAG,
                "EPG mapped cache HIT: entries=${cachedEntries.size}, ageMs=$ageMs, epgEpoch=$cachedMappedEpgForChannelsEpochMs, channelsEpoch=$cachedChannelsAtEpochMs"
            )
            return cachedEntries
        }

        return withContext(Dispatchers.Default) {
            runCatching {
                val remotePrograms = epgRemoteDataSource.fetchPrograms(cachedSourceEpgUrl)
                val mapped = mapRemoteEpgToKnownChannels(remotePrograms, channels)

                val matchedChannelIds = mapped.map { it.channelId }.toSet()

                val channelsWithEpg = channels.count { it.id in matchedChannelIds }
                val channelCoveragePercent = if (channels.isEmpty()) 0 else (channelsWithEpg * 100) / channels.size

                Log.d(
                    TAG,
                    "EPG channel coverage: $channelsWithEpg/${channels.size} ($channelCoveragePercent%)"
                )

                val result = mapped.ifEmpty {
                    Log.d(TAG, "Mapped EPG is empty, using simulated fallback")
                    buildFallbackEpgForChannels(channels)
                }

                cachedMappedEpgEntries = result
                cachedMappedEpgAtEpochMs = now
                cachedMappedEpgForChannelsEpochMs = cachedChannelsAtEpochMs

                Log.d(
                    TAG,
                    "EPG mapped cache STORE: entries=${result.size}, channelsEpoch=$cachedChannelsAtEpochMs"
                )

                result
            }.getOrElse { throwable ->
                Log.e(TAG, "EPG fetch/map failed, using simulated fallback", throwable)
                val fallback = buildFallbackEpgForChannels(channels)
                // Cache fallback too, so next calls can HIT instead of remapping every time
                cachedMappedEpgEntries = fallback
                cachedMappedEpgAtEpochMs = now
                cachedMappedEpgForChannelsEpochMs = cachedChannelsAtEpochMs
                Log.d(
                    TAG,
                    "EPG mapped cache STORE (fallback): entries=${fallback.size}, channelsEpoch=$cachedChannelsAtEpochMs"
                )
                fallback
            }
        }
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

    private fun buildFallbackEpgForChannels(channels: List<Channel>): List<EpgEntry> {
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
                    title = "Ahora · ${channel.name}",
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
        val byNormalizeName = channels.associateBy { applyAlias(normalizeChannelName(it.name)) }

        val mapped = remotePrograms.mapNotNull { entry ->
            val xmlChannelKey = entry.channelId.trim().lowercase()
            val byTvg = cachedTvgIdToChannelId[xmlChannelKey]
            if (byTvg != null) {
                return@mapNotNull entry.copy(channelId = byTvg)
            }

            val candidateNames = buildList {
                add(entry.channelId)
                entry.sourceDisplayName?.let { add(it) }
            }

            val normalizedCandidates = candidateNames
                .asSequence()
                .map { applyAlias(normalizeChannelName(it)) }
                .filter { it.isNotBlank() }
                .toList()

            val byName = normalizedCandidates
                .firstNotNullOfOrNull { byNormalizeName[it] }
                ?: return@mapNotNull null

            /*
            val exactMatch = normalizedCandidates
                .firstNotNullOfOrNull { byNormalizeName[it] }

            val byName = exactMatch ?: run {
                channels.firstOrNull { channel ->
                    val channelNorm = normalizeChannelName(channel.name)
                    if (channelNorm.length < 4) return@firstOrNull false

                    normalizedCandidates.any { candidate ->
                        candidate.length >= 4 &&
                                (channelNorm.contains(candidate)
                                        || candidate.contains(channelNorm))
                    }
                }
            } ?: return@mapNotNull null
             */

            entry.copy(channelId = byName.id)
        }

        return mapped
    }

    private fun normalizeChannelName(raw: String): String {
        val noAccents = Normalizer.normalize(raw, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return noAccents
            .lowercase()
            .replace(Regex("\\[[^]]*]"), " ")      // [Not 24/7], [Geo-blocked]
            .replace(Regex("\\([^)]*\\)"), " ")      // (720p), (1080p)
            .replace(Regex("\\b(hd|fhd|uhd|sd|tv|canal|mx)\\b"), " ")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    private val configuredAliases: Map<String, String> by lazy {
        BuildConfig.EPG_CHANNEL_ALIASES
            .split(";")
            .mapNotNull { pair ->
                val parts = pair.split("=")
                if (parts.size != 2) return@mapNotNull null
                val from = normalizeChannelName(parts[0])
                val to = normalizeChannelName(parts[1])
                if (from.isBlank() || to.isBlank()) null else from to to
            }
            .toMap()
    }

    private fun applyAlias(normalized: String): String =
        configuredAliases[normalized] ?: normalized
}
