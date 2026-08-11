package com.englishpal.app.domain.repository

import com.englishpal.app.domain.model.InterviewMessage
import com.englishpal.app.domain.model.InterviewReportCard
import com.englishpal.app.domain.model.InterviewStage

interface InterviewRepository {
    suspend fun startInterview(userId: String): Result<InterviewMessage>
    
    suspend fun processCandidateResponse(
        userId: String,
        currentStage: InterviewStage,
        history: List<InterviewMessage>,
        candidateText: String
    ): Result<Pair<InterviewMessage, InterviewStage>>

    suspend fun generateReportCard(
        userId: String,
        history: List<InterviewMessage>
    ): Result<InterviewReportCard>
}
