package com.englishpal.app.domain.usecase.streak

import com.englishpal.app.domain.model.StreakInfo
import com.englishpal.app.domain.repository.StreakRepository
import javax.inject.Inject

/**
 * UseCase for recording daily app usage / quiz completion activity.
 */
class RecordDailyActivityUseCase @Inject constructor(
    private val streakRepository: StreakRepository
) {
    suspend operator fun invoke(userId: String): Result<StreakInfo> {
        if (userId.isBlank()) {
            return Result.failure(IllegalArgumentException("User ID must not be empty"))
        }
        return streakRepository.recordDailyActivity(userId)
    }
}
