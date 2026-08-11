package com.englishpal.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.englishpal.app.domain.repository.AuthRepository
import com.englishpal.app.presentation.navigation.NavGraph
import com.englishpal.app.presentation.navigation.Screen
import com.englishpal.app.presentation.theme.EnglishPalTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Single Activity hosting the Jetpack Compose UI & Navigation Graph.
 * Annotated with @AndroidEntryPoint to enable Hilt field injection in Activities.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Determine initial route based on authentication state
        val startDestination = if (authRepository.isUserLoggedIn()) {
            Screen.Home.route
        } else {
            Screen.Auth.route
        }

        setContent {
            EnglishPalTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavGraph(
                        navController = navController,
                        startDestination = startDestination
                    )
                }
            }
        }
    }
}
