package com.mivan.streamingsandbox.feature.channels.data.m3u

import android.util.Log

data class M3uChannel(
    val name: String,
    val url: String,
    val tvgId: String? = null,
    val tvgLogo: String? = null
)

data class M3uPlaylist(
    val channels: List<M3uChannel>,
    val epgUrl: String? = null
)

object M3uParser {

    private const val TAG = "*|M3uParser"

    fun parse(input: String): M3uPlaylist {
        val lines = input
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()

        val channels = mutableListOf<M3uChannel>()
        var pendingName: String? = null
        var pendingTvgId: String? = null
        var pendingTvgLogo: String? = null

        // Parse optional XMLTV endpoint for EXTM3U header.
        val firstLine = lines.firstOrNull().orEmpty()
        val epgUrl = Regex("""x-tvg-url="([^"]+)"""", RegexOption.IGNORE_CASE)
            .find(firstLine)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.ifBlank { null }

        for (i in 0 until lines.size) {
            val line = lines[i]
            if (line.startsWith("#EXTINF", ignoreCase = true)) {
                // EXTINF format: #EXTINF:-1 ... ,Channel Name
                pendingName = line.substringAfterLast(",").trim().ifBlank { "Unnamed channel" }

                val tvgIdMatch = Regex("""tvg-id="([^"]+)"""", RegexOption.IGNORE_CASE)
                    .find(line)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.trim()

                pendingTvgId = tvgIdMatch?.ifBlank { null }

                val tvgLogoMatch = Regex("""tvg-logo="([^"]+)"""", RegexOption.IGNORE_CASE)
                    .find(line)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.trim()

                pendingTvgLogo = tvgLogoMatch?.ifBlank { null }

                Log.d(TAG, "Logo URL: $pendingTvgLogo")


                continue
            }

            if (line.startsWith("#")) {
                // Ignore comments/metadata lines
                continue
            }

            // Non-comment line after EXTINF is expected to be stream URL
            val url = line
            val name = pendingName ?: "Unnamed channel"

            if (url.startsWith("http://") || url.startsWith("https://")) {
                channels.add(M3uChannel(
                    name,
                    url,
                    tvgId = pendingTvgId,
                    tvgLogo = pendingTvgLogo
                ))
            }

            pendingName = null
            pendingTvgId = null
            pendingTvgLogo = null
        }

        return M3uPlaylist(
            channels = channels,
            epgUrl = epgUrl
        )
    }
}