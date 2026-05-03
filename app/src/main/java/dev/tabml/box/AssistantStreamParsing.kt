package dev.tabml.box

import okhttp3.ResponseBody
import okio.BufferedSource
import org.json.JSONObject
import java.io.IOException

/** SSE / NDJSON helpers for OpenAI-compatible streaming (unit-tested). */
object AssistantStreamParsing {

    fun isLineStreamingContentType(ctype: String): Boolean {
        val c = ctype.lowercase()
        return c.contains("text/event-stream") ||
            c.contains("ndjson") ||
            c.contains("x-ndjson")
    }

    fun extractStreamContentPiece(json: JSONObject): String {
        val choices = json.optJSONArray("choices")
        if (choices != null && choices.length() > 0) {
            val c0 = choices.optJSONObject(0) ?: return ""
            c0.optJSONObject("delta")?.optString("content", "")
                ?.takeIf { it.isNotEmpty() }?.let { return it }
            c0.optJSONObject("message")?.optString("content", "")
                ?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        json.optJSONObject("message")?.optString("content", "")
            ?.takeIf { it.isNotEmpty() }?.let { return it }
        return ""
    }

    fun readLineStreamingBody(
        body: ResponseBody,
        onPartial: (String) -> Unit,
        isActive: () -> Boolean,
    ): String {
        val source: BufferedSource = body.source()
        var acc = ""
        try {
            while (!source.exhausted() && isActive()) {
                val line = source.readUtf8Line() ?: break
                val trimmed = line.trimEnd('\r')
                if (trimmed.isBlank()) continue
                val payload = when {
                    trimmed.startsWith("data:") -> trimmed.removePrefix("data:").trim()
                    else -> trimmed
                }
                if (payload == "[DONE]") break
                if (payload.isEmpty()) continue
                val piece = try {
                    extractStreamContentPiece(JSONObject(payload))
                } catch (_: Exception) {
                    continue
                }
                if (piece.isNotEmpty()) {
                    acc += piece
                    onPartial(acc)
                }
            }
        } catch (e: IOException) {
            if (acc.isNotEmpty()) return acc.trim()
            throw e
        }
        return acc.trim()
    }

    fun readLineStreamingFromText(
        text: String,
        onPartial: (String) -> Unit,
        isActive: () -> Boolean,
    ): String {
        var acc = ""
        for (line in text.lineSequence()) {
            if (!isActive()) break
            val trimmed = line.trimEnd('\r')
            if (trimmed.isBlank()) continue
            val payload = when {
                trimmed.startsWith("data:") -> trimmed.removePrefix("data:").trim()
                else -> trimmed
            }
            if (payload == "[DONE]") break
            if (payload.isEmpty()) continue
            val piece = try {
                extractStreamContentPiece(JSONObject(payload))
            } catch (_: Exception) {
                continue
            }
            if (piece.isNotEmpty()) {
                acc += piece
                onPartial(acc)
            }
        }
        return acc.trim()
    }
}
