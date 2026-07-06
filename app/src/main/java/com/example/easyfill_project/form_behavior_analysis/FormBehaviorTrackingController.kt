package com.example.easyfill_project.form_behavior_analysis

import android.os.SystemClock
import android.util.Log
import kotlin.math.max

import com.example.easyfill_project.distress_scoring.DistressScoringManager


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



    // Step navigation is checked within this time window.
// This helps detect repeated movement between form steps in a short period.
    private const val STEP_NAVIGATION_WINDOW_MS = 60_000L

    // A step visit shorter than this, without editing fields, is treated as a possible no-progress visit.
// This is not a distress trigger by itself, only a supporting navigation signal.
    private const val SHORT_STEP_VISIT_MS = 10_000L



    private val activeSessions = mutableMapOf<String, FieldBehaviorSession>()
    private val completedSamples = mutableListOf<FieldBehaviorSample>()
    private val fieldFocusCounts = mutableMapOf<String, Int>()

    private var currentBaseline: FormBehaviorBaseline? = null

    private val baselineRepository = FormBehaviorBaselineRepository()


    // Stores the last time the user moved between form steps.
    private var lastStepChangeTimeMs: Long? = null

    // Stores the last navigation direction.
// 1 = forward, -1 = backward, 0 = no previous direction.
    private var lastStepDirection: Int = 0

    // Stores recent step navigation events inside the navigation time window.
// This allows the score to go down after the user stops moving back and forth.
    private val recentStepNavigationEvents = mutableListOf<StepNavigationEvent>()

    // Stores fields edited since the last step change.
// This helps detect movement between steps without real progress.
    private val editedFieldsSinceLastStepChange = mutableSetOf<String>()

    // Stores the latest step navigation result so other modules can read it later.
    private var lastStepNavigationResult = FormStepNavigationResult()

    // Stores the latest field behavior comparison result.
    // It is updated after a field sample is compared to the baseline.
    private var lastFieldComparisonResult: FormBehaviorComparisonResult? = null

    // Stores the latest overall form behavior result.
    // This combines field behavior and step navigation behavior.
    private var lastOverallFormBehaviorResult = FormBehaviorOverallResult()

    private data class StepNavigationEvent(
        val timestampMs: Long,
        val wasBackward: Boolean,
        val directionChanged: Boolean,
        val noProgress: Boolean,
        val shortNoProgress: Boolean
    )

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

        // Marks this field as edited since the last step change.
        // This helps detect navigation between steps without real form progress.
        if (delta.insertedChars > 0 || delta.deletedChars > 0) {
            editedFieldsSinceLastStepChange.add(fieldId)
        }

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

                    // Keeps the compact log.
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

                    // Saves the latest field comparison result and updates the overall form behavior score.
                    lastFieldComparisonResult = comparisonResult
                    updateOverallFormBehaviorResult()
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

        // Clears step navigation behavior data for a new form session.
        lastStepChangeTimeMs = null
        lastStepDirection = 0

        recentStepNavigationEvents.clear()
        editedFieldsSinceLastStepChange.clear()
        lastStepNavigationResult = FormStepNavigationResult()

        // Clears the latest behavior results for a new form session.
        lastFieldComparisonResult = null
        lastOverallFormBehaviorResult = FormBehaviorOverallResult()

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


    // Tracks navigation behavior between form steps.
