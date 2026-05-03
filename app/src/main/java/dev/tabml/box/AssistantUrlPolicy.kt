package dev.tabml.box

/**
 * Cali expects an OpenAI-compatible `POST …/v1/chat/completions` server — i.e. the **custom LLM**
 * stack you run (Ollama, vLLM, LiteLLM, your own gateway, etc.). This policy only constrains
 * which URL schemes are accepted.
 */
object AssistantUrlPolicy {

    fun isValidBaseUrl(trimmed: String): Boolean {
        if (trimmed.isEmpty()) return false
        if (trimmed.startsWith("https://")) return true
        return BuildConfig.DEBUG && trimmed.startsWith("http://")
    }
}
