package com.englishpal.app.presentation.quiz

import com.englishpal.app.domain.model.Quiz
import com.englishpal.app.domain.model.QuizAttempt
import com.englishpal.app.domain.model.QuizEvaluationResult

/**
 * State object representing Quiz selection, question progression, option choices, last attempt, and feedback.
 */
data class QuizUiState(
    val quizzes: List<Quiz> = emptyList(),
    val selectedQuiz: Quiz? = null,
    val lastQuizAttempt: QuizAttempt? = null,
    val currentQuestionIndex: Int = 0,
    val userAnswers: Map<String, Int> = emptyMap(), // questionId -> selectedOptionIndex
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val isSubmitted: Boolean = false,
    val evaluationResult: QuizEvaluationResult? = null,
    val errorMessage: String? = null
)
