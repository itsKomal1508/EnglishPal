package com.englishpal.app.domain.model

/**
 * Domain entity holding the complete evaluation result returned from Gemini Cloud Function.
 */
data class QuizEvaluationResult(
    val score: Int = 0,
    val totalQuestions: Int = 0,
    val correctCount: Int = 0,
    val mistakes: List<GrammarMistakeDetail> = emptyList(),
    val generalFeedback: String = ""
)
