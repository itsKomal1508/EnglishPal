package com.englishpal.app.domain.model

/**
 * Domain model representing gentle inline grammar corrections provided by Gemini.
 */
data class InlineCorrection(
    val originalText: String = "",
    val correctedText: String = "",
    val explanation: String = ""
)
