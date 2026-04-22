package com.mivan.streamingsandbox.feature.channels.data.epg

import android.util.Log
import com.mivan.streamingsandbox.BuildConfig
import com.mivan.streamingsandbox.feature.channels.domain.model.EpgEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.Volatile

@Singleton
class EpgRemoteDataSource @Inject constructor() {

    @Volatile
    private var cachedEntries: List<EpgEntry>? = null
    private var cachedAtEpochMs: Long = 0L

    private val httpClient = OkHttpClient()

    private companion object {
        private const val TAG = "*|EpgRemoteDataSource"
        private const val EPG_CACHE_TTL_MS = 6 * 60 * 60 * 1000L
    }

    suspend fun fetchPrograms(epgUrlOverride: String? = null): List<EpgEntry> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cached = cachedEntries
        if (cached != null && (now - cachedAtEpochMs) < EPG_CACHE_TTL_MS) {
            return@withContext cached
        }

        val effectiveEpgUrl = epgUrlOverride
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: BuildConfig.EPG_XML_GZ_URL
                .trim()
                .takeIf { it.isNotEmpty() }
            ?: error("No EPG URL configured (override and BuildConfig are empty)")

        Log.d(TAG, "Using EPG URL: $effectiveEpgUrl")

        val request = Request.Builder()
            .url(effectiveEpgUrl)
            .build()

        val responseBytes = httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("EPG request failed: ${response.code}")
            }
            response.body?.bytes() ?: error("EPG body is empty")
        }

        val xml = ungzipToString(responseBytes)
        val parsed = XmlTvParser.parseProgrammes(xml)
        if (parsed.isEmpty()) {
            error("Parsed EPG is empty")
        }

        cachedEntries = parsed
        cachedAtEpochMs = now
        parsed
    }

    private fun ungzipToString(gzBytes: ByteArray): String {
        return GZIPInputStream(ByteArrayInputStream(gzBytes))
            .bufferedReader()
            .use { it.readText() }
    }
}