package dev.tabml.box

import android.content.Context
import com.suhel.llamabro.sdk.chat.ChatEvent
import com.suhel.llamabro.sdk.chat.CompletionResult
import com.suhel.llamabro.sdk.chat.LlamaChatSession
import com.suhel.llamabro.sdk.config.LoadableModel
import com.suhel.llamabro.sdk.config.ModelLoadConfig
import com.suhel.llamabro.sdk.config.ModelProfile
import com.suhel.llamabro.sdk.config.ModelProfiles
import com.suhel.llamabro.sdk.config.OverflowStrategy
import com.suhel.llamabro.sdk.config.SessionConfig
import com.suhel.llamabro.sdk.engine.LlamaEngine
import com.suhel.llamabro.sdk.engine.LlamaSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext

/**
 * Runs Cali chat on a local GGUF file using Llama Bro (llama.cpp). All operations are serialized
 * on a single dispatcher because the native session is not safe for concurrent use.
 */
object OnDeviceLlm {

    private val exec = Dispatchers.IO.limitedParallelism(1)

    private var engine: LlamaEngine? = null
    private var session: LlamaSession? = null
    private var chat: LlamaChatSession? = null

    private var loadedPath: String? = null
    private var loadedProfileKey: String? = null
    private var lastSystemPromptHash: Int? = null

    private val modelProfiles: ModelProfiles by lazy {
        @Suppress("UNCHECKED_CAST")
        Class.forName("com.suhel.llamabro.sdk.config.ModelProfiles")
            .getField("INSTANCE")
            .get(null) as ModelProfiles
    }

    fun profileForPreferenceKey(key: String): ModelProfile {
        val p = modelProfiles
        fun invoke(name: String): ModelProfile =
            p.javaClass.getMethod(name).invoke(p) as ModelProfile
        return when (key) {
            "GEMMA" -> invoke("getGEMMA")
            "QWEN_2_5" -> invoke("getQWEN_2_5")
            "SMOLLM2" -> invoke("getSMOLLM2")
            else -> invoke("getLLAMA_3_2")
        }
    }

    suspend fun closeAll() = withContext(exec) {
        runCatching {
            chat = null
            session?.close()
            session = null
            engine?.close()
            engine = null
        }
        loadedPath = null
        loadedProfileKey = null
        lastSystemPromptHash = null
    }

    /** Drop chat/KV state but keep weights loaded (faster new topic without reloading GGUF). */
    suspend fun resetConversation() = withContext(exec) {
        runCatching {
            chat = null
            session?.close()
            session = null
        }
        lastSystemPromptHash = null
    }

    /** Ask the native decoder to stop (e.g. user tapped Stop). */
    suspend fun abortGeneration() = withContext(exec) {
        runCatching { session?.abort() }
    }

    private suspend fun ensureEngine(
        context: Context,
        profileKey: String,
        profile: ModelProfile,
        onProgress: (Float?) -> Unit,
    ) {
        val path = TabletGgufStore.modelFile(context).absolutePath
        if (engine != null && loadedPath == path && loadedProfileKey == profileKey) return
        runCatching {
            engine?.close()
        }
        engine = null
        session = null
        chat = null
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
        val loadable = LoadableModel(
            ModelLoadConfig(path = path, useMMap = true, useMLock = false, threads = threads),
            profile,
        )
        engine = LlamaEngine.create(loadable) { prog ->
            onProgress(prog)
            true
        }
        loadedPath = path
        loadedProfileKey = profileKey
        lastSystemPromptHash = null
    }

    private suspend fun ensureChat(systemPrompt: String) {
        val eng = engine ?: error("LLM not loaded")
        val h = systemPrompt.hashCode()
        if (chat != null && lastSystemPromptHash == h) return
        runCatching {
            session?.close()
        }
        session = null
        chat = null
        session = eng.createSession(
            SessionConfig(
                contextSize = 4096,
                overflowStrategy = OverflowStrategy.RollingWindow(500),
            ),
        )
        chat = session!!.createChatSession(systemPrompt) {
            emptyList()
        }
        lastSystemPromptHash = h
    }

    private fun transcriptToEvents(messages: List<ChatMessage>): List<ChatEvent> {
        val out = ArrayList<ChatEvent>(messages.size)
        for (m in messages) {
            when (m.speaker) {
                ChatSpeaker.User ->
                    out.add(ChatEvent.UserEvent(content = m.apiText ?: m.text, think = false))
                ChatSpeaker.Assistant ->
                    out.add(
                        ChatEvent.AssistantEvent(
                            parts = listOf(ChatEvent.AssistantEvent.Part.TextPart(m.text)),
                        ),
                    )
            }
        }
        return out
    }

    private fun partsToVisibleText(parts: List<ChatEvent.AssistantEvent.Part>): String {
        val sb = StringBuilder()
        for (part in parts) {
            when (part) {
                is ChatEvent.AssistantEvent.Part.TextPart -> sb.append(part.content)
                is ChatEvent.AssistantEvent.Part.ThinkingPart -> { }
                is ChatEvent.AssistantEvent.Part.ToolCallPart -> { }
            }
        }
        return sb.toString().trim()
    }

    suspend fun generateReply(
        context: Context,
        profileKey: String,
        profile: ModelProfile,
        systemPrompt: String,
        transcript: List<ChatMessage>,
        onProgress: (Float?) -> Unit = {},
        onPartial: suspend (String) -> Unit = {},
    ): Result<String> = withContext(exec) {
        runCatching {
            require(transcript.isNotEmpty()) { "empty transcript" }
            require(transcript.last().speaker == ChatSpeaker.User) { "last turn must be user" }
            ensureEngine(context, profileKey, profile, onProgress)
            ensureChat(systemPrompt)
            val c = chat ?: error("chat session missing")
            val history = transcriptToEvents(transcript.subList(0, transcript.size - 1))
            if (history.isNotEmpty()) {
                c.feedHistory(history)
            }
            val last = requireNotNull(transcript.last())
            val userContent = last.apiText ?: last.text
            var text = ""
            var lastEmitted = ""
            c.completion(ChatEvent.UserEvent(userContent, think = false)).collect { res ->
                when (res) {
                    is CompletionResult.Streaming -> {
                        text = partsToVisibleText(res.events)
                        if (text != lastEmitted) {
                            lastEmitted = text
                            onPartial(text)
                        }
                    }
                    is CompletionResult.Complete -> {
                        text = partsToVisibleText(res.events)
                        if (text != lastEmitted) {
                            lastEmitted = text
                            onPartial(text)
                        }
                    }
                    is CompletionResult.Error ->
                        throw res.error
                }
            }
            if (text.isBlank()) error("empty on-device reply")
            text
        }
    }
}
