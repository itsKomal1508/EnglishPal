package com.englishpal.app.domain.model

/**
 * Domain representation of a chat message in the AI conversation stream.
 */
data class ChatMessage(
    val id: String = "",
    val sender: String = "user", // "user" or "ai"
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val correction: InlineCorrection? = null
)
