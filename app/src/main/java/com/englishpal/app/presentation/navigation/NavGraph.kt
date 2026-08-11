package com.englishpal.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.englishpal.app.presentation.auth.AuthScreen
import com.englishpal.app.presentation.conversation.ConversationScreen
import com.englishpal.app.presentation.home.HomeScreen
import com.englishpal.app.presentation.interview.InterviewScreen
import com.englishpal.app.presentation.mistakes.MistakesScreen
import com.englishpal.app.presentation.quiz.QuizFeedbackScreen
import com.englishpal.app.presentation.quiz.QuizScreen

import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import com.englishpal.app.presentation.quiz.QuizViewModel

/**
 * Base Navigation Graph for EnglishPal using Jetpack Compose Navigation.
 */
@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Auth.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Auth.route) {
            AuthScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToQuiz = { navController.navigate(Screen.Quiz.route) },
                onNavigateToMistakes = { navController.navigate(Screen.Mistakes.route) },
                onNavigateToConversation = { navController.navigate(Screen.Conversation.route) },
                onNavigateToInterview = { navController.navigate(Screen.Interview.route) },
                onSignOut = {
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Quiz.route) { backStackEntry ->
            val quizViewModel: QuizViewModel = hiltViewModel(backStackEntry)
            QuizScreen(
                onNavigateToFeedback = { navController.navigate(Screen.QuizFeedback.route) },
                viewModel = quizViewModel
            )
        }

        composable(Screen.QuizFeedback.route) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(Screen.Quiz.route)
            }
            val quizViewModel: QuizViewModel = hiltViewModel(parentEntry)
            QuizFeedbackScreen(
                onNavigateHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Quiz.route) { inclusive = true }
                    }
                },
                onRetakeQuiz = {
                    navController.popBackStack(Screen.Quiz.route, inclusive = false)
                },
                viewModel = quizViewModel
            )
        }

        composable(Screen.Mistakes.route) {
            MistakesScreen()
        }

        composable(Screen.Conversation.route) {
            ConversationScreen()
        }

        composable(Screen.Interview.route) {
            InterviewScreen(
                onNavigateHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Interview.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
