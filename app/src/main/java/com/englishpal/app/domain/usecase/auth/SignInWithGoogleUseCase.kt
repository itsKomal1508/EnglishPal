package com.englishpal.app.domain.usecase.auth

import com.englishpal.app.domain.model.UserProfile
import com.englishpal.app.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * UseCase for authenticating via Google Sign-In ID Token.
 */
class SignInWithGoogleUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(idToken: String): Result<UserProfile> {
        if (idToken.isBlank()) {
            return Result.failure(IllegalArgumentException("Invalid Google ID Token"))
        }
        return authRepository.signInWithGoogleCredential(idToken)
    }

    suspend fun signInWithProfile(email: String, name: String, photoUrl: String): Result<UserProfile> {
        return authRepository.signInWithGoogleProfile(email, name, photoUrl)
    }
}
