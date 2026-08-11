package com.englishpal.app.domain.usecase.quiz

import com.englishpal.app.domain.model.Quiz
import com.englishpal.app.domain.repository.QuizRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * UseCase for generating a dynamic quiz with 10 random questions from the 45+ question bank.
 */
class GetRandomQuizUseCase @Inject constructor(
    private val quizRepository: QuizRepository
) {
    operator fun invoke(questionCount: Int = 10): Flow<Quiz> {
        return quizRepository.getRandomQuiz(questionCount)
    }
}
