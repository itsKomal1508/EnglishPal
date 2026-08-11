package com.englishpal.app.domain.model

/**
 * Domain representation of a complete MCQ Quiz.
 */
data class Quiz(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val category: String = "General",
    val questions: List<QuizQuestion> = emptyList()
)
