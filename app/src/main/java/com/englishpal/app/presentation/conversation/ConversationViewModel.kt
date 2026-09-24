package com.englishpal.app.presentation.conversation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.englishpal.app.domain.repository.AuthRepository
import com.englishpal.app.domain.repository.ChatRepository
import com.englishpal.app.domain.usecase.chat.GetConversationMessagesUseCase
import com.englishpal.app.domain.usecase.chat.SendChatMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConversationViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val getConversationMessagesUseCase: GetConversationMessagesUseCase,
    private val sendChatMessageUseCase: SendChatMessageUseCase,
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConversationUiState())
    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    private var currentUserId: String = ""

    init {
        observeMessages()
    }

    private fun observeMessages() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val initialUser = authRepository.getOrAwaitUser()
            if (initialUser != null && initialUser.uid.isNotBlank()) {
                currentUserId = initialUser.uid
                getConversationMessagesUseCase(initialUser.uid).collect { list ->
                    _uiState.update { state ->
                        // Preserve optimistic messages if Firestore stream has not caught up yet
                        val merged = if (state.isSending) {
                            (list + state.messages).distinctBy { it.id }
                        } else {
                            list
                        }
                        state.copy(
                            isLoading = false,
                            messages = merged
                        )
                    }
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onInputTextChanged(text: String) {
        _uiState.update { it.copy(inputText = text, errorMessage = null) }
    }

    fun sendMessage() {
        val textToSend = _uiState.value.inputText.trim()
        if (textToSend.isBlank()) {
            Log.w("GeminiConversation", "sendMessage ignored: input text is blank")
            return
        }

        viewModelScope.launch {
            val user = if (currentUserId.isNotBlank()) {
                com.englishpal.app.domain.model.UserProfile(uid = currentUserId, email = "", displayName = "")
            } else {
                authRepository.getOrAwaitUser()
            }

            val userId = user?.uid ?: ""
            if (userId.isBlank()) {
                Log.e("GeminiConversation", "sendMessage failed: could not resolve active userId")
                _uiState.update { it.copy(errorMessage = "User session invalid. Please check connection.") }
                return@launch
            }
            currentUserId = userId

            Log.d("GeminiConversation", "1. Optimistic UI: Appending user message '$textToSend' for userId '$userId'")
            val userMsg = com.englishpal.app.domain.model.ChatMessage(
                id = java.util.UUID.randomUUID().toString(),
                sender = "user",
                text = textToSend,
                timestamp = System.currentTimeMillis()
            )

            val history = _uiState.value.messages
            val updatedMessages = history + userMsg

            _uiState.update {
                it.copy(
                    inputText = "",
                    isSending = true,
                    errorMessage = null,
                    messages = updatedMessages
                )
            }

            val result = sendChatMessageUseCase(userId, history, textToSend)
            result.fold(
                onSuccess = { aiMsg ->
                    Log.d("GeminiConversation", "4. Message send completed. AI Message ID: ${aiMsg.id}")
                    _uiState.update { state ->
                        val currentList = state.messages
                        val finalMessages = if (currentList.any { it.id == aiMsg.id }) {
                            currentList
                        } else {
                            currentList + aiMsg
                        }
                        state.copy(
                            isSending = false,
                            messages = finalMessages
                        )
                    }
                },
                onFailure = { err ->
                    val rawMsg = err.localizedMessage.takeIf { !it.isNullOrBlank() }
                        ?: err.message.takeIf { !it.isNullOrBlank() }
                        ?: "Failed to get AI response."
                    Log.e("GeminiConversation", "4. Message send FAILED: $rawMsg", err)

                    val friendlyMsg = formatUserFriendlyError(rawMsg)
                    _uiState.update { state ->
                        state.copy(
                            isSending = false,
                            errorMessage = "Message failed to send — $friendlyMsg"
                        )
                    }
                }
            )
        }
    }

    private fun formatUserFriendlyError(rawError: String): String {
        val lower = rawError.lowercase()
        return when {
            lower.contains("quota exceeded") || lower.contains("rate limit") || lower.contains("429") || lower.contains("resource_exhausted") -> {
                val match = Regex("""retry in ([\d\.]+)s""", RegexOption.IGNORE_CASE).find(rawError)
                if (match != null) {
                    val seconds = match.groupValues[1].toDoubleOrNull()?.toInt() ?: 30
                    "AI rate limit reached. Please retry in $seconds seconds."
                } else {
                    "AI rate limit reached. Please wait a few seconds and try again."
                }
            }
            lower.contains("404") || lower.contains("not found") -> {
                "AI Model endpoint error (404). Model name updated to gemini-3.6-flash."
            }
            lower.contains("api key not valid") || lower.contains("api_key_invalid") || lower.contains("invalid api key") -> {
                "Invalid Gemini API Key. Please update GEMINI_API_KEY in local.properties."
            }
            lower.contains("unavailable") || lower.contains("connect") || lower.contains("timeout") -> {
                "Network issue. Please check your internet connection and try again."
            }
            else -> rawError.take(150)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearChatHistory() {
        if (currentUserId.isNotBlank()) {
            viewModelScope.launch {
                chatRepository.clearChat(currentUserId)
            }
        }
    }
}
