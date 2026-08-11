package com.englishpal.app.presentation.interview

import com.englishpal.app.domain.model.InterviewMessage
import com.englishpal.app.domain.model.InterviewReportCard
import com.englishpal.app.domain.model.InterviewStage

data class InterviewUiState(
    val messages: List<InterviewMessage> = emptyList(),
    val currentStage: InterviewStage = InterviewStage.INTRO,
    val inputText: String = "",
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val isEvaluatingReport: Boolean = false,
    val reportCard: InterviewReportCard? = null,
    val errorMessage: String? = null
)
