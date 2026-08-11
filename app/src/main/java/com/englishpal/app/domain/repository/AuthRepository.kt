package com.englishpal.app.domain.repository

import com.englishpal.app.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

/**
 * Domain interface defining Authentication operations.
 * Abstraction layer separating business logic from Firebase SDK.
 */
interface AuthRepository {
    val currentUser: Flow<UserProfile?>
    fun isUserLoggedIn(): Boolean
    suspend fun signInWithEmail(email: String, pass: String): Result<UserProfile>
    suspend fun signUpWithEmail(email: String, pass: String, name: String): Result<UserProfile>
    suspend fun signInWithGoogleCredential(idToken: String): Result<UserProfile>
    suspend fun signInWithGoogleProfile(email: String, displayName: String, photoUrl: String): Result<UserProfile>
    suspend fun signOut()
}
