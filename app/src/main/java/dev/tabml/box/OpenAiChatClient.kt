package dev.tabml.box

import org.json.JSONArray
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Minimal OpenAI-compatible `POST /v1/chat/completions` client. */
class OpenAiChatClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    /** [messages] as pairs of OpenAI role to content (`system`, `user`, `assistant`). */
    fun chat(messages: List<Pair<String, String>>): String {
        val url = "${baseUrl.trimEnd('/')}/v1/chat/completions"
        val arr = JSONArray()
        for ((role, content) in messages) {
            arr.put(
                JSONObject()
                    .put("role", role)
                    .put("content", content),
            )
        }
        val body = JSONObject()
            .put("model", model)
            .put("messages", arr)
        val req = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .apply {
                if (apiKey.isNotBlank()) {
                    addHeader("Authorization", "Bearer $apiKey")
                }
            }
            .post(body.toString().toRequestBody(jsonMedia))
            .build()
        client.newCall(req).execute().use { resp ->
            val responseBody = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IOException("HTTP ${resp.code}: $responseBody")
            }
            val json = JSONObject(responseBody)
            return json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        }
    }
}
