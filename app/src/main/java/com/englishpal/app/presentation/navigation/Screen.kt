package com.englishpal.app.presentation.navigation

/**
 * Sealed class defining all navigation routes for EnglishPal.
 */
sealed class Screen(val route: String) {
    object Auth : Screen("auth_screen")
    object Home : Screen("home_screen")
    object Quiz : Screen("quiz_screen")
    object QuizFeedback : Screen("quiz_feedback_screen")
    object Mistakes : Screen("mistakes_screen")
    object Conversation : Screen("conversation_screen")
    object Interview : Screen("interview_screen")
}
