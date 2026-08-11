package com.englishpal.app.presentation.mistakes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.englishpal.app.domain.repository.AuthRepository
import com.englishpal.app.domain.repository.MistakeRepository
import com.englishpal.app.domain.usecase.mistake.GetMistakeHistoryUseCase
import com.englishpal.app.domain.usecase.mistake.GetWeakAreaAnalyticsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
@HiltViewModel
class MistakesViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val getMistakeHistoryUseCase: GetMistakeHistoryUseCase,
    private val getWeakAreaAnalyticsUseCase: GetWeakAreaAnalyticsUseCase,
    private val mistakeRepository: MistakeRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(MistakesUiState())
    val uiState: StateFlow<MistakesUiState> = _uiState.asStateFlow()

    init {
        loadUserMistakes()
    }

    private fun loadUserMistakes() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                if (user != null && user.uid.isNotBlank()) {
                    getMistakeHistoryUseCase(user.uid).collect { list ->
                        val summary = getWeakAreaAnalyticsUseCase(list)
                        val categories = listOf("All") + list.map { it.category }.distinct()

                        _uiState.update { state ->
                            val activeCategory = state.selectedCategory
                            val filtered = if (activeCategory == "All") list
                            else list.filter { it.category == activeCategory }

                            state.copy(
                                isLoading = false,
                                mistakes = list,
                                filteredMistakes = filtered,
                                weakAreaSummary = summary,
                                availableCategories = categories
                            )
                        }
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, mistakes = emptyList()) }
                }
            }
        }
    }

    fun selectCategoryFilter(category: String) {
        _uiState.update { state ->
            val filtered = if (category == "All") state.mistakes
            else state.mistakes.filter { it.category == category }

            state.copy(
                selectedCategory = category,
                filteredMistakes = filtered
            )
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            val user = authRepository.currentUser.first()
            if (user != null && user.uid.isNotBlank()) {
                mistakeRepository.clearMistakes(user.uid)
            }
        }
    }
}
