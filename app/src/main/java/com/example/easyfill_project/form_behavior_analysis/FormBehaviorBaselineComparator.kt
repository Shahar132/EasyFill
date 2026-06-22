package com.example.easyfill_project.form_behavior_analysis

import kotlin.math.abs
import kotlin.math.roundToInt

object FormBehaviorBaselineComparator {

    fun compare(
        sample: FieldBehaviorSample,
        baseline: FormBehaviorBaseline
    ): FormBehaviorComparisonResult {

        val dwellTimeZ = calculateZScore(
            value = sample.dwellTimeMs.toDouble(),
            average = baseline.avgDwellTimeMs,
            std = baseline.stdDwellTimeMs
        )

        val thinkingTimeZ = calculateZScore(
            value = sample.thinkingTimeMs.toDouble(),
            average = baseline.avgThinkingTimeMs,
            std = baseline.stdThinkingTimeMs
        )

        val typingSpeedZ = calculateZScore(
            value = sample.typingMsPerInsertedChar,
            average = baseline.avgTypingMsPerInsertedChar,
            std = baseline.stdTypingMsPerInsertedChar
        )

        val idleTimeZ = calculateZScore(
            value = sample.maxIdleTimeMs.toDouble(),
            average = baseline.avgMaxIdleTimeMs,
            std = baseline.stdMaxIdleTimeMs
        )

        val reviewTimeZ = calculateZScore(
            value = sample.reviewTimeMs.toDouble(),
            average = baseline.avgReviewTimeMs,
            std = baseline.stdReviewTimeMs
        )

        val deleteRatioZ = calculateZScore(
            value = sample.deleteRatio,
            average = baseline.avgDeleteRatio,
            std = baseline.stdDeleteRatio
        )

        val longPausesZ = calculateZScore(
            value = sample.longPauses.toDouble(),
            average = baseline.avgLongPauses,
            std = baseline.stdLongPauses
        )

        val metricScores = listOf(
            MetricScore(
                name = "זמן שהייה ארוך בשדה",
                points = zToPoints(dwellTimeZ, maxPoints = 15)
            ),
            MetricScore(
                name = "זמן חשיבה ארוך לפני כתיבה",
                points = zToPoints(thinkingTimeZ, maxPoints = 20)
            ),
            MetricScore(
                name = "קצב כתיבה איטי מהרגיל",
                points = zToPoints(typingSpeedZ, maxPoints = 20)
            ),
            MetricScore(
                name = "חוסר פעילות / תקיעה בשדה",
                points = zToPoints(idleTimeZ, maxPoints = 25)
            ),
            MetricScore(
                name = "הפסקות ארוכות בזמן כתיבה",
                points = zToPoints(longPausesZ, maxPoints = 10)
            )
        )

        val primaryScore = metricScores.sumOf { it.points }

        // Supporting metrics should not trigger a warning by themselves.
        // They only strengthen the score if there is already a primary abnormal signal.
        val supportingScore = if (primaryScore >= 25) {
            zToPoints(reviewTimeZ, maxPoints = 5) +
                    zToPoints(deleteRatioZ, maxPoints = 5) +
                    idleEventsToPoints(sample.idleEvents)
        } else {
            0
        }

        val totalScore = (primaryScore + supportingScore).coerceIn(0, 100)

        val level = when {
            totalScore >= 60 -> FormBehaviorLoadLevel.HIGH_LOAD
            totalScore >= 30 -> FormBehaviorLoadLevel.MODERATE_LOAD
            else -> FormBehaviorLoadLevel.NORMAL
        }

        val topContributor = metricScores
            .maxByOrNull { it.points }
            ?.takeIf { it.points > 0 }
            ?.name
            ?: "לא נמצאה חריגה משמעותית"

        return FormBehaviorComparisonResult(
            fieldId = sample.fieldId,

            score = totalScore,
            level = level,

            topContributor = topContributor,

            dwellTimeZ = dwellTimeZ,
            thinkingTimeZ = thinkingTimeZ,
            typingSpeedZ = typingSpeedZ,
            idleTimeZ = idleTimeZ,
            reviewTimeZ = reviewTimeZ,
            deleteRatioZ = deleteRatioZ,
            longPausesZ = longPausesZ,

            shouldSuggestHelp = totalScore >= 30
        )
    }

    private fun calculateZScore(
        value: Double,
        average: Double,
        std: Double
    ): Double {
        if (std == 0.0) {
            return when {
                value <= average -> 0.0
                average == 0.0 && value > 0.0 -> 3.0
                else -> abs(value - average) / average.coerceAtLeast(1.0)
            }
        }

        return (value - average) / std
    }

    private fun zToPoints(
        zScore: Double,
        maxPoints: Int
    ): Int {
        val severity = when {
            zScore < 1.5 -> 0.0
            zScore < 2.0 -> 0.25
            zScore < 3.0 -> 0.6
            else -> 1.0
        }

        return (severity * maxPoints).roundToInt()
    }

    private fun idleEventsToPoints(idleEvents: Int): Int {
        return when {
            idleEvents >= 3 -> 15
            idleEvents == 2 -> 10
            idleEvents == 1 -> 5
            else -> 0
        }
    }

    private data class MetricScore(
        val name: String,
        val points: Int
    )
}