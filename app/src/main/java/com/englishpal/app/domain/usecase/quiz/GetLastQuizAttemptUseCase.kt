package com.englishpal.app.domain.usecase.quiz

import com.englishpal.app.domain.model.QuizAttempt
import com.englishpal.app.domain.repository.AuthRepository
import com.englishpal.app.domain.repository.QuizRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/**
 * UseCase for retrieving the user's most recent quiz attempt from Firestore.
 */
class GetLastQuizAttemptUseCase @Inject constructor(
    private val quizRepository: QuizRepository,
    private val authRepository: AuthRepository
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<QuizAttempt?> {
        return authRepository.currentUser.flatMapLatest { user ->
            if (user != null && user.uid.isNotBlank()) {
                quizRepository.getLastQuizAttempt(user.uid)
            } else {
                flowOf(null)
            }
        }
    }
}
