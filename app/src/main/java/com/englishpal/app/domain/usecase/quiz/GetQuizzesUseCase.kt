package com.englishpal.app.domain.usecase.quiz

import com.englishpal.app.domain.model.Quiz
import com.englishpal.app.domain.repository.QuizRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * UseCase for fetching available grammar quizzes from Firestore.
 */
class GetQuizzesUseCase @Inject constructor(
    private val quizRepository: QuizRepository
) {
    operator fun invoke(): Flow<List<Quiz>> {
        return quizRepository.getQuizzes()
    }
}
