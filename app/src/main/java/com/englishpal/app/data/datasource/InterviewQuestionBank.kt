package com.englishpal.app.data.datasource

import com.englishpal.app.domain.model.InterviewStage
import kotlin.random.Random

/**
 * Structured Question Bank Data Source for SE Mock Interview fallback engine.
 * Provides rich, varied, topic-categorized technical and behavioral questions
 * so offline or quota-fallback mode never feels repetitive or rigid.
 */
object InterviewQuestionBank {

    private val androidQuestions = listOf(
        "Since you mentioned mobile/Android development, could you explain how Kotlin Coroutines and StateFlow manage asynchronous state in modern app architectures?",
        "Great mobile background! How do you prevent memory leaks when working with ViewModels, context references, and background operations in Android?",
        "Nice mobile focus! Could you walk me through the Android Activity lifecycle and how you handle configuration changes without losing UI state?"
    )

    private val backendQuestions = listOf(
        "Great background in backend engineering! Could you explain how database indexing works under the hood and when B-Trees are preferred over Hash indexes?",
        "Solid backend experience! How do you handle database transaction isolation levels and prevent race conditions in high-concurrency APIs?",
        "Awesome backend background! Could you explain the key differences between RESTful APIs and gRPC/GraphQL, and when you would choose one over the other?"
    )

    private val frontendQuestions = listOf(
        "Nice frontend experience! Could you explain how the Virtual DOM diffing algorithm works in React and how to prevent unnecessary component re-renders?",
        "Great web focus! How do you optimize web application performance, specifically regarding Core Web Vitals like Largest Contentful Paint (LCP)?",
        "Solid frontend background! Could you explain the concept of state management hoisting and how you handle complex client-side state?"
    )

    private val javaQuestions = listOf(
        "Solid experience! Could you explain memory management in Java, specifically how Garbage Collection algorithms clean up heap allocations?",
        "Nice Java background! Could you explain the difference between synchronized blocks, locks, and atomic variables in Java concurrency?"
    )

    private val generalSystemDesignQuestions = listOf(
        "Moving into technical depth: Could you explain the key architectural differences between Monolithic and Microservice architectures, and how you decide between them?",
        "Great background! How would you design a rate-limiting mechanism for a public API that receives millions of requests per minute?",
        "Moving to technical depth: Could you explain how caching strategies (like Read-Through and Write-Through) improve database performance in distributed systems?"
    )

    private val behavioralQuestions = listOf(
        "Could you describe a situation where you faced a difficult technical disagreement with a teammate, and how you worked together to resolve it?",
        "Could you walk me through a time when a production release broke or faced an outage, and how you managed the incident and post-mortem?",
        "Describe a project where you had to meet a tight deadline under changing requirements. How did you prioritize your technical tasks?",
        "Tell me about a technical decision you made that turned out to be a mistake. What did you learn from it and how did you fix it?"
    )

    fun getQuestionForStage(stage: InterviewStage, candidateText: String): Triple<String, String, String> {
        val lower = candidateText.lowercase().trim()
        val isNonAnswer = lower.contains("don't have idea") || lower.contains("no idea") || 
                          lower.contains("don't know") || lower.contains("not sure") || 
                          lower.contains("idk") || lower.length < 15

        return when (stage) {
            InterviewStage.TECHNICAL -> {
                val pool = when {
                    lower.contains("android") || lower.contains("kotlin") || lower.contains("mobile") -> androidQuestions
                    lower.contains("python") || lower.contains("backend") || lower.contains("api") -> backendQuestions
                    lower.contains("react") || lower.contains("frontend") || lower.contains("web") -> frontendQuestions
                    lower.contains("java") || lower.contains("spring") -> javaQuestions
                    else -> generalSystemDesignQuestions
                }
                val selectedQ = pool.random(Random(System.currentTimeMillis()))
                Triple(
                    "Thank you for introducing yourself! $selectedQ",
                    "Candidate introduced their background and technical focus areas.",
                    "Phrased engineering background clearly."
                )
            }

            InterviewStage.BEHAVIORAL -> {
                val behQuestion = behavioralQuestions.random(Random(System.currentTimeMillis()))
                if (isNonAnswer) {
                    val engTip = if (lower.contains("don't have idea")) {
                        "Phrasing tip: Use 'I don't have any idea' or 'I'm not sure' instead of 'don't have idea'."
                    } else {
                        "Phrasing tip: Express uncertainty clearly using 'I'm not sure about that concept'."
                    }
                    Triple(
                        "That's completely okay - not knowing every technical concept is a natural part of interviewing! In brief, key system design concepts involve evaluating trade-offs between performance and complexity. Moving to behavioral and team dynamics: $behQuestion",
                        "Candidate indicated unfamiliarity with the technical question.",
                        engTip
                    )
                } else {
                    Triple(
                        "Great explanation! You demonstrated solid technical understanding. Moving to behavioral and team dynamics: $behQuestion",
                        "Demonstrated solid technical understanding and hands-on experience.",
                        "Communicated technical concepts clearly with good vocabulary."
                    )
                }
            }

            InterviewStage.WRAP_UP -> {
                if (isNonAnswer) {
                    Triple(
                        "That's fine! Behavioral questions can be tricky without preparation. That completes our core interview turns today. Do you have any questions for me about our engineering team or tech stack?",
                        "Candidate gave a brief response for the behavioral turn.",
                        "Phrasing tip: Use the STAR method (Situation, Task, Action, Result) for behavioral answers."
                    )
                } else {
                    Triple(
                        "Thank you for sharing that behavioral experience! That completes our core interview questions today. Do you have any questions for me about our engineering team or tech stack?",
                        "Demonstrated good team collaboration and problem-solving structure.",
                        "Good communication style and teamwork vocabulary."
                    )
                }
            }

            else -> {
                Triple(
                    "Thank you for your responses! That concludes our mock interview session.",
                    "Interview session completed.",
                    "Overall clear presentation."
                )
            }
        }
    }
}
