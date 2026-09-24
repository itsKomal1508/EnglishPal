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
            val currentUser = authRepository.getOrAwaitUser()
            if (currentUser != null && currentUser.uid.isNotBlank()) {
                val userId = currentUser.uid
                // 1. Record daily activity for streak tracking
                val streakRes = recordDailyActivityUseCase(userId)
                streakRes.onFailure { err ->
                    android.util.Log.e("SubmitQuizAnswers", "Failed to update streak for user $userId", err)
                }

                // 2. Save mistakes if present with source = "quiz"
                if (result.mistakes.isNotEmpty()) {
                    val formattedMistakes = result.mistakes.map { m ->
                        m.copy(
                            source = "quiz",
                            questionTopic = m.category
                        )
                    }
                    val mistakeRes = mistakeRepository.saveMistakes(userId, formattedMistakes)
                    mistakeRes.onFailure { err ->
                        android.util.Log.e("SubmitQuizAnswers", "Failed to save ${formattedMistakes.size} mistakes to history", err)
                    }
                }

                // 3. Save quiz attempt history to Firestore
                val attempt = QuizAttempt(
                    userId = userId,
                    timestamp = System.currentTimeMillis(),
                    score = result.score,
                    correctCount = result.correctCount,
                    totalQuestions = result.totalQuestions,
                    category = quiz.category,
                    mistakesCount = result.mistakes.size
                )
                val attemptRes = quizRepository.saveQuizAttempt(userId, attempt)
                attemptRes.onFailure { err ->
                    android.util.Log.e("SubmitQuizAnswers", "Failed to save quiz attempt for user $userId", err)
                }
            } else {
                android.util.Log.w("SubmitQuizAnswers", "Skipping persistence: No active user profile returned")
            }
        }

        return evalResult
    }
}
