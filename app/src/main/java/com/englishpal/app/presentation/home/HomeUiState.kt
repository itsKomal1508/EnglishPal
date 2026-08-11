package com.englishpal.app.presentation.home

import com.englishpal.app.domain.model.QuizAttempt
import com.englishpal.app.domain.model.StreakInfo
import com.englishpal.app.domain.model.UserProfile

/**
 * State representation for Home Screen including User Profile, Streak Data & Last Quiz Attempt.
 */
data class HomeUiState(
    val userProfile: UserProfile? = null,
    val streakInfo: StreakInfo = StreakInfo(),
    val lastQuizAttempt: QuizAttempt? = null,
    val isLoading: Boolean = false,
    val isSignedOut: Boolean = false
)
