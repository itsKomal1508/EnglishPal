package com.englishpal.app.domain.usecase.quiz

import com.englishpal.app.domain.model.Quiz
import com.englishpal.app.domain.model.QuizAttempt
import com.englishpal.app.domain.model.QuizEvaluationResult
import com.englishpal.app.domain.repository.AuthRepository
import com.englishpal.app.domain.repository.MistakeRepository
import com.englishpal.app.domain.repository.QuizRepository
import com.englishpal.app.domain.usecase.streak.RecordDailyActivityUseCase
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

/**
 * UseCase for submitting completed quiz answers for evaluation,
 * logging mistakes, persisting quiz attempt history, and recording daily activity.
 */
class SubmitQuizAnswersUseCase @Inject constructor(
    private val quizRepository: QuizRepository,
    private val mistakeRepository: MistakeRepository,
    private val authRepository: AuthRepository,
    private val recordDailyActivityUseCase: RecordDailyActivityUseCase
) {
    suspend operator fun invoke(
        quiz: Quiz,
        userAnswers: Map<String, Int>
    ): Result<QuizEvaluationResult> {
        if (userAnswers.isEmpty()) {
            return Result.failure(IllegalArgumentException("Please answer at least one question"))
        }

        val evalResult = quizRepository.evaluateQuiz(quiz, userAnswers)

        evalResult.onSuccess { result ->
            val currentUser = authRepository.currentUser.firstOrNull()
            if (currentUser != null && currentUser.uid.isNotBlank()) {
                // 1. Record daily activity for streak tracking
                recordDailyActivityUseCase(currentUser.uid)

                // 2. Save mistakes if present
                if (result.mistakes.isNotEmpty()) {
                    mistakeRepository.saveMistakes(currentUser.uid, result.mistakes)
                }

                // 3. Save quiz attempt history to Firestore
                val attempt = QuizAttempt(
                    userId = currentUser.uid,
                    timestamp = System.currentTimeMillis(),
                    score = result.score,
                    correctCount = result.correctCount,
                    totalQuestions = result.totalQuestions,
                    category = quiz.category,
                    mistakesCount = result.mistakes.size
                )
                quizRepository.saveQuizAttempt(currentUser.uid, attempt)
            }
        }

        return evalResult
    }
}
