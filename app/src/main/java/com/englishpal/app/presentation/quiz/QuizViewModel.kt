package com.englishpal.app.presentation.quiz

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.englishpal.app.domain.model.Quiz
import com.englishpal.app.domain.usecase.quiz.GetLastQuizAttemptUseCase
import com.englishpal.app.domain.usecase.quiz.GetQuizzesUseCase
import com.englishpal.app.domain.usecase.quiz.GetRandomQuizUseCase
import com.englishpal.app.domain.usecase.quiz.SubmitQuizAnswersUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QuizViewModel @Inject constructor(
    private val getQuizzesUseCase: GetQuizzesUseCase,
    private val getRandomQuizUseCase: GetRandomQuizUseCase,
    private val getLastQuizAttemptUseCase: GetLastQuizAttemptUseCase,
    private val submitQuizAnswersUseCase: SubmitQuizAnswersUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuizUiState())
    val uiState: StateFlow<QuizUiState> = _uiState.asStateFlow()

    init {
        loadLastQuizAttempt()
        loadRandomQuiz()
    }

    private fun loadLastQuizAttempt() {
        viewModelScope.launch {
            getLastQuizAttemptUseCase().collect { attempt ->
                _uiState.update { it.copy(lastQuizAttempt = attempt) }
            }
        }
    }

    fun loadRandomQuiz() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            getRandomQuizUseCase(questionCount = 10).collect { randomQuiz ->
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        selectedQuiz = randomQuiz,
                        currentQuestionIndex = 0,
                        userAnswers = emptyMap(),
                        isSubmitted = false,
                        evaluationResult = null
                    )
                }
            }
        }
    }

    fun startNewRandomQuiz() {
        Log.d("QuizViewModel", "Generating fresh 10-question random quiz...")
        loadRandomQuiz()
    }

    fun selectQuiz(quiz: Quiz) {
        _uiState.update {
            it.copy(
                selectedQuiz = quiz,
                currentQuestionIndex = 0,
                userAnswers = emptyMap(),
                isSubmitted = false,
                evaluationResult = null,
                errorMessage = null
            )
        }
    }

    fun selectOption(questionId: String, optionIndex: Int) {
        _uiState.update { state ->
            val updatedAnswers = state.userAnswers.toMutableMap()
            updatedAnswers[questionId] = optionIndex
            state.copy(userAnswers = updatedAnswers)
        }
    }

    fun nextQuestion() {
        _uiState.update { state ->
            val quiz = state.selectedQuiz ?: return@update state
            if (state.currentQuestionIndex < quiz.questions.size - 1) {
                state.copy(currentQuestionIndex = state.currentQuestionIndex + 1)
            } else {
                state
            }
        }
    }

    fun previousQuestion() {
        _uiState.update { state ->
            if (state.currentQuestionIndex > 0) {
                state.copy(currentQuestionIndex = state.currentQuestionIndex - 1)
            } else {
                state
            }
        }
    }

    fun submitQuiz() {
        val quiz = _uiState.value.selectedQuiz ?: return
        val answers = _uiState.value.userAnswers

        if (_uiState.value.isSubmitting) return

        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                Log.d("QuizViewModel", "Submitting quiz ${quiz.id} with ${answers.size} answers")
                val result = submitQuizAnswersUseCase(quiz, answers)
                result.fold(
                    onSuccess = { eval ->
                        Log.d("QuizViewModel", "Quiz evaluation successful! Score: ${eval.score}%")
                        _uiState.update {
                            it.copy(
                                isSubmitting = false,
                                isSubmitted = true,
                                evaluationResult = eval
                            )
                        }
                    },
                    onFailure = { err ->
                        Log.e("QuizViewModel", "Quiz evaluation failed", err)
                        _uiState.update {
                            it.copy(
                                isSubmitting = false,
                                errorMessage = err.localizedMessage ?: "Failed to evaluate quiz"
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e("QuizViewModel", "Unexpected exception during quiz submission", e)
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = e.localizedMessage ?: "An unexpected error occurred."
                    )
                }
            } finally {
                _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    fun resetQuiz() {
        Log.d("QuizViewModel", "resetQuiz called: clearing state and starting a new random quiz")
        startNewRandomQuiz()
    }
}
