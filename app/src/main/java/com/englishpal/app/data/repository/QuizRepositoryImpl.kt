package com.englishpal.app.data.repository

import android.util.Log
import com.englishpal.app.domain.model.GrammarMistakeDetail
import com.englishpal.app.domain.model.Quiz
import com.englishpal.app.domain.model.QuizAttempt
import com.englishpal.app.domain.model.QuizEvaluationResult
import com.englishpal.app.domain.model.QuizQuestion
import com.englishpal.app.domain.repository.QuizRepository
import com.google.ai.client.generativeai.GenerativeModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Concrete implementation of [QuizRepository].
 * - Contains a 45+ question bank across multiple grammar categories.
 * - Randomly selects 10 fresh questions for every new quiz.
 * - Saves and retrieves quiz attempt history in Firestore under /users/{userId}/quiz_attempts.
 */
@Singleton
class QuizRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val generativeModel: GenerativeModel
) : QuizRepository {

    override fun getQuizzes(): Flow<List<Quiz>> = callbackFlow {
        val listener = firestore.collection("quizzes")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                // If snapshot is empty OR contains legacy documents with small question count (< 10), seed full bank to Firestore
                val needsSeeding = snapshot == null || snapshot.isEmpty ||
                        snapshot.documents.any { doc ->
                            val questionsList = doc.get("questions") as? List<*>
                            questionsList == null || questionsList.size < 10
                        }

                if (needsSeeding) {
                    seedSampleQuizzes()
                }

                if (snapshot != null && !snapshot.isEmpty) {
                    val quizList = snapshot.documents.mapNotNull { doc ->
                        val id = doc.id
                        val title = doc.getString("title") ?: "Grammar Quiz"
                        val description = doc.getString("description") ?: ""
                        val category = doc.getString("category") ?: "General"
                        val rawQuestions = doc.get("questions") as? List<Map<String, Any>> ?: emptyList()

                        val questions = rawQuestions.mapIndexed { idx, q ->
                            QuizQuestion(
                                id = q["id"] as? String ?: "q_$idx",
                                questionText = q["questionText"] as? String ?: "",
                                options = (q["options"] as? List<String>) ?: emptyList(),
                                correctAnswerIndex = (q["correctAnswerIndex"] as? Long)?.toInt() ?: 0,
                                category = q["category"] as? String ?: category
                            )
                        }

                        Quiz(id, title, description, category, questions)
                    }
                    trySend(quizList)
                } else {
                    trySend(listOf(getComprehensiveQuizPool()))
                }
            }

        awaitClose { listener.remove() }
    }

    /**
     * Dynamically generates a quiz containing 10 random questions picked from the 60-question pool.
     */
    override fun getRandomQuiz(questionCount: Int): Flow<Quiz> = callbackFlow {
        val listener = firestore.collection("quizzes")
            .addSnapshotListener { snapshot, error ->
                val allQuestions = mutableListOf<QuizQuestion>()

                if (snapshot != null && !snapshot.isEmpty) {
                    snapshot.documents.forEach { doc ->
                        val rawQuestions = doc.get("questions") as? List<Map<String, Any>> ?: emptyList()
                        rawQuestions.forEachIndexed { idx, q ->
                            allQuestions += QuizQuestion(
                                id = q["id"] as? String ?: "q_${doc.id}_$idx",
                                questionText = q["questionText"] as? String ?: "",
                                options = (q["options"] as? List<String>) ?: emptyList(),
                                correctAnswerIndex = (q["correctAnswerIndex"] as? Long)?.toInt() ?: 0,
                                category = q["category"] as? String ?: "Grammar"
                            )
                        }
                    }
                }

                val pool = if (allQuestions.isNotEmpty()) {
                    allQuestions.distinctBy { it.questionText }
                } else {
                    getComprehensiveQuestionBank()
                }

                // Randomly shuffle and take specified count (default 10)
                val shuffledQuestions = pool.shuffled(Random(System.currentTimeMillis())).take(questionCount)

                val randomQuiz = Quiz(
                    id = "random_quiz_${System.currentTimeMillis()}",
                    title = "Daily English Quiz 🧠",
                    description = "10 randomized questions from our 60-question grammar bank.",
                    category = "Mixed Practice",
                    questions = shuffledQuestions
                )

                Log.d("QuizRepository", "Generated random quiz with ${shuffledQuestions.size} questions from pool of ${pool.size}")
                trySend(randomQuiz)
            }

        awaitClose { listener.remove() }
    }

    override suspend fun saveQuizAttempt(userId: String, attempt: QuizAttempt): Result<Unit> {
        return try {
            if (userId.isBlank()) return Result.success(Unit)
            val docRef = firestore.collection("users").document(userId)
                .collection("quiz_attempts").document()
            
            val attemptData = hashMapOf(
                "id" to docRef.id,
                "userId" to userId,
                "timestamp" to attempt.timestamp,
                "score" to attempt.score,
                "correctCount" to attempt.correctCount,
                "totalQuestions" to attempt.totalQuestions,
                "category" to attempt.category,
                "mistakesCount" to attempt.mistakesCount
            )

            docRef.set(attemptData).await()
            Log.d("QuizRepository", "Quiz attempt saved to Firestore successfully: score=${attempt.score}%")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("QuizRepository", "Failed to save quiz attempt to Firestore", e)
            Result.failure(e)
        }
    }

    override fun getLastQuizAttempt(userId: String): Flow<QuizAttempt?> = callbackFlow {
        if (userId.isBlank()) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val listener = firestore.collection("users").document(userId)
            .collection("quiz_attempts")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("QuizRepository", "Error reading last quiz attempt", error)
                    trySend(null)
                    return@addSnapshotListener
                }

                if (snapshot != null && !snapshot.isEmpty) {
                    val doc = snapshot.documents.first()
                    val attempt = QuizAttempt(
                        id = doc.id,
                        userId = doc.getString("userId") ?: userId,
                        timestamp = doc.getLong("timestamp") ?: 0L,
                        score = (doc.getLong("score") ?: 0L).toInt(),
                        correctCount = (doc.getLong("correctCount") ?: 0L).toInt(),
                        totalQuestions = (doc.getLong("totalQuestions") ?: 10L).toInt(),
                        category = doc.getString("category") ?: "Mixed Grammar",
                        mistakesCount = (doc.getLong("mistakesCount") ?: 0L).toInt()
                    )
                    trySend(attempt)
                } else {
                    trySend(null)
                }
            }

        awaitClose { listener.remove() }
    }

    override suspend fun evaluateQuiz(
        quiz: Quiz,
        userAnswers: Map<String, Int>
    ): Result<QuizEvaluationResult> {
        return try {
            var correctCount = 0
            data class RawMistake(
                val questionId: String,
                val category: String,
                val questionText: String,
                val userAnswer: String,
                val correctAnswer: String
            )
            val rawMistakes = mutableListOf<RawMistake>()

            quiz.questions.forEach { q ->
                val selectedIdx = userAnswers[q.id] ?: -1
                val isCorrect = selectedIdx == q.correctAnswerIndex
                if (isCorrect) {
                    correctCount++
                } else {
                    val userText = if (selectedIdx in q.options.indices) q.options[selectedIdx] else "Skipped"
                    val correctText = if (q.correctAnswerIndex in q.options.indices) q.options[q.correctAnswerIndex] else ""
                    rawMistakes += RawMistake(q.id, q.category, q.questionText, userText, correctText)
                }
            }

            val totalQuestions = quiz.questions.size
            val score = if (totalQuestions > 0) Math.round(correctCount.toFloat() / totalQuestions * 100) else 0
            val generalFeedback = if (score >= 80) "Great job! Keep up the good work." else "Good effort! Review the mistakes below to improve."

            Log.d("QuizEvaluation", "Quiz scored locally: $correctCount/$totalQuestions ($score%). Mistakes count: ${rawMistakes.size}")

            if (rawMistakes.isEmpty()) {
                return Result.success(
                    QuizEvaluationResult(
                        score = score,
                        totalQuestions = totalQuestions,
                        correctCount = correctCount,
                        mistakes = emptyList(),
                        generalFeedback = generalFeedback
                    )
                )
            }

            val mistakesSummary = rawMistakes.joinToString("\n") { m ->
                "- Question: \"${m.questionText}\" | User answered: \"${m.userAnswer}\" | Correct: \"${m.correctAnswer}\" | Category: ${m.category}"
            }

            val prompt = """
You are an expert English grammar tutor evaluating a student's quiz results.

Quiz title: "${quiz.title}"
Score: $score% ($correctCount/$totalQuestions correct)

Mistakes the student made:
$mistakesSummary

For each mistake, provide a JSON array explaining what went wrong. Return ONLY a valid JSON array (no markdown fences):
[
  {
    "questionId": "string",
    "category": "string",
    "userAnswer": "string",
    "correctAnswer": "string",
    "originalSentence": "full sentence with user's wrong word filled in",
    "correctedSentence": "full sentence with correct word filled in",
    "explanation": "clear 1-2 sentence grammar explanation"
  }
]

The questionIds in order are: ${rawMistakes.joinToString(", ") { it.questionId }}
""".trimIndent()
            Log.d("QuizEvaluation", "Sending evaluation request to Gemini API (30s timeout)...")
            val rawText = try {
                val response = withTimeout(30_000L) {
                    generativeModel.generateContent(prompt)
                }
                response.text?.trim() ?: ""
            } catch (e: Exception) {
                Log.e("QuizEvaluation", "Gemini API call encountered an issue. Falling back to local explanations.", e)
                ""
            }

            val jsonText = rawText
                .replace("```json", "")
                .replace("```", "")
                .trim()

            val mistakes = parseGeminiMistakesJson(jsonText, rawMistakes.map { m ->
                Triple(m.questionId, m.category, m.questionText to (m.userAnswer to m.correctAnswer))
            })

            Log.d("QuizEvaluation", "Quiz evaluation completed successfully.")
            Result.success(
                QuizEvaluationResult(
                    score = score,
                    totalQuestions = totalQuestions,
                    correctCount = correctCount,
                    mistakes = mistakes,
                    generalFeedback = if (score >= 80) "Great job! Keep up the good work." else "Good effort! Review the mistakes below."
                )
            )
        } catch (e: Exception) {
            Log.e("QuizEvaluation", "Error during quiz evaluation", e)
            Result.failure(e)
        }
    }

    private fun parseGeminiMistakesJson(
        json: String,
        rawMistakes: List<Triple<String, String, Pair<String, Pair<String, String>>>>
    ): List<GrammarMistakeDetail> {
        return try {
            val array = org.json.JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                GrammarMistakeDetail(
                    questionId = obj.optString("questionId", ""),
                    category = obj.optString("category", "Grammar"),
                    userAnswer = obj.optString("userAnswer", ""),
                    correctAnswer = obj.optString("correctAnswer", ""),
                    originalSentence = obj.optString("originalSentence", ""),
                    correctedSentence = obj.optString("correctedSentence", ""),
                    explanation = obj.optString("explanation", "")
                )
            }
        } catch (e: Exception) {
            Log.e("QuizEvaluation", "Failed to parse Gemini JSON output. Using local fallback explanations.", e)
            rawMistakes.map { (qId, category, pair) ->
                val (questionText, answerPair) = pair
                val (userAnswer, correctAnswer) = answerPair
                GrammarMistakeDetail(
                    questionId = qId,
                    category = category,
                    userAnswer = userAnswer,
                    correctAnswer = correctAnswer,
                    originalSentence = questionText.replace("____", userAnswer),
                    correctedSentence = questionText.replace("____", correctAnswer),
                    explanation = "The correct answer is '$correctAnswer' for this $category rule."
                )
            }
        }
    }

    private fun seedSampleQuizzes() {
        val allQuestions = getComprehensiveQuestionBank()
        val categories = allQuestions.groupBy { it.category }

        // Seed master pool document
        val masterQuiz = getComprehensiveQuizPool()
        val masterMap = hashMapOf(
            "title" to masterQuiz.title,
            "description" to masterQuiz.description,
            "category" to masterQuiz.category,
            "questions" to masterQuiz.questions.map { q ->
                mapOf(
                    "id" to q.id,
                    "questionText" to q.questionText,
                    "options" to q.options,
                    "correctAnswerIndex" to q.correctAnswerIndex,
                    "category" to q.category
                )
            }
        )
        firestore.collection("quizzes").document("master_grammar_pool").set(masterMap)

        // Seed category-specific documents
        categories.forEach { (catName, qList) ->
            val docId = catName.lowercase().replace(" ", "_").replace("-", "_") + "_quiz"
            val quizMap = hashMapOf(
                "title" to "$catName Mastery Quiz 🧠",
                "description" to "${qList.size} practice questions focusing on $catName.",
                "category" to catName,
                "questions" to qList.map { q ->
                    mapOf(
                        "id" to q.id,
                        "questionText" to q.questionText,
                        "options" to q.options,
                        "correctAnswerIndex" to q.correctAnswerIndex,
                        "category" to q.category
                    )
                }
            )
            firestore.collection("quizzes").document(docId).set(quizMap)
        }
        Log.d("QuizRepository", "Seeded ${allQuestions.size} questions across ${categories.size} category documents to Firestore.")
    }

    private fun getComprehensiveQuizPool(): Quiz {
        return Quiz(
            id = "master_grammar_pool",
            title = "Master Grammar Question Bank",
            description = "60 comprehensive questions across 5 essential English grammar categories.",
            category = "Mixed Grammar",
            questions = getComprehensiveQuestionBank()
        )
    }

    /**
     * Comprehensive Question Bank containing 60 questions across 5 categories (12 questions each).
     */
    private fun getComprehensiveQuestionBank(): List<QuizQuestion> {
        return listOf(
            // ── CATEGORY 1: Verb Tenses & Conditionals (12 Questions) ──────────
            QuizQuestion("t1", "She ____ to the grocery store yesterday afternoon.", listOf("go", "went", "gone", "going"), 1, "Tenses"),
            QuizQuestion("t2", "By this time tomorrow, we ____ our final project.", listOf("finish", "will finish", "will have finished", "finished"), 2, "Tenses"),
            QuizQuestion("t3", "They ____ in London since 2018.", listOf("are living", "have lived", "lived", "live"), 1, "Tenses"),
            QuizQuestion("t4", "If I ____ richer, I would travel the world.", listOf("am", "was", "were", "be"), 2, "Tenses"),
            QuizQuestion("t5", "Listen! Someone ____ at the front door.", listOf("knocks", "is knocking", "knocked", "has knocked"), 1, "Tenses"),
            QuizQuestion("t6", "When we arrived at the cinema, the movie ____.", listOf("already started", "has already started", "had already started", "is starting"), 2, "Tenses"),
            QuizQuestion("t7", "He usually ____ coffee in the morning, but today he is drinking tea.", listOf("drinks", "is drinking", "drank", "has drunk"), 0, "Tenses"),
            QuizQuestion("t8", "If you ____ your homework now, you can play outside.", listOf("finish", "finished", "will finish", "finishing"), 0, "Tenses"),
            QuizQuestion("t9", "She told me that she ____ to Paris twice.", listOf("is", "was", "had been", "has been"), 2, "Tenses"),
            QuizQuestion("t10", "I will call you as soon as I ____ at the airport.", listOf("arrive", "will arrive", "arrived", "am arriving"), 0, "Tenses"),
            QuizQuestion("t11", "While I ____ a book, the lights suddenly went out.", listOf("read", "was reading", "have read", "had read"), 1, "Tenses"),
            QuizQuestion("t12", "How long ____ for the bus before it finally arrived?", listOf("do you wait", "are you waiting", "had you been waiting", "have you waited"), 2, "Tenses"),

            // ── CATEGORY 2: Prepositions & Phrasal Verbs (12 Questions) ─────────
            QuizQuestion("p1", "He is very interested ____ artificial intelligence.", listOf("on", "at", "in", "with"), 2, "Prepositions"),
            QuizQuestion("p2", "We will meet ____ 5:00 PM on Friday.", listOf("at", "in", "on", "by"), 0, "Prepositions"),
            QuizQuestion("p3", "She congratulated him ____ passing his driving test.", listOf("for", "on", "about", "with"), 1, "Prepositions"),
            QuizQuestion("p4", "Are you afraid ____ spiders?", listOf("from", "with", "of", "about"), 2, "Prepositions"),
            QuizQuestion("p5", "He insisted ____ paying for the dinner.", listOf("on", "in", "for", "to"), 0, "Prepositions"),
            QuizQuestion("p6", "Please turn ____ the lights before leaving the room.", listOf("off", "out", "down", "away"), 0, "Prepositions"),
            QuizQuestion("p7", "I am looking forward ____ meeting your family.", listOf("to", "for", "at", "with"), 0, "Prepositions"),
            QuizQuestion("p8", "She divided the cake ____ four equal slices.", listOf("into", "in", "with", "among"), 0, "Prepositions"),
            QuizQuestion("p9", "The meeting was postponed ____ next Tuesday.", listOf("until", "to", "at", "on"), 0, "Prepositions"),
            QuizQuestion("p10", "He is accused ____ stealing the company documents.", listOf("with", "of", "for", "about"), 1, "Prepositions"),
            QuizQuestion("p11", "My house is situated ____ the river and the mountain.", listOf("among", "between", "along", "through"), 1, "Prepositions"),
            QuizQuestion("p12", "She depends ____ her parents for financial support.", listOf("on", "with", "at", "for"), 0, "Prepositions"),

            // ── CATEGORY 3: Articles & Determiners (12 Questions) ──────────────
            QuizQuestion("a1", "She wants to buy ____ European car.", listOf("a", "an", "the", "no article"), 0, "Articles"),
            QuizQuestion("a2", "Honesty is ____ best policy.", listOf("a", "an", "the", "no article"), 2, "Articles"),
            QuizQuestion("a3", "I had ____ apple for breakfast this morning.", listOf("a", "an", "the", "no article"), 1, "Articles"),
            QuizQuestion("a4", "____ Mount Everest is the highest mountain in the world.", listOf("A", "An", "The", "No article"), 3, "Articles"),
            QuizQuestion("a5", "Can you pass me ____ salt on the table, please?", listOf("a", "an", "the", "no article"), 2, "Articles"),
            QuizQuestion("a6", "He plays ____ guitar very well.", listOf("a", "an", "the", "no article"), 2, "Articles"),
            QuizQuestion("a7", "We need ____ information about the new flight schedule.", listOf("a", "an", "some", "many"), 2, "Articles"),
            QuizQuestion("a8", "She is studying to become ____ university professor.", listOf("a", "an", "the", "no article"), 0, "Articles"),
            QuizQuestion("a9", "____ Nile is one of the longest rivers in the world.", listOf("A", "An", "The", "No article"), 2, "Articles"),
            QuizQuestion("a10", "He turned out to be ____ honest man.", listOf("a", "an", "the", "no article"), 1, "Articles"),
            QuizQuestion("a11", "She bought ____ umbrella because it started raining.", listOf("a", "an", "the", "no article"), 1, "Articles"),
            QuizQuestion("a12", "____ sun rises in the east.", listOf("A", "An", "The", "No article"), 2, "Articles"),

            // ── CATEGORY 4: Subject-Verb Agreement (12 Questions) ──────────────
            QuizQuestion("s1", "Neither the teacher nor the students ____ present yesterday.", listOf("was", "were", "is", "are"), 1, "Subject-Verb Agreement"),
            QuizQuestion("s2", "Each of the candidates ____ interviewed individually.", listOf("was", "were", "have been", "are"), 0, "Subject-Verb Agreement"),
            QuizQuestion("s3", "Bread and butter ____ his favorite breakfast.", listOf("is", "are", "were", "have been"), 0, "Subject-Verb Agreement"),
            QuizQuestion("s4", "Ten miles ____ a long distance to walk in one day.", listOf("is", "are", "were", "be"), 0, "Subject-Verb Agreement"),
            QuizQuestion("s5", "A team of researchers ____ working on the cure.", listOf("is", "are", "were", "have"), 0, "Subject-Verb Agreement"),
            QuizQuestion("s6", "Neither of my brothers ____ a car.", listOf("own", "owns", "owning", "have owned"), 1, "Subject-Verb Agreement"),
            QuizQuestion("s7", "The news about the election ____ surprising to everyone.", listOf("was", "were", "are", "have been"), 0, "Subject-Verb Agreement"),
            QuizQuestion("s8", "Either my mother or my sisters ____ going to bake the cake.", listOf("is", "are", "was", "be"), 1, "Subject-Verb Agreement"),
            QuizQuestion("s9", "Every student and teacher ____ required to attend the seminar.", listOf("is", "are", "were", "have"), 0, "Subject-Verb Agreement"),
            QuizQuestion("s10", "The quality of these mangoes ____ not very good.", listOf("are", "is", "were", "have been"), 1, "Subject-Verb Agreement"),
            QuizQuestion("s11", "Statistics ____ a difficult subject for many students.", listOf("is", "are", "were", "be"), 0, "Subject-Verb Agreement"),
            QuizQuestion("s12", "A number of students ____ absent today.", listOf("is", "are", "was", "has been"), 1, "Subject-Verb Agreement"),

            // ── CATEGORY 5: Vocabulary & Word Choice (12 Questions) ────────────
            QuizQuestion("v1", "His explanation was so ____ that everyone understood immediately.", listOf("obscure", "lucid", "ambiguous", "complex"), 1, "Vocabulary"),
            QuizQuestion("v2", "The heavy rain caused severe ____ in the downtown area.", listOf("drought", "flooding", "erosion", "famine"), 1, "Vocabulary"),
            QuizQuestion("v3", "She showed great ____ when dealing with difficult clients.", listOf("patience", "patient", "patiently", "impatient"), 0, "Vocabulary"),
            QuizQuestion("v4", "Please ____ your signature at the bottom of the page.", listOf("affix", "effect", "affect", "except"), 0, "Vocabulary"),
            QuizQuestion("v5", "The new policy will have a positive ____ on employee morale.", listOf("affect", "effect", "effective", "effectively"), 1, "Vocabulary"),
            QuizQuestion("v6", "She is known for her ____ dedication to high-quality work.", listOf("unwavering", "hesitant", "reluctant", "careless"), 0, "Vocabulary"),
            QuizQuestion("v7", "The detective tried to ____ the truth behind the mysterious disappearance.", listOf("uncover", "conceal", "disguise", "overlook"), 0, "Vocabulary"),
            QuizQuestion("v8", "He gave a ____ response, answering the question in just two words.", listOf("verbose", "succinct", "lengthy", "elaborate"), 1, "Vocabulary"),
            QuizQuestion("v9", "The contract contains several ____ clauses that need legal clarification.", listOf("ambiguous", "clear", "obvious", "transparent"), 0, "Vocabulary"),
            QuizQuestion("v10", "Her speech was so ____ that the entire audience stood up to applaud.", listOf("inspiring", "boring", "tedious", "monotonous"), 0, "Vocabulary"),
            QuizQuestion("v11", "He tried to ____ his anger, but his clenched fists gave him away.", listOf("suppress", "express", "demonstrate", "amplify"), 0, "Vocabulary"),
            QuizQuestion("v12", "The company plans to ____ its operations into international markets.", listOf("expand", "reduce", "diminish", "shrink"), 0, "Vocabulary")
        )
    }
}
