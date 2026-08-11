package com.englishpal.app.presentation.conversation

import com.englishpal.app.domain.model.ChatMessage

/**
 * State object representing AI partner chat messages and sending status.
 */
data class ConversationUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isSending: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
