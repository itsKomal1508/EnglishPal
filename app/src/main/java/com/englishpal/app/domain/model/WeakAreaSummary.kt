package com.englishpal.app.domain.model

data class CategoryPercentage(
    val category: String,
    val count: Int,
    val percentage: Int,
    val progress: Float
)

/**
 * Domain entity holding aggregated analytics for user weak areas.
 */
data class WeakAreaSummary(
    val totalMistakes: Int = 0,
    val topWeakCategory: String = "None",
    val topWeakPercentage: Int = 0,
    val categoryBreakdown: List<CategoryPercentage> = emptyList()
)
