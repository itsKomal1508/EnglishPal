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
    private val firestore: FirebaseFirestore
) : MistakeRepository {

    override fun getMistakes(userId: String): Flow<List<MistakeItem>> = callbackFlow {
        if (userId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = firestore.collection("users")
            .document(userId)
            .collection("mistakes")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                if (snapshot == null || snapshot.isEmpty) {
                    trySend(emptyList())
                } else {
                    val mistakeList = snapshot.documents.mapNotNull { doc ->
                        MistakeItem(
                            id = doc.id,
                            category = doc.getString("category") ?: "General",
                            originalSentence = doc.getString("originalSentence") ?: "",
                            correctedSentence = doc.getString("correctedSentence") ?: "",
                            userAnswer = doc.getString("userAnswer") ?: "",
                            correctAnswer = doc.getString("correctAnswer") ?: "",
                            explanation = doc.getString("explanation") ?: "",
                            timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                        )
                    }
                    trySend(mistakeList)
                }
            }

        awaitClose { listener.remove() }
    }

    override suspend fun saveMistakes(
        userId: String,
        mistakes: List<GrammarMistakeDetail>
    ): Result<Unit> {
        if (userId.isBlank() || mistakes.isEmpty()) return Result.success(Unit)

        return try {
            val batch = firestore.batch()
            val collectionRef = firestore.collection("users")
                .document(userId)
                .collection("mistakes")

            val now = System.currentTimeMillis()
            mistakes.forEach { detail ->
                val docRef = collectionRef.document(UUID.randomUUID().toString())
                val mistakeMap = hashMapOf(
                    "category" to detail.category,
                    "originalSentence" to detail.originalSentence,
                    "correctedSentence" to detail.correctedSentence,
                    "userAnswer" to detail.userAnswer,
                    "correctAnswer" to detail.correctAnswer,
                    "explanation" to detail.explanation,
                    "timestamp" to now
                )
                batch.set(docRef, mistakeMap)
            }

            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun clearMistakes(userId: String): Result<Unit> {
        if (userId.isBlank()) return Result.success(Unit)
        return try {
            val snapshot = firestore.collection("users")
                .document(userId)
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
            Result.failure(e)
        }
    }
}
