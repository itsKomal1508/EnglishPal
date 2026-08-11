package com.englishpal.app.domain.repository

import com.englishpal.app.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow

/**
 * Domain repository interface for AI Conversation Partner chat.
 */
interface ChatRepository {
    fun getMessages(userId: String): Flow<List<ChatMessage>>
    suspend fun sendMessage(
        userId: String,
        history: List<ChatMessage>,
        userText: String
    ): Result<ChatMessage>
    suspend fun clearChat(userId: String): Result<Unit>
}
