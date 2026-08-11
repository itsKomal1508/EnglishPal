package com.englishpal.app.domain.usecase.auth

import com.englishpal.app.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * UseCase for signing out the user.
 */
class SignOutUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke() {
        authRepository.signOut()
    }
}
