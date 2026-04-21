package com.mivan.streamingsandbox.feature.channels.data.epg

import com.mivan.streamingsandbox.feature.channels.domain.model.EpgEntry
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.xml.parsers.DocumentBuilderFactory

object XmlTvParser {
    private val xmlTvDateFormat = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun parseProgrammes(xml: String): List<EpgEntry> {
        val doc = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(InputSource(StringReader(xml)))

        val programmeNodes = doc.getElementsByTagName("programme")
        val result = mutableListOf<EpgEntry>()

        for (i in 0 until programmeNodes.length) {
            val node = programmeNodes.item(i) as? Element ?: continue

            val channelId = node.getAttribute("channel").orEmpty()
            val startRaw = node.getAttribute("start").orEmpty()
            val stopRaw = node.getAttribute("stop").orEmpty()

            if (channelId.isBlank() || startRaw.isBlank() || stopRaw.isBlank()) continue

            val startMs = parseXmlTvDate(startRaw) ?: continue
            val endMs = parseXmlTvDate(stopRaw) ?: continue
            if (endMs <= startMs) continue

            val title = node.getElementsByTagName("title")
                .item(0)
                ?.textContent
                ?.trim()
                .orEmpty()

            val desc = node.getElementsByTagName("desc")
                .item(0)
                ?.textContent
                ?.trim()
                ?.ifBlank { null }

            if (title.isBlank()) continue

            result.add(EpgEntry(
                channelId = channelId,
                title = title,
                startEpochMs = startMs,
                endEpochMs = endMs,
                description = desc
            ))
        }

        return result
    }

    private fun parseXmlTvDate(raw: String): Long? {
        return runCatching {
            val cleaned = raw.trim().take(19)
            xmlTvDateFormat.parse(cleaned)?.time
        }.getOrNull()
    }
}