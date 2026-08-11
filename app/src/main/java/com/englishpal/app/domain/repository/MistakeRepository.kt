package com.englishpal.app.domain.repository

import com.englishpal.app.domain.model.GrammarMistakeDetail
import com.englishpal.app.domain.model.MistakeItem
import kotlinx.coroutines.flow.Flow

/**
 * Domain repository interface for Mistake History & Weak-Area analytics.
 */
interface MistakeRepository {
    fun getMistakes(userId: String): Flow<List<MistakeItem>>
    suspend fun saveMistakes(userId: String, mistakes: List<GrammarMistakeDetail>): Result<Unit>
    suspend fun clearMistakes(userId: String): Result<Unit>
}
