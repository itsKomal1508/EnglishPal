package com.englishpal.app.presentation.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.englishpal.app.domain.model.StreakInfo
import com.englishpal.app.domain.repository.AuthRepository
import com.englishpal.app.domain.usecase.quiz.GetLastQuizAttemptUseCase
import com.englishpal.app.domain.usecase.streak.GetStreakInfoUseCase
import com.englishpal.app.domain.usecase.streak.RecordDailyActivityUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val getStreakInfoUseCase: GetStreakInfoUseCase,
    private val recordDailyActivityUseCase: RecordDailyActivityUseCase,
    private val getLastQuizAttemptUseCase: GetLastQuizAttemptUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var streakJob: Job? = null
    private var lastQuizJob: Job? = null

    init {
        observeCurrentUserAndData()
    }

    private fun observeCurrentUserAndData() {
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                val currentUid = user?.uid ?: ""
                Log.d("HomeViewModel", "Active FirebaseAuth UID on Home load: '$currentUid'")
                streakJob?.cancel()
                lastQuizJob?.cancel()

                if (user == null || user.uid.isBlank()) {
                    _uiState.value = HomeUiState()
                } else {
                    _uiState.value = HomeUiState(userProfile = user, streakInfo = StreakInfo())

                    // 1. Observe current user's streak document
                    streakJob = viewModelScope.launch {
                        getStreakInfoUseCase(user.uid).collect { streak ->
                            _uiState.update { state ->
                                if (state.userProfile?.uid == currentUid) {
                                    state.copy(streakInfo = streak)
                                } else state
                            }
                        }
                    }

                    // 2. Observe current user's latest quiz attempt
                    lastQuizJob = viewModelScope.launch {
                        getLastQuizAttemptUseCase().collect { attempt ->
                            _uiState.update { state ->
                                if (state.userProfile?.uid == currentUid) {
                                    state.copy(lastQuizAttempt = attempt)
                                } else state
                            }
                        }
                    }
                }
            }
        }
    }

    fun recordActivity() {
        val user = _uiState.value.userProfile ?: return
        if (user.uid.isNotBlank()) {
            viewModelScope.launch {
                recordDailyActivityUseCase(user.uid)
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            streakJob?.cancel()
            lastQuizJob?.cancel()
            authRepository.signOut()
            _uiState.value = HomeUiState(isSignedOut = true)
        }
    }
}
