package com.example.easyfill_project.form_behavior_analysis

import android.os.SystemClock
import android.util.Log
import kotlin.math.max

object FormBehaviorTrackingController {

    private const val TAG = "FORM_BEHAVIOR"

    private const val MIN_BASELINE_SAMPLES = 4

    // A typing pause is counted when the time between two text changes is longer than this.
    private const val LONG_TYPING_PAUSE_MS = 2_000L

    // Idle levels are used only for tracking/logging for now.
    // Later, these values can be compared to the user's personal baseline.

// Preliminary idle monitoring levels.
// 10 seconds is used as the first attention-related idle marker,
// based on common HCI guidance that around 10 seconds is a meaningful
// limit for maintaining user attention on a task.
// 20 and 30 seconds are progressive engineering levels for longer idle periods.
// These are not distress thresholds by themselves.
// Later, actual abnormality should be evaluated against the user's personal baseline.
    private const val IDLE_LEVEL_1_MS = 10_000L
    private const val IDLE_LEVEL_2_MS = 20_000L
    private const val IDLE_LEVEL_3_MS = 30_000L

    private val activeSessions = mutableMapOf<String, FieldBehaviorSession>()
    private val completedSamples = mutableListOf<FieldBehaviorSample>()
    private val fieldFocusCounts = mutableMapOf<String, Int>()

    private var currentBaseline: FormBehaviorBaseline? = null

    private val baselineRepository = FormBehaviorBaselineRepository()

    fun onFieldFocused(
        fieldId: String,
        currentValue: String
    ) {
        val now = SystemClock.elapsedRealtime()

        val focusCount = (fieldFocusCounts[fieldId] ?: 0) + 1
        fieldFocusCounts[fieldId] = focusCount

        activeSessions[fieldId] = FieldBehaviorSession(
            fieldId = fieldId,
            focusStartTimeMs = now,
            initialValue = currentValue,
            lastValue = currentValue,
            refocusCount = focusCount
        )

        Log.d(TAG, "Field focused: $fieldId | focusCount=$focusCount")
    }

    fun onFieldValueChanged(
        fieldId: String,
        oldValue: String,
        newValue: String
    ) {
        val now = SystemClock.elapsedRealtime()

        // If for some reason a value change arrives before focus was tracked,
        // create a session so the event is not lost.
        val session = activeSessions[fieldId] ?: FieldBehaviorSession(
            fieldId = fieldId,
            focusStartTimeMs = now,
            initialValue = oldValue,
            lastValue = oldValue,
            refocusCount = fieldFocusCounts[fieldId] ?: 1
        ).also {
            activeSessions[fieldId] = it
        }

        // First text change inside this field.
        if (session.firstEditTimeMs == null) {
            session.firstEditTimeMs = now
        }

        val previousEditTime = session.lastEditEventTimeMs
        if (previousEditTime != null) {
            val gapMs = now - previousEditTime

            // Counts long pauses between typing/editing actions.
            if (gapMs > LONG_TYPING_PAUSE_MS) {
                session.longPauses++
            }
        }

        val delta = calculateTextDelta(
            oldValue = session.lastValue,
            newValue = newValue
        )

        session.insertedChars += delta.insertedChars
        session.deletedChars += delta.deletedChars
        session.editEvents++

        session.lastEditTimeMs = now
        session.lastEditEventTimeMs = now
        session.lastValue = newValue

        // The user acted again, so idle reporting should restart from level 0.
        // This allows detecting a new idle period after the current text change.
        session.lastIdleLevelReported = 0

        Log.d(
            TAG,
            "Value changed: $fieldId | inserted=${delta.insertedChars}, deleted=${delta.deletedChars}, events=${session.editEvents}"
        )
    }

