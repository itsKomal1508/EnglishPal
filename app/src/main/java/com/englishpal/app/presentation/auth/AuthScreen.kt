package com.englishpal.app.presentation.auth

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.englishpal.app.presentation.theme.GradientButton
import com.englishpal.app.presentation.theme.PlayfulCard
import com.englishpal.app.presentation.theme.PrimaryGradient
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import android.util.Log

/**
 * Vibrant & Playful Authentication Screen
 */
@Composable
fun AuthScreen(
    onLoginSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Reset stale auth state when entering AuthScreen
    LaunchedEffect(Unit) {
        viewModel.resetAuthState()
    }

    // Trigger navigation on successful authentication
    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            if (viewModel.isUserLoggedIn()) {
                Log.d("AuthFlow", "Confirmed active FirebaseAuth session. Navigating to Home.")
                onLoginSuccess()
            } else {
                Log.e("AuthFlow", "uiState.isSuccess was true, but authRepository.isUserLoggedIn() returned false!")
                viewModel.setErrorMessage("Failed to establish active user session. Please try again.")
            }
        }
    }

    // Google Sign-In Activity Result Launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d("AuthFlow", "Google Sign-In Activity Result code: ${result.resultCode}, intent data: ${result.data}")
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken
            val email = account?.email ?: ""
            val displayName = account?.displayName ?: ""
            val photoUrl = account?.photoUrl?.toString() ?: ""

            Log.d("AuthFlow", "Google account retrieved: email=$email, name=$displayName, idTokenPresent=${!idToken.isNullOrBlank()}")

            if (!idToken.isNullOrBlank()) {
                viewModel.authenticateWithGoogle(idToken)
            } else if (email.isNotBlank()) {
                Log.w("AuthFlow", "Google idToken is null/empty. Authenticating via Google account profile for $email.")
                viewModel.authenticateWithGoogleProfile(email, displayName, photoUrl)
            } else {
                viewModel.setErrorMessage("Google Sign-In returned an empty account profile.")
            }
        } catch (e: ApiException) {
            if (e.statusCode != 12501) { // 12501 = user closed prompt
                val errorDetail = when (e.statusCode) {
                    10 -> "Code 10 (DEVELOPER_ERROR): Check SHA-1 fingerprint or OAuth Web Client ID in Firebase Console for package 'com.englishpal.app'."
                    12500 -> "Code 12500 (SIGN_IN_FAILED): Google Sign-In failed on device. Ensure Google Play Services is updated."
                    7 -> "Code 7 (NETWORK_ERROR): Network connection error during Google Sign-In."
                    else -> "Status Code ${e.statusCode}: ${e.localizedMessage ?: "Google Sign-In failed"}"
                }
                Log.e("AuthFlow", "Google Sign-In failed with ApiException: $errorDetail (statusCode: ${e.statusCode})", e)
                viewModel.setErrorMessage(errorDetail)
            } else {
                Log.d("AuthFlow", "Google Sign-In prompt dismissed by user.")
            }
        } catch (e: Exception) {
            Log.e("AuthFlow", "Unexpected error processing Google Sign-In result", e)
            viewModel.setErrorMessage(e.localizedMessage ?: "Google Sign-In failed")
        }
    }

    var isPasswordVisible by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            PlayfulCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                elevation = 12.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header Brand Icon Badge
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(PrimaryGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = "App Logo",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Header Title
                    Text(
                        text = "EnglishPal",
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 32.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (uiState.isSignUpMode) "Start your learning journey 🚀" else "Welcome back, learner! 👋",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Error Card
                    AnimatedVisibility(visible = uiState.errorMessage != null) {
                        uiState.errorMessage?.let { error ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = error,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(12.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    // Display Name Field (Sign-Up mode only)
                    AnimatedVisibility(visible = uiState.isSignUpMode) {
                        OutlinedTextField(
                            value = uiState.displayName,
                            onValueChange = viewModel::onDisplayNameChanged,
                            label = { Text("Full Name") },
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = "Name", tint = MaterialTheme.colorScheme.primary)
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                        )
                    }

                    // Email Field
                    OutlinedTextField(
                        value = uiState.email,
                        onValueChange = viewModel::onEmailChanged,
                        label = { Text("Email Address") },
                        leadingIcon = {
                            Icon(Icons.Default.Email, contentDescription = "Email", tint = MaterialTheme.colorScheme.primary)
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                    )

                    // Password Field
                    OutlinedTextField(
                        value = uiState.password,
                        onValueChange = viewModel::onPasswordChanged,
                        label = { Text("Password") },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = "Password", tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle password visibility"
                                )
                            }
                        },
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Primary Action Button (Gradient)
                    GradientButton(
                        text = if (uiState.isSignUpMode) "Create Account 🚀" else "Sign In ✨",
                        onClick = viewModel::authenticateWithEmail,
                        enabled = !uiState.isLoading,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Mode Switch Button
                    TextButton(onClick = viewModel::toggleAuthMode) {
                        Text(
                            text = if (uiState.isSignUpMode)
                                "Already have an account? Sign In"
                            else
                                "Don't have an account? Sign Up",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    // Divider
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f))
                        Text(
                            text = "  OR  ",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f))
                    }

                    // Google Sign-In Button
                    OutlinedButton(
                        onClick = {
                            Log.d("AuthFlow", "Continue with Google button clicked")
                            viewModel.clearError()
                            try {
                                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                    .requestEmail()
                                    .requestProfile()
                                    .build()

                                val client = GoogleSignIn.getClient(context, gso)

                                client.signOut().addOnCompleteListener {
                                    Log.d("AuthFlow", "Launching Google Sign-In intent...")
                                    googleSignInLauncher.launch(client.signInIntent)
                                }
                            } catch (e: Exception) {
                                Log.e("AuthFlow", "Error starting Google Sign-In intent", e)
                                viewModel.setErrorMessage("Could not launch Google Sign-In: ${e.localizedMessage}")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(28.dp),
                        border = ButtonDefaults.outlinedButtonBorder.copy(width = 2.dp)
                    ) {
                        Text(
                            text = "🌐 Continue with Google",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        )
                    }
                }
            }
        }
    }
}
