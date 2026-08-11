package com.englishpal.app.domain.repository

import com.englishpal.app.domain.model.Quiz
import com.englishpal.app.domain.model.QuizAttempt
import com.englishpal.app.domain.model.QuizEvaluationResult
import kotlinx.coroutines.flow.Flow

/**
 * Domain interface for Quiz fetching, random generation, and evaluation.
 */
interface QuizRepository {
    fun getQuizzes(): Flow<List<Quiz>>
    fun getRandomQuiz(questionCount: Int = 10): Flow<Quiz>
    suspend fun evaluateQuiz(
        quiz: Quiz,
        userAnswers: Map<String, Int>
    ): Result<QuizEvaluationResult>

    suspend fun saveQuizAttempt(userId: String, attempt: QuizAttempt): Result<Unit>
    fun getLastQuizAttempt(userId: String): Flow<QuizAttempt?>
}
