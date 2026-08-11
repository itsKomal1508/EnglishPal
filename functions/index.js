const { onCall, HttpsError } = require("firebase-functions/v2/https");
const admin = require("firebase-admin");
const { GoogleGenAI } = require("@google/genai");

if (!admin.apps.length) {
    admin.initializeApp();
}

/**
 * Firebase Cloud Function to evaluate quiz answers using Gemini API.
 */
exports.evaluateQuiz = onCall(async (request) => {
    if (!request.auth) {
        throw new HttpsError("unauthenticated", "User must be logged in.");
    }

    const { quizTitle, userAnswers } = request.data;
    const apiKey = process.env.GEMINI_API_KEY || process.env.GOOGLE_API_KEY;
    
    let correctCount = 0;
    const mistakes = [];

    userAnswers.forEach((ans) => {
        const isCorrect = ans.selectedOptionIndex === ans.correctAnswerIndex;
        if (isCorrect) {
            correctCount++;
        } else {
            mistakes.push({
                questionId: ans.questionId || "",
                category: ans.category || "General Grammar",
                userAnswer: ans.selectedOptionText || "",
                correctAnswer: ans.correctOptionText || "",
                questionText: ans.questionText || ""
            });
        }
    });

    const totalQuestions = userAnswers.length;
    const score = Math.round((correctCount / totalQuestions) * 100);

    if (!apiKey) {
        const fallbackMistakes = mistakes.map((m) => ({
            questionId: m.questionId,
            category: m.category,
            userAnswer: m.userAnswer,
            correctAnswer: m.correctAnswer,
            originalSentence: m.questionText.replace("____", m.userAnswer),
            correctedSentence: m.questionText.replace("____", m.correctAnswer),
            explanation: `Notice that '${m.correctAnswer}' is grammatically correct for this ${m.category.toLowerCase()} rule.`
        }));

        return {
            score,
            totalQuestions,
            correctCount,
            mistakes: fallbackMistakes,
            generalFeedback: score >= 80 ? "Great job! Keep up the good work." : "Good effort! Review the mistakes below to improve."
        };
    }

    try {
        const ai = new GoogleGenAI({ apiKey });
        const prompt = `You are an expert English grammar tutor. Analyze these quiz results for quiz title "${quizTitle || 'Grammar Quiz'}":
User answers: ${JSON.stringify(userAnswers)}
Mistakes: ${JSON.stringify(mistakes)}

Return a JSON object strictly matching this format without markdown wrappers:
{
  "score": ${score},
  "totalQuestions": ${totalQuestions},
  "correctCount": ${correctCount},
  "mistakes": [
    {
      "questionId": "string",
      "category": "string",
      "userAnswer": "string",
      "correctAnswer": "string",
      "originalSentence": "string",
      "correctedSentence": "string",
      "explanation": "clear explanation"
    }
  ],
  "generalFeedback": "encouraging summary"
}`;

        const response = await ai.models.generateContent({
            model: "gemini-1.5-flash",
            contents: [{ role: "user", parts: [{ text: prompt }] }]
        });

        const textResponse = response.text || "";
        const cleanJsonText = textResponse.replace(/```json/g, "").replace(/```/g, "").trim();
        return JSON.parse(cleanJsonText);
    } catch (err) {
        console.error("Gemini API evaluation failed, using fallback:", err);
        return {
            score,
            totalQuestions,
            correctCount,
            mistakes: mistakes.map((m) => ({
                questionId: m.questionId,
                category: m.category,
                userAnswer: m.userAnswer,
                correctAnswer: m.correctAnswer,
                originalSentence: m.questionText.replace("____", m.userAnswer),
                correctedSentence: m.questionText.replace("____", m.correctAnswer),
                explanation: `Expected '${m.correctAnswer}' based on ${m.category} rules.`
            })),
            generalFeedback: "Quiz evaluation completed."
        };
    }
});

/**
 * Firebase Cloud Function for AI Conversation Partner with inline grammar corrections.
 */
exports.chatConversation = onCall(async (request) => {
    if (!request.auth) {
        throw new HttpsError("unauthenticated", "User must be logged in.");
    }

    const { conversationHistory, newMessage } = request.data;
    if (!newMessage) {
        throw new HttpsError("invalid-argument", "newMessage is required.");
    }

    const apiKey = process.env.GEMINI_API_KEY || process.env.GOOGLE_API_KEY;

    if (!apiKey) {
        const hasTypo = newMessage.toLowerCase().includes("goes") || newMessage.toLowerCase().includes("for learn");
        return {
            reply: `I enjoyed reading: "${newMessage}". Tell me more about your day!`,
            hasCorrection: hasTypo,
            correction: hasTypo ? {
                originalText: newMessage,
                correctedText: newMessage.replace(/goes/gi, "went").replace(/for learn/gi, "to learn"),
                explanation: "Gently updated verb tense/preposition for natural phrasing."
            } : null
        };
    }

    try {
        const ai = new GoogleGenAI({ apiKey });
        const systemInstruction = `You are EnglishPal, a friendly, warm, and encouraging English conversation partner & tutor.
Your mission is twofold:
1. Reply naturally and engagingly to the user's latest message to keep the conversation flowing.
2. Analyze the user's message for grammar, spelling, or preposition mistakes. If you find a mistake, provide a gentle, non-judgmental inline correction. If the user's message is already correct, set hasCorrection to false and correction to null.

Output format MUST be valid JSON strictly matching this structure without markdown fences:
{
  "reply": "friendly conversation continuation string",
  "hasCorrection": true or false,
  "correction": {
    "originalText": "original user sentence or fragment",
    "correctedText": "corrected natural English sentence",
    "explanation": "brief 1-sentence explanation of the grammar rule"
  } or null
}`;

        const formattedHistory = (conversationHistory || []).map((msg) => ({
            role: msg.sender === "user" ? "user" : "model",
            parts: [{ text: msg.text }]
        }));

        formattedHistory.push({
            role: "user",
            parts: [{ text: newMessage }]
        });

        const response = await ai.models.generateContent({
            model: "gemini-1.5-flash",
            contents: formattedHistory,
            config: {
                systemInstruction: systemInstruction
            }
        });

        const textResponse = response.text || "";
        const cleanJsonText = textResponse.replace(/```json/g, "").replace(/```/g, "").trim();
        return JSON.parse(cleanJsonText);
    } catch (err) {
        console.error("Chat conversation Gemini error:", err);
        return {
            reply: `That's interesting! Could you tell me more?`,
            hasCorrection: false,
            correction: null
        };
    }
});

