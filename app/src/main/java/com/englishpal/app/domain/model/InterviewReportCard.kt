package com.englishpal.app.domain.model

/**
 * Domain entity holding the final dual evaluation report card for the mock interview.
 */
data class InterviewReportCard(
    val technicalScore: Int = 0,
    val englishFluencyScore: Int = 0,
    val technicalFeedback: String = "",
    val englishFluencyFeedback: String = "",
    val strengths: List<String> = emptyList(),
    val areasForImprovement: List<String> = emptyList()
)
