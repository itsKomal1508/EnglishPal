package com.englishpal.app.data.repository

import android.util.Log
import com.englishpal.app.BuildConfig
import com.englishpal.app.domain.model.ChatMessage
import com.englishpal.app.domain.model.GrammarMistakeDetail
import com.englishpal.app.domain.model.InlineCorrection
import com.englishpal.app.domain.repository.ChatRepository
import com.englishpal.app.domain.repository.MistakeRepository
import com.englishpal.app.domain.repository.StreakRepository
import com.google.ai.client.generativeai.GenerativeModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val generativeModel: GenerativeModel,
    private val streakRepository: StreakRepository,
    private val mistakeRepository: MistakeRepository
) : ChatRepository {

    override fun getMessages(userId: String): Flow<List<ChatMessage>> = callbackFlow {
        if (userId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = firestore.collection("users")
            .document(userId)
            .collection("conversations")
            .document("partner")
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("ChatRepository", "Firestore error reading chat messages", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                if (snapshot == null || snapshot.isEmpty) {
                    trySend(emptyList())
                } else {
                    val messages = snapshot.documents.mapNotNull { doc ->
                        val id = doc.id
                        val sender = doc.getString("sender") ?: "user"
                        val text = doc.getString("text") ?: ""
                        val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()

                        val rawCorrection = doc.get("correction") as? Map<String, Any>
                        val correction = if (rawCorrection != null) {
                            InlineCorrection(
                                originalText = rawCorrection["originalText"] as? String ?: "",
                                correctedText = rawCorrection["correctedText"] as? String ?: "",
                                explanation = rawCorrection["explanation"] as? String ?: ""
                            )
                        } else null

                        ChatMessage(id, sender, text, timestamp, correction)
                    }
                    trySend(messages)
                }
            }

        awaitClose { listener.remove() }
    }

    override suspend fun sendMessage(
        userId: String,
        history: List<ChatMessage>,
        userText: String
    ): Result<ChatMessage> {
        if (userId.isBlank() || userText.isBlank()) {
            return Result.failure(IllegalArgumentException("User ID or message text invalid"))
        }

        return try {
            Log.d("GeminiConversation", "1a. ChatRepositoryImpl.sendMessage started for user: '$userId', text: '$userText'")

            val messagesRef = firestore.collection("users")
                .document(userId)
                .collection("conversations")
                .document("partner")
                .collection("messages")

            // 1. Save user message to Firestore safely
            val userMsgId = UUID.randomUUID().toString()
            val userTimestamp = System.currentTimeMillis()
            val userMsgMap = hashMapOf(
                "sender" to "user",
                "text" to userText,
                "timestamp" to userTimestamp
            )
            try {
                messagesRef.document(userMsgId).set(userMsgMap).await()
                Log.d("GeminiConversation", "1b. User message written to Firestore successfully. Msg ID: $userMsgId")
            } catch (fsEx: Exception) {
                Log.w("GeminiConversation", "User message Firestore write warning: ${fsEx.message}")
            }

            // Record streak activity for practice
            try {
                streakRepository.recordDailyActivity(userId)
            } catch (streakEx: Exception) {
                Log.w("GeminiConversation", "Streak activity recording warning: ${streakEx.message}")
            }

            // 2. Prepare Gemini prompt with full chat history and latest message
            val historyPrompt = if (history.isEmpty()) {
                "No prior messages. This is the beginning of the conversation."
            } else {
                history.takeLast(20).joinToString("\n") { msg ->
                    val senderLabel = if (msg.sender == "user") "Student" else "Tutor (EnglishPal)"
                    "$senderLabel: ${msg.text}"
                }
            }

            val prompt = """
                You are an English conversation practice partner and tutor.

                CRITICAL MANDATORY RULES:
                1. DIRECTLY RESPOND TO SPECIFIC CONTENT: You MUST directly acknowledge and engage with the SPECIFIC content, topic, and details of the user's most recent message. Never give a generic greeting or generic question unless the user's message is itself strictly a simple greeting (like "hi" or "hello").
                2. COMPREHENSIVE GRAMMAR & PHRASING CORRECTION: If the user's message contains ANY grammar, tense, spelling, vocabulary, article, or phrasing mistake (e.g. "It were good day" -> "It was a good day", "have you eat" -> "Have you eaten?", "I goed" -> "I went"), you MUST point it out and show the correction, referencing the EXACT words they used.
                   - If a mistake is found OR if the user explicitly asks to correct their English ("Correct my English if something goes wrong"), set "hasCorrection" to true and populate the "correction" object:
                     * "originalText": exact phrase containing the mistake (e.g., "It were good day")
                     * "correctedText": the correct, natural English phrase (e.g., "It was a good day")
                     * "explanation": clear 1-sentence grammar rule explanation (e.g., "Use singular past tense 'was' with 'it', and include the article 'a' before 'good day'.")
                   - If there are NO mistakes, set "hasCorrection" to false and "correction" to null.
                3. ACKNOWLEDGE EXPLICIT REQUESTS & TOPICS: Always acknowledge and engage with the actual topic/content the user shared (e.g., if they talk about sharing their day, respond directly to what they said about their day).
                4. CONVERSATIONAL MEMORY: Review the Recent Conversation Context below carefully. Build continuously on prior turns and never repeat a generic question if the user has already shared something substantive.

                Recent Conversation Context:
                $historyPrompt

                Student's Latest Message:
                "$userText"

                Return ONLY raw valid JSON (no markdown formatting, no extra code blocks):
                {
                  "reply": "Your natural response directly answering or engaging with what the student typed.",
                  "hasCorrection": false,
                  "correction": null
                }
            """.trimIndent()

            Log.d("GeminiConversation", "========== GEMINI REQUEST PAYLOAD START ==========\n$prompt\n========== GEMINI REQUEST PAYLOAD END ==========")

            // 3. Call Gemini API directly (with smart local fallback if API fails/quota exceeded)
            var aiReplyText = ""
            var inlineCorrection: InlineCorrection? = null

            val rawText = try {
                val response = generativeModel.generateContent(prompt)
                val text = response.text?.trim() ?: ""
                Log.d("GeminiConversation", "========== GEMINI RAW RESPONSE RECEIVED ==========\n$text\n==================================================")
                text
            } catch (e: Exception) {
                Log.e("GeminiConversation", "!!! GEMINI API EXCEPTION CAUGHT !!! Type: ${e.javaClass.name}, Message: ${e.message}", e)
                Log.w("GeminiConversation", "Gemini API unavailable. Falling back to local conversation engine.")
                ""
            }

            if (rawText.isNotBlank()) {
                val jsonText = rawText
                    .replace("```json", "")
                    .replace("```", "")
                    .trim()

                val startIndex = jsonText.indexOf('{')
                val endIndex = jsonText.lastIndexOf('}')
                val cleanJson = if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
                    jsonText.substring(startIndex, endIndex + 1)
                } else {
                    jsonText
                }

                aiReplyText = rawText

                try {
                    val jsonObj = JSONObject(cleanJson)
                    val replyFromObj = jsonObj.optString("reply", "").trim()
                    if (replyFromObj.isNotBlank()) {
                        aiReplyText = replyFromObj
                    }
                    val hasCorrection = jsonObj.optBoolean("hasCorrection", false)
                    if (hasCorrection && !jsonObj.isNull("correction")) {
                        val corrObj = jsonObj.optJSONObject("correction")
                        if (corrObj != null) {
                            inlineCorrection = InlineCorrection(
                                originalText = corrObj.optString("originalText", userText),
                                correctedText = corrObj.optString("correctedText", ""),
                                explanation = corrObj.optString("explanation", "")
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.w("GeminiConversation", "Failed to parse JSON response from Gemini, using raw response text", e)
                }
            }

            if (aiReplyText.isBlank()) {
                val (fallbackReply, fallbackCorr) = generateSmartFallbackResponse(userText, history)
                aiReplyText = fallbackReply
                inlineCorrection = fallbackCorr
                Log.d("GeminiConversation", "3. Smart fallback response generated: reply='$aiReplyText', correction=$inlineCorrection")
            }

            // Auto-save inline grammar corrections to MistakeRepository vault
            if (inlineCorrection != null && userId.isNotBlank()) {
                try {
                    val detail = GrammarMistakeDetail(
                        questionId = userMsgId,
                        category = "Grammar & Phrasal Usage",
                        userAnswer = inlineCorrection.originalText,
                        correctAnswer = inlineCorrection.correctedText,
                        originalSentence = userText,
                        correctedSentence = userText.replace(inlineCorrection.originalText, inlineCorrection.correctedText),
                        explanation = inlineCorrection.explanation
                    )
                    mistakeRepository.saveMistakes(userId, listOf(detail))
                    Log.d("GeminiConversation", "Logged chat grammar mistake to MistakeRepository successfully")
                } catch (mEx: Exception) {
                    Log.w("GeminiConversation", "Failed to save chat mistake to vault: ${mEx.message}")
                }
            }

            // 4. Save AI Response (with inline correction if present) to Firestore safely
            val aiMsgId = UUID.randomUUID().toString()
            val aiTimestamp = System.currentTimeMillis()
            val aiMsgMap = hashMapOf(
                "sender" to "ai",
                "text" to aiReplyText,
                "timestamp" to aiTimestamp,
                "correction" to if (inlineCorrection != null) mapOf(
                    "originalText" to inlineCorrection.originalText,
                    "correctedText" to inlineCorrection.correctedText,
                    "explanation" to inlineCorrection.explanation
                ) else null
            )
            try {
                messagesRef.document(aiMsgId).set(aiMsgMap).await()
                Log.d("GeminiConversation", "4. AI Response saved to Firestore. Msg ID: $aiMsgId")
            } catch (fsEx: Exception) {
                Log.w("GeminiConversation", "AI Response Firestore write warning: ${fsEx.message}")
            }

            val aiChatMessage = ChatMessage(
                id = aiMsgId,
                sender = "ai",
                text = aiReplyText,
                timestamp = aiTimestamp,
                correction = inlineCorrection
            )

            Result.success(aiChatMessage)
        } catch (e: Exception) {
            Log.e("GeminiConversation", "CRITICAL FAILURE in sendMessage: ${e.javaClass.name} - ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun clearChat(userId: String): Result<Unit> {
        if (userId.isBlank()) return Result.success(Unit)
        return try {
            val snapshot = firestore.collection("users")
                .document(userId)
                .collection("conversations")
                .document("partner")
                .collection("messages")
                .get()
                .await()

            val batch = firestore.batch()
            snapshot.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun generateSmartFallbackResponse(
        userText: String,
        history: List<ChatMessage>
    ): Pair<String, InlineCorrection?> {
        val trimmed = userText.trim()
        val lowerText = trimmed.lowercase()
        var correction: InlineCorrection? = null

        // ── 1. COMPREHENSIVE DYNAMIC GRAMMAR & PHRASING ENGINE ─────────────────────
        when {
            lowerText.contains("how sweet you") || lowerText.contains("sweet you are") || lowerText.contains("how sweet of you") -> {
                if (lowerText.contains("how sweet you") && !lowerText.contains("are") && !lowerText.contains("of")) {
                    correction = InlineCorrection(
                        originalText = "how sweet you",
                        correctedText = "How sweet of you!",
                        explanation = "Natural phrasing for compliments: 'How sweet of you!' or 'How sweet you are!'"
                    )
                }
            }
            lowerText.contains("cry tomorrow") || (lowerText.contains("tomorrow") && (lowerText.contains("i cry") || lowerText.contains("she cry") || lowerText.contains("he cry"))) -> {
                val orig = if (lowerText.contains("i cry")) "I cry" else "cry"
                correction = InlineCorrection(
                    originalText = "$orig tomorrow",
                    correctedText = "I will cry tomorrow",
                    explanation = "Use future tense 'will' when talking about future events ('tomorrow')."
                )
            }
            lowerText.contains("my name are") -> {
                correction = InlineCorrection(
                    originalText = "my name are",
                    correctedText = "My name is",
                    explanation = "'Name' is singular, so use 'is' instead of 'are'."
                )
            }
            lowerText.contains("it were") || lowerText.contains("were good day") || lowerText.contains("was good day") -> {
                correction = InlineCorrection(
                    originalText = if (lowerText.contains("it were good day")) "It were good day" else "It were",
                    correctedText = "It was a good day",
                    explanation = "Use singular past tense 'was' with 'it', and include the article 'a' before 'good day'."
                )
            }
            lowerText.contains("have you eat") -> {
                correction = InlineCorrection(
                    originalText = "have you eat",
                    correctedText = "Have you eaten?",
                    explanation = "Use the past participle 'eaten' (not 'eat') after 'have you' in present perfect tense."
                )
            }
            lowerText.contains("i goed") || lowerText.contains("he goed") || lowerText.contains("she goed") -> {
                val original = if (lowerText.contains("i goed")) "I goed" else if (lowerText.contains("he goed")) "he goed" else "she goed"
                val fix = original.replace("goed", "went")
                correction = InlineCorrection(
                    originalText = original,
                    correctedText = fix,
                    explanation = "'Go' is an irregular verb. The past tense of 'go' is 'went'."
                )
            }
            lowerText.contains("i am agree") || lowerText.contains("i'm agree") -> {
                correction = InlineCorrection(
                    originalText = if (lowerText.contains("i am agree")) "I am agree" else "I'm agree",
                    correctedText = "I agree",
                    explanation = "'Agree' is a verb on its own, so we say 'I agree' without 'am'."
                )
            }
            lowerText.contains("she don't") || lowerText.contains("he don't") || lowerText.contains("it don't") -> {
                val orig = if (lowerText.contains("she don't")) "she don't" else if (lowerText.contains("he don't")) "he don't" else "it don't"
                val fix = orig.replace("don't", "doesn't")
                correction = InlineCorrection(
                    originalText = orig,
                    correctedText = fix,
                    explanation = "Use 'doesn't' (not 'don't') with third-person singular subjects (he, she, it)."
                )
            }
            lowerText.contains("how to say") -> {
                correction = InlineCorrection(
                    originalText = "how to say",
                    correctedText = "How do you say...?",
                    explanation = "When asking for vocabulary, 'How do you say...?' is the natural question format."
                )
            }
            lowerText.contains("i is ") || lowerText.endsWith("i is") -> {
                correction = InlineCorrection(
                    originalText = "I is",
                    correctedText = "I am",
                    explanation = "Use 'am' with the pronoun 'I' in present tense."
                )
            }
            lowerText.contains("more better") -> {
                correction = InlineCorrection(
                    originalText = "more better",
                    correctedText = "better",
                    explanation = "'Better' is already comparative; do not add 'more'."
                )
            }
            lowerText.contains("didn't went") || lowerText.contains("did not went") -> {
                correction = InlineCorrection(
                    originalText = "didn't went",
                    correctedText = "didn't go",
                    explanation = "Use base verb form 'go' after auxiliary 'didn't'."
                )
            }
            lowerText.contains("yesterday i go") -> {
                correction = InlineCorrection(
                    originalText = "yesterday I go",
                    correctedText = "yesterday I went",
                    explanation = "Use past tense 'went' when referring to 'yesterday'."
                )
            }
        }

        // ── 2. DYNAMIC CONTEXT-AWARE RESPONSE GENERATOR ────────────────────────────
        fun extractStudentName(): String? {
            val userMessages = history.filter { it.sender == "user" }.map { it.text } + userText
            for (text in userMessages.reversed()) {
                val match = Regex("""(?:my name is|my name are|i'm|i am|call me)\s+([A-Za-z]+)""", RegexOption.IGNORE_CASE).find(text.trim())
                if (match != null) {
                    val candidate = match.groupValues[1]
                    if (candidate.lowercase() !in listOf("englishpal", "a", "an", "the", "asking", "learning", "student", "fine", "good")) {
                        return candidate.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                    }
                }
            }
            return null
        }

        val studentName = extractStudentName()
        val isGreetingOnly = lowerText.matches(Regex("""^\s*(hi|hello|hey|greetings|good morning|good evening|good afternoon)\s*!*${'$'}""", RegexOption.IGNORE_CASE))

        val reply = when {
            // Specific Intent: Compliment / Appreciation ("how sweet you")
            lowerText.contains("sweet") || lowerText.contains("kind of you") || lowerText.contains("so nice") -> {
                val namePart = if (!studentName.isNullOrBlank()) ", $studentName" else ""
                "Thank you so much$namePart! 😊 That is very kind of you to say. I really enjoy helping you practice English. What topic should we practice next?"
            }
            // Specific Intent: Sadness / Crying / Distress ("I cry tomorrow")
            lowerText.contains("cry") || lowerText.contains("sad") || lowerText.contains("upset") || lowerText.contains("unhappy") -> {
                "Oh no! Why do you feel like you will be crying? Is everything okay, or is something difficult or stressful happening?"
            }
            // Specific Intent: Name / Identity
            lowerText.contains("my name are") || lowerText.contains("my name is") || lowerText.startsWith("call me") -> {
                val name = studentName ?: "there"
                "Nice to meet you, $name! 😊 It's wonderful to practice English with you. How can I help you today?"
            }
            lowerText.contains("what is my name") || lowerText.contains("what's my name") || lowerText.contains("do you know my name") -> {
                if (!studentName.isNullOrBlank()) {
                    "Your name is $studentName! 😊 How are you doing today, $studentName?"
                } else {
                    "You haven't told me your name yet! What is your name?"
                }
            }
            // Specific Intent: Sharing Day / Daily Life
            lowerText.contains("share my day") || lowerText.contains("my day") || lowerText.contains("today i") -> {
                "I would love to hear about your day! Tell me what you did today."
            }
            // Specific Intent: Food / Eating
            lowerText.contains("have you eat") || lowerText.contains("have you eaten") || lowerText.contains("did you eat") || lowerText.contains("food") || lowerText.contains("dinner") || lowerText.contains("lunch") -> {
                "I don't eat real food since I'm an AI, but I love discussing delicious food! What is your favorite meal?"
            }
            // Specific Intent: Greetings
            isGreetingOnly -> {
                if (!studentName.isNullOrBlank()) {
                    "Hello $studentName! It's great to talk with you. What topic would you like to practice today?"
                } else {
                    "Hello! It's great to talk with you. What topic would you like to practice today?"
                }
            }
            lowerText.contains("how are you") || lowerText.contains("how's it going") -> {
                "I'm doing wonderful, thank you for asking! How is your day going so far?"
            }
            lowerText.contains("weather") || lowerText.contains("rain") || lowerText.contains("sunny") || lowerText.contains("cold") -> {
                "How is the weather where you live right now?"
            }
            lowerText.contains("thank") -> {
                "You're very welcome! Keep up the great practice. What shall we discuss next?"
            }
            // Topic-Specific Dynamic Synthesizer (never generic filler!)
            else -> {
                val keyWords = trimmed.split(" ").filter { it.length > 3 && it.lowercase() !in listOf("this", "that", "with", "from", "have", "will", "what", "where", "they", "them", "your", "more") }
                if (keyWords.isNotEmpty()) {
                    val topicWord = keyWords.last().replace(Regex("[^a-zA-Z]"), "")
                    "You mentioned '$topicWord'! Could you tell me a bit more about what you mean regarding $topicWord?"
                } else {
                    "I understand! Could you share a few more details about '${trimmed.take(30)}' in English?"
                }
            }
        }

        return Pair(reply, correction)
    }
}

