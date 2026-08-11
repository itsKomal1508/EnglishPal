package com.englishpal.app.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.englishpal.app.domain.model.StreakInfo
import com.englishpal.app.presentation.theme.EmeraldMint
import com.englishpal.app.presentation.theme.FlameAmber
import com.englishpal.app.presentation.theme.PlayfulCard
import com.englishpal.app.presentation.theme.PrimaryGradient
import com.englishpal.app.presentation.theme.StreakBadge
import com.englishpal.app.presentation.theme.StreakGradient
import com.englishpal.app.presentation.theme.SuccessGradient
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToQuiz: () -> Unit,
    onNavigateToMistakes: () -> Unit,
    onNavigateToConversation: () -> Unit,
    onNavigateToInterview: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val streak = uiState.streakInfo

    LaunchedEffect(uiState.isSignedOut) {
        if (uiState.isSignedOut) {
            onSignOut()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "EnglishPal",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 24.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "✨", fontSize = 18.sp)
                    }
                },
                actions = {
                    StreakBadge(streakCount = streak.currentStreak)
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = viewModel::signOut,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Sign Out",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(2.dp))

            // User Welcome Header Banner
            PlayfulCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = Color.Transparent,
                elevation = 8.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PrimaryGradient)
                        .padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "User Avatar",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Column {
                            val nameToDisplay = remember(uiState.userProfile) {
                                val name = uiState.userProfile?.displayName ?: ""
                                if (name.isNotBlank()) {
                                    name
                                } else {
                                    val email = uiState.userProfile?.email ?: ""
                                    if (email.isNotBlank()) {
                                        email.substringBefore("@")
                                            .replace(".", " ")
                                            .split(" ")
                                            .joinToString(" ") { word -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }
                                    } else {
                                        "English Learner"
                                    }
                                }
                            }
                            Text(
                                text = "Hello, $nameToDisplay! 👋",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 22.sp
                                ),
                                color = Color.White
                            )
                            Text(
                                text = if (streak.isCompletedToday) "Great job! Keep the momentum going! 🎉" else "Ready for today's practice session? 🚀",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }

            // Streak & Daily Activity Card
            PlayfulCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "🔥", fontSize = 28.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "${streak.currentStreak} Day Streak",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    fontSize = 18.sp
                                )
                                Text(
                                    text = "Record Best: ${streak.longestStreak} days",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Streak Status Chip
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (streak.isCompletedToday) EmeraldMint.copy(alpha = 0.15f)
                            else FlameAmber.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = if (streak.isCompletedToday) "Active Today! ✅" else "Practice Needed ⚡",
                                color = if (streak.isCompletedToday) EmeraldMint else FlameAmber,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Monthly Practice Calendar Grid Component
                    CalendarViewComponent(completedDates = streak.completedDates)
                }
            }

            Text(
                text = "Practice Modes 🎯",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start
            )

            // Dynamic Quiz Badge Text
            val quizBadge = uiState.lastQuizAttempt?.let { attempt ->
                "Last: ${attempt.correctCount}/${attempt.totalQuestions} (${attempt.score}%)"
            } ?: "Random 10 Qs"

            // Feature Navigation Cards
            FeatureCard(
                title = "Grammar Quiz Practice",
                subtitle = "Test your skills & receive instant Gemini AI corrections",
                icon = Icons.Default.Quiz,
                badgeText = quizBadge,
                gradient = PrimaryGradient,
                onClick = onNavigateToQuiz
            )

            FeatureCard(
                title = "AI Conversation Partner",
                subtitle = "Chat freely with your personal AI English tutor",
                icon = Icons.Default.ChatBubbleOutline,
                badgeText = "Voice & Text",
                gradient = SuccessGradient,
                onClick = onNavigateToConversation
            )

            FeatureCard(
                title = "AI Mock SE Interview",
                subtitle = "Practice tech & behavioral interview responses with feedback",
                icon = Icons.Default.Work,
                badgeText = "Career Practice",
                gradient = StreakGradient,
                onClick = onNavigateToInterview
            )

            FeatureCard(
                title = "Mistake History & Weak Areas",
                subtitle = "Review saved grammar mistakes and track improvement",
                icon = Icons.Default.Psychology,
                badgeText = "Smart Review",
                gradient = Brush.horizontalGradient(listOf(Color(0xFFE056FD), Color(0xFF686DE0))),
                onClick = onNavigateToMistakes
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Vibrant Feature Navigation Card Component
 */
@Composable
fun FeatureCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    badgeText: String,
    gradient: Brush,
    onClick: () -> Unit
) {
    PlayfulCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = 6.dp,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(gradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Calendar Grid Component rendering current month practice history.
 */
@Composable
fun CalendarViewComponent(completedDates: List<String>) {
    val currentMonth = remember { YearMonth.now() }
    val daysInMonth = currentMonth.lengthOfMonth()
    val firstDayOfWeek = currentMonth.atDay(1).dayOfWeek.value
    val today = remember { LocalDate.now() }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${currentMonth.year}",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { dayLabel ->
                Text(
                    text = dayLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }

        val totalSlots = firstDayOfWeek - 1 + daysInMonth
        val rows = (totalSlots + 6) / 7

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (row in 0 until rows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    for (col in 0..6) {
                        val dayIndex = row * 7 + col - (firstDayOfWeek - 1) + 1
                        if (dayIndex in 1..daysInMonth) {
                            val dateObj = currentMonth.atDay(dayIndex)
                            val dateStr = dateObj.toString()
                            val isCompleted = completedDates.contains(dateStr)
                            val isToday = dateObj.isEqual(today)

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .padding(2.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isCompleted -> EmeraldMint
                                            isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                            else -> Color.Transparent
                                        }
                                    )
                                    .then(
                                        if (isToday && !isCompleted) Modifier.border(
                                            2.dp,
                                            MaterialTheme.colorScheme.primary,
                                            CircleShape
                                        ) else Modifier
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isCompleted) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Completed",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                } else {
                                    Text(
                                        text = "$dayIndex",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal),
                                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
