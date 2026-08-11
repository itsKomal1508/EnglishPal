package com.englishpal.app.domain.usecase.auth

import com.englishpal.app.domain.model.UserProfile
import com.englishpal.app.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * UseCase for signing in an existing user with Email and Password.
 */
class SignInWithEmailUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(email: String, pass: String): Result<UserProfile> {
        if (email.isBlank() || pass.isBlank()) {
            return Result.failure(IllegalArgumentException("Email and password must not be empty"))
        }
        return authRepository.signInWithEmail(email.trim(), pass)
    }
}
