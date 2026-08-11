package com.englishpal.app.data.repository

import android.util.Log
import com.englishpal.app.domain.model.InterviewMessage
import com.englishpal.app.domain.model.InterviewReportCard
import com.englishpal.app.domain.model.InterviewStage
import com.englishpal.app.domain.repository.InterviewRepository
import com.englishpal.app.domain.repository.StreakRepository
import com.google.ai.client.generativeai.GenerativeModel
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InterviewRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val generativeModel: GenerativeModel,
    private val streakRepository: StreakRepository
) : InterviewRepository {

    override suspend fun startInterview(userId: String): Result<InterviewMessage> {
        return try {
            val prompt = """
                You are Alex, a Lead Software Engineer conducting a Software Engineer job interview in English.
                Introduce yourself warmly, welcome the candidate to this mock interview session, and ask them to introduce themselves and briefly share their tech background.

                Return ONLY raw valid JSON:
                {
                  "question": "Welcome! I'm Alex, Lead Engineer here. Thanks for joining the mock interview today. To start off, could you introduce yourself and tell me a bit about your engineering background?",
                  "technicalNote": null,
                  "englishNote": null
                }
            """.trimIndent()

            val response = generativeModel.generateContent(prompt)
            val rawText = response.text?.trim() ?: ""
            val jsonText = rawText.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

            var questionText = "Welcome to the Software Engineer Mock Interview! Could you introduce yourself and briefly share your engineering background?"
            var techNote: String? = null
            var engNote: String? = null

            try {
                val jsonObj = JSONObject(jsonText)
                questionText = jsonObj.optString("question", questionText)
                techNote = jsonObj.optString("technicalNote", "").ifBlank { null }
                engNote = jsonObj.optString("englishNote", "").ifBlank { null }
            } catch (e: Exception) {
                if (rawText.isNotBlank() && !rawText.startsWith("{")) {
                    questionText = rawText
                }
            }

            val msg = InterviewMessage(
                id = UUID.randomUUID().toString(),
                sender = "interviewer",
                text = questionText,
                timestamp = System.currentTimeMillis(),
                technicalNote = techNote,
                englishNote = engNote
            )

            if (userId.isNotBlank()) {
                saveMessageToFirestore(userId, msg)
                streakRepository.recordDailyActivity(userId)
            }

            Result.success(msg)
        } catch (e: Exception) {
            // Fallback initial message if offline/error
            val fallbackMsg = InterviewMessage(
                id = UUID.randomUUID().toString(),
                sender = "interviewer",
                text = "Welcome to the Software Engineer Mock Interview! To get started, please introduce yourself and describe your software engineering background.",
                timestamp = System.currentTimeMillis()
            )
            Result.success(fallbackMsg)
        }
    }

    override suspend fun processCandidateResponse(
        userId: String,
        currentStage: InterviewStage,
        history: List<InterviewMessage>,
        candidateText: String
    ): Result<Pair<InterviewMessage, InterviewStage>> {
        if (candidateText.isBlank()) {
            return Result.failure(IllegalArgumentException("Candidate response cannot be empty"))
        }

        return try {
            // 1. Save candidate message
            val candidateMsg = InterviewMessage(
                id = UUID.randomUUID().toString(),
                sender = "candidate",
                text = candidateText,
                timestamp = System.currentTimeMillis()
            )
            if (userId.isNotBlank()) {
                saveMessageToFirestore(userId, candidateMsg)
                streakRepository.recordDailyActivity(userId)
            }

            // Determine next stage
            val nextStage = when (currentStage) {
                InterviewStage.INTRO -> InterviewStage.TECHNICAL
                InterviewStage.TECHNICAL -> InterviewStage.BEHAVIORAL
                InterviewStage.BEHAVIORAL -> InterviewStage.WRAP_UP
                InterviewStage.WRAP_UP -> InterviewStage.COMPLETED
                InterviewStage.COMPLETED -> InterviewStage.COMPLETED
            }

            if (nextStage == InterviewStage.COMPLETED) {
                val wrapUpAckMsg = InterviewMessage(
                    id = UUID.randomUUID().toString(),
                    sender = "interviewer",
                    text = "Thank you so much for your responses! That concludes our mock interview. I'm compiling your evaluation report card now.",
                    timestamp = System.currentTimeMillis()
                )
                if (userId.isNotBlank()) saveMessageToFirestore(userId, wrapUpAckMsg)
                return Result.success(Pair(wrapUpAckMsg, InterviewStage.COMPLETED))
            }

            // Format transcript context
            val transcript = history.joinToString("\n") { m ->
                val role = if (m.sender == "interviewer") "Interviewer (Alex)" else "Candidate"
                "$role: ${m.text}"
            } + "\nCandidate: $candidateText"

            val prompt = """
                You are Alex, a Tech Lead conducting a mock Software Engineering interview in English.

                CRITICAL HONEST EVALUATION RULES:
                1. HONEST TECHNICAL EVALUATION: When evaluating the candidate's answer to a technical or behavioral question, be completely honest and accurate:
                   - If the answer is a non-answer, too vague, says "I don't know" / "don't have idea", or shows no real understanding, clearly and kindly say so (e.g. "That's okay - not knowing every concept is part of interviewing! In brief..."). NEVER praise a non-answer as if it were a good one.
                   - Only give positive praise ("Great explanation!", "Solid technical understanding") when the candidate's answer genuinely demonstrates relevant technical depth.
                2. RIGOROUS ENGLISH & GRAMMAR CORRECTION: ALWAYS inspect the candidate's answer for grammar, spelling, or phrasing mistakes, even short or incomplete answers (e.g. "don't have idea" -> "I don't have any idea" or "I'm not sure"). Put this explicit correction in the "englishNote" field.
                3. CONSTRUCTIVE FEEDBACK: If an answer is weak or incomplete, acknowledge it constructively before offering a brief hint and proceeding to the next question.
                4. STRUCTURE:
                   - Introduction -> Technical Question -> Behavioral Question -> Wrap-up Report.
                   - Track the step using the Recent Transcript below.

                Current Stage: ${currentStage.title}
                Next Stage Target: ${nextStage.title}

                Recent Transcript:
                $transcript

                Return ONLY raw valid JSON (no markdown formatting, no extra code blocks):
                {
                  "question": "Your honest interviewer response evaluating their last answer and asking the next question for ${nextStage.title}.",
                  "technicalNote": "1 short sentence honestly evaluating their technical depth (or noting if answer was incomplete/weak).",
                  "englishNote": "1 short sentence correcting grammar/phrasing mistakes (e.g. correcting 'don't have idea' to 'I don't have any idea').",
                  "nextStage": "${nextStage.name}"
                }
            """.trimIndent()

            Log.d("SEInterview", "========== INTERVIEW REQUEST PAYLOAD START ==========\n$prompt\n========== INTERVIEW REQUEST PAYLOAD END ==========")

            val rawText = try {
                val response = generativeModel.generateContent(prompt)
                val text = response.text?.trim() ?: ""
                Log.d("SEInterview", "========== INTERVIEW RAW RESPONSE RECEIVED ==========\n$text\n=====================================================")
                text
            } catch (e: Exception) {
                Log.e("SEInterview", "!!! GEMINI INTERVIEW API EXCEPTION CAUGHT !!! Type: ${e.javaClass.name}, Message: ${e.message}", e)
                ""
            }

            val jsonText = rawText.replace("```json", "").replace("```", "").trim()

            var questionText = ""
            var techNote: String? = null
            var engNote: String? = null

            if (jsonText.isNotBlank()) {
                try {
                    val jsonObj = JSONObject(jsonText)
                    questionText = jsonObj.optString("question", "").trim()
                    techNote = jsonObj.optString("technicalNote", "").ifBlank { null }
                    engNote = jsonObj.optString("englishNote", "").ifBlank { null }
                } catch (e: Exception) {
                    if (rawText.isNotBlank() && !rawText.startsWith("{")) {
                        questionText = rawText
                    }
                }
            }

            // Fallback smart question generator if API failed or returned empty question
            if (questionText.isBlank() || questionText.contains("move on to the next part")) {
                val (fbQ, fbTech, fbEng) = generateFallbackInterviewQuestion(nextStage, candidateText)
                questionText = fbQ
                techNote = techNote ?: fbTech
                engNote = engNote ?: fbEng
                Log.d("SEInterview", "Generated smart fallback interview question for $nextStage: '$questionText'")
            }

            val interviewerMsg = InterviewMessage(
                id = UUID.randomUUID().toString(),
                sender = "interviewer",
                text = questionText,
                timestamp = System.currentTimeMillis(),
                technicalNote = techNote,
                englishNote = engNote
            )

            if (userId.isNotBlank()) {
                saveMessageToFirestore(userId, interviewerMsg)
            }

            Result.success(Pair(interviewerMsg, nextStage))
        } catch (e: Exception) {
            Log.e("SEInterview", "CRITICAL EXCEPTION in processCandidateResponse: ${e.javaClass.name} - ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun generateReportCard(
        userId: String,
        history: List<InterviewMessage>
    ): Result<InterviewReportCard> {
        return try {
            val transcript = history.joinToString("\n") { m ->
                val role = if (m.sender == "interviewer") "Interviewer" else "Candidate"
                "$role: ${m.text}"
            }

            val prompt = """
                You are a Senior Technical Hiring Manager and English Assessment Specialist.
                Evaluate the candidate's performance in the following full mock interview transcript:

                $transcript

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

            val response = generativeModel.generateContent(prompt)
            val rawText = response.text?.trim() ?: ""
            val jsonText = rawText.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

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
                saveReportCardToFirestore(userId, reportCard)
            }

            Result.success(reportCard)
        } catch (e: Exception) {
            // Fallback report card if AI evaluation encounters an issue
            val fallbackReport = InterviewReportCard(
                technicalScore = 80,
                englishFluencyScore = 85,
                technicalFeedback = "Good effort answering technical and behavioral questions.",
                englishFluencyFeedback = "Clear communication style throughout the session.",
                strengths = listOf("Responded promptly", "Used software engineering concepts"),
                areasForImprovement = listOf("Expand further on system design details", "Practice STAR method responses")
            )
            Result.success(fallbackReport)
        }
    }

    private suspend fun saveMessageToFirestore(userId: String, msg: InterviewMessage) {
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
        } catch (_: Exception) {}
    }

    private suspend fun saveReportCardToFirestore(userId: String, report: InterviewReportCard) {
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
        } catch (_: Exception) {}
    }

    private fun generateFallbackInterviewQuestion(
        stage: InterviewStage,
        candidateText: String
    ): Triple<String, String, String> {
        return com.englishpal.app.data.datasource.InterviewQuestionBank.getQuestionForStage(stage, candidateText)
    }
}