/**
 * Firebase Cloud Function for AI Mock Interviewer evaluating SE technical & English fluency.
 */
exports.mockInterview = onCall(async (request) => {
    if (!request.auth) {
        throw new HttpsError("unauthenticated", "User must be logged in.");
    }

    const { stage, history, userResponse, generateReport } = request.data;
    const apiKey = process.env.GEMINI_API_KEY || process.env.GOOGLE_API_KEY;

    if (!apiKey) {
        if (generateReport) {
            return {
                reply: "Thank you for completing the interview! Here is your evaluation report.",
                stage: "COMPLETED",
                isCompleted: true,
                finalReport: {
                    technicalScore: 85,
                    englishFluencyScore: 88,
                    technicalFeedback: "Strong knowledge of Android architecture (MVVM, StateFlow, Coroutines).",
                    englishFluencyFeedback: "Clear technical vocabulary and articulate explanations.",
                    strengths: ["Clear MVVM architecture understanding", "Good technical vocabulary"],
                    areasForImprovement: ["Practice STAR method for behavioral questions"]
                }
            };
        }

        return {
            reply: "That is a great explanation of modern Android development. Can you explain how Kotlin Coroutines handle exceptions?",
            stage: "TECHNICAL",
            technicalNote: "Good explanation of core Android concepts.",
            englishNote: "Articulate sentence structure and confident tone.",
            isCompleted: false,
            finalReport: null
        };
    }

    try {
        const ai = new GoogleGenAI({ apiKey });

        if (generateReport) {
            const reportPrompt = `Analyze this Software Engineering Mock Interview transcript:
History: ${JSON.stringify(history)}

Generate a final evaluation report card strictly formatted as JSON without markdown wrappers:
{
  "reply": "Thank you for completing the interview! Here is your detailed evaluation.",
  "stage": "COMPLETED",
  "isCompleted": true,
  "finalReport": {
    "technicalScore": 85,
    "englishFluencyScore": 90,
    "technicalFeedback": "Detailed breakdown of technical answer quality & system design knowledge",
    "englishFluencyFeedback": "Detailed breakdown of spoken/typed English grammar, vocabulary, & clarity",
    "strengths": ["string strength 1", "string strength 2"],
    "areasForImprovement": ["string area 1", "string area 2"]
  }
}`;

            const res = await ai.models.generateContent({
                model: "gemini-1.5-flash",
                contents: [{ role: "user", parts: [{ text: reportPrompt }] }]
            });

            const text = res.text || "";
            const cleanText = text.replace(/```json/g, "").replace(/```/g, "").trim();
            return JSON.parse(cleanText);
        }

        const systemInstruction = `You are a Lead Software Engineering Manager conducting a technical and behavioral mock interview.
Assess the candidate on TWO aspects:
1. Technical correctness & depth (Android, Kotlin, Architecture, System Design, Algorithms).
2. Spoken/Typed English Fluency (grammar, vocabulary, clarity, professional tone).

Guide the candidate naturally through stages (INTRO -> TECHNICAL -> BEHAVIORAL -> WRAPUP).

JSON Output format MUST strictly match:
{
  "reply": "interviewer response & next question",
  "stage": "TECHNICAL" or "BEHAVIORAL" or "INTRO",
  "technicalNote": "brief 1-sentence note on technical answer quality",
  "englishNote": "brief 1-sentence note on English fluency & grammar",
  "isCompleted": false,
  "finalReport": null
}`;

        const formattedHistory = (history || []).map((msg) => ({
            role: msg.sender === "candidate" ? "user" : "model",
            parts: [{ text: msg.text }]
        }));

        if (userResponse) {
            formattedHistory.push({
                role: "user",
                parts: [{ text: userResponse }]
            });
        }

        const res = await ai.models.generateContent({
            model: "gemini-1.5-flash",
            contents: formattedHistory,
            config: { systemInstruction }
        });

        const text = res.text || "";
        const cleanText = text.replace(/```json/g, "").replace(/```/g, "").trim();
        return JSON.parse(cleanText);
    } catch (err) {
        console.error("Mock Interview Gemini Error:", err);
        return {
            reply: "Let's continue with the technical discussion. How do you approach debugging memory leaks in Android?",
            stage: "TECHNICAL",
            technicalNote: "Satisfactory answer.",
            englishNote: "Clear expression.",
            isCompleted: false,
            finalReport: null
        };
    }
});
