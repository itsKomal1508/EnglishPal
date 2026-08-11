package com.englishpal.app.domain.usecase.streak

import com.englishpal.app.domain.model.StreakInfo
import com.englishpal.app.domain.repository.StreakRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * UseCase for observing live streak information and completed practice dates.
 */
class GetStreakInfoUseCase @Inject constructor(
    private val streakRepository: StreakRepository
) {
    operator fun invoke(userId: String): Flow<StreakInfo> {
        return streakRepository.getStreakInfo(userId)
    }
}
