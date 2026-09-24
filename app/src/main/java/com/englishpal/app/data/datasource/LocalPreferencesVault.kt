package com.englishpal.app.data.datasource

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.englishpal.app.domain.model.MistakeItem
import com.englishpal.app.domain.model.StreakInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalPreferencesVault @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("english_pal_local_vault", Context.MODE_PRIVATE)
    }

    /**
     * Returns a persistent device-local user ID that survives app restarts and offline sessions.
     */
    fun getOrCreateLocalUserId(): String {
        var id = prefs.getString("local_user_id", "") ?: ""
        if (id.isBlank()) {
            id = "local_user_" + UUID.randomUUID().toString().replace("-", "").take(12)
            prefs.edit().putString("local_user_id", id).apply()
            Log.d("LocalVault", "Generated new persistent local user ID: $id")
        }
        return id
    }

    // ── MISTAKE HISTORY LOCAL STORAGE ──────────────────────────────────────────

    fun getLocalMistakes(): List<MistakeItem> {
        val jsonString = prefs.getString("key_local_mistakes", "[]") ?: "[]"
        return try {
            val array = JSONArray(jsonString)
            val list = mutableListOf<MistakeItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    MistakeItem(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        category = obj.optString("category", "General"),
                        originalSentence = obj.optString("originalSentence", ""),
                        correctedSentence = obj.optString("correctedSentence", ""),
                        userAnswer = obj.optString("userAnswer", ""),
                        correctAnswer = obj.optString("correctAnswer", ""),
                        explanation = obj.optString("explanation", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        source = obj.optString("source", "quiz"),
                        userId = obj.optString("userId", getOrCreateLocalUserId()),
                        questionTopic = obj.optString("questionTopic", "General")
                    )
                )
            }
            list.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            Log.e("LocalVault", "Error parsing local mistakes JSON", e)
            emptyList()
        }
    }

    fun saveLocalMistakes(newItems: List<MistakeItem>) {
        if (newItems.isEmpty()) return
        try {
            val current = getLocalMistakes().toMutableList()
            val existingIds = current.map { it.id }.toSet()
            newItems.forEach { item ->
                if (item.id !in existingIds) {
                    current.add(0, item)
                }
            }

            val array = JSONArray()
            current.forEach { item ->
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("category", item.category)
                    put("originalSentence", item.originalSentence)
                    put("correctedSentence", item.correctedSentence)
                    put("userAnswer", item.userAnswer)
                    put("correctAnswer", item.correctAnswer)
                    put("explanation", item.explanation)
                    put("timestamp", item.timestamp)
                    put("source", item.source)
                    put("userId", item.userId)
                    put("questionTopic", item.questionTopic)
                }
                array.put(obj)
            }
            prefs.edit().putString("key_local_mistakes", array.toString()).apply()
            Log.d("LocalVault", "Saved ${newItems.size} new mistakes locally. Total local count: ${current.size}")
        } catch (e: Exception) {
            Log.e("LocalVault", "Error saving mistakes locally", e)
        }
    }

    fun clearLocalMistakes() {
        prefs.edit().remove("key_local_mistakes").apply()
        Log.d("LocalVault", "Cleared local mistakes vault")
    }

    // ── DAILY STREAK LOCAL STORAGE ─────────────────────────────────────────────

    fun getLocalStreak(): StreakInfo {
        val jsonString = prefs.getString("key_local_streak", null)
        if (jsonString == null) {
            return StreakInfo()
        }
        return try {
            val obj = JSONObject(jsonString)
            val lastActiveDate = obj.optString("lastActiveDate", "")
            val rawCurrentStreak = obj.optInt("currentStreak", 0)
            val longestStreak = obj.optInt("longestStreak", 0)

            val completedArray = obj.optJSONArray("completedDates") ?: JSONArray()
            val completedDates = mutableListOf<String>()
            for (i in 0 until completedArray.length()) {
                completedDates.add(completedArray.getString(i))
            }

            val today = LocalDate.now(ZoneId.systemDefault()).toString()
            val yesterday = LocalDate.now(ZoneId.systemDefault()).minusDays(1).toString()

            val isCompletedToday = lastActiveDate == today
            val currentStreak = when (lastActiveDate) {
                today, yesterday -> rawCurrentStreak
                else -> 0
            }

            StreakInfo(
                currentStreak = currentStreak,
                longestStreak = longestStreak,
                lastActiveDate = lastActiveDate,
                completedDates = completedDates,
                isCompletedToday = isCompletedToday
            )
        } catch (e: Exception) {
            Log.e("LocalVault", "Error parsing local streak JSON", e)
            StreakInfo()
        }
    }

    fun recordLocalDailyActivity(): StreakInfo {
        try {
            val today = LocalDate.now(ZoneId.systemDefault()).toString()
            val yesterday = LocalDate.now(ZoneId.systemDefault()).minusDays(1).toString()

            val currentInfo = getLocalStreak()
            val storedLastActiveDate = currentInfo.lastActiveDate
            val storedCurrentStreak = currentInfo.currentStreak
            val storedLongestStreak = currentInfo.longestStreak
            val storedCompletedDates = currentInfo.completedDates

            if (storedLastActiveDate == today && storedCurrentStreak > 0) {
                Log.d("LocalVault", "Activity already recorded today. Streak remains: $storedCurrentStreak")
                return currentInfo.copy(isCompletedToday = true)
            }

            val newCurrentStreak = when (storedLastActiveDate) {
                yesterday -> storedCurrentStreak + 1
                today -> if (storedCurrentStreak > 0) storedCurrentStreak else 1
                else -> 1
            }

            val newLongestStreak = maxOf(storedLongestStreak, newCurrentStreak)
            val updatedCompletedDates = (storedCompletedDates + today).distinct()

            val updatedInfo = StreakInfo(
                currentStreak = newCurrentStreak,
                longestStreak = newLongestStreak,
                lastActiveDate = today,
                completedDates = updatedCompletedDates,
                isCompletedToday = true
            )

            val obj = JSONObject().apply {
                put("currentStreak", newCurrentStreak)
                put("longestStreak", newLongestStreak)
                put("lastActiveDate", today)
                put("completedDates", JSONArray(updatedCompletedDates))
            }

            prefs.edit().putString("key_local_streak", obj.toString()).apply()
            Log.d("LocalVault", "Recorded daily activity locally. New streak: $newCurrentStreak (today: $today)")
            return updatedInfo
        } catch (e: Exception) {
            Log.e("LocalVault", "Error recording local daily activity", e)
            return StreakInfo(currentStreak = 1, isCompletedToday = true)
        }
    }
}
