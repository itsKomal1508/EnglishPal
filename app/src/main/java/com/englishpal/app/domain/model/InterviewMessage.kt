package com.englishpal.app.domain.model

import java.util.UUID

/**
 * Domain entity representing a message in the SE Mock Interview transcript.
 */
data class InterviewMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: String = "interviewer", // "interviewer" or "candidate"
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val technicalNote: String? = null,
    val englishNote: String? = null
)
