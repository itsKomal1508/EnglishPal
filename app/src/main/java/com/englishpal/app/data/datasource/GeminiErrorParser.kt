package com.englishpal.app.data.datasource

import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.json.JSONObject

/**
 * Structured classification of errors returned by the Google Gemini API or SDK.
 */
enum class GeminiErrorType {
    RATE_LIMIT_TEMPORARY_429,
    QUOTA_EXHAUSTED_DAILY_429,
    SERVICE_UNAVAILABLE_503,
    MODEL_NOT_FOUND_404,
    AUTH_ERROR_401_403,
    TIMEOUT,
    UNKNOWN
}

/**
 * Detailed error diagnosis object returned by [GeminiErrorParser].
 */
data class GeminiParsedError(
    val type: GeminiErrorType,
    val userMessage: String,
    val isRetryable: Boolean,
    val httpCode: Int? = null
)
/**
 * Data model for Google Gemini REST API Error responses.
 * [details] uses [JsonElement] so decoding NEVER throws a serialization exception
 * regardless of whether Google returns a list of strings or structured JSON objects.
 */
@Serializable
data class GeminiErrorPayload(
    val error: GeminiErrorDetails? = null
)

@Serializable
data class GeminiErrorDetails(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null,
    val details: JsonElement? = null
)

