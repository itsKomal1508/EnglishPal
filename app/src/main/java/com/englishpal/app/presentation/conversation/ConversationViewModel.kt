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
            authRepository.currentUser.collect { user ->
                if (user != null && user.uid.isNotBlank()) {
                    currentUserId = user.uid
                    getConversationMessagesUseCase(user.uid).collect { list ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                messages = list
                            )
                        }
                    }
                }
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
        if (currentUserId.isBlank()) {
            Log.e("GeminiConversation", "sendMessage failed: currentUserId is blank")
            _uiState.update { it.copy(errorMessage = "User session invalid. Please log in again.") }
            return
        }

        Log.d("GeminiConversation", "1. User initiated send message: '$textToSend', userId: '$currentUserId'")
        val history = _uiState.value.messages
        _uiState.update { it.copy(inputText = "", isSending = true, errorMessage = null) }

        viewModelScope.launch {
            val result = sendChatMessageUseCase(currentUserId, history, textToSend)
            result.fold(
                onSuccess = { aiMsg ->
                    Log.d("GeminiConversation", "4. Message send completed successfully. AI Message ID: ${aiMsg.id}")
                    _uiState.update { it.copy(isSending = false) }
                },
                onFailure = { err ->
                    val rawMsg = err.localizedMessage.takeIf { !it.isNullOrBlank() }
                        ?: err.message.takeIf { !it.isNullOrBlank() }
                        ?: "Failed to get AI response."
                    Log.e("GeminiConversation", "4. Message send FAILED (raw error): $rawMsg", err)

                    val friendlyMsg = formatUserFriendlyError(rawMsg)
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            errorMessage = friendlyMsg
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
                "AI Model endpoint error (404). Model name updated to gemini-1.5-flash-latest."
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