    fun checkCurrentFieldIdle(fieldId: String) {
        val now = SystemClock.elapsedRealtime()
        val session = activeSessions[fieldId] ?: return

        // If the user has typed before, idle is counted from the last edit.
        // If the user has not typed yet, idle is counted from the focus start.
        val lastActivityTimeMs = session.lastEditEventTimeMs
            ?: session.focusStartTimeMs

        val idleTimeMs = now - lastActivityTimeMs

        // Save the longest idle time inside this field session.
        if (idleTimeMs > session.maxIdleTimeMs) {
            session.maxIdleTimeMs = idleTimeMs
        }

        val idleLevel = when {
            idleTimeMs >= IDLE_LEVEL_3_MS -> 3
            idleTimeMs >= IDLE_LEVEL_2_MS -> 2
            idleTimeMs >= IDLE_LEVEL_1_MS -> 1
            else -> 0
        }

        // Report each idle level only once per idle period.
        if (idleLevel > session.lastIdleLevelReported) {
            session.lastIdleLevelReported = idleLevel
            session.idleEvents++

            Log.d(
                TAG,
                "Idle detected: $fieldId | idleLevel=$idleLevel | idleTimeMs=$idleTimeMs"
            )
        }
    }

    fun onFieldUnfocused(fieldId: String) {
        val now = SystemClock.elapsedRealtime()
        val session = activeSessions.remove(fieldId) ?: return

        val sample = buildSample(
            session = session,
            focusEndTimeMs = now
        )

        if (shouldSaveSample(sample)) {
            Log.d(
                TAG,
                "Sample saved: $sample"
            )

            // If baseline is not ready yet, use clean samples to build it.
            if (currentBaseline == null) {
                if (shouldUseSampleForBaseline(sample)) {
                    completedSamples.add(sample)

                    Log.d(
                        TAG,
                        "Sample used for baseline: ${sample.fieldId}"
                    )

                    updateBaselineIfPossible()
                } else {
                    Log.d(
                        TAG,
                        "Sample saved but not used for baseline: ${sample.fieldId}"
                    )
                }
            } else {
                // Baseline is already ready.
                // From now on, samples should be compared to the baseline, not added to it.
                Log.d(
                    TAG,
                    "Baseline already ready. Sample should be compared, not added: ${sample.fieldId}"
                )

                val baseline = currentBaseline

                if (baseline != null) {
                    val comparisonResult = FormBehaviorBaselineComparator.compare(
                        sample = sample,
                        baseline = baseline
                    )

                    // Keeps the compact log you already had.
                    Log.d(
                        TAG,
                        "Comparison result: $comparisonResult"
                    )

                    // Detailed log for testing the baseline comparison without DistressScoringManager.
                    Log.d(
                        "FORM_COMPARISON",
                        """
            Field = ${comparisonResult.fieldId}
            Score = ${comparisonResult.score}
            Level = ${comparisonResult.level}
            Top contributor = ${comparisonResult.topContributor}
            
            dwellTimeZ = ${comparisonResult.dwellTimeZ}
            thinkingTimeZ = ${comparisonResult.thinkingTimeZ}
            typingSpeedZ = ${comparisonResult.typingSpeedZ}
            idleTimeZ = ${comparisonResult.idleTimeZ}
            reviewTimeZ = ${comparisonResult.reviewTimeZ}
            deleteRatioZ = ${comparisonResult.deleteRatioZ}
            longPausesZ = ${comparisonResult.longPausesZ}
            
            shouldSuggestHelp = ${comparisonResult.shouldSuggestHelp}
            """.trimIndent()
                    )
                }
            }
        } else {
            Log.d(
                TAG,
                "Sample ignored: $fieldId | dwell=${sample.dwellTimeMs}, edits=${sample.editEvents}, inserted=${sample.insertedChars}"
            )
        }
    }

    fun getCompletedSamples(): List<FieldBehaviorSample> {
        return completedSamples.toList()
    }

    fun getBaselineSampleCount(): Int {
        return completedSamples.size
    }

    fun clear() {
        activeSessions.clear()
        completedSamples.clear()
        fieldFocusCounts.clear()
        currentBaseline = null

        Log.d(TAG, "Form behavior tracking cleared. New baseline session started.")
    }

