package dev.tabml.box

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** HTTPS client for your custom LLM: `POST …/v1/chat/completions` (OpenAI-style messages in, reply text out). */
class AssistantChatClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(240, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    /**
     * ngrok free endpoints often return **403** to non-browser clients until this header is sent.
     * See ngrok docs / community notes on skipping the browser warning for APIs.
     */
    private fun Request.Builder.applyNgrokFreeTierHeaders(url: String): Request.Builder {
        if (!url.contains("ngrok", ignoreCase = true)) return this
        addHeader("ngrok-skip-browser-warning", "true")
        addHeader("Accept-Language", "en-US,en;q=0.9")
        // Default OkHttp User-Agent is sometimes blocked at the edge; mimic a normal client.
        addHeader(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36 TabMLBox",
        )
        return this
    }

    private fun buildRequestBody(messages: List<Pair<String, String>>, stream: Boolean): RequestBody {
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
        if (stream) {
            body.put("stream", true)
        }
        return body.toString().toRequestBody(jsonMedia)
    }

    private fun newChatCall(messages: List<Pair<String, String>>, stream: Boolean): Call {
        val url = "${baseUrl.trimEnd('/')}/v1/chat/completions"
        val reqBuilder = Request.Builder()
            .url(url)
            .applyNgrokFreeTierHeaders(url)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json, text/event-stream")
            .apply {
                if (apiKey.isNotBlank()) {
                    addHeader("Authorization", "Bearer $apiKey")
                }
            }
            .post(buildRequestBody(messages, stream = stream))
        return client.newCall(reqBuilder.build())
    }

    /** Roles: `system`, `user`, `assistant` — content strings. */
    fun chat(messages: List<Pair<String, String>>): String {
        newChatCall(messages, stream = false).execute().use { resp ->
            val responseBody = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IOException("HTTP ${resp.code}: $responseBody")
            }
            return parseAssistantContent(responseBody)
        }
    }

    /**
     * Like [chat] but the in-flight HTTP request is cancelled when the coroutine is cancelled
     * (e.g. user taps Stop in the chat toolbar).
     */
    suspend fun chatSuspend(messages: List<Pair<String, String>>): String =
        suspendCancellableCoroutine { cont ->
            val call = newChatCall(messages, stream = false)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        when {
                            !cont.isActive -> { }
                            call.isCanceled() ->
                                cont.cancel(CancellationException("Request cancelled", e))
                            else -> cont.resumeWithException(e)
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (!cont.isActive) {
                            response.close()
                            return
                        }
                        try {
                            response.use { resp ->
                                val responseBody = resp.body?.string().orEmpty()
                                if (!resp.isSuccessful) {
                                    cont.resumeWithException(IOException("HTTP ${resp.code}: $responseBody"))
                                    return
                                }
                                cont.resume(parseAssistantContent(responseBody))
                            }
                        } catch (e: Throwable) {
                            cont.resumeWithException(e)
                        }
                    }
                },
            )
        }

    /**
     * Tries streaming first; on any failure except [CancellationException], runs a non-streaming request once.
     */
    suspend fun chatStreamWithFallback(
        messages: List<Pair<String, String>>,
        onPartialAccumulated: (String) -> Unit,
    ): String =
        try {
            chatStreamSuspend(messages, onPartialAccumulated)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            val full = chatSuspend(messages)
            onPartialAccumulated(full)
            full
        }

    /**
     * Requests `stream: true` and parses Server-Sent Events (`data: …`) or NDJSON lines (Ollama-style).
     */
    suspend fun chatStreamSuspend(
        messages: List<Pair<String, String>>,
        onPartialAccumulated: (String) -> Unit,
    ): String =
        suspendCancellableCoroutine { cont ->
            val call = newChatCall(messages, stream = true)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        when {
                            !cont.isActive -> { }
                            call.isCanceled() ->
                                cont.cancel(CancellationException("Request cancelled", e))
                            else -> cont.resumeWithException(e)
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (!cont.isActive) {
                            response.close()
                            return
                        }
                        try {
                            response.use { resp ->
                                if (!resp.isSuccessful) {
                                    val b = resp.body?.string().orEmpty()
                                    cont.resumeWithException(IOException("HTTP ${resp.code}: $b"))
                                    return@use
                                }
                                val body = resp.body
                                if (body == null) {
                                    cont.resumeWithException(IOException("empty body"))
                                    return@use
                                }
                                val ctype = resp.header("Content-Type").orEmpty()
                                val text = when {
                                    AssistantStreamParsing.isLineStreamingContentType(ctype) ->
                                        AssistantStreamParsing.readLineStreamingBody(
                                            body,
                                            onPartialAccumulated,
                                        ) { cont.isActive }
                                    else -> {
                                        val full = body.string()
                                        val oneShot = runCatching { parseAssistantContent(full) }.getOrNull()
                                        if (oneShot != null) {
                                            onPartialAccumulated(oneShot)
                                            oneShot.trim()
                                        } else {
                                            AssistantStreamParsing.readLineStreamingFromText(
                                                full,
                                                onPartialAccumulated,
                                            ) { cont.isActive }
                                        }
                                    }
                                }
                                if (text.isBlank()) {
                                    cont.resumeWithException(IOException("empty assistant reply"))
                                } else {
                                    cont.resume(text.trim())
                                }
                            }
                        } catch (e: Throwable) {
                            if (cont.isActive) cont.resumeWithException(e)
                        }
                    }
                },
            )
        }

    /** Lists model ids from `GET /v1/models` when exposed by the server. */
    suspend fun fetchModelIds(): List<String> = withContext(Dispatchers.IO) {
        val url = "${baseUrl.trimEnd('/')}/v1/models"
        val req = Request.Builder()
            .url(url)
            .applyNgrokFreeTierHeaders(url)
            .get()
            .apply {
                if (apiKey.isNotBlank()) {
                    addHeader("Authorization", "Bearer $apiKey")
                }
            }
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IOException("HTTP ${resp.code}: $body")
            }
            val data = JSONObject(body).optJSONArray("data") ?: return@withContext emptyList()
            buildList {
                for (i in 0 until data.length()) {
                    val id = data.optJSONObject(i)?.optString("id").orEmpty().trim()
                    if (id.isNotEmpty()) add(id)
                }
            }
        }
    }

    private fun parseAssistantContent(responseBody: String): String {
        val json = JSONObject(responseBody)
        return json.getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
    }
}
