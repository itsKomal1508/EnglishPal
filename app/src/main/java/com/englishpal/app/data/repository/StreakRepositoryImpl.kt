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
    private val firestore: FirebaseFirestore,
    private val localVault: com.englishpal.app.data.datasource.LocalPreferencesVault
) : StreakRepository {

    override fun getStreakInfo(userId: String): Flow<StreakInfo> = callbackFlow {
        // Emit local streak info immediately
        val localStreak = localVault.getLocalStreak()
        trySend(localStreak)

        val targetUserId = userId.ifBlank { localVault.getOrCreateLocalUserId() }

        val listener = firestore.collection("users")
            .document(targetUserId)
            .collection("streak")
            .document("info")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("StreakRepository", "Firestore error reading streak info for '$targetUserId'. Using local streak.", error)
                    trySend(localVault.getLocalStreak())
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val lastActiveDate = snapshot.getString("lastActiveDate") ?: ""
                    val rawCurrentStreak = (snapshot.getLong("currentStreak") ?: 0L).toInt()
                    val longestStreak = (snapshot.getLong("longestStreak") ?: 0L).toInt()
                    val completedDates = snapshot.get("completedDates") as? List<String> ?: emptyList()

                    val today = getTodayDateString()
                    val yesterday = getYesterdayDateString()
                    val isCompletedToday = lastActiveDate == today
                    val currentStreak = when (lastActiveDate) {
                        today, yesterday -> rawCurrentStreak
                        else -> 0
                    }

                    val remoteStreak = StreakInfo(
                        currentStreak = currentStreak,
                        longestStreak = longestStreak,
                        lastActiveDate = lastActiveDate,
                        completedDates = completedDates,
                        isCompletedToday = isCompletedToday
                    )
                    // Take higher streak between local and remote
                    val localCurrent = localVault.getLocalStreak()
                    if (remoteStreak.currentStreak >= localCurrent.currentStreak) {
                        trySend(remoteStreak)
                    } else {
                        trySend(localCurrent)
                    }
                } else {
                    trySend(localVault.getLocalStreak())
                }
            }

        awaitClose { listener.remove() }
    }

    override suspend fun recordDailyActivity(userId: String): Result<StreakInfo> {
        // 1. ALWAYS record activity locally on device disk immediately
        val updatedLocalStreak = localVault.recordLocalDailyActivity()
        android.util.Log.d("StreakRepository", "Recorded local daily activity: currentStreak=${updatedLocalStreak.currentStreak}")

        val targetUserId = userId.ifBlank { localVault.getOrCreateLocalUserId() }

        // 2. Async sync to Firestore
        try {
            val docRef = firestore.collection("users")
                .document(targetUserId)
                .collection("streak")
                .document("info")

            val today = getTodayDateString()
            val updatedMap = hashMapOf(
                "currentStreak" to updatedLocalStreak.currentStreak,
                "longestStreak" to updatedLocalStreak.longestStreak,
                "lastActiveDate" to today,
                "completedDates" to updatedLocalStreak.completedDates
            )

            docRef.set(updatedMap).await()
            android.util.Log.d("StreakRepository", "Synced streak to Firestore for user '$targetUserId': currentStreak=${updatedLocalStreak.currentStreak}")
        } catch (e: Exception) {
            android.util.Log.w("StreakRepository", "Remote Firestore sync warning for streak (local copy preserved): ${e.message}")
        }

        return Result.success(updatedLocalStreak)
    }

    private fun getTodayDateString(): String {
        return LocalDate.now(ZoneId.systemDefault()).toString()
    }

    private fun getYesterdayDateString(): String {
        return LocalDate.now(ZoneId.systemDefault()).minusDays(1).toString()
    }
}
