package com.englishpal.app.data.repository

import com.englishpal.app.domain.model.GrammarMistakeDetail
import com.englishpal.app.domain.model.MistakeItem
import com.englishpal.app.domain.repository.MistakeRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MistakeRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val localVault: com.englishpal.app.data.datasource.LocalPreferencesVault
) : MistakeRepository {

    override fun getMistakes(userId: String): Flow<List<MistakeItem>> = callbackFlow {
        // Always emit local disk mistakes immediately so UI is never empty
        val localItems = localVault.getLocalMistakes()
        trySend(localItems)

        val targetUserId = userId.ifBlank { localVault.getOrCreateLocalUserId() }

        val listener = firestore.collection("users")
            .document(targetUserId)
            .collection("mistakes")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("MistakeRepository", "Firestore error reading mistakes for user '$targetUserId'. Using local vault.", error)
                    trySend(localVault.getLocalMistakes())
                    return@addSnapshotListener
                }

                if (snapshot != null && !snapshot.isEmpty) {
                    val remoteList = snapshot.documents.mapNotNull { doc ->
                        MistakeItem(
                            id = doc.id,
                            category = doc.getString("category") ?: "General",
                            originalSentence = doc.getString("originalSentence") ?: "",
                            correctedSentence = doc.getString("correctedSentence") ?: "",
                            userAnswer = doc.getString("userAnswer") ?: "",
                            correctAnswer = doc.getString("correctAnswer") ?: "",
                            explanation = doc.getString("explanation") ?: "",
                            timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis(),
                            source = doc.getString("source") ?: "quiz",
                            userId = targetUserId,
                            questionTopic = doc.getString("questionTopic") ?: doc.getString("category") ?: "General"
                        )
                    }
                    // Sync remote entries to local vault and emit merged list
                    localVault.saveLocalMistakes(remoteList)
                    trySend(localVault.getLocalMistakes())
                } else {
                    trySend(localVault.getLocalMistakes())
                }
            }

        awaitClose { listener.remove() }
    }

    override suspend fun saveMistakes(
        userId: String,
        mistakes: List<GrammarMistakeDetail>
    ): Result<Unit> {
        if (mistakes.isEmpty()) return Result.success(Unit)
        val targetUserId = userId.ifBlank { localVault.getOrCreateLocalUserId() }

        val now = System.currentTimeMillis()
        val mistakeItems = mistakes.map { detail ->
            MistakeItem(
                id = UUID.randomUUID().toString(),
                category = detail.category,
                originalSentence = detail.originalSentence,
                correctedSentence = detail.correctedSentence,
                userAnswer = detail.userAnswer,
                correctAnswer = detail.correctAnswer,
                explanation = detail.explanation,
                timestamp = now,
                source = detail.source.ifBlank { "quiz" },
                userId = targetUserId,
                questionTopic = detail.questionTopic.ifBlank { detail.category }
            )
        }

        // 1. ALWAYS persist to local disk vault immediately
        localVault.saveLocalMistakes(mistakeItems)
        android.util.Log.d("MistakeRepository", "Saved ${mistakeItems.size} mistakes to local disk vault for user '$targetUserId'")

        // 2. Attempt async sync to Firestore
        try {
            val batch = firestore.batch()
            val collectionRef = firestore.collection("users")
                .document(targetUserId)
                .collection("mistakes")

            mistakeItems.forEach { item ->
                val docRef = collectionRef.document(item.id)
                val mistakeMap = hashMapOf(
                    "category" to item.category,
                    "originalSentence" to item.originalSentence,
                    "correctedSentence" to item.correctedSentence,
                    "userAnswer" to item.userAnswer,
                    "correctAnswer" to item.correctAnswer,
                    "explanation" to item.explanation,
                    "timestamp" to item.timestamp,
                    "source" to item.source,
                    "userId" to targetUserId,
                    "questionTopic" to item.questionTopic
                )
                batch.set(docRef, mistakeMap)
            }

            batch.commit().await()
            android.util.Log.d("MistakeRepository", "Synced ${mistakeItems.size} mistakes to remote Firestore for user: $targetUserId")
        } catch (e: Exception) {
            android.util.Log.w("MistakeRepository", "Remote Firestore sync warning for mistakes (local copy preserved): ${e.message}")
        }

        return Result.success(Unit)
    }

    override suspend fun clearMistakes(userId: String): Result<Unit> {
        localVault.clearLocalMistakes()
        val targetUserId = userId.ifBlank { localVault.getOrCreateLocalUserId() }
        return try {
            val snapshot = firestore.collection("users")
                .document(targetUserId)
                .collection("mistakes")
                .get()
                .await()

            val batch = firestore.batch()
            snapshot.documents.forEach { doc ->
                batch.delete(doc.reference)
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.success(Unit)
        }
    }
}
