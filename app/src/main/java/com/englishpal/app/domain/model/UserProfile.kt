package com.englishpal.app.domain.model

/**
 * Domain entity representing a User Profile.
 * Pure Kotlin data class decoupled from Firestore implementation details.
 */
data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val photoUrl: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
