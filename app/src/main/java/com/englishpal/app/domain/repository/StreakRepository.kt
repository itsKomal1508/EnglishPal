package com.englishpal.app.domain.repository

import com.englishpal.app.domain.model.StreakInfo
import kotlinx.coroutines.flow.Flow

/**
 * Domain interface for Streak tracking and calendar history.
 */
interface StreakRepository {
    fun getStreakInfo(userId: String): Flow<StreakInfo>
    suspend fun recordDailyActivity(userId: String): Result<StreakInfo>
}
