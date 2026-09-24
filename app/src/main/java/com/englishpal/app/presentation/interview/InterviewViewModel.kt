package com.englishpal.app.presentation.interview

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.englishpal.app.data.datasource.GeminiErrorParser
import com.englishpal.app.domain.model.InterviewMessage
import com.englishpal.app.domain.model.InterviewStage
import com.englishpal.app.domain.repository.AuthRepository
import com.englishpal.app.domain.repository.InterviewRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private var interviewJob: Job? = null
    private var reportJob: Job? = null

    init {
        startNewInterview()
    }

    fun startNewInterview() {
        if (_uiState.value.isLoading || _uiState.value.isSending || _uiState.value.isEvaluatingReport) return

        interviewJob?.cancel()
        reportJob?.cancel()

        _uiState.update {
            it.copy(
                messages = emptyList(),
                currentStage = InterviewStage.INTRO,
                inputText = "",
                isLoading = true,
                isSending = false,
                isEvaluatingReport = false,
                reportCard = null,
                errorMessage = null,
                lastFailedAction = null,
                lastFailedInputText = null,
                isRetryable = true,
                errorType = null
            )
        }

        interviewJob = viewModelScope.launch {
            try {
                val userId = authRepository.getOrAwaitUser()?.uid ?: ""
                val result = interviewRepository.startInterview(userId)
                result.fold(
                    onSuccess = { initialMsg ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = null,
                                lastFailedAction = null,
                                messages = listOf(initialMsg)
                            )
                        }
                    },
                    onFailure = { err ->
                        Log.e("InterviewViewModel", "Failed to start interview", err)
                        val parsed = GeminiErrorParser.parseException(err)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = parsed.userMessage,
                                lastFailedAction = InterviewFailedAction.START_INTERVIEW,
                                isRetryable = parsed.isRetryable,
                                errorType = parsed.type
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e("InterviewViewModel", "Exception starting interview", e)
                val parsed = GeminiErrorParser.parseException(e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = parsed.userMessage,
                        lastFailedAction = InterviewFailedAction.START_INTERVIEW,
                        isRetryable = parsed.isRetryable,
                        errorType = parsed.type
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
        if (text.isBlank() || _uiState.value.isSending || _uiState.value.isLoading || _uiState.value.isEvaluatingReport) return

        val currentMessages = _uiState.value.messages
        val lastMsg = currentMessages.lastOrNull()

        val (updatedMessages, candidateText) = if (lastMsg != null && lastMsg.sender == "candidate" && lastMsg.text == text) {
            Pair(currentMessages, text)
        } else {
            val candidateMsg = InterviewMessage(
                id = java.util.UUID.randomUUID().toString(),
                sender = "candidate",
                text = text,
                timestamp = System.currentTimeMillis()
            )
            Pair(currentMessages + candidateMsg, text)
        }

        val currentStage = _uiState.value.currentStage

        _uiState.update {
            it.copy(
                messages = updatedMessages,
                inputText = "",
                isSending = true,
                errorMessage = null,
                lastFailedAction = null,
                lastFailedInputText = candidateText
            )
        }

        executeSendAnswer(updatedMessages, currentStage, candidateText)
    }

    private fun executeSendAnswer(
        historyMessages: List<InterviewMessage>,
        currentStage: InterviewStage,
        candidateText: String
    ) {
        if (interviewJob?.isActive == true) return

        interviewJob = viewModelScope.launch {
            try {
                val userId = authRepository.getOrAwaitUser()?.uid ?: ""
                val result = interviewRepository.processCandidateResponse(
                    userId = userId,
                    currentStage = currentStage,
                    history = historyMessages,
                    candidateText = candidateText
                )

                result.fold(
                    onSuccess = { (interviewerMsg, nextStage) ->
                        val newMessages = historyMessages + interviewerMsg
                        _uiState.update {
                            it.copy(
                                isSending = false,
                                errorMessage = null,
                                messages = newMessages,
                                currentStage = nextStage,
                                lastFailedAction = null,
                                lastFailedInputText = null
                            )
                        }

                        if (nextStage == InterviewStage.COMPLETED) {
                            generateReportCardInternal(userId, newMessages)
                        }
                    },
                    onFailure = { err ->
                        Log.e("InterviewViewModel", "Failed to process candidate response", err)
                        val parsed = GeminiErrorParser.parseException(err)
                        _uiState.update {
                            it.copy(
                                isSending = false,
                                errorMessage = parsed.userMessage,
                                lastFailedAction = InterviewFailedAction.SEND_ANSWER,
                                lastFailedInputText = candidateText,
                                isRetryable = parsed.isRetryable,
                                errorType = parsed.type
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e("InterviewViewModel", "Exception sending candidate response", e)
                val parsed = GeminiErrorParser.parseException(e)
                _uiState.update {
                    it.copy(
                        isSending = false,
                        errorMessage = parsed.userMessage,
                        lastFailedAction = InterviewFailedAction.SEND_ANSWER,
                        lastFailedInputText = candidateText,
                        isRetryable = parsed.isRetryable,
                        errorType = parsed.type
                    )
                }
            }
        }
    }

    fun retryLastAction() {
        val failedAction = _uiState.value.lastFailedAction ?: return
        if (_uiState.value.isSending || _uiState.value.isLoading || _uiState.value.isEvaluatingReport) return

        _uiState.update { it.copy(errorMessage = null) }

        when (failedAction) {
            InterviewFailedAction.START_INTERVIEW -> {
                startNewInterview()
            }
            InterviewFailedAction.SEND_ANSWER -> {
                val lastText = _uiState.value.lastFailedInputText ?: return
                _uiState.update { it.copy(isSending = true) }
                // Re-use current state messages (which already contains candidateMsg) to prevent duplicate insertion
                executeSendAnswer(_uiState.value.messages, _uiState.value.currentStage, lastText)
            }
            InterviewFailedAction.GENERATE_REPORT -> {
                finishAndGenerateReport()
            }
        }
    }

    fun finishAndGenerateReport() {
        if (_uiState.value.isEvaluatingReport || reportJob?.isActive == true) return
        viewModelScope.launch {
            val userId = authRepository.getOrAwaitUser()?.uid ?: ""
            generateReportCardInternal(userId, _uiState.value.messages)
        }
    }

    private fun generateReportCardInternal(userId: String, history: List<InterviewMessage>) {
        if (reportJob?.isActive == true) return
        _uiState.update { it.copy(isEvaluatingReport = true, errorMessage = null, lastFailedAction = null) }

        reportJob = viewModelScope.launch {
            val result = interviewRepository.generateReportCard(userId, history)
            result.fold(
                onSuccess = { report ->
                    _uiState.update {
                        it.copy(
                            isEvaluatingReport = false,
                            errorMessage = null,
                            currentStage = InterviewStage.COMPLETED,
                            reportCard = report,
                            lastFailedAction = null
                        )
                    }
                },
                onFailure = { err ->
                    Log.e("InterviewViewModel", "Failed to generate report card", err)
                    val parsed = GeminiErrorParser.parseException(err)
                    _uiState.update {
                        it.copy(
                            isEvaluatingReport = false,
                            errorMessage = parsed.userMessage,
                            lastFailedAction = InterviewFailedAction.GENERATE_REPORT,
                            isRetryable = parsed.isRetryable,
                            errorType = parsed.type
                        )
                    }
                }
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