// This detects repeated backtracking, direction changes, and navigation without editing fields.
// The result is used as a supporting signal only, not as a standalone distress trigger.
    fun onStepChanged(
        fromStep: Int,
        toStep: Int
    ) {
        if (fromStep == toStep) return

        val now = SystemClock.elapsedRealtime()

        val direction = if (toStep > fromStep) {
            1 // Forward
        } else {
            -1 // Backward
        }

        val previousStepChangeTime = lastStepChangeTimeMs
        val timeSinceLastStepChange = previousStepChangeTime?.let {
            now - it
        }

        val editedFieldsCount = editedFieldsSinceLastStepChange.size

        // A direction change means forward -> backward or backward -> forward.
        val directionChanged =
            lastStepDirection != 0 &&
                    direction != lastStepDirection &&
                    timeSinceLastStepChange != null &&
                    timeSinceLastStepChange <= STEP_NAVIGATION_WINDOW_MS

        // Pure forward scanning is not counted as no-progress confusion.
        // No-progress navigation is counted only when the user moves backward
        // or changes direction without editing fields.
        val noProgress =
            editedFieldsCount == 0 &&
                    (direction == -1 || directionChanged)

        // A short no-progress visit is a quick movement away from a step without editing fields.
        // This is treated as a supporting signal only.
        val shortNoProgress =
            timeSinceLastStepChange != null &&
                    timeSinceLastStepChange <= SHORT_STEP_VISIT_MS &&
                    noProgress

        val event = StepNavigationEvent(
            timestampMs = now,
            wasBackward = direction == -1,
            directionChanged = directionChanged,
            noProgress = noProgress,
            shortNoProgress = shortNoProgress
        )

        recentStepNavigationEvents.add(event)

        // Keeps only events that happened inside the selected navigation window.
        recentStepNavigationEvents.removeAll { stepEvent ->
            now - stepEvent.timestampMs > STEP_NAVIGATION_WINDOW_MS
        }

        val backStepCount = recentStepNavigationEvents.count { it.wasBackward }
        val directionChangeCount = recentStepNavigationEvents.count { it.directionChanged }
        val noProgressStepSwitches = recentStepNavigationEvents.count { it.noProgress }
        val shortNoProgressStepVisits = recentStepNavigationEvents.count { it.shortNoProgress }
        val changesInLastWindow = recentStepNavigationEvents.size

        val score = calculateStepNavigationScore(
            backStepCount = backStepCount,
            directionChangeCount = directionChangeCount,
            noProgressStepSwitches = noProgressStepSwitches,
            shortNoProgressStepVisits = shortNoProgressStepVisits,
            changesInLastWindow = changesInLastWindow
        )

        val level = when {
            score >= 75 -> FormBehaviorLoadLevel.HIGH_LOAD
            score >= 30 -> FormBehaviorLoadLevel.MODERATE_LOAD
            else -> FormBehaviorLoadLevel.NORMAL
        }

        val topContributor = getStepNavigationTopContributor(
            backStepCount = backStepCount,
            directionChangeCount = directionChangeCount,
            noProgressStepSwitches = noProgressStepSwitches,
            shortNoProgressStepVisits = shortNoProgressStepVisits,
            changesInLastWindow = changesInLastWindow
        )

        lastStepNavigationResult = FormStepNavigationResult(
            score = score,
            level = level,
            topContributor = topContributor,

            backStepCount = backStepCount,
            directionChangeCount = directionChangeCount,
            noProgressStepSwitches = noProgressStepSwitches,
            shortNoProgressStepVisits = shortNoProgressStepVisits,
            changesInLastWindow = changesInLastWindow,

            shouldSuggestHelp = score >= 30
        )

        Log.d(
            "FORM_STEP_BEHAVIOR",
            """
        Step changed: $fromStep -> $toStep
        Direction = ${if (direction == 1) "FORWARD" else "BACKWARD"}
        Edited fields since last step = $editedFieldsCount
        
        Back steps = $backStepCount
        Direction changes = $directionChangeCount
        No progress step switches = $noProgressStepSwitches
        Short no-progress step visits = $shortNoProgressStepVisits
        Changes in last 60 seconds = $changesInLastWindow
        
        Step navigation score = $score
        Step navigation level = $level
        Top contributor = $topContributor
        Should suggest help = ${lastStepNavigationResult.shouldSuggestHelp}
        """.trimIndent()
        )

        // After a step change, start tracking edited fields for the next step interval.
        editedFieldsSinceLastStepChange.clear()

        lastStepChangeTimeMs = now
        lastStepDirection = direction
    }


    // Calculates a rule-based navigation score from 0 to 100.
