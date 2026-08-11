package com.englishpal.app.domain.usecase.chat

import com.englishpal.app.domain.model.ChatMessage
import com.englishpal.app.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * UseCase for observing AI partner conversation messages stream.
 */
class GetConversationMessagesUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    operator fun invoke(userId: String): Flow<List<ChatMessage>> {
        return chatRepository.getMessages(userId)
    }
}
