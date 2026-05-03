package dev.tabml.box

import org.json.JSONArray
import org.json.JSONObject

/** Optional machine-readable tail on assistant messages (###ACTION JSON). */
data class AiAction(
    val downloadTfliteUrl: String? = null,
    val webSearchQuery: String? = null,
    val navigateQuery: String? = null,
    /** Multi-stop driving directions (HTTPS Google Maps). */
    val navigateWaypoints: List<String>? = null,
    val openHttpsUrl: String? = null,
)

object AiActionParser {

    private const val MARKER = "###ACTION"

    fun stripActionBlock(assistantReply: String): String {
        val idx = assistantReply.indexOf(MARKER)
        if (idx < 0) return assistantReply.trimEnd()
        return assistantReply.substring(0, idx).trimEnd()
    }

    fun parseJsonTail(assistantReply: String): JSONObject? {
        val idx = assistantReply.indexOf(MARKER)
        if (idx < 0) return null
        val tail = assistantReply.substring(idx + MARKER.length).trim()
        if (tail.isEmpty()) return null
        return try {
            JSONObject(tail)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Looks for a block after [MARKER] containing JSON.
     */
    fun parse(assistantReply: String): AiAction? {
        val json = parseJsonTail(assistantReply) ?: return null
        return parseActionFromJson(json)
    }

    fun parseActionFromJson(json: JSONObject): AiAction? {
        return try {
            val tflite = when {
                json.has("download_tflite") -> json.getString("download_tflite").trim()
                json.has("tflite_url") -> json.getString("tflite_url").trim()
                else -> null
            }?.takeIf { it.startsWith("https://") }
            val searchRaw = if (json.has("web_search")) json.getString("web_search").trim() else null
            val search = searchQ(searchRaw)
            val navRaw = when {
                json.has("navigate_query") -> json.getString("navigate_query").trim()
                json.has("navigation_query") -> json.getString("navigation_query").trim()
                else -> null
            }
            val navigate = searchQ(navRaw)
            val navWp = parseWaypointList(json)
            val openRaw = when {
                json.has("open_https_url") -> json.getString("open_https_url").trim()
                json.has("open_url") -> json.getString("open_url").trim()
                else -> null
            }
            val openUrl = openRaw?.takeIf { it.startsWith("https://") }
            if (tflite == null && search == null && navigate == null && navWp == null && openUrl == null) {
                null
            } else {
                AiAction(tflite, search, navigate, navWp, openUrl)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseWaypointList(json: JSONObject): List<String>? {
        val key = when {
            json.has("navigate_waypoints") -> "navigate_waypoints"
            json.has("navigation_waypoints") -> "navigation_waypoints"
            else -> return null
        }
        val arr: JSONArray = json.optJSONArray(key) ?: return null
        val out = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val s = arr.optString(i, "").trim()
            if (s.isNotEmpty()) out.add(s)
        }
        return out.takeIf { it.isNotEmpty() }
    }

    private fun searchQ(s: String?): String? {
        val t = s?.trim().orEmpty()
        return t.takeIf { it.isNotEmpty() }
    }
}
