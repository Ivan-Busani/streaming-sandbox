package com.mivan.streamingsandbox.feature.vod.data.json

import com.mivan.streamingsandbox.feature.channels.domain.model.StreamType
import com.mivan.streamingsandbox.feature.player.domain.DrmConfig
import com.mivan.streamingsandbox.feature.player.domain.DrmScheme
import com.mivan.streamingsandbox.feature.vod.domain.model.VodItem
import org.json.JSONArray
import org.json.JSONObject

object VodCatalogParser {

    fun parse(json: String): List<VodItem> {
        val arr = JSONArray(json)
        return buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                parseVodItem(obj)?.let { add(it) }
            }
        }
    }

    private fun parseVodItem(obj: JSONObject): VodItem? {
        val id = obj.optString("id").trim()
        val name = obj.optString("name").trim()
        val type = parseStreamType(obj.optString("type")) ?: return null
        val url = obj.optString("url").trim()

        if (id.isEmpty() || name.isEmpty() || url.isEmpty()) return null

        return VodItem(
            id = id,
            name = name,
            type = type,
            url = url,
            urlPortrait = obj.optString("urlPortrait").trim().ifBlank { null },
            drm = parseDrm(obj.optJSONObject("drm"))
        )
    }

    private fun parseStreamType(raw: String): StreamType? = when (raw.trim().uppercase()) {
        "HLS" -> StreamType.HLS
        "DASH" -> StreamType.DASH
        else -> null
    }

    private fun parseDrm(drmObj: JSONObject?): DrmConfig? {
        if (drmObj == null) return null

        val scheme = when (drmObj.optString("scheme").trim().uppercase()) {
            "WIDEVINE" -> DrmScheme.WIDEVINE
            else -> return null
        }

        val licenseUrl = drmObj.optString("licenseUrl").trim()
        if (licenseUrl.isEmpty()) return null

        val headers = parseHeaders(drmObj.optJSONObject("headers"))
        val multiSession = drmObj.optBoolean("multiSession", false)

        return DrmConfig(
            scheme = scheme,
            licenseUrl = licenseUrl,
            headers = headers,
            multiSession = multiSession
        )
    }

    private fun parseHeaders(headersObj: JSONObject?): Map<String, String> {
        if (headersObj == null) return emptyMap()
        val map = mutableMapOf<String, String>()
        val keys = headersObj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = headersObj.optString(key).trim()
            if (value.isNotEmpty()) {
                map[key] = value
            }
        }
        return map
    }
}
