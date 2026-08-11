package com.englishpal.app.domain.model

/**
 * Stages in the 5-step mock interview flow.
 */
enum class InterviewStage(val stepNumber: Int, val title: String) {
    INTRO(1, "Introduction"),
    TECHNICAL(2, "Technical Question"),
    BEHAVIORAL(3, "Behavioral Question"),
    WRAP_UP(4, "Wrap-up"),
    COMPLETED(5, "Final Feedback")
}
