package com.englishpal.app.domain.model

/**
 * Domain entity representing a detailed grammar mistake identified by Gemini.
 */
data class GrammarMistakeDetail(
    val questionId: String = "",
    val category: String = "Grammar",
    val userAnswer: String = "",
    val correctAnswer: String = "",
    val originalSentence: String = "",
    val correctedSentence: String = "",
    val explanation: String = ""
)
