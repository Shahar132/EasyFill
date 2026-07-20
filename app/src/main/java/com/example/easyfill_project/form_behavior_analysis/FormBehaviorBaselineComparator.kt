package com.example.easyfill_project.form_behavior_analysis

import kotlin.math.abs
import kotlin.math.roundToInt

object FormBehaviorBaselineComparator {

    // Compares one field sample to the baseline.
    fun compare(
        sample: FieldBehaviorSample,
        baseline: FormBehaviorBaseline
    ): FormBehaviorComparisonResult {

        // How unusual the dwell time is.
        val dwellTimeZ = calculateZScore(
            value = sample.dwellTimeMs.toDouble(),
            average = baseline.avgDwellTimeMs,
            std = baseline.stdDwellTimeMs
        )

        // How unusual the thinking time is.
        val thinkingTimeZ = calculateZScore(
            value = sample.thinkingTimeMs.toDouble(),
            average = baseline.avgThinkingTimeMs,
            std = baseline.stdThinkingTimeMs
        )

        // How unusual the typing speed is.
        // Higher value means slower typing per inserted character.
        val typingSpeedZ = calculateZScore(
            value = sample.typingMsPerInsertedChar,
            average = baseline.avgTypingMsPerInsertedChar,
            std = baseline.stdTypingMsPerInsertedChar
        )

        // How unusual the longest idle time is.
        val idleTimeZ = calculateZScore(
            value = sample.maxIdleTimeMs.toDouble(),
            average = baseline.avgMaxIdleTimeMs,
            std = baseline.stdMaxIdleTimeMs
        )

        // How unusual the review time is.
        val reviewTimeZ = calculateZScore(
            value = sample.reviewTimeMs.toDouble(),
            average = baseline.avgReviewTimeMs,
            std = baseline.stdReviewTimeMs
        )

        // How unusual the delete ratio is.
        val deleteRatioZ = calculateZScore(
            value = sample.deleteRatio,
            average = baseline.avgDeleteRatio,
            std = baseline.stdDeleteRatio
        )

        // How unusual the number of long pauses is.
        val longPausesZ = calculateZScore(
            value = sample.longPauses.toDouble(),
            average = baseline.avgLongPauses,
            std = baseline.stdLongPauses
        )

    // Time-based signals may represent normal thinking.
        val hasTimeSignal =
            dwellTimeZ >= 2.0 ||
                    thinkingTimeZ >= 2.0 ||
                    idleTimeZ >= 2.0 ||
                    reviewTimeZ >= 2.0

    // Active difficulty requires meaningful behavior,
    // not only one isolated deletion or pause.
        val hasActiveDifficultySignal =
            typingSpeedZ >= 2.0 ||
                    (
                            deleteRatioZ >= 2.0 &&
                                    sample.deletedChars >= 2
                            ) ||
                    (
                            longPausesZ >= 2.0 &&
                                    sample.longPauses >= 2
                            ) ||
                    sample.refocusCount >= 2



        // Main metrics that can create a distress/load score.
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

        // Sum of the main warning signals.
        val primaryScore = metricScores.sumOf { it.points }

        // Supporting metrics are added only if there is already a clear primary signal.
        val supportingScore = if (primaryScore >= 25) {
            zToPoints(deleteRatioZ, maxPoints = 5) +
                    idleEventsToPoints(sample.idleEvents)
        } else {
            0
        }

        // Final score, limited between 0 and 100.
        val rawTotalScore =
            (primaryScore + supportingScore).coerceIn(0, 100)

        // Long thinking or waiting alone cannot trigger a help suggestion.
        val totalScore = if (
            hasTimeSignal &&
            !hasActiveDifficultySignal
        ) {
            rawTotalScore.coerceAtMost(15)
        } else {
            rawTotalScore
        }

        // Convert numeric score to load level.
        val level = when {
            totalScore >= 60 -> FormBehaviorLoadLevel.HIGH_LOAD
            totalScore >= 30 -> FormBehaviorLoadLevel.MODERATE_LOAD
            else -> FormBehaviorLoadLevel.NORMAL
        }

        // Finds the metric that contributed the most points.
        val topContributor = metricScores
            .maxByOrNull { it.points }
            ?.takeIf { it.points > 0 }
            ?.name
            ?: "לא נמצאה חריגה משמעותית"

        // Return the final comparison result.
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

    // Calculates how far a value is from the baseline average.
    private fun calculateZScore(
        value: Double,
        average: Double,
        std: Double
    ): Double {
        // Special handling when standard deviation is zero.
        if (std == 0.0) {
            return when {
                value <= average -> 0.0
                average == 0.0 && value > 0.0 -> 3.0
                else -> abs(value - average) / average.coerceAtLeast(1.0)
            }
        }

        return (value - average) / std
    }

    // Converts Z-score into points.
    //every Z-score is translated into a severity level

    //Z-score says how unusual the value is.
    //maxPoints says how important this parameter is (the maximum point it can get).
    //zToPoints combines both into final points.
    private fun zToPoints(
        zScore: Double,
        maxPoints: Int
    ): Int {
        val severity = when {
            //score 0 meaning under 25%
            //Within 1 SD (typical behavior, ~68%)
            zScore < 1 -> 0.0
            //25%
            //Between 1–2 SD (mild deviation)
            zScore < 2.0 -> 0.25
            // 60%
            //Between 2–3 SD (moderate deviation)
            zScore < 3.0 -> 0.6
            //100% meaning higher than 3
            // Above 3 SD (strong deviation)
            else -> 1.0
        }

        return (severity * maxPoints).roundToInt()
    }

    // Converts idle event count into extra supporting points.
    //It does not use Z-score. It gives fixed extra points.
    private fun idleEventsToPoints(idleEvents: Int): Int {
        return when {
            idleEvents >= 3 -> 15
            idleEvents == 2 -> 10
            idleEvents == 1 -> 5
            else -> 0
        }
    }

    // Small helper class that stores metric name and points.
    private data class MetricScore(
        val name: String,
        val points: Int
    )
}