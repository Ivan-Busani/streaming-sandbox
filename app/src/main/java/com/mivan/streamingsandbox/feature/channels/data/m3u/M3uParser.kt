package com.mivan.streamingsandbox.feature.channels.data.m3u

data class M3uChannel(
    val name: String,
    val url: String,
    val tvgId: String? = null
)

object M3uParser {
    fun parse(input: String): List<M3uChannel> {
        val lines = input
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()

        val channels = mutableListOf<M3uChannel>()
        var pendingName: String? = null
        var pendingTvgId: String? = null

        for (line in lines) {
            if (line.startsWith("#EXTINF", ignoreCase = true)) {
                // EXTINF format: #EXTINF:-1 ... ,Channel Name
                pendingName = line.substringAfterLast(",").trim().ifBlank { "Unnamed channel" }

                val tvgIdMatch = Regex("""tvg-id="([^"]+)"""", RegexOption.IGNORE_CASE)
                    .find(line)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.trim()

                pendingTvgId = tvgIdMatch?.ifBlank { null }
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
                    tvgId = pendingTvgId
                ))
            }

            pendingName = null
            pendingTvgId = null
        }

        return channels
    }
}