object GeminiErrorParser {

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * Safely parses any Exception thrown by Gemini SDK into a structured [GeminiParsedError].
     * Never leaks sensitive API keys or internal stack traces to logs or UI.
     */
    fun parseException(e: Throwable): GeminiParsedError {
        return try {
            val rawMsg = sanitizeMessage(e.message ?: e.localizedMessage ?: "")

            // 1. Try parsing structured JSON payload embedded in error string
            var extractedMessage: String? = null
            var extractedCode: Int? = null
            var extractedStatus: String? = null

            if (rawMsg.contains("{") && rawMsg.contains("}")) {
                val jsonStart = rawMsg.indexOf("{")
                val jsonEnd = rawMsg.lastIndexOf("}") + 1
                if (jsonEnd > jsonStart) {
                    val jsonSub = rawMsg.substring(jsonStart, jsonEnd)
                    try {
                        val parsed = jsonParser.decodeFromString<GeminiErrorPayload>(jsonSub)
                        parsed.error?.let { err ->
                            extractedMessage = err.message?.ifBlank { null }
                            extractedCode = err.code
                            extractedStatus = err.status
                        }
                    } catch (_: Exception) {
                        try {
                            val jsonObj = JSONObject(jsonSub)
                            val errObj = jsonObj.optJSONObject("error")
                            if (errObj != null) {
                                extractedMessage = errObj.optString("message", "").ifBlank { null }
                                extractedCode = errObj.optInt("code", 0).takeIf { it > 0 }
                                extractedStatus = errObj.optString("status", "").ifBlank { null }
                            }
                        } catch (_: Exception) {}
                    }
                }
            }

            val code = extractedCode ?: extractHttpCode(rawMsg)
            val combinedMsg = (extractedMessage ?: rawMsg).lowercase()
            val statusStr = (extractedStatus ?: "").lowercase()

            when {
                // 404 Model Endpoint Error
                code == 404 || combinedMsg.contains("404") || combinedMsg.contains("not_found") || combinedMsg.contains("model not found") -> {
                    GeminiParsedError(
                        type = GeminiErrorType.MODEL_NOT_FOUND_404,
                        userMessage = "Gemini Model Endpoint Error (404): The requested AI model version was not found. Please check application model configuration.",
                        isRetryable = false,
                        httpCode = 404
                    )
                }

                // 401 / 403 Authentication Error
                code == 401 || code == 403 || combinedMsg.contains("401") || combinedMsg.contains("403") || combinedMsg.contains("api key") || combinedMsg.contains("unauthorized") || combinedMsg.contains("permission_denied") -> {
                    GeminiParsedError(
                        type = GeminiErrorType.AUTH_ERROR_401_403,
                        userMessage = "Gemini Authentication Error: Invalid or unauthorized API key. Please check your GEMINI_API_KEY configuration.",
                        isRetryable = false,
                        httpCode = code ?: 403
                    )
                }

                // 503 Service Unavailable
                code == 503 || combinedMsg.contains("503") || combinedMsg.contains("service_unavailable") || combinedMsg.contains("overloaded") || combinedMsg.contains("server error") -> {
                    GeminiParsedError(
                        type = GeminiErrorType.SERVICE_UNAVAILABLE_503,
                        userMessage = "Gemini Service Temporarily Unavailable (503): Google's AI servers are busy or undergoing maintenance. Please wait a moment and tap Retry.",
                        isRetryable = true,
                        httpCode = 503
                    )
                }

                // 429 Quota / Rate Limit
                code == 429 || combinedMsg.contains("429") || combinedMsg.contains("quota") || combinedMsg.contains("rate limit") || combinedMsg.contains("resource_exhausted") -> {
                    val isDailyExhausted = combinedMsg.contains("per_day") || combinedMsg.contains("daily") || statusStr.contains("daily_quota") || combinedMsg.contains("quota exceeded for the day")
                    if (isDailyExhausted) {
                        GeminiParsedError(
                            type = GeminiErrorType.QUOTA_EXHAUSTED_DAILY_429,
                            userMessage = "Gemini Daily Quota Exceeded (429): Free tier daily allocation reached for this API key. Please try again tomorrow or upgrade API plan.",
                            isRetryable = false,
                            httpCode = 429
                        )
                    } else {
                        GeminiParsedError(
                            type = GeminiErrorType.RATE_LIMIT_TEMPORARY_429,
                            userMessage = "Gemini Rate Limit Reached (429): Too many requests per minute. Please wait 30-60 seconds and tap Retry.",
                            isRetryable = true,
                            httpCode = 429
                        )
                    }
                }

                // Timeout / Network Failure
                combinedMsg.contains("timed out") || combinedMsg.contains("timeout") || combinedMsg.contains("sockettimeout") || combinedMsg.contains("unknownhostexception") -> {
                    GeminiParsedError(
                        type = GeminiErrorType.TIMEOUT,
                        userMessage = "Gemini Request Timed Out: Network request took too long. Please check your internet connection and tap Retry.",
                        isRetryable = true,
                        httpCode = null
                    )
                }

                // Default Unknown Error
                else -> {
                    val userMsg = extractedMessage
                        ?: rawMsg.substringBefore("kotlinx.serialization")
                            .substringBefore("com.google.ai")
                            .trim()
                            .takeIf { it.isNotBlank() }
                        ?: "An error occurred contacting Gemini AI. Please tap Retry."
                    GeminiParsedError(
                        type = GeminiErrorType.UNKNOWN,
                        userMessage = userMsg,
                        isRetryable = false,
                        httpCode = code
                    )
                }
            }
        } catch (fallbackEx: Exception) {
            Log.e("GeminiErrorParser", "Failed to parse exception safely", fallbackEx)
            GeminiParsedError(
                type = GeminiErrorType.UNKNOWN,
                userMessage = "AI Service Error: ${e.localizedMessage ?: e.javaClass.simpleName}",
                isRetryable = false
            )
        }
    }

    fun parseExceptionToMessage(e: Throwable): String {
        return parseException(e).userMessage
    }

    private fun extractHttpCode(msg: String): Int? {
        val match = Regex("""\b(401|403|404|429|500|502|503|504)\b""").find(msg)
        return match?.value?.toIntOrNull()
    }

    private fun sanitizeMessage(msg: String): String {
        return msg.replace(Regex("""AIzaSy[A-Za-z0-9_-]{33}"""), "[REDACTED_API_KEY]")
    }
}
