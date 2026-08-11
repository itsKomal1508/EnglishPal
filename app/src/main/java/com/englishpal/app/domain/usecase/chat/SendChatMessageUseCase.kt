package com.englishpal.app.domain.usecase.chat

import com.englishpal.app.domain.model.ChatMessage
import com.englishpal.app.domain.repository.ChatRepository
import javax.inject.Inject

/**
 * UseCase for sending user message to Cloud Function and receiving AI reply with inline corrections.
 */
class SendChatMessageUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(
        userId: String,
        history: List<ChatMessage>,
        text: String
    ): Result<ChatMessage> {
        if (text.isBlank()) {
            return Result.failure(IllegalArgumentException("Message cannot be blank"))
        }
        return chatRepository.sendMessage(userId, history, text.trim())
    }
}
