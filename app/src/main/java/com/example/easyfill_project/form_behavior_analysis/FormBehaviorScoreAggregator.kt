package com.example.easyfill_project.form_behavior_analysis

import kotlin.math.roundToInt

object FormBehaviorScoreAggregator {

    // Combines field behavior and step navigation behavior into one overall form behavior score.
    // Field behavior is stronger because it reflects direct difficulty inside form fields.
    // Step navigation is treated as a supporting signal.
    fun aggregate(
        fieldComparisonResult: FormBehaviorComparisonResult?,
        stepNavigationResult: FormStepNavigationResult
    ): FormBehaviorOverallResult {

        val fieldScore = fieldComparisonResult?.score ?: 0
        val stepScore = stepNavigationResult.score

        val fieldTopContributor =
            fieldComparisonResult?.topContributor ?: "אין חריגה משמעותית בשדות"

        val stepTopContributor = stepNavigationResult.topContributor

        val combinedScore = calculateCombinedScore(
            fieldScore = fieldScore,
            stepScore = stepScore,
            hasFieldResult = fieldComparisonResult != null
        )

        val level = when {
            combinedScore >= 75 -> FormBehaviorLoadLevel.HIGH_LOAD
            combinedScore >= 30 -> FormBehaviorLoadLevel.MODERATE_LOAD
            else -> FormBehaviorLoadLevel.NORMAL
        }

        val topContributor = when {
            fieldScore >= stepScore && fieldScore >= 30 -> fieldTopContributor
            stepScore > fieldScore && stepScore >= 30 -> stepTopContributor
            else -> "לא נמצאה חריגה משמעותית בהתנהגות הטופס"
        }

        return FormBehaviorOverallResult(
            score = combinedScore,
            level = level,
            topContributor = topContributor,

            fieldBehaviorScore = fieldScore,
            stepNavigationScore = stepScore,

            fieldTopContributor = fieldTopContributor,
            stepTopContributor = stepTopContributor,

            shouldSuggestHelp = combinedScore >= 30
        )
    }

    private fun calculateCombinedScore(
        fieldScore: Int,
        stepScore: Int,
        hasFieldResult: Boolean
    ): Int {
        // If there is no field behavior result yet, step navigation can only create a moderate signal.
        // This prevents navigation alone from becoming a strong distress signal.
        if (!hasFieldResult) {
            return (stepScore * 0.4).roundToInt().coerceIn(0, 40)
        }

        // Field behavior gets more weight because it is based on actual interaction inside fields.
        // Step navigation supports the decision but does not dominate it.
        val weightedScore =
            fieldScore * 0.75 +
                    stepScore * 0.25

        return weightedScore.roundToInt().coerceIn(0, 100)
    }
}