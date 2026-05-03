package dev.tabml.box

enum class ChatSpeaker {
    User,
    Assistant,
}

/**
 * [text] is shown in the UI. For user turns, [apiText] is sent to the model when non-null.
 */
data class ChatMessage(
    val speaker: ChatSpeaker,
    val text: String,
    val apiText: String? = null,
)
