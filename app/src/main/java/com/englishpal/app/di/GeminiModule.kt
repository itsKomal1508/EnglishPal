package com.englishpal.app.di

import android.util.Log
import com.englishpal.app.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thread-safe API Key rotation provider.
 * Automatically switches between multiple configured free Gemini API keys
 * when a rate-limit (HTTP 429) occurs.
 */
@Singleton
class GeminiModelProvider @Inject constructor() {

    private val apiKeys: List<String> = BuildConfig.GEMINI_API_KEY
        .split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() && it != "DUMMY_GEMINI_API_KEY" }
        .distinct()
        .ifEmpty { listOf(BuildConfig.GEMINI_API_KEY.ifBlank { "DUMMY_GEMINI_API_KEY" }) }

    private val currentKeyIndex = AtomicInteger(0)

    val keyCount: Int get() = apiKeys.size

    fun getModel(forceRotate: Boolean = false): GenerativeModel {
        var index = currentKeyIndex.get()
        if (forceRotate && apiKeys.size > 1) {
            index = currentKeyIndex.incrementAndGet()
            Log.i("GeminiModelProvider", "🔄 Rotated Gemini API Key to key #${(Math.abs(index) % apiKeys.size) + 1} of ${apiKeys.size}")
        }
        val selectedKey = apiKeys[Math.abs(index) % apiKeys.size]
        return GenerativeModel(
            modelName = GeminiModule.GEMINI_MODEL_NAME,
            apiKey = selectedKey
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
object GeminiModule {

    const val GEMINI_MODEL_NAME = "gemini-3.6-flash"

    @Provides
    @Singleton
    fun provideGenerativeModel(provider: GeminiModelProvider): GenerativeModel {
        return provider.getModel(forceRotate = false)
    }
}
