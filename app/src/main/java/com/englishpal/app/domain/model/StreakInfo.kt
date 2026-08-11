package com.englishpal.app.domain.model

import java.time.LocalDate

/**
 * Domain entity representing User Streak state and activity history.
 */
data class StreakInfo(
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val lastActiveDate: String = "",
    val completedDates: List<String> = emptyList(),
    val isCompletedToday: Boolean = false
) {
    companion object {
        fun getTodayString(): String = LocalDate.now().toString()
        fun getYesterdayString(): String = LocalDate.now().minusDays(1).toString()
    }
}
