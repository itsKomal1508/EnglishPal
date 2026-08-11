package com.englishpal.app.presentation.interview

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.englishpal.app.domain.model.InterviewMessage
import com.englishpal.app.domain.model.InterviewStage
import com.englishpal.app.domain.repository.AuthRepository
import com.englishpal.app.domain.repository.InterviewRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InterviewViewModel @Inject constructor(
    private val interviewRepository: InterviewRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(InterviewUiState())
    val uiState: StateFlow<InterviewUiState> = _uiState.asStateFlow()

    init {
        startNewInterview()
    }

    fun startNewInterview() {
        _uiState.update {
            it.copy(
                messages = emptyList(),
                currentStage = InterviewStage.INTRO,
                inputText = "",
                isLoading = true,
                isSending = false,
                isEvaluatingReport = false,
                reportCard = null,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            try {
                val userId = authRepository.currentUser.firstOrNull()?.uid ?: ""
                val result = interviewRepository.startInterview(userId)
                result.fold(
                    onSuccess = { initialMsg ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                messages = listOf(initialMsg)
                            )
                        }
                    },
                    onFailure = { err ->
                        Log.e("InterviewViewModel", "Failed to start interview", err)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = err.localizedMessage ?: "Failed to start interview session"
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e("InterviewViewModel", "Exception starting interview", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.localizedMessage ?: "An error occurred starting the interview."
                    )
                }
            }
        }
    }

    fun onInputTextChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendAnswer() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank() || _uiState.value.isSending || _uiState.value.isEvaluatingReport) return

        val candidateMsg = InterviewMessage(
            id = java.util.UUID.randomUUID().toString(),
            sender = "candidate",
            text = text,
            timestamp = System.currentTimeMillis()
        )

        val updatedMessages = _uiState.value.messages + candidateMsg
        val currentStage = _uiState.value.currentStage

        _uiState.update {
            it.copy(
                messages = updatedMessages,
                inputText = "",
                isSending = true,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            try {
                val userId = authRepository.currentUser.firstOrNull()?.uid ?: ""
                val result = interviewRepository.processCandidateResponse(
                    userId = userId,
                    currentStage = currentStage,
                    history = updatedMessages,
                    candidateText = text
                )

                result.fold(
                    onSuccess = { (interviewerMsg, nextStage) ->
                        val newMessages = updatedMessages + interviewerMsg
                        _uiState.update {
                            it.copy(
                                isSending = false,
                                messages = newMessages,
                                currentStage = nextStage
                            )
                        }

                        // If interview reached completed stage, trigger report evaluation
                        if (nextStage == InterviewStage.COMPLETED) {
                            generateReportCardInternal(userId, newMessages)
                        }
                    },
                    onFailure = { err ->
                        Log.e("InterviewViewModel", "Failed to process candidate response", err)
                        _uiState.update {
                            it.copy(
                                isSending = false,
                                errorMessage = err.localizedMessage ?: "Failed to send response"
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e("InterviewViewModel", "Exception sending candidate response", e)
                _uiState.update {
                    it.copy(
                        isSending = false,
                        errorMessage = e.localizedMessage ?: "An error occurred while sending."
                    )
                }
            }
        }
    }

    fun finishAndGenerateReport() {
        if (_uiState.value.isEvaluatingReport) return
        viewModelScope.launch {
            val userId = authRepository.currentUser.firstOrNull()?.uid ?: ""
            generateReportCardInternal(userId, _uiState.value.messages)
        }
    }

    private suspend fun generateReportCardInternal(userId: String, history: List<InterviewMessage>) {
        _uiState.update { it.copy(isEvaluatingReport = true, errorMessage = null) }
        val result = interviewRepository.generateReportCard(userId, history)
        result.fold(
            onSuccess = { report ->
                _uiState.update {
                    it.copy(
                        isEvaluatingReport = false,
                        currentStage = InterviewStage.COMPLETED,
                        reportCard = report
                    )
                }
            },
            onFailure = { err ->
                Log.e("InterviewViewModel", "Failed to generate report card", err)
                _uiState.update {
                    it.copy(
                        isEvaluatingReport = false,
                        errorMessage = err.localizedMessage ?: "Failed to generate interview report card"
                    )
                }
            }
        )
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
