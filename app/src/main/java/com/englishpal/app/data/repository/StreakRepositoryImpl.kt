package com.englishpal.app.data.repository

import com.englishpal.app.domain.model.StreakInfo
import com.englishpal.app.domain.repository.StreakRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreakRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore
) : StreakRepository {

    override fun getStreakInfo(userId: String): Flow<StreakInfo> = callbackFlow {
        if (userId.isBlank()) {
            trySend(StreakInfo())
            close()
            return@callbackFlow
        }

        val listener = firestore.collection("users")
            .document(userId)
            .collection("streak")
            .document("info")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                if (snapshot == null || !snapshot.exists()) {
                    trySend(StreakInfo())
                } else {
                    val lastActiveDate = snapshot.getString("lastActiveDate") ?: ""
                    val rawCurrentStreak = (snapshot.getLong("currentStreak") ?: 0L).toInt()
                    val longestStreak = (snapshot.getLong("longestStreak") ?: 0L).toInt()
                    val completedDates = snapshot.get("completedDates") as? List<String> ?: emptyList()

                    val today = getTodayDateString()
                    val yesterday = getYesterdayDateString()

                    val isCompletedToday = lastActiveDate == today
                    // Handle streak broken logic
                    val currentStreak = when (lastActiveDate) {
                        today, yesterday -> rawCurrentStreak
                        else -> 0
                    }

                    trySend(
                        StreakInfo(
                            currentStreak = currentStreak,
                            longestStreak = longestStreak,
                            lastActiveDate = lastActiveDate,
                            completedDates = completedDates,
                            isCompletedToday = isCompletedToday
                        )
                    )
                }
            }

        awaitClose { listener.remove() }
    }

    override suspend fun recordDailyActivity(userId: String): Result<StreakInfo> {
        if (userId.isBlank()) return Result.failure(IllegalArgumentException("User ID invalid"))

        return try {
            val docRef = firestore.collection("users")
                .document(userId)
                .collection("streak")
                .document("info")

            val snapshot = docRef.get().await()
            val today = getTodayDateString()
            val yesterday = getYesterdayDateString()

            val storedLastActiveDate = snapshot.getString("lastActiveDate") ?: ""
            val storedCurrentStreak = (snapshot.getLong("currentStreak") ?: 0L).toInt()
            val storedLongestStreak = (snapshot.getLong("longestStreak") ?: 0L).toInt()
            val storedCompletedDates = (snapshot.get("completedDates") as? List<String>) ?: emptyList()

            // If already completed today, no change needed
            if (storedLastActiveDate == today) {
                return Result.success(
                    StreakInfo(
                        currentStreak = storedCurrentStreak,
                        longestStreak = storedLongestStreak,
                        lastActiveDate = today,
                        completedDates = storedCompletedDates,
                        isCompletedToday = true
                    )
                )
            }

            // Determine new streak based on whether streak was maintained yesterday or broken
            val newCurrentStreak = if (storedLastActiveDate == yesterday) {
                storedCurrentStreak + 1
            } else {
                1 // Streak broken or first activity
            }

            val newLongestStreak = maxOf(storedLongestStreak, newCurrentStreak)
            val updatedCompletedDates = (storedCompletedDates + today).distinct()

            val updatedMap = hashMapOf(
                "currentStreak" to newCurrentStreak,
                "longestStreak" to newLongestStreak,
                "lastActiveDate" to today,
                "completedDates" to updatedCompletedDates
            )

            docRef.set(updatedMap).await()

            val updatedInfo = StreakInfo(
                currentStreak = newCurrentStreak,
                longestStreak = newLongestStreak,
                lastActiveDate = today,
                completedDates = updatedCompletedDates,
                isCompletedToday = true
            )

            Result.success(updatedInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun getTodayDateString(): String {
        return LocalDate.now(ZoneId.systemDefault()).toString()
    }

    private fun getYesterdayDateString(): String {
        return LocalDate.now(ZoneId.systemDefault()).minusDays(1).toString()
    }
}
