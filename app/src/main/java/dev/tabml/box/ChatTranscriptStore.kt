package dev.tabml.box

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Persists Cali chat messages across process death (not secrets — display text only). */
object ChatTranscriptStore {

    private const val PREFS = "cali_chat_transcript_v1"
    private const val KEY_JSON = "messages_json"

    fun save(context: Context, messages: List<ChatMessage>) {
        val arr = JSONArray()
        for (m in messages) {
            arr.put(
                JSONObject()
                    .put("s", m.speaker.name)
                    .put("t", m.text)
                    .put("a", m.apiText ?: JSONObject.NULL),
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_JSON, arr.toString())
            .apply()
    }

    fun load(context: Context): List<ChatMessage> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_JSON, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val speaker = ChatSpeaker.valueOf(o.getString("s"))
                    val text = o.getString("t")
                    val api = if (o.isNull("a")) null else o.getString("a")
                    add(ChatMessage(speaker, text, api))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
