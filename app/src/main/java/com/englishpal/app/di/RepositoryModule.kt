package com.englishpal.app.di

import com.englishpal.app.data.repository.AuthRepositoryImpl
import com.englishpal.app.data.repository.ChatRepositoryImpl
import com.englishpal.app.data.repository.MistakeRepositoryImpl
import com.englishpal.app.data.repository.QuizRepositoryImpl
import com.englishpal.app.data.repository.StreakRepositoryImpl
import com.englishpal.app.domain.repository.AuthRepository
import com.englishpal.app.domain.repository.ChatRepository
import com.englishpal.app.domain.repository.MistakeRepository
import com.englishpal.app.domain.repository.QuizRepository
import com.englishpal.app.domain.repository.StreakRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

import com.englishpal.app.data.repository.InterviewRepositoryImpl
import com.englishpal.app.domain.repository.InterviewRepository

/**
 * Hilt Module for binding repository implementations to their domain interfaces.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        authRepositoryImpl: AuthRepositoryImpl
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindQuizRepository(
        quizRepositoryImpl: QuizRepositoryImpl
    ): QuizRepository

    @Binds
    @Singleton
    abstract fun bindMistakeRepository(
        mistakeRepositoryImpl: MistakeRepositoryImpl
    ): MistakeRepository

    @Binds
    @Singleton
    abstract fun bindStreakRepository(
        streakRepositoryImpl: StreakRepositoryImpl
    ): StreakRepository

    @Binds
    @Singleton
    abstract fun bindChatRepository(
        chatRepositoryImpl: ChatRepositoryImpl
    ): ChatRepository

    @Binds
    @Singleton
    abstract fun bindInterviewRepository(
        interviewRepositoryImpl: InterviewRepositoryImpl
    ): InterviewRepository
}