    private fun buildSample(
        session: FieldBehaviorSession,
        focusEndTimeMs: Long
    ): FieldBehaviorSample {
        val dwellTimeMs = focusEndTimeMs - session.focusStartTimeMs

        val firstEdit = session.firstEditTimeMs
        val lastEdit = session.lastEditTimeMs

        val thinkingTimeMs = if (firstEdit != null) {
            firstEdit - session.focusStartTimeMs
        } else {
            dwellTimeMs
        }

        val typingTimeMs = if (firstEdit != null && lastEdit != null) {
            lastEdit - firstEdit
        } else {
            0L
        }

        val reviewTimeMs = if (lastEdit != null) {
            focusEndTimeMs - lastEdit
        } else {
            0L
        }

        val changedChars = max(
            1,
            session.insertedChars + session.deletedChars
        )

        val insertedCharsSafe = session.insertedChars.coerceAtLeast(1)

        return FieldBehaviorSample(
            fieldId = session.fieldId,

            dwellTimeMs = dwellTimeMs,
            thinkingTimeMs = thinkingTimeMs,
            typingTimeMs = typingTimeMs,
            reviewTimeMs = reviewTimeMs,

            insertedChars = session.insertedChars,
            deletedChars = session.deletedChars,
            changedChars = changedChars,
            editEvents = session.editEvents,
            longPauses = session.longPauses,
            refocusCount = session.refocusCount,

            maxIdleTimeMs = session.maxIdleTimeMs,
            idleEvents = session.idleEvents,

            // Time in field divided by all text changes.
            dwellMsPerChangedChar = dwellTimeMs.toDouble() / changedChars,

            // Typing time divided by all text changes.
            typingMsPerChangedChar = typingTimeMs.toDouble() / changedChars,

            // Actual writing speed based only on inserted characters.
            typingMsPerInsertedChar = typingTimeMs.toDouble() / insertedCharsSafe,

            // Deletions are a supporting metric only.
            deleteRatio = session.deletedChars.toDouble() / changedChars
        )
    }

    private fun shouldSaveSample(sample: FieldBehaviorSample): Boolean {
        return sample.editEvents >= 2 &&
                sample.insertedChars >= 2 &&
                sample.dwellTimeMs in 500L..180_000L
    }

    private fun shouldUseSampleForBaseline(sample: FieldBehaviorSample): Boolean {
        return shouldSaveSample(sample) &&
                sample.idleEvents == 0 &&
                sample.maxIdleTimeMs < 10_000L
    }
    private fun calculateTextDelta(
        oldValue: String,
        newValue: String
    ): TextDelta {
        if (oldValue == newValue) {
            return TextDelta(0, 0)
        }

        var prefix = 0
        val minLength = minOf(oldValue.length, newValue.length)

        while (
            prefix < minLength &&
            oldValue[prefix] == newValue[prefix]
        ) {
            prefix++
        }

        var oldSuffix = oldValue.length - 1
        var newSuffix = newValue.length - 1

        while (
            oldSuffix >= prefix &&
            newSuffix >= prefix &&
            oldValue[oldSuffix] == newValue[newSuffix]
        ) {
            oldSuffix--
            newSuffix--
        }

        val deleted = (oldSuffix - prefix + 1).coerceAtLeast(0)
        val inserted = (newSuffix - prefix + 1).coerceAtLeast(0)

        return TextDelta(
            insertedChars = inserted,
            deletedChars = deleted
        )
    }

    private data class TextDelta(
        val insertedChars: Int,
        val deletedChars: Int
    )




    fun getCurrentBaseline(): FormBehaviorBaseline? {
        return currentBaseline
    }

    private fun updateBaselineIfPossible() {
        if (currentBaseline != null) {
            Log.d(TAG, "Baseline already exists. Skipping recalculation.")
            return
        }

        if (completedSamples.size < MIN_BASELINE_SAMPLES) {
            Log.d(
                TAG,
                "Baseline not ready yet: ${completedSamples.size}/$MIN_BASELINE_SAMPLES samples"
            )
            return
        }

        val baseline = FormBehaviorBaselineCalculator.calculate(completedSamples)

        if (baseline == null) {
            Log.d(TAG, "Baseline calculation failed")
            return
        }

        currentBaseline = baseline

        Log.d(
            TAG,
            "Baseline created and locked: $baseline"
        )

        baselineRepository.saveBaseline(
            baseline = baseline,
            onSuccess = {
                Log.d(TAG, "Baseline saved to Firebase")
            },
            onFailure = { e ->
                Log.e(TAG, "Failed to save baseline to Firebase", e)
            }
        )
    }
}