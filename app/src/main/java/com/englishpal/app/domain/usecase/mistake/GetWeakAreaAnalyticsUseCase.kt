package com.englishpal.app.domain.usecase.mistake

import com.englishpal.app.domain.model.CategoryPercentage
import com.englishpal.app.domain.model.MistakeItem
import com.englishpal.app.domain.model.WeakAreaSummary
import javax.inject.Inject

/**
 * Pure Domain UseCase that aggregates raw mistake items into weak-area statistics & percentages.
 * e.g. "60% of mistakes are tense-related"
 */
class GetWeakAreaAnalyticsUseCase @Inject constructor() {

    operator fun invoke(mistakes: List<MistakeItem>): WeakAreaSummary {
        if (mistakes.isEmpty()) {
            return WeakAreaSummary()
        }

        val totalCount = mistakes.size
        val categoryCounts = mistakes.groupBy { it.category }
            .mapValues { entry -> entry.value.size }

        val sortedCategories = categoryCounts.entries.sortedByDescending { it.value }
        val topCategoryEntry = sortedCategories.firstOrNull()

        val topCategory = topCategoryEntry?.key ?: "None"
        val topCount = topCategoryEntry?.value ?: 0
        val topPercentage = if (totalCount > 0) Math.round((topCount.toFloat() / totalCount) * 100) else 0

        val categoryBreakdown = sortedCategories.map { (cat, count) ->
            val pct = Math.round((count.toFloat() / totalCount) * 100)
            CategoryPercentage(
                category = cat,
                count = count,
                percentage = pct,
                progress = count.toFloat() / totalCount
            )
        }

        return WeakAreaSummary(
            totalMistakes = totalCount,
            topWeakCategory = topCategory,
            topWeakPercentage = topPercentage,
            categoryBreakdown = categoryBreakdown
        )
    }
}
