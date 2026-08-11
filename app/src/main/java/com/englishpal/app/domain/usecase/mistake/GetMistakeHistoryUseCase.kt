package com.englishpal.app.domain.usecase.mistake

import com.englishpal.app.domain.model.MistakeItem
import com.englishpal.app.domain.repository.MistakeRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * UseCase for fetching user mistake history from Firestore.
 */
class GetMistakeHistoryUseCase @Inject constructor(
    private val mistakeRepository: MistakeRepository
) {
    operator fun invoke(userId: String): Flow<List<MistakeItem>> {
        return mistakeRepository.getMistakes(userId)
    }
}
