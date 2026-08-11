package com.englishpal.app.presentation.mistakes

import com.englishpal.app.domain.model.MistakeItem
import com.englishpal.app.domain.model.WeakAreaSummary

data class MistakesUiState(
    val mistakes: List<MistakeItem> = emptyList(),
    val filteredMistakes: List<MistakeItem> = emptyList(),
    val weakAreaSummary: WeakAreaSummary = WeakAreaSummary(),
    val selectedCategory: String = "All",
    val availableCategories: List<String> = listOf("All"),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
