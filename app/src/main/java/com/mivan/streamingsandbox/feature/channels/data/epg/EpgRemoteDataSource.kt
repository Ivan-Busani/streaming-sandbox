package com.mivan.streamingsandbox.feature.channels.data.epg

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
        private const val EPG_ES_XML_GZ_URL = "https://iptv-epg.org/files/epg-es.xml.gz"
        private const val EPG_CACHE_TTL_MS = 6 * 60 * 60 * 1000L
    }

    suspend fun fetchPrograms(): List<EpgEntry> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cached = cachedEntries
        if (cached != null && (now - cachedAtEpochMs) < EPG_CACHE_TTL_MS) {
            return@withContext cached
        }

        val request = Request.Builder()
            .url(EPG_ES_XML_GZ_URL)
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