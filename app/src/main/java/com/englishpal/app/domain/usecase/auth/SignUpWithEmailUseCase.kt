package com.englishpal.app.domain.usecase.auth

import com.englishpal.app.domain.model.UserProfile
import com.englishpal.app.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * UseCase for registering a new user with Email, Password, and Display Name.
 */
class SignUpWithEmailUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(email: String, pass: String, name: String): Result<UserProfile> {
        if (email.isBlank()) {
            return Result.failure(IllegalArgumentException("Email cannot be empty"))
        }
        if (pass.length < 6) {
            return Result.failure(IllegalArgumentException("Password must be at least 6 characters"))
        }
        if (name.isBlank()) {
            return Result.failure(IllegalArgumentException("Name cannot be empty"))
        }
        return authRepository.signUpWithEmail(email.trim(), pass, name.trim())
    }
}
