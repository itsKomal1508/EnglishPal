package com.englishpal.app.domain.model

/**
 * Open-ended interview phases without hardcoded question caps.
 */
enum class InterviewStage(val title: String) {
    INTRO("Introduction"),
    TECHNICAL("Technical Depth"),
    BEHAVIORAL("Behavioral & Leadership"),
    SYSTEM_DESIGN("System Design & Architecture"),
    WRAP_UP("Closing & Wrap-Up"),
    COMPLETED("Interview Completed")
}