// The score is based on repeated backtracking, direction changes, and navigation without progress.
// This score should be treated as a supporting signal, not as a standalone distress decision.
    private fun calculateStepNavigationScore(
        backStepCount: Int,
        directionChangeCount: Int,
        noProgressStepSwitches: Int,
        shortNoProgressStepVisits: Int,
        changesInLastWindow: Int
    ): Int {
        val backStepScore = when {
            backStepCount >= 3 -> 25
            backStepCount == 2 -> 15
            else -> 0
        }

        val directionChangeScore = when {
            directionChangeCount >= 3 -> 30
            directionChangeCount == 2 -> 20
            directionChangeCount == 1 -> 10
            else -> 0
        }

        val noProgressScore = when {
            noProgressStepSwitches >= 4 -> 25
            noProgressStepSwitches >= 2 -> 15
            else -> 0
        }

        val shortNoProgressScore = when {
            shortNoProgressStepVisits >= 3 -> 20
            shortNoProgressStepVisits >= 2 -> 10
            else -> 0
        }

        val hasNavigationProblem =
            backStepCount > 0 ||
                    directionChangeCount > 0 ||
                    noProgressStepSwitches > 0 ||
                    shortNoProgressStepVisits > 0

        val frequentNavigationScore = if (hasNavigationProblem) {
            when {
                changesInLastWindow >= 6 -> 20
                changesInLastWindow >= 4 -> 10
                else -> 0
            }
        } else {
            0
        }

        return (
                backStepScore +
                        directionChangeScore +
                        noProgressScore +
                        shortNoProgressScore +
                        frequentNavigationScore
                ).coerceIn(0, 100)
    }

    fun getLastStepNavigationResult(): FormStepNavigationResult {
        return lastStepNavigationResult
    }

    fun getLastOverallFormBehaviorResult(): FormBehaviorOverallResult {
        return lastOverallFormBehaviorResult
    }


    // Finds the main reason for the step navigation score.
// This is used for logs and later can help the chatbot explain what was detected.
    private fun getStepNavigationTopContributor(
        backStepCount: Int,
        directionChangeCount: Int,
        noProgressStepSwitches: Int,
        shortNoProgressStepVisits: Int,
        changesInLastWindow: Int
    ): String {
        return when {
            directionChangeCount >= 2 ->
                "שינויי כיוון חוזרים בין שלבים"

            noProgressStepSwitches >= 2 ->
                "מעבר בין שלבים ללא התקדמות במילוי"

            shortNoProgressStepVisits >= 2 ->
                "ביקורים קצרים בשלבים ללא עריכת שדות"

            backStepCount >= 2 ->
                "חזרה חוזרת לשלבים קודמים"

            changesInLastWindow >= 6 ->
                "ריבוי מעברים בין שלבים בזמן קצר"

            else ->
                "לא נמצאה חריגה משמעותית בניווט"
        }
    }

    // Updates the overall form behavior result by combining field behavior and step navigation behavior.
// This result is not connected to DistressScoringManager yet.
// For now, it is used only for testing and logging.
    private fun updateOverallFormBehaviorResult() {
        lastOverallFormBehaviorResult = FormBehaviorScoreAggregator.aggregate(
            fieldComparisonResult = lastFieldComparisonResult,
            stepNavigationResult = lastStepNavigationResult
        )

        // Convert form behavior score from 0–100 to global distress scale 0–4.
        val formScore0To4 = when {
            lastOverallFormBehaviorResult.score >= 75 -> 4
            lastOverallFormBehaviorResult.score >= 60 -> 3
            lastOverallFormBehaviorResult.score >= 30 -> 2
            lastOverallFormBehaviorResult.score >= 15 -> 1
            else -> 0
        }

        // Send form behavior score to the global distress scoring manager.
        DistressScoringManager.updateFormBehaviorScore(formScore0To4)

        Log.d(
            "FORM_OVERALL_BEHAVIOR",
            """
        Overall form behavior score = ${lastOverallFormBehaviorResult.score}
        Overall form behavior level = ${lastOverallFormBehaviorResult.level}
        Top contributor = ${lastOverallFormBehaviorResult.topContributor}
        
        Field behavior score = ${lastOverallFormBehaviorResult.fieldBehaviorScore}
        Step navigation score = ${lastOverallFormBehaviorResult.stepNavigationScore}
        
        Field top contributor = ${lastOverallFormBehaviorResult.fieldTopContributor}
        Step top contributor = ${lastOverallFormBehaviorResult.stepTopContributor}
        
        Converted form score 0-4 = $formScore0To4
        Should suggest help = ${lastOverallFormBehaviorResult.shouldSuggestHelp}
        """.trimIndent()
        )
    }
}