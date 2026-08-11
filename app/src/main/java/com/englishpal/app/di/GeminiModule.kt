package com.englishpal.app.di

import com.englishpal.app.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt Module providing the Google Gemini [GenerativeModel] as a Singleton.
 * The API key is read from BuildConfig, which sources it from local.properties at build time.
 * This replaces the Firebase Cloud Functions transport for AI-powered features.
 */
@Module
@InstallIn(SingletonComponent::class)
object GeminiModule {

    @Provides
    @Singleton
    fun provideGenerativeModel(): GenerativeModel = GenerativeModel(
        modelName = "gemini-pro",
        apiKey = BuildConfig.GEMINI_API_KEY
    )
}
