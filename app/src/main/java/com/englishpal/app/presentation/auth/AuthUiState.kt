package com.englishpal.app.presentation.auth

import com.englishpal.app.domain.model.UserProfile

/**
 * Immutable State Representation for Authentication UI.
 * Follows the UiState pattern for Jetpack Compose reactivity.
 */
data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val displayName: String = "",
    val isSignUpMode: Boolean = false,
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val userProfile: UserProfile? = null,
    val errorMessage: String? = null
)
