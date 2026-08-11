package com.englishpal.app.presentation.quiz

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.englishpal.app.presentation.theme.AnimatedProgressBar
import com.englishpal.app.presentation.theme.EmeraldMint
import com.englishpal.app.presentation.theme.FlameAmber
import com.englishpal.app.presentation.theme.GradientButton
import com.englishpal.app.presentation.theme.PlayfulCard
import com.englishpal.app.presentation.theme.PrimaryGradient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(
    onNavigateToFeedback: () -> Unit,
    viewModel: QuizViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Trigger navigation to feedback screen once submitted
    LaunchedEffect(uiState.isSubmitted) {
        if (uiState.isSubmitted) {
            onNavigateToFeedback()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Grammar Practice Quiz 📝",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 20.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    // Start New Random Quiz Action Button
                    IconButton(onClick = viewModel::startNewRandomQuiz) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "New Random Quiz",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Generating random 10-question quiz...",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        } else {
            val quiz = uiState.selectedQuiz
            if (quiz == null || quiz.questions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No questions available. Tap refresh to load.")
                }
            } else {
                val currentQIndex = uiState.currentQuestionIndex
                val currentQuestion = quiz.questions[currentQIndex]
                val selectedOption = uiState.userAnswers[currentQuestion.id]

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Spacer(modifier = Modifier.height(2.dp))

                    // Last Attempt Summary Banner (if user has previously taken a quiz)
                    uiState.lastQuizAttempt?.let { attempt ->
                        val lastDateStr = remember(attempt.timestamp) {
                            if (attempt.timestamp > 0) {
                                SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(Date(attempt.timestamp))
                            } else ""
                        }
                        PlayfulCard(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            elevation = 2.dp
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.EmojiEvents,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Last Quiz Result: ${attempt.correctCount}/${attempt.totalQuestions} (${attempt.score}%)",
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        if (lastDateStr.isNotBlank()) {
                                            Text(
                                                text = "Completed $lastDateStr",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (attempt.score >= 70) EmeraldMint.copy(alpha = 0.2f) else FlameAmber.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = if (attempt.score >= 70) "Pass 🎉" else "Practice 💪",
                                        color = if (attempt.score >= 70) EmeraldMint else FlameAmber,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Progress Indicator Header
                    val progress = (currentQIndex + 1).toFloat() / quiz.questions.size
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Question ${currentQIndex + 1} of ${quiz.questions.size}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${uiState.userAnswers.size}/${quiz.questions.size} Answered",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        AnimatedProgressBar(progress = progress)
                    }

                    // Question Card
                    PlayfulCard(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = 6.dp
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                modifier = Modifier.padding(bottom = 12.dp)
                            ) {
                                Text(
                                    text = currentQuestion.category,
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            Text(
                                text = currentQuestion.questionText,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontSize = 20.sp,
                                    lineHeight = 28.sp
                                ),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Options List with Visual Highlight Cards
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        currentQuestion.options.forEachIndexed { index, optionText ->
                            val isSelected = selectedOption == index
                            val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface

                            PlayfulCard(
                                onClick = { viewModel.selectOption(currentQuestion.id, index) },
                                modifier = Modifier.fillMaxWidth(),
                                backgroundColor = containerColor,
                                borderColor = borderColor,
                                elevation = if (isSelected) 6.dp else 2.dp
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { viewModel.selectOption(currentQuestion.id, index) },
                                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = optionText,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Question Navigation Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = viewModel::previousQuestion,
                            enabled = currentQIndex > 0,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Previous", fontWeight = FontWeight.Bold)
                        }

                        if (currentQIndex < quiz.questions.size - 1) {
                            Button(
                                onClick = viewModel::nextQuestion,
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Text("Next", fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.ArrowForward, contentDescription = null)
                            }
                        } else {
                            GradientButton(
                                text = if (uiState.isSubmitting) "Evaluating..." else "Submit Quiz ✨",
                                onClick = viewModel::submitQuiz,
                                enabled = !uiState.isSubmitting,
                                modifier = Modifier.height(48.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}
