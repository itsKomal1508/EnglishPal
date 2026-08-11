package com.englishpal.app.presentation.quiz

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.englishpal.app.presentation.theme.CircularProgressRing
import com.englishpal.app.presentation.theme.CoralDark
import com.englishpal.app.presentation.theme.CoralPink
import com.englishpal.app.presentation.theme.EmeraldMint
import com.englishpal.app.presentation.theme.GradientButton
import com.englishpal.app.presentation.theme.PlayfulCard
import com.englishpal.app.presentation.theme.PrimaryGradient
import com.englishpal.app.presentation.theme.SuccessGradient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizFeedbackScreen(
    onNavigateHome: () -> Unit,
    onRetakeQuiz: () -> Unit = {},
    viewModel: QuizViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val evaluation = uiState.evaluationResult

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Quiz Results & AI Feedback 🏆",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            )
        }
    ) { padding ->
        if (evaluation == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No feedback data available.")
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Overall Score Header Banner
                val isHighScore = evaluation.score >= 70
                val bannerGradient = if (isHighScore) SuccessGradient else PrimaryGradient

                PlayfulCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = Color.Transparent,
                    elevation = 8.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bannerGradient)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            CircularProgressRing(
                                progress = evaluation.score / 100f,
                                modifier = Modifier.size(110.dp),
                                ringColor = Color.White,
                                trackColor = Color.White.copy(alpha = 0.3f)
                            ) {
                                Text(
                                    text = "${evaluation.score}%",
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Black
                                    ),
                                    color = Color.White
                                )
                            }

                            Text(
                                text = if (isHighScore) "Awesome Job! 🎉" else "Good Practice Effort! 💪",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 22.sp
                                ),
                                color = Color.White
                            )

                            Text(
                                text = "${evaluation.correctCount} of ${evaluation.totalQuestions} Questions Correct",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White.copy(alpha = 0.9f)
                            )

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color.White.copy(alpha = 0.2f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = evaluation.generalFeedback,
                                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                                    )
                                }
                            }
                        }
                    }
                }

                // Section Title: Grammar Mistakes & AI Analysis
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (evaluation.mistakes.isEmpty()) "Perfect Score! 🌟" else "AI Feedback & Breakdown 🔍",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "${evaluation.mistakes.size} Mistakes",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                if (evaluation.mistakes.isEmpty()) {
                    PlayfulCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = EmeraldMint.copy(alpha = 0.15f),
                        borderColor = EmeraldMint
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = EmeraldMint,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = "Outstanding job! You answered every question correctly.",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                color = EmeraldMint
                            )
                        }
                    }
                } else {
                    // Mistakes Detail List
                    evaluation.mistakes.forEachIndexed { idx, mistake ->
                        PlayfulCard(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = 4.dp
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Category Tag & Question Number
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = CoralPink.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = mistake.category,
                                            color = CoralDark,
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(
                                        text = "#${idx + 1}",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // User Answer vs Correction Cards
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    // User Incorrect Answer Tag
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = null,
                                            tint = CoralDark,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Your answer: ",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = mistake.userAnswer,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = CoralDark
                                        )
                                    }

                                    // Corrected Sentence Tag
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = EmeraldMint,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Corrected: ",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = mistake.correctedSentence,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = EmeraldMint
                                        )
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                // Gemini Explanation
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Lightbulb,
                                        contentDescription = "Explanation",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Text(
                                        text = mistake.explanation,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Bottom Action Buttons Row: Home & Retake Quiz (New Set)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onNavigateHome,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        Text("Home 🏠", fontWeight = FontWeight.Bold)
                    }

                    GradientButton(
                        text = "Retake Quiz 🔄",
                        onClick = {
                            viewModel.startNewRandomQuiz()
                            onRetakeQuiz()
                        },
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Refresh
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
