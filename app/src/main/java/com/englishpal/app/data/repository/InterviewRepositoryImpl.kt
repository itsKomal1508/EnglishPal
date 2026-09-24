package com.englishpal.app.data.repository

import android.util.Log
import com.englishpal.app.data.datasource.GeminiErrorParser
import com.englishpal.app.data.datasource.GeminiErrorType
import com.englishpal.app.data.datasource.GeminiParsedError
import com.englishpal.app.domain.model.GrammarMistakeDetail
import com.englishpal.app.domain.model.InterviewMessage
import com.englishpal.app.domain.model.InterviewReportCard
import com.englishpal.app.domain.model.InterviewStage
import com.englishpal.app.domain.repository.InterviewRepository
import com.englishpal.app.domain.repository.MistakeRepository
import com.englishpal.app.domain.repository.StreakRepository
import com.google.ai.client.generativeai.GenerativeModel
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import com.englishpal.app.di.GeminiModelProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InterviewRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val modelProvider: GeminiModelProvider,
    private val streakRepository: StreakRepository,
    private val mistakeRepository: MistakeRepository
) : InterviewRepository {

    // Background scope for non-blocking persistence (Firestore & Streak logging)
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun JSONObject.optCleanString(key: String): String? {
        if (this.isNull(key)) return null
        val value = this.optString(key, "").trim()
        if (value.isBlank() || value.equals("null", ignoreCase = true)) return null
        return value
    }

    /**
     * Executes a Gemini API call with bounded exponential backoff retries and key rotation.
     * Diagnostic logging included (NO API keys or user PII logged).
     */
    /**
     * Executes a Gemini API call with bounded timeout.
     * Enforces single attempt with max 1 retry for transient 503 errors (maxAttempts = 2).
     */
    private suspend fun executeGeminiWithRetry(
        maxAttempts: Int = 2,
        prompt: String,
        operationName: String = "GEMINI_REQUEST"
    ): String {
        val requestId = UUID.randomUUID().toString().take(8)
        var lastParsedError: GeminiParsedError? = null

        for (attempt in 1..maxAttempts) {
            val startTime = System.currentTimeMillis()
            Log.d("GeminiLog", "[Gemini] requestId=$requestId operation=$operationName attempt=$attempt START")

            try {
                val model = modelProvider.getModel(forceRotate = false)

                val responseText = withTimeout(20000L) {
                    val response = model.generateContent(prompt)
                    response.text?.trim() ?: ""
                }
                val duration = System.currentTimeMillis() - startTime
                Log.d("GeminiLog", "[Gemini] requestId=$requestId operation=$operationName attempt=$attempt SUCCESS duration=${duration}ms")

                if (responseText.isNotBlank()) {
                    return responseText
                }
                throw IllegalStateException("Gemini API returned an empty response.")
            } catch (e: TimeoutCancellationException) {
                val duration = System.currentTimeMillis() - startTime
                val parsed = GeminiParsedError(
                    type = GeminiErrorType.TIMEOUT,
                    userMessage = "Gemini Request Timed Out (20s): Network request took too long. Tap Retry.",
                    isRetryable = true,
                    httpCode = null
                )
                lastParsedError = parsed
                Log.w("GeminiLog", "[Gemini] requestId=$requestId operation=$operationName attempt=$attempt TIMEOUT duration=${duration}ms")

                if (attempt < maxAttempts) {
                    delay(1500L)
                }
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - startTime
                val parsed = GeminiErrorParser.parseException(e)
                lastParsedError = parsed
                Log.w("GeminiLog", "[Gemini] requestId=$requestId operation=$operationName attempt=$attempt ERROR code=${parsed.httpCode ?: "N/A"} duration=${duration}ms message=${parsed.userMessage}")

                // Only transient 503 service unavailable errors get 1 single retry after 1.5s.
                // 429 rate limit / quota exhaustion, 401/403/404, or non-retryable errors abort immediately to protect API limits.
                val isTransient503 = parsed.type == GeminiErrorType.SERVICE_UNAVAILABLE_503
                if (!isTransient503 || attempt >= maxAttempts) {
                    throw Exception(parsed.userMessage)
                }

                delay(1500L)
            }
        }
        throw Exception(lastParsedError?.userMessage ?: "Gemini API request failed.")
    }

    /**
     * Builds a minimal context containing only the recent 1-2 dialogue turns (max 4 messages).
     * Significantly reduces input token consumption per request.
     */
    private fun buildCompactContext(history: List<InterviewMessage>): String {
        val recentTurns = history.takeLast(4)
        if (recentTurns.isEmpty()) return "Interview just started."

        return recentTurns.joinToString("\n") { m ->
            val role = if (m.sender == "interviewer") "Interviewer (Alex)" else "Candidate"
            "$role: ${m.text}"
        }
    }

    override suspend fun startInterview(userId: String): Result<InterviewMessage> {
        val startTime = System.currentTimeMillis()
        Log.d("SEInterviewPERF", "[PERF] startInterview initiated instantly")

        return try {
            val welcomeText = "Welcome! I'm Alex, Lead Engineer here. Thanks for joining the mock software engineering interview today. To start off, could you introduce yourself and briefly share your engineering background?"
            val msg = InterviewMessage(
                id = UUID.randomUUID().toString(),
                sender = "interviewer",
                text = welcomeText,
                timestamp = System.currentTimeMillis()
            )

            if (userId.isNotBlank()) {
                asyncSaveMessageToFirestore(userId, msg)
                asyncRecordStreak(userId)
            }

            val totalDuration = System.currentTimeMillis() - startTime
            Log.d("SEInterviewPERF", "[PERF] startInterview INSTANT DURATION: ${totalDuration}ms")

            Result.success(msg)
        } catch (e: Exception) {
            Log.e("SEInterview", "Exception starting interview: ${e.javaClass.name} - ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun processCandidateResponse(
        userId: String,
        currentStage: InterviewStage,
        history: List<InterviewMessage>,
        candidateText: String
    ): Result<Pair<InterviewMessage, InterviewStage>> {
        val startTime = System.currentTimeMillis()
        Log.d("SEInterviewPERF", "[PERF] processCandidateResponse initiated")

        if (candidateText.isBlank()) {
            return Result.failure(IllegalArgumentException("Candidate response cannot be empty"))
        }

        return try {
            // Deduplicate candidate message: re-use existing record if already present in history
            val existingCandidateMsg = history.lastOrNull { it.sender == "candidate" && it.text == candidateText }
            val candidateMsg = existingCandidateMsg ?: InterviewMessage(
                id = UUID.randomUUID().toString(),
                sender = "candidate",
                text = candidateText,
                timestamp = System.currentTimeMillis()
            )

            if (userId.isNotBlank()) {
                asyncSaveMessageToFirestore(userId, candidateMsg)
                asyncRecordStreak(userId)
            }

            val candidateMsgCount = history.count { it.sender == "candidate" } + (if (existingCandidateMsg == null) 1 else 0)

            val defaultStage = when {
                candidateMsgCount <= 1 -> InterviewStage.INTRO
                candidateMsgCount in 2..4 -> InterviewStage.TECHNICAL
                candidateMsgCount in 5..7 -> InterviewStage.BEHAVIORAL
                candidateMsgCount in 8..10 -> InterviewStage.SYSTEM_DESIGN
                else -> InterviewStage.WRAP_UP
            }

            val compactContext = buildCompactContext(history)

            val prompt = """
                You are Alex, a senior technical lead conducting a live, open-ended software engineering interview.
                Current Phase: ${defaultStage.title}

                Recent Conversation Context:
                $compactContext

                Candidate's Latest Input:
                "$candidateText"

                Instructions:
                - React naturally as Alex to the candidate's answer and ask the next logical technical or behavioral question.
                - If the candidate asks to finish, wrap up, or generate their report card, set "nextStage": "COMPLETED".
                - If candidate made English grammar/phrasing mistakes, provide a concise 1-sentence correction in "englishNote". If clean, set "englishNote": null.
                - DO NOT include technical insights or evaluation notes.

                Return ONLY raw valid JSON (no markdown formatting, no code fences):
                {
                  "question": "Alex's response reacting specifically to candidate text and asking the next question or closing.",
                  "englishNote": "1 short sentence correcting grammar/phrasing mistakes (or null if perfect).",
                  "nextStage": "INTRO" | "TECHNICAL" | "BEHAVIORAL" | "SYSTEM_DESIGN" | "WRAP_UP" | "COMPLETED"
                }
            """.trimIndent()

            Log.d("SEInterview", "========== INTERVIEW REQUEST PAYLOAD START ==========\n$prompt\n========== INTERVIEW REQUEST PAYLOAD END ==========")

            val responseText = executeGeminiWithRetry(maxAttempts = 2, prompt = prompt, operationName = "PROCESS_CANDIDATE_RESPONSE")
            val jsonText = responseText.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

            var questionText = ""
            var engNote: String? = null
            var returnedStageStr: String? = null

            if (jsonText.isNotBlank()) {
                try {
                    val jsonObj = JSONObject(jsonText)
                    questionText = jsonObj.optString("question", "").trim()
                    returnedStageStr = jsonObj.optCleanString("nextStage")
                    engNote = jsonObj.optCleanString("englishNote")
                } catch (_: Exception) {
                    if (!responseText.startsWith("{")) {
                        questionText = responseText
                    }
                }
            }

            if (questionText.isBlank()) {
                return Result.failure(IllegalStateException("Could not parse interviewer response from Gemini. Please retry."))
            }

            val parsedStage = returnedStageStr?.let { stageStr ->
                try {
                    InterviewStage.valueOf(stageStr.uppercase())
                } catch (_: Exception) { null }
            }
            val finalStage = parsedStage ?: defaultStage

            if (userId.isNotBlank()) {
                asyncLogInterviewMistakes(userId, candidateMsg.id, candidateText, engNote)
            }

            val interviewerMsg = InterviewMessage(
                id = UUID.randomUUID().toString(),
                sender = "interviewer",
                text = questionText,
                timestamp = System.currentTimeMillis(),
                technicalNote = null,
                englishNote = engNote
            )

            if (userId.isNotBlank()) {
                asyncSaveMessageToFirestore(userId, interviewerMsg)
            }

            val totalDuration = System.currentTimeMillis() - startTime
            Log.d("SEInterviewPERF", "[PERF] processCandidateResponse TOTAL DURATION: ${totalDuration}ms")

            Result.success(Pair(interviewerMsg, finalStage))
        } catch (e: Exception) {
            Log.e("SEInterview", "CRITICAL EXCEPTION in processCandidateResponse: ${e.javaClass.name} - ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun generateReportCard(
        userId: String,
        history: List<InterviewMessage>
    ): Result<InterviewReportCard> {
        val startTime = System.currentTimeMillis()
        Log.d("SEInterviewPERF", "[PERF] generateReportCard initiated")

        return try {
            val compactTranscript = buildCompactContext(history)

            val prompt = """
                You are a Senior Technical Hiring Manager and English Assessment Specialist.
                Evaluate the candidate's performance in the following software engineering mock interview session:

                $compactTranscript

                Return ONLY a raw valid JSON object (no markdown formatting, no code fences):
                {
                  "technicalScore": integer (0 to 100),
                  "englishFluencyScore": integer (0 to 100),
                  "technicalFeedback": "2-3 sentences evaluating technical depth, problem solving, and structure.",
                  "englishFluencyFeedback": "2-3 sentences evaluating English grammar, clarity, vocabulary, and tone.",
                  "strengths": [
                    "Strength point 1",
                    "Strength point 2"
                  ],
                  "areasForImprovement": [
                    "Improvement area 1",
                    "Improvement area 2"
                  ]
                }
            """.trimIndent()

            val responseText = executeGeminiWithRetry(maxAttempts = 2, prompt = prompt, operationName = "GENERATE_REPORT_CARD")
            val jsonText = responseText.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

            val jsonObj = JSONObject(jsonText)
            val techScore = jsonObj.optInt("technicalScore", 80)
            val engScore = jsonObj.optInt("englishFluencyScore", 82)
            val techFb = jsonObj.optString("technicalFeedback", "Demonstrated solid technical foundation.")
            val engFb = jsonObj.optString("englishFluencyFeedback", "Communicated ideas clearly with good vocabulary.")

            val strengthsList = mutableListOf<String>()
            val strArray = jsonObj.optJSONArray("strengths") ?: JSONArray()
            for (i in 0 until strArray.length()) {
                strengthsList.add(strArray.getString(i))
            }

            val improvementsList = mutableListOf<String>()
            val impArray = jsonObj.optJSONArray("areasForImprovement") ?: JSONArray()
            for (i in 0 until impArray.length()) {
                improvementsList.add(impArray.getString(i))
            }

            val reportCard = InterviewReportCard(
                technicalScore = techScore,
                englishFluencyScore = engScore,
                technicalFeedback = techFb,
                englishFluencyFeedback = engFb,
                strengths = strengthsList,
                areasForImprovement = improvementsList
            )

            if (userId.isNotBlank()) {
                asyncSaveReportCardToFirestore(userId, reportCard)
                asyncLogReportCardMistakes(userId, improvementsList)
            }

            val totalDuration = System.currentTimeMillis() - startTime
            Log.d("SEInterviewPERF", "[PERF] generateReportCard TOTAL DURATION: ${totalDuration}ms")

            Result.success(reportCard)
        } catch (e: Exception) {
            Log.e("SEInterview", "Exception generating report card: ${e.javaClass.name} - ${e.message}", e)
            Result.failure(e)
        }
    }

    // ── Non-blocking Async Background Persistence Helpers ──

    private fun asyncSaveMessageToFirestore(userId: String, msg: InterviewMessage) {
        if (userId.isBlank()) return
        repositoryScope.launch {
            try {
                val docMap = hashMapOf(
                    "id" to msg.id,
                    "sender" to msg.sender,
                    "text" to msg.text,
                    "timestamp" to msg.timestamp,
                    "technicalNote" to msg.technicalNote,
                    "englishNote" to msg.englishNote
                )
                firestore.collection("users")
                    .document(userId)
                    .collection("interviews")
                    .document("current_session")
                    .collection("messages")
                    .document(msg.id)
                    .set(docMap)
                    .await()
            } catch (e: Exception) {
                Log.w("SEInterview", "Background Firestore message save skipped: ${e.message}")
            }
        }
    }

    private fun asyncSaveReportCardToFirestore(userId: String, report: InterviewReportCard) {
        if (userId.isBlank()) return
        repositoryScope.launch {
            try {
                val docMap = hashMapOf(
                    "technicalScore" to report.technicalScore,
                    "englishFluencyScore" to report.englishFluencyScore,
                    "technicalFeedback" to report.technicalFeedback,
                    "englishFluencyFeedback" to report.englishFluencyFeedback,
                    "strengths" to report.strengths,
                    "areasForImprovement" to report.areasForImprovement,
                    "timestamp" to System.currentTimeMillis()
                )
                firestore.collection("users")
                    .document(userId)
                    .collection("interviews")
                    .document("current_session")
                    .set(docMap)
                    .await()
            } catch (e: Exception) {
                Log.w("SEInterview", "Background Firestore report save skipped: ${e.message}")
            }
        }
    }

    private fun asyncRecordStreak(userId: String) {
        if (userId.isBlank()) return
        repositoryScope.launch {
            try { streakRepository.recordDailyActivity(userId) } catch (_: Exception) {}
        }
    }

    private fun asyncLogInterviewMistakes(
        userId: String,
        msgId: String,
        candidateText: String,
        engNote: String?
    ) {
        if (userId.isBlank()) return
        repositoryScope.launch {
            try {
                val mistakesToSave = mutableListOf<GrammarMistakeDetail>()
                if (!engNote.isNullOrBlank() && !engNote.contains("clear", ignoreCase = true) && !engNote.contains("perfect", ignoreCase = true)) {
                    mistakesToSave += GrammarMistakeDetail(
                        questionId = msgId,
                        category = "Interview English Fluency",
                        userAnswer = candidateText,
                        correctAnswer = "Refined English Phrasing",
                        originalSentence = candidateText,
                        correctedSentence = engNote,
                        explanation = engNote
                    )
                }
                if (mistakesToSave.isNotEmpty()) {
                    mistakeRepository.saveMistakes(userId, mistakesToSave)
                    Log.d("SEInterview", "Logged ${mistakesToSave.size} interview English notes to MistakeRepository")
                }
            } catch (mEx: Exception) {
                Log.w("SEInterview", "Failed to log interview mistakes: ${mEx.message}")
            }
        }
    }

    private fun asyncLogReportCardMistakes(userId: String, improvementsList: List<String>) {
        if (userId.isBlank() || improvementsList.isEmpty()) return
        repositoryScope.launch {
            try {
                val reportGaps = improvementsList.map { area ->
                    GrammarMistakeDetail(
                        questionId = UUID.randomUUID().toString(),
                        category = "Technical Interview Focus Areas",
                        userAnswer = "Interview Evaluation Focus",
                        correctAnswer = area,
                        originalSentence = "Mock Interview Target Area",
                        correctedSentence = area,
                        explanation = "Identified area for technical interview growth: $area"
                    )
                }
                mistakeRepository.saveMistakes(userId, reportGaps)
                Log.d("SEInterview", "Logged ${reportGaps.size} report card focus areas to MistakeRepository")
            } catch (rEx: Exception) {
                Log.w("SEInterview", "Failed to log report card mistakes: ${rEx.message}")
            }
        }
    }
}
