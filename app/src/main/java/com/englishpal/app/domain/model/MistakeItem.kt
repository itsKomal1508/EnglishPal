package com.englishpal.app.domain.model

/**
 * Domain entity representing an individual grammar mistake stored in history.
 */
data class MistakeItem(
    val id: String = "",
    val category: String = "General",
    val originalSentence: String = "",
    val correctedSentence: String = "",
    val userAnswer: String = "",
    val correctAnswer: String = "",
    val explanation: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
