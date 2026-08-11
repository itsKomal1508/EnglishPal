package com.englishpal.app.domain.model

/**
 * Represents a completed quiz attempt by a user.
 */
data class QuizAttempt(
    val id: String = "",
    val userId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val score: Int = 0,
    val correctCount: Int = 0,
    val totalQuestions: Int = 10,
    val category: String = "Mixed Grammar",
    val mistakesCount: Int = 0
)
