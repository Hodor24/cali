package dev.tabml.box

import org.json.JSONObject

/** Optional machine-readable tail on assistant messages. */
data class AiAction(val downloadTfliteUrl: String?)

object AiActionParser {

    /**
     * Looks for a block after `###ACTION` containing JSON, e.g.
     * `{"download_tflite":"https://.../model.tflite"}`.
     */
    fun parse(assistantReply: String): AiAction? {
        val key = "###ACTION"
        val idx = assistantReply.indexOf(key)
        if (idx < 0) return null
        val tail = assistantReply.substring(idx + key.length).trim()
        if (tail.isEmpty()) return null
        return try {
            val json = JSONObject(tail)
            val raw = when {
                json.has("download_tflite") -> json.getString("download_tflite")
                json.has("tflite_url") -> json.getString("tflite_url")
                else -> null
            }?.trim()
            val url = raw?.takeIf { it.startsWith("https://") }
            if (url == null) null else AiAction(url)
        } catch (_: Exception) {
            null
        }
    }
}
