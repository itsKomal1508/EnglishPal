package com.englishpal.app.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.englishpal.app.domain.repository.AuthRepository
import com.englishpal.app.domain.usecase.auth.SignInWithEmailUseCase
import com.englishpal.app.domain.usecase.auth.SignInWithGoogleUseCase
import com.englishpal.app.domain.usecase.auth.SignUpWithEmailUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

import android.util.Log

/**
 * ViewModel managing Authentication state and business interactions.
 * Uses StateFlow to deliver lifecycle-aware UI updates to AuthScreen.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val signInWithEmailUseCase: SignInWithEmailUseCase,
    private val signUpWithEmailUseCase: SignUpWithEmailUseCase,
    private val signInWithGoogleUseCase: SignInWithGoogleUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun isUserLoggedIn(): Boolean = authRepository.isUserLoggedIn()

    fun onEmailChanged(email: String) {
        _uiState.update { it.copy(email = email, errorMessage = null) }
    }

    fun onPasswordChanged(password: String) {
        _uiState.update { it.copy(password = password, errorMessage = null) }
    }

    fun onDisplayNameChanged(name: String) {
        _uiState.update { it.copy(displayName = name, errorMessage = null) }
    }

    fun toggleAuthMode() {
        _uiState.update {
            it.copy(
                isSignUpMode = !it.isSignUpMode,
                errorMessage = null
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun resetAuthState() {
        Log.d("AuthFlow", "resetAuthState called: resetting AuthUiState")
        _uiState.value = AuthUiState()
    }

    fun setErrorMessage(message: String) {
        Log.e("AuthFlow", "Setting UI Auth Error Message: $message")
        _uiState.update { it.copy(isLoading = false, errorMessage = message) }
    }

    fun authenticateWithEmail() {
        val currentState = _uiState.value
        val email = currentState.email.trim()
        val password = currentState.password
        val name = currentState.displayName.trim()

        if (email.isBlank()) {
            setErrorMessage("Please enter an email address.")
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            setErrorMessage("Please enter a valid email address format (e.g. name@example.com).")
            return
        }

        if (password.isBlank()) {
            setErrorMessage("Please enter a password.")
            return
        }

        if (currentState.isSignUpMode) {
            if (name.isBlank()) {
                setErrorMessage("Please enter your full name for sign up.")
                return
            }
            if (password.length < 6) {
                setErrorMessage("Password must be at least 6 characters long.")
                return
            }
        }

        Log.d("AuthFlow", "authenticateWithEmail starting for email: '$email', isSignUpMode=${currentState.isSignUpMode}")
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            val result = if (currentState.isSignUpMode) {
                signUpWithEmailUseCase(
                    email = email,
                    pass = password,
                    name = name
                )
            } else {
                signInWithEmailUseCase(
                    email = email,
                    pass = password
                )
            }

            result.fold(
                onSuccess = { user ->
                    Log.d("AuthFlow", "Email authentication success for UID: ${user.uid}")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isSuccess = true,
                            userProfile = user
                        )
                    }
                },
                onFailure = { throwable ->
                    Log.e("AuthFlow", "Email authentication failure", throwable)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = throwable.localizedMessage ?: "Authentication failed"
                        )
                    }
                }
            )
        }
    }

    fun authenticateWithGoogle(idToken: String) {
        Log.d("AuthFlow", "authenticateWithGoogle called with idToken len=${idToken.length}")
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            val result = signInWithGoogleUseCase(idToken)
            result.fold(
                onSuccess = { user ->
                    Log.d("AuthFlow", "Google authentication success for UID: ${user.uid}")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isSuccess = true,
                            userProfile = user
                        )
                    }
                },
                onFailure = { throwable ->
                    Log.e("AuthFlow", "Google authentication failure", throwable)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = throwable.localizedMessage ?: "Google Sign-In failed"
                        )
                    }
                }
            )
        }
    }

    fun authenticateWithGoogleProfile(email: String, name: String, photoUrl: String) {
        Log.d("AuthFlow", "authenticateWithGoogleProfile called for email: $email, name: $name")
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            val result = signInWithGoogleUseCase.signInWithProfile(email, name, photoUrl)
            result.fold(
                onSuccess = { user ->
                    Log.d("AuthFlow", "Google profile authentication success for UID: ${user.uid}")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isSuccess = true,
                            userProfile = user
                        )
                    }
                },
                onFailure = { throwable ->
                    Log.e("AuthFlow", "Google profile authentication failure", throwable)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = throwable.localizedMessage ?: "Google Sign-In profile processing failed"
                        )
                    }
                }
            )
        }
    }
}
