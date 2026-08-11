package com.englishpal.app.domain.model

/**
 * Domain representation of an MCQ Quiz Question.
 */
data class QuizQuestion(
    val id: String = "",
    val questionText: String = "",
    val options: List<String> = emptyList(),
    val correctAnswerIndex: Int = 0,
    val category: String = "Grammar" // e.g. Tense, Prepositions, Articles, Subject-Verb
)
