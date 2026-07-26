//package com.example.easyfill_project.form_behavior_analysis
//
//import android.os.SystemClock
//import android.util.Log
//import kotlin.math.max
//
//import com.example.easyfill_project.distress_scoring.DistressScoringManager
//
//
//object FormBehaviorTrackingController {
//
//    private const val TAG = "FORM_BEHAVIOR"
//
//    private const val MIN_BASELINE_SAMPLES = 4
//
//    // A typing pause is counted when the time between two text changes is longer than this.
//    private const val LONG_TYPING_PAUSE_MS = 2_000L
//
//    // Idle levels are used only for tracking/logging for now.
//    // Later, these values can be compared to the user's personal baseline.
//
//    // Preliminary idle monitoring levels.
//// 10 seconds is used as the first attention-related idle marker,
//// based on common HCI guidance that around 10 seconds is a meaningful
//// limit for maintaining user attention on a task.
//// 20 and 30 seconds are progressive engineering levels for longer idle periods.
//// These are not distress thresholds by themselves.
//// Later, actual abnormality should be evaluated against the user's personal baseline.
//    private const val IDLE_LEVEL_1_MS = 10_000L
//    private const val IDLE_LEVEL_2_MS = 20_000L
//    private const val IDLE_LEVEL_3_MS = 30_000L
//
//
//
//    // Step navigation is checked within this time window.
//// This helps detect repeated movement between form steps in a short period.
//    private const val STEP_NAVIGATION_WINDOW_MS = 60_000L
//
//    // A step visit shorter than this, without editing fields, is treated as a possible no-progress visit.
//// This is not a distress trigger by itself, only a supporting navigation signal.
//    private const val SHORT_STEP_VISIT_MS = 10_000L
//
//
//
//    private val activeSessions = mutableMapOf<String, FieldBehaviorSession>()
//    private val completedSamples = mutableListOf<FieldBehaviorSample>()
//    private val fieldFocusCounts = mutableMapOf<String, Int>()
//
//    private var currentBaseline: FormBehaviorBaseline? = null
//
//    private val baselineRepository = FormBehaviorBaselineRepository()
//
//
//    // Stores the last time the user moved between form steps.
//    private var lastStepChangeTimeMs: Long? = null
//
//    // Stores the last navigation direction.
//// 1 = forward, -1 = backward, 0 = no previous direction.
//    private var lastStepDirection: Int = 0
//
//    // Stores recent step navigation events inside the navigation time window.
//// This allows the score to go down after the user stops moving back and forth.
//    private val recentStepNavigationEvents = mutableListOf<StepNavigationEvent>()
//
//    // Stores fields edited since the last step change.
//// This helps detect movement between steps without real progress.
//    private val editedFieldsSinceLastStepChange = mutableSetOf<String>()
//
//    // Stores the latest step navigation result so other modules can read it later.
//    private var lastStepNavigationResult = FormStepNavigationResult()
//
//    // ID of the field that is currently focused.
//    // This prevents an old field's unfocus callback from clearing
//    // the live result of a newly focused field.
//    private var currentFocusedFieldId: String? = null
//
//    // Latest live comparison result of the currently focused field.
//    // Completed-field comparison results are never stored here.
//    private var currentFieldComparisonResult: FormBehaviorComparisonResult? = null
//
//
//
//
//    // Stores the latest overall form behavior result.
//    // This combines field behavior and step navigation behavior.
//    private var lastOverallFormBehaviorResult = FormBehaviorOverallResult()
//
//    private data class StepNavigationEvent(
//        val timestampMs: Long,
//        val wasBackward: Boolean,
//        val directionChanged: Boolean,
//        val noProgress: Boolean,
//        val shortNoProgress: Boolean
//    )
//
//    fun onFieldFocused(
//        fieldId: String,
//        currentValue: String
//    ) {
//        val now = SystemClock.elapsedRealtime()
//
//        // This field is now the only field whose live comparison
//        // is allowed to affect the current form-behavior score.
//        currentFocusedFieldId = fieldId
//
//        // A newly focused field has no live comparison result yet.
//        // Until the user types or reaches an idle threshold,
//        // the form score contains only the navigation component.
//        currentFieldComparisonResult = null
//
//        updateOverallFormBehaviorResult()
//
//        val focusCount = (fieldFocusCounts[fieldId] ?: 0) + 1
//        fieldFocusCounts[fieldId] = focusCount
//
//        activeSessions[fieldId] = FieldBehaviorSession(
//            fieldId = fieldId,
//            focusStartTimeMs = now,
//            initialValue = currentValue,
//            lastValue = currentValue,
//            refocusCount = focusCount
//        )
//
//        Log.d(
//            TAG,
//            "Field focused: $fieldId | focusCount=$focusCount"
//        )
//    }
//
//    fun onFieldValueChanged(
//        fieldId: String,
//        oldValue: String,
//        newValue: String
//    ) {
//        val now = SystemClock.elapsedRealtime()
//
//        // Ignore delayed text callbacks from a field that is no longer focused.
//        if (currentFocusedFieldId != fieldId) {
//            Log.d(
//                TAG,
//                "Ignoring value change from inactive field: $fieldId"
//            )
//            return
//        }
//
//        // If for some reason a value change arrives before focus was tracked,
//        // create a session so the event is not lost.
//        val session = activeSessions[fieldId] ?: FieldBehaviorSession(
//            fieldId = fieldId,
//            focusStartTimeMs = now,
//            initialValue = oldValue,
//            lastValue = oldValue,
//            refocusCount = fieldFocusCounts[fieldId] ?: 1
//        ).also {
//            activeSessions[fieldId] = it
//        }
//
//        // First text change inside this field.
//        if (session.firstEditTimeMs == null) {
//            session.firstEditTimeMs = now
//        }
//
//        val previousEditTime = session.lastEditEventTimeMs
//        if (previousEditTime != null) {
//            val gapMs = now - previousEditTime
//
//            // Counts long pauses between typing/editing actions.
//            if (gapMs > LONG_TYPING_PAUSE_MS) {
//                session.longPauses++
//            }
//        }
//
//        val delta = calculateTextDelta(
//            oldValue = session.lastValue,
//            newValue = newValue
//        )
//
//        // Marks this field as edited since the last step change.
//        // This helps detect navigation between steps without real form progress.
//        if (delta.insertedChars > 0 || delta.deletedChars > 0) {
//            editedFieldsSinceLastStepChange.add(fieldId)
//        }
//
//        session.insertedChars += delta.insertedChars
//        session.deletedChars += delta.deletedChars
//        session.editEvents++
//
//        session.lastEditTimeMs = now
//        session.lastEditEventTimeMs = now
//        session.lastValue = newValue
//
//        // The user acted again, so idle reporting should restart from level 0.
//        // This allows detecting a new idle period after the current text change.
//        session.lastIdleLevelReported = 0
//
//        Log.d(
//            TAG,
//            "Value changed: $fieldId | inserted=${delta.insertedChars}, deleted=${delta.deletedChars}, events=${session.editEvents}"
//        )
//
//        evaluateActiveField(fieldId)
//    }
//
//    fun checkCurrentFieldIdle(fieldId: String) {
//        // Ignore idle checks belonging to a field that is no longer focused.
//        if (currentFocusedFieldId != fieldId) {
//            return
//        }
//
//        val now = SystemClock.elapsedRealtime()
//        val session = activeSessions[fieldId] ?: return
//
//        // If the user has typed before, idle is counted from the last edit.
//        // If the user has not typed yet, idle is counted from the focus start.
//        val lastActivityTimeMs = session.lastEditEventTimeMs
//            ?: session.focusStartTimeMs
//
//        val idleTimeMs = now - lastActivityTimeMs
//
//        // Save the longest idle time inside this field session.
//        if (idleTimeMs > session.maxIdleTimeMs) {
//            session.maxIdleTimeMs = idleTimeMs
//        }
//
//        val idleLevel = when {
//            idleTimeMs >= IDLE_LEVEL_3_MS -> 3
//            idleTimeMs >= IDLE_LEVEL_2_MS -> 2
//            idleTimeMs >= IDLE_LEVEL_1_MS -> 1
//            else -> 0
//        }
//
//        // Report each idle level only once per idle period.
//        if (idleLevel > session.lastIdleLevelReported) {
//
//            // Count only one event for one continuous idle period.
//            if (session.lastIdleLevelReported == 0) {
//                session.idleEvents++
//            }
//
//            session.lastIdleLevelReported = idleLevel
//
//            Log.d(
//                TAG,
//                "Idle detected: $fieldId | idleLevel=$idleLevel | idleTimeMs=$idleTimeMs"
//            )
//
//            // Recalculate the live score as the idle duration grows.
//            evaluateActiveField(fieldId)
//        }
//    }
//
//    fun onFieldUnfocused(fieldId: String) {
//        val now = SystemClock.elapsedRealtime()
//        val session = activeSessions.remove(fieldId) ?: return
//
//        val sample = buildSample(
//            session = session,
//            focusEndTimeMs = now
//        )
//
//        if (shouldSaveSample(sample)) {
//            Log.d(TAG, "Final field sample created: $sample")
//
//            /*
//             * Completed samples are kept only while creating the personal baseline.
//             *
//             * They are not placed in a distress-scoring queue and are not compared
//             * again after the field is left.
//             */
//            if (currentBaseline == null) {
//                if (shouldUseSampleForBaseline(sample)) {
//                    completedSamples.add(sample)
//
//                    Log.d(
//                        TAG,
//                        "Sample used for baseline: ${sample.fieldId}"
//                    )
//
//                    updateBaselineIfPossible()
//                } else {
//                    Log.d(
//                        TAG,
//                        "Sample not used for baseline: ${sample.fieldId}"
//                    )
//                }
//            } else {
//                /*
//                 * Once the baseline exists, leaving the field does not create
//                 * or publish a final distress comparison.
//                 *
//                 * Distress scoring uses only the live result while the field
//                 * is actively focused.
//                 */
//                Log.d(
//                    TAG,
//                    "Field completed. No final distress score published: ${sample.fieldId}"
//                )
//            }
//        } else {
//            Log.d(
//                TAG,
//                "Sample ignored: $fieldId | " +
//                        "dwell=${sample.dwellTimeMs}, " +
//                        "edits=${sample.editEvents}, " +
//                        "inserted=${sample.insertedChars}"
//            )
//        }
//
//        /*
//         * Clear the live field score only when this is still the active field.
//         *
//         * A newly focused field must not be cleared by a delayed unfocus callback
//         * from the previous field.
//         */
//        if (currentFocusedFieldId == fieldId) {
//            currentFocusedFieldId = null
//            currentFieldComparisonResult = null
//
//            /*
//             * The field component is now zero/null.
//             * Navigation behavior remains active and is still published.
//             */
//            updateOverallFormBehaviorResult()
//        }
//    }
//
//    fun getCompletedSamples(): List<FieldBehaviorSample> {
//        return completedSamples.toList()
//    }
//
//    fun getBaselineSampleCount(): Int {
//        return completedSamples.size
//    }
//
//    fun clear() {
//        activeSessions.clear()
//        completedSamples.clear()
//        fieldFocusCounts.clear()
//        currentBaseline = null
//
//        lastStepChangeTimeMs = null
//        lastStepDirection = 0
//        recentStepNavigationEvents.clear()
//        editedFieldsSinceLastStepChange.clear()
//        lastStepNavigationResult = FormStepNavigationResult()
//
//        currentFocusedFieldId = null
//        currentFieldComparisonResult = null
//
//        lastOverallFormBehaviorResult = FormBehaviorOverallResult()
//
//        /*
//         * Form-behavior tracking is no longer active.
//         *
//         * Do not submit score 0, because 0 means the analysis was
//         * available and reliably detected no distress.
//         *
//         * Clearing means the modality is unavailable.
//         */
//        DistressScoringManager
//            .clearFormBehaviorScore()
//    }
//
//    private fun buildSample(
//        session: FieldBehaviorSession,
//        focusEndTimeMs: Long
//    ): FieldBehaviorSample {
//        val dwellTimeMs = focusEndTimeMs - session.focusStartTimeMs
//
//        val firstEdit = session.firstEditTimeMs
//        val lastEdit = session.lastEditTimeMs
//
//        val thinkingTimeMs = if (firstEdit != null) {
//            firstEdit - session.focusStartTimeMs
//        } else {
//            dwellTimeMs
//        }
//
//        val typingTimeMs = if (firstEdit != null && lastEdit != null) {
//            lastEdit - firstEdit
//        } else {
//            0L
//        }
//
//        val reviewTimeMs = if (lastEdit != null) {
//            focusEndTimeMs - lastEdit
//        } else {
//            0L
//        }
//
//        val changedChars = max(
//            1,
//            session.insertedChars + session.deletedChars
//        )
//
//        val insertedCharsSafe = session.insertedChars.coerceAtLeast(1)
//
//        return FieldBehaviorSample(
//            fieldId = session.fieldId,
//
//            dwellTimeMs = dwellTimeMs,
//            thinkingTimeMs = thinkingTimeMs,
//            typingTimeMs = typingTimeMs,
//            reviewTimeMs = reviewTimeMs,
//
//            insertedChars = session.insertedChars,
//            deletedChars = session.deletedChars,
//            changedChars = changedChars,
//            editEvents = session.editEvents,
//            longPauses = session.longPauses,
//            refocusCount = session.refocusCount,
//
//            maxIdleTimeMs = session.maxIdleTimeMs,
//            idleEvents = session.idleEvents,
//
//            // Time in field divided by all text changes.
//            dwellMsPerChangedChar = dwellTimeMs.toDouble() / changedChars,
//
//            // Typing time divided by all text changes.
//            typingMsPerChangedChar = typingTimeMs.toDouble() / changedChars,
//
//            // Actual writing speed based only on inserted characters.
//            typingMsPerInsertedChar = typingTimeMs.toDouble() / insertedCharsSafe,
//
//            // Deletions are a supporting metric only.
//            deleteRatio = session.deletedChars.toDouble() / changedChars
//        )
//    }
//
//    private fun shouldSaveSample(sample: FieldBehaviorSample): Boolean {
//        return sample.editEvents >= 2 &&
//                sample.insertedChars >= 2 &&
//                sample.dwellTimeMs in 500L..180_000L
//    }
//
//    private fun shouldUseSampleForBaseline(
//        sample: FieldBehaviorSample
//    ): Boolean {
//        return shouldSaveSample(sample) &&
//                sample.refocusCount == 1
//    }
//    private fun calculateTextDelta(
//        oldValue: String,
//        newValue: String
//    ): TextDelta {
//        if (oldValue == newValue) {
//            return TextDelta(0, 0)
//        }
//
//        var prefix = 0
//        val minLength = minOf(oldValue.length, newValue.length)
//
//        while (
//            prefix < minLength &&
//            oldValue[prefix] == newValue[prefix]
//        ) {
//            prefix++
//        }
//
//        var oldSuffix = oldValue.length - 1
//        var newSuffix = newValue.length - 1
//
//        while (
//            oldSuffix >= prefix &&
//            newSuffix >= prefix &&
//            oldValue[oldSuffix] == newValue[newSuffix]
//        ) {
//            oldSuffix--
//            newSuffix--
//        }
//
//        val deleted = (oldSuffix - prefix + 1).coerceAtLeast(0)
//        val inserted = (newSuffix - prefix + 1).coerceAtLeast(0)
//
//        return TextDelta(
//            insertedChars = inserted,
//            deletedChars = deleted
//        )
//    }
//
//    private data class TextDelta(
//        val insertedChars: Int,
//        val deletedChars: Int
//    )
//
//
//
//
//    fun getCurrentBaseline(): FormBehaviorBaseline? {
//        return currentBaseline
//    }
//
//    private fun updateBaselineIfPossible() {
//        if (currentBaseline != null) {
//            Log.d(TAG, "Baseline already exists. Skipping recalculation.")
//            return
//        }
//
//        if (completedSamples.size < MIN_BASELINE_SAMPLES) {
//            Log.d(
//                TAG,
//                "Baseline not ready yet: ${completedSamples.size}/$MIN_BASELINE_SAMPLES samples"
//            )
//            return
//        }
//
//        val baseline = FormBehaviorBaselineCalculator.calculate(completedSamples)
//
//        if (baseline == null) {
//            Log.d(TAG, "Baseline calculation failed")
//            return
//        }
//
//        currentBaseline = baseline
//
//        Log.d(
//            TAG,
//            "Baseline created and locked: $baseline"
//        )
//
//        baselineRepository.saveBaseline(
//            baseline = baseline,
//            onSuccess = {
//                Log.d(TAG, "Baseline saved to Firebase")
//            },
//            onFailure = { e ->
//                Log.e(TAG, "Failed to save baseline to Firebase", e)
//            }
//        )
//    }
//
//
//    // Tracks navigation behavior between form steps.
//    // This detects repeated backtracking, direction changes, and navigation without editing fields.
//    // The result is used as a supporting signal only, not as a standalone distress trigger.
//    fun onStepChanged(
//        fromStep: Int,
//        toStep: Int
//    ) {
//        if (fromStep == toStep) return
//
//        val now = SystemClock.elapsedRealtime()
//
//        val direction = if (toStep > fromStep) {
//            1 // Forward
//        } else {
//            -1 // Backward
//        }
//
//        val previousStepChangeTime = lastStepChangeTimeMs
//        val timeSinceLastStepChange = previousStepChangeTime?.let {
//            now - it
//        }
//
//        val editedFieldsCount = editedFieldsSinceLastStepChange.size
//
//        // A direction change means forward -> backward or backward -> forward.
//        val directionChanged =
//            lastStepDirection != 0 &&
//                    direction != lastStepDirection &&
//                    timeSinceLastStepChange != null &&
//                    timeSinceLastStepChange <= STEP_NAVIGATION_WINDOW_MS
//
//        // Pure forward scanning is not counted as no-progress confusion.
//        // No-progress navigation is counted only when the user moves backward
//        // or changes direction without editing fields.
//        val noProgress =
//            editedFieldsCount == 0 &&
//                    (direction == -1 || directionChanged)
//
//        // A short no-progress visit is a quick movement away from a step without editing fields.
//        // This is treated as a supporting signal only.
//        val shortNoProgress =
//            timeSinceLastStepChange != null &&
//                    timeSinceLastStepChange <= SHORT_STEP_VISIT_MS &&
//                    noProgress
//
//        val event = StepNavigationEvent(
//            timestampMs = now,
//            wasBackward = direction == -1,
//            directionChanged = directionChanged,
//            noProgress = noProgress,
//            shortNoProgress = shortNoProgress
//        )
//
//        recentStepNavigationEvents.add(event)
//
//        // Keeps only events that happened inside the selected navigation window.
//        recentStepNavigationEvents.removeAll { stepEvent ->
//            now - stepEvent.timestampMs > STEP_NAVIGATION_WINDOW_MS
//        }
//
//        val backStepCount = recentStepNavigationEvents.count { it.wasBackward }
//        val directionChangeCount = recentStepNavigationEvents.count { it.directionChanged }
//        val noProgressStepSwitches = recentStepNavigationEvents.count { it.noProgress }
//        val shortNoProgressStepVisits = recentStepNavigationEvents.count { it.shortNoProgress }
//        val changesInLastWindow = recentStepNavigationEvents.size
//
//        val score = calculateStepNavigationScore(
//            backStepCount = backStepCount,
//            directionChangeCount = directionChangeCount,
//            noProgressStepSwitches = noProgressStepSwitches,
//            shortNoProgressStepVisits = shortNoProgressStepVisits,
//            changesInLastWindow = changesInLastWindow
//        )
//
//        val level = when {
//            score >= 75 -> FormBehaviorLoadLevel.HIGH_LOAD
//            score >= 30 -> FormBehaviorLoadLevel.MODERATE_LOAD
//            else -> FormBehaviorLoadLevel.NORMAL
//        }
//
//        val topContributor = getStepNavigationTopContributor(
//            backStepCount = backStepCount,
//            directionChangeCount = directionChangeCount,
//            noProgressStepSwitches = noProgressStepSwitches,
//            shortNoProgressStepVisits = shortNoProgressStepVisits,
//            changesInLastWindow = changesInLastWindow
//        )
//
//        lastStepNavigationResult = FormStepNavigationResult(
//            score = score,
//            level = level,
//            topContributor = topContributor,
//
//            backStepCount = backStepCount,
//            directionChangeCount = directionChangeCount,
//            noProgressStepSwitches = noProgressStepSwitches,
//            shortNoProgressStepVisits = shortNoProgressStepVisits,
//            changesInLastWindow = changesInLastWindow,
//
//            shouldSuggestHelp = score >= 30
//        )
//
//        Log.d(
//            "FORM_STEP_BEHAVIOR",
//            """
//        Step changed: $fromStep -> $toStep
//        Direction = ${if (direction == 1) "FORWARD" else "BACKWARD"}
//        Edited fields since last step = $editedFieldsCount
//
//        Back steps = $backStepCount
//        Direction changes = $directionChangeCount
//        No progress step switches = $noProgressStepSwitches
//        Short no-progress step visits = $shortNoProgressStepVisits
//        Changes in last 60 seconds = $changesInLastWindow
//
//        Step navigation score = $score
//        Step navigation level = $level
//        Top contributor = $topContributor
//        Should suggest help = ${lastStepNavigationResult.shouldSuggestHelp}
//        """.trimIndent()
//        )
//
//        // After a step change, start tracking edited fields for the next step interval.
//        editedFieldsSinceLastStepChange.clear()
//
//        lastStepChangeTimeMs = now
//        lastStepDirection = direction
//
//        // Publish the updated navigation behavior immediately.
//        updateOverallFormBehaviorResult()
//    }
//
//
//    // Calculates a rule-based navigation score from 0 to 100.
//    // The score is based on repeated backtracking, direction changes, and navigation without progress.
//    // This score should be treated as a supporting signal, not as a standalone distress decision.
//    private fun calculateStepNavigationScore(
//        backStepCount: Int,
//        directionChangeCount: Int,
//        noProgressStepSwitches: Int,
//        shortNoProgressStepVisits: Int,
//        changesInLastWindow: Int
//    ): Int {
//        val backStepScore = when {
//            backStepCount >= 3 -> 25
//            backStepCount == 2 -> 15
//            else -> 0
//        }
//
//        val directionChangeScore = when {
//            directionChangeCount >= 3 -> 30
//            directionChangeCount == 2 -> 20
//            directionChangeCount == 1 -> 10
//            else -> 0
//        }
//
//        val noProgressScore = when {
//            noProgressStepSwitches >= 4 -> 25
//            noProgressStepSwitches >= 2 -> 15
//            else -> 0
//        }
//
//        val shortNoProgressScore = when {
//            shortNoProgressStepVisits >= 3 -> 20
//            shortNoProgressStepVisits >= 2 -> 10
//            else -> 0
//        }
//
//        val hasNavigationProblem =
//            backStepCount > 0 ||
//                    directionChangeCount > 0 ||
//                    noProgressStepSwitches > 0 ||
//                    shortNoProgressStepVisits > 0
//
//        val frequentNavigationScore = if (hasNavigationProblem) {
//            when {
//                changesInLastWindow >= 6 -> 20
//                changesInLastWindow >= 4 -> 10
//                else -> 0
//            }
//        } else {
//            0
//        }
//
//        return (
//                backStepScore +
//                        directionChangeScore +
//                        noProgressScore +
//                        shortNoProgressScore +
//                        frequentNavigationScore
//                ).coerceIn(0, 100)
//    }
//
//    fun getLastStepNavigationResult(): FormStepNavigationResult {
//        return lastStepNavigationResult
//    }
//
//    fun getLastOverallFormBehaviorResult(): FormBehaviorOverallResult {
//        return lastOverallFormBehaviorResult
//    }
//
//
//    // Finds the main reason for the step navigation score.
//// This is used for logs and later can help the chatbot explain what was detected.
//    private fun getStepNavigationTopContributor(
//        backStepCount: Int,
//        directionChangeCount: Int,
//        noProgressStepSwitches: Int,
//        shortNoProgressStepVisits: Int,
//        changesInLastWindow: Int
//    ): String {
//        return when {
//            directionChangeCount >= 2 ->
//                "שינויי כיוון חוזרים בין שלבים"
//
//            noProgressStepSwitches >= 2 ->
//                "מעבר בין שלבים ללא התקדמות במילוי"
//
//            shortNoProgressStepVisits >= 2 ->
//                "ביקורים קצרים בשלבים ללא עריכת שדות"
//
//            backStepCount >= 2 ->
//                "חזרה חוזרת לשלבים קודמים"
//
//            changesInLastWindow >= 6 ->
//                "ריבוי מעברים בין שלבים בזמן קצר"
//
//            else ->
//                "לא נמצאה חריגה משמעותית בניווט"
//        }
//    }
//
//
//    /*
// * Publishes the current form-behavior snapshot.
// *
// * The snapshot combines:
// * 1. the newest live comparison of the currently focused field;
// * 2. the recent step-navigation result.
// *
// * Completed field comparisons are not queued, retained, or published.
// */
//    private fun updateOverallFormBehaviorResult() {
//
//        lastOverallFormBehaviorResult =
//            FormBehaviorScoreAggregator.aggregate(
//                fieldComparisonResult = currentFieldComparisonResult,
//                stepNavigationResult = lastStepNavigationResult
//            )
//
//        val formScore0To4 = when {
//            lastOverallFormBehaviorResult.score >= 75 -> 4
//            lastOverallFormBehaviorResult.score >= 60 -> 3
//            lastOverallFormBehaviorResult.score >= 30 -> 2
//            lastOverallFormBehaviorResult.score >= 15 -> 1
//            else -> 0
//        }
//
//        DistressScoringManager.updateFormBehaviorScore(formScore0To4)
//
//        Log.d(
//            "FORM_OVERALL_BEHAVIOR",
//            """
//        Overall form behavior score = ${lastOverallFormBehaviorResult.score}
//        Overall form behavior level = ${lastOverallFormBehaviorResult.level}
//        Top contributor = ${lastOverallFormBehaviorResult.topContributor}
//
//        Current focused field = $currentFocusedFieldId
//        Has live field result = ${currentFieldComparisonResult != null}
//
//        Live field behavior score = ${lastOverallFormBehaviorResult.fieldBehaviorScore}
//        Step navigation score = ${lastOverallFormBehaviorResult.stepNavigationScore}
//
//        Converted form score 0-4 = $formScore0To4
//        Should suggest help = ${lastOverallFormBehaviorResult.shouldSuggestHelp}
//        """.trimIndent()
//        )
//    }
//
//
//    private fun evaluateActiveField(fieldId: String) {
//        // Only the currently focused field may publish a live comparison.
//        if (currentFocusedFieldId != fieldId) {
//            return
//        }
//
//        val baseline = currentBaseline ?: return
//        val session = activeSessions[fieldId] ?: return
//
//        val sample = buildSample(
//            session = session,
//            focusEndTimeMs = SystemClock.elapsedRealtime()
//        )
//
//        // Avoid evaluating an extremely short interaction.
//        if (sample.dwellTimeMs < 500L) {
//            return
//        }
//
//        val comparisonResult = FormBehaviorBaselineComparator.compare(
//            sample = sample,
//            baseline = baseline
//        )
//
//        /*
//         * Replace the previous live result.
//         *
//         * There is no queue: only the latest comparison of the active field
//         * is retained.
//         */
//        currentFieldComparisonResult = comparisonResult
//
//        Log.d(
//            "FORM_LIVE_COMPARISON",
//            """
//        Field = ${comparisonResult.fieldId}
//        Live score = ${comparisonResult.score}
//        Live level = ${comparisonResult.level}
//        Top contributor = ${comparisonResult.topContributor}
//        """.trimIndent()
//        )
//
//        updateOverallFormBehaviorResult()
//    }
//
//}

//////////////////////////////////


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



    private val activeSessions =
        mutableMapOf<String, FieldBehaviorSession>()

    /*
     * Stores only the valid baseline samples collected during
     * the current form entry.
     *
     * Historical samples are represented by the accumulated
     * baseline statistics and are not kept in memory or Firebase.
     */
    private val completedSamples =
        mutableListOf<FieldBehaviorSample>()

    private val fieldFocusCounts =
        mutableMapOf<String, Int>()

    /*
     * Baseline loaded at the beginning of the current form
     * entry.
     *
     * When an accumulated update is saved, this object remains
     * unchanged until the next form entry so all live comparisons
     * in the current form use one stable reference.
     */
    private var currentBaseline:
            FormBehaviorBaseline? = null

    private val baselineRepository =
        FormBehaviorBaselineRepository()

    /*
     * Baseline loading and updating state for the current form
     * entry.
     */
    private var baselineLoadCompleted = false
    private var baselineUpdateCompletedForSession = false
    private var baselineSaveInProgress = false

    /*
     * Invalidates delayed Firebase callbacks from a previous
     * form entry.
     */
    private var sessionGeneration = 0L


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

    // ID of the field that is currently focused.
    // This prevents an old field's unfocus callback from clearing
    // the live result of a newly focused field.
    private var currentFocusedFieldId: String? = null

    // Latest live comparison result of the currently focused field.
    // Completed-field comparison results are never stored here.
    private var currentFieldComparisonResult: FormBehaviorComparisonResult? = null




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

    /*
     * Starts one form-behavior tracking session.
     *
     * Call this once when the form screen is entered.
     *
     * The saved baseline is loaded immediately and remains fixed
     * for all live comparisons during this form entry.
     */
    fun startFormSession() {
        sessionGeneration += 1L

        val generation =
            sessionGeneration

        resetFormSessionState()

        Log.d(
            TAG,
            "Starting form behavior session and loading baseline"
        )

        baselineRepository.getBaseline(
            onSuccess = { data ->

                if (generation != sessionGeneration) {
                    return@getBaseline
                }

                currentBaseline =
                    mapToBaseline(
                        data = data
                    )

                baselineLoadCompleted =
                    true

                val loadedBaseline =
                    currentBaseline

                if (loadedBaseline != null) {
                    Log.d(
                        TAG,
                        """
                        Accumulated form behavior baseline loaded.
                        sessions=${loadedBaseline.validSessionCount}
                        samples=${loadedBaseline.sampleCount}
                        """.trimIndent()
                    )
                } else {
                    Log.d(
                        TAG,
                        """
                        No compatible saved form behavior baseline exists.
                        The first $MIN_BASELINE_SAMPLES valid samples from
                        this form entry will create it.
                        """.trimIndent()
                    )
                }

                /*
                 * Samples may have been completed while Firebase
                 * was loading.
                 */
                updateBaselineIfPossible()
            },

            onFailure = { error ->

                if (generation != sessionGeneration) {
                    return@getBaseline
                }

                baselineLoadCompleted =
                    true

                currentBaseline =
                    null

                Log.e(
                    TAG,
                    """
                    Failed to load the saved form behavior baseline.
                    A local baseline can still be created from this
                    form entry.
                    """.trimIndent(),
                    error
                )

                updateBaselineIfPossible()
            }
        )
    }


    fun onFieldFocused(
        fieldId: String,
        currentValue: String
    ) {
        val now = SystemClock.elapsedRealtime()

        // This field is now the only field whose live comparison
        // is allowed to affect the current form-behavior score.
        currentFocusedFieldId = fieldId

        // A newly focused field has no live comparison result yet.
        // Until the user types or reaches an idle threshold,
        // the form score contains only the navigation component.
        currentFieldComparisonResult = null

        updateOverallFormBehaviorResult()

        val focusCount = (fieldFocusCounts[fieldId] ?: 0) + 1
        fieldFocusCounts[fieldId] = focusCount

        activeSessions[fieldId] = FieldBehaviorSession(
            fieldId = fieldId,
            focusStartTimeMs = now,
            initialValue = currentValue,
            lastValue = currentValue,
            refocusCount = focusCount
        )

        Log.d(
            TAG,
            "Field focused: $fieldId | focusCount=$focusCount"
        )
    }

    fun onFieldValueChanged(
        fieldId: String,
        oldValue: String,
        newValue: String
    ) {
        val now = SystemClock.elapsedRealtime()

        // Ignore delayed text callbacks from a field that is no longer focused.
        if (currentFocusedFieldId != fieldId) {
            Log.d(
                TAG,
                "Ignoring value change from inactive field: $fieldId"
            )
            return
        }

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

        evaluateActiveField(fieldId)
    }

    fun checkCurrentFieldIdle(fieldId: String) {
        // Ignore idle checks belonging to a field that is no longer focused.
        if (currentFocusedFieldId != fieldId) {
            return
        }

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

            // Count only one event for one continuous idle period.
            if (session.lastIdleLevelReported == 0) {
                session.idleEvents++
            }

            session.lastIdleLevelReported = idleLevel

            Log.d(
                TAG,
                "Idle detected: $fieldId | idleLevel=$idleLevel | idleTimeMs=$idleTimeMs"
            )

            // Recalculate the live score as the idle duration grows.
            evaluateActiveField(fieldId)
        }
    }

    fun onFieldUnfocused(
        fieldId: String
    ) {
        val now =
            SystemClock.elapsedRealtime()

        val session =
            activeSessions.remove(fieldId)
                ?: return

        val sample =
            buildSample(
                session = session,
                focusEndTimeMs = now
            )

        if (shouldSaveSample(sample)) {
            Log.d(
                TAG,
                "Final field sample created: $sample"
            )

            /*
             * The live distress score is still produced only while
             * a field is focused.
             *
             * The completed sample is used only for the fixed-size
             * accumulated baseline update.
             */
            if (
                shouldUseSampleForBaseline(
                    sample = sample
                ) &&
                !baselineUpdateCompletedForSession &&
                !baselineSaveInProgress
            ) {
                completedSamples.add(
                    sample
                )

                Log.d(
                    TAG,
                    """
                    Sample collected for this form's baseline update.
                    field=${sample.fieldId}
                    collected=${completedSamples.size}/$MIN_BASELINE_SAMPLES
                    """.trimIndent()
                )

                updateBaselineIfPossible()

            } else {
                Log.d(
                    TAG,
                    """
                    Sample not added to this form's baseline update.
                    field=${sample.fieldId}
                    refocusCount=${sample.refocusCount}
                    updateCompleted=$baselineUpdateCompletedForSession
                    saveInProgress=$baselineSaveInProgress
                    """.trimIndent()
                )
            }

            /*
             * Leaving the field never publishes a final distress
             * comparison. Only the latest live result of the active
             * field affects distress scoring.
             */
            Log.d(
                TAG,
                "Field completed. No final distress score published: ${sample.fieldId}"
            )

        } else {
            Log.d(
                TAG,
                "Sample ignored: $fieldId | " +
                        "dwell=${sample.dwellTimeMs}, " +
                        "edits=${sample.editEvents}, " +
                        "inserted=${sample.insertedChars}"
            )
        }

        /*
         * Clear the live field score only when this is still the
         * active field.
         */
        if (currentFocusedFieldId == fieldId) {
            currentFocusedFieldId =
                null

            currentFieldComparisonResult =
                null

            updateOverallFormBehaviorResult()
        }
    }

    fun getCompletedSamples(): List<FieldBehaviorSample> {
        return completedSamples.toList()
    }

    fun getBaselineSampleCount(): Int {
        return completedSamples.size
    }

    fun clear() {
        /*
         * Invalidate delayed Firebase callbacks belonging to the
         * form entry that is now closing.
         */
        sessionGeneration += 1L

        resetFormSessionState()

        /*
         * Clearing means this modality is unavailable.
         */
        DistressScoringManager
            .clearFormBehaviorScore()
    }

    /*
     * Clears only in-memory state belonging to one form entry.
     *
     * The accumulated Firebase baseline is not deleted.
     */
    private fun resetFormSessionState() {
        activeSessions.clear()
        completedSamples.clear()
        fieldFocusCounts.clear()

        currentBaseline =
            null

        baselineLoadCompleted =
            false

        baselineUpdateCompletedForSession =
            false

        baselineSaveInProgress =
            false

        lastStepChangeTimeMs =
            null

        lastStepDirection =
            0

        recentStepNavigationEvents.clear()
        editedFieldsSinceLastStepChange.clear()

        lastStepNavigationResult =
            FormStepNavigationResult()

        currentFocusedFieldId =
            null

        currentFieldComparisonResult =
            null

        lastOverallFormBehaviorResult =
            FormBehaviorOverallResult()
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

    private fun shouldUseSampleForBaseline(
        sample: FieldBehaviorSample
    ): Boolean {
        return shouldSaveSample(sample) &&
                sample.refocusCount == 1
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
        if (
            baselineUpdateCompletedForSession ||
            baselineSaveInProgress
        ) {
            return
        }

        if (!baselineLoadCompleted) {
            Log.d(
                TAG,
                """
                Waiting for Firebase baseline loading before
                creating or merging this form's samples.
                """.trimIndent()
            )

            return
        }

        if (
            completedSamples.size <
            MIN_BASELINE_SAMPLES
        ) {
            Log.d(
                TAG,
                """
                Baseline update is not ready:
                ${completedSamples.size}/$MIN_BASELINE_SAMPLES samples
                """.trimIndent()
            )

            return
        }

        /*
         * Preserve the previous behavior: one form entry
         * contributes exactly the first four valid samples.
         */
        val sessionSamples =
            completedSamples
                .take(
                    MIN_BASELINE_SAMPLES
                )

        val baselineAtSessionStart =
            currentBaseline

        val updatedBaseline =
            if (baselineAtSessionStart == null) {
                FormBehaviorBaselineCalculator
                    .calculate(
                        samples =
                            sessionSamples
                    )
            } else {
                FormBehaviorBaselineCalculator
                    .merge(
                        existingBaseline =
                            baselineAtSessionStart,

                        newSamples =
                            sessionSamples
                    )
            }

        if (updatedBaseline == null) {
            Log.d(
                TAG,
                "Form behavior baseline calculation or merge failed"
            )

            return
        }

        val generation =
            sessionGeneration

        val isInitialBaseline =
            baselineAtSessionStart == null

        baselineSaveInProgress =
            true

        /*
         * For the first-ever baseline, preserve the previous
         * behavior and allow live comparisons during the remainder
         * of the current form entry.
         *
         * For an existing baseline, keep the old active baseline
         * fixed until the next form entry.
         */
        if (isInitialBaseline) {
            currentBaseline =
                updatedBaseline

            baselineUpdateCompletedForSession =
                true
        }

        baselineRepository.saveBaseline(
            baseline =
                updatedBaseline,

            onSuccess = {

                if (generation != sessionGeneration) {
                    return@saveBaseline
                }

                baselineSaveInProgress =
                    false

                baselineUpdateCompletedForSession =
                    true

                Log.d(
                    TAG,
                    """
                    Fixed-size form behavior baseline saved.
                    updateType=${if (isInitialBaseline) "initial" else "accumulated"}
                    addedSamples=${sessionSamples.size}
                    totalSamples=${updatedBaseline.sampleCount}
                    totalSessions=${updatedBaseline.validSessionCount}
                    """.trimIndent()
                )
            },

            onFailure = { error ->

                if (generation != sessionGeneration) {
                    return@saveBaseline
                }

                baselineSaveInProgress =
                    false

                /*
                 * When an existing baseline update fails, allow one
                 * later valid field to trigger another save attempt.
                 *
                 * An initial local baseline remains usable for the
                 * current form even if Firebase saving fails.
                 */
                if (!isInitialBaseline) {
                    baselineUpdateCompletedForSession =
                        false
                }

                Log.e(
                    TAG,
                    "Failed to save accumulated form behavior baseline",
                    error
                )
            }
        )
    }


    /*
     * Converts the single Firebase baseline document into the
     * accumulated model used by the controller.
     *
     * A document from the previous non-accumulated structure is
     * treated as incompatible and will be replaced after four new
     * valid samples are collected.
     */
    private fun mapToBaseline(
        data: Map<String, Any>?
    ): FormBehaviorBaseline? {

        if (data == null) {
            return null
        }

        val baseline =
            FormBehaviorBaseline(
                sampleCount =
                    data.intValue(
                        key = "sampleCount"
                    ) ?: return null,

                validSessionCount =
                    data.intValue(
                        key = "validSessionCount"
                    ) ?: return null,

                calculatedAtMs =
                    data.longValue(
                        key = "calculatedAtMs"
                    ) ?: return null,

                avgDwellTimeMs =
                    data.doubleValue(
                        key = "avgDwellTimeMs"
                    ) ?: return null,

                stdDwellTimeMs =
                    data.doubleValue(
                        key = "stdDwellTimeMs"
                    ) ?: return null,

                dwellTimeM2 =
                    data.doubleValue(
                        key = "dwellTimeM2"
                    ) ?: return null,

                avgThinkingTimeMs =
                    data.doubleValue(
                        key = "avgThinkingTimeMs"
                    ) ?: return null,

                stdThinkingTimeMs =
                    data.doubleValue(
                        key = "stdThinkingTimeMs"
                    ) ?: return null,

                thinkingTimeM2 =
                    data.doubleValue(
                        key = "thinkingTimeM2"
                    ) ?: return null,

                avgTypingMsPerInsertedChar =
                    data.doubleValue(
                        key = "avgTypingMsPerInsertedChar"
                    ) ?: return null,

                stdTypingMsPerInsertedChar =
                    data.doubleValue(
                        key = "stdTypingMsPerInsertedChar"
                    ) ?: return null,

                typingMsPerInsertedCharM2 =
                    data.doubleValue(
                        key = "typingMsPerInsertedCharM2"
                    ) ?: return null,

                avgReviewTimeMs =
                    data.doubleValue(
                        key = "avgReviewTimeMs"
                    ) ?: return null,

                stdReviewTimeMs =
                    data.doubleValue(
                        key = "stdReviewTimeMs"
                    ) ?: return null,

                reviewTimeM2 =
                    data.doubleValue(
                        key = "reviewTimeM2"
                    ) ?: return null,

                avgMaxIdleTimeMs =
                    data.doubleValue(
                        key = "avgMaxIdleTimeMs"
                    ) ?: return null,

                stdMaxIdleTimeMs =
                    data.doubleValue(
                        key = "stdMaxIdleTimeMs"
                    ) ?: return null,

                maxIdleTimeM2 =
                    data.doubleValue(
                        key = "maxIdleTimeM2"
                    ) ?: return null,

                avgIdleEvents =
                    data.doubleValue(
                        key = "avgIdleEvents"
                    ) ?: return null,

                stdIdleEvents =
                    data.doubleValue(
                        key = "stdIdleEvents"
                    ) ?: return null,

                idleEventsM2 =
                    data.doubleValue(
                        key = "idleEventsM2"
                    ) ?: return null,

                avgDeleteRatio =
                    data.doubleValue(
                        key = "avgDeleteRatio"
                    ) ?: return null,

                stdDeleteRatio =
                    data.doubleValue(
                        key = "stdDeleteRatio"
                    ) ?: return null,

                deleteRatioM2 =
                    data.doubleValue(
                        key = "deleteRatioM2"
                    ) ?: return null,

                avgLongPauses =
                    data.doubleValue(
                        key = "avgLongPauses"
                    ) ?: return null,

                stdLongPauses =
                    data.doubleValue(
                        key = "stdLongPauses"
                    ) ?: return null,

                longPausesM2 =
                    data.doubleValue(
                        key = "longPausesM2"
                    ) ?: return null
            )

        return baseline.takeIf {
            isLoadedBaselineValid(
                baseline = it
            )
        }
    }


    private fun isLoadedBaselineValid(
        baseline: FormBehaviorBaseline
    ): Boolean {

        return baseline.sampleCount > 0 &&
                baseline.validSessionCount > 0 &&
                baseline.calculatedAtMs > 0L &&

                baseline.avgDwellTimeMs.isValidNonNegative() &&
                baseline.stdDwellTimeMs.isValidNonNegative() &&
                baseline.dwellTimeM2.isValidNonNegative() &&

                baseline.avgThinkingTimeMs.isValidNonNegative() &&
                baseline.stdThinkingTimeMs.isValidNonNegative() &&
                baseline.thinkingTimeM2.isValidNonNegative() &&

                baseline.avgTypingMsPerInsertedChar.isValidNonNegative() &&
                baseline.stdTypingMsPerInsertedChar.isValidNonNegative() &&
                baseline.typingMsPerInsertedCharM2.isValidNonNegative() &&

                baseline.avgReviewTimeMs.isValidNonNegative() &&
                baseline.stdReviewTimeMs.isValidNonNegative() &&
                baseline.reviewTimeM2.isValidNonNegative() &&

                baseline.avgMaxIdleTimeMs.isValidNonNegative() &&
                baseline.stdMaxIdleTimeMs.isValidNonNegative() &&
                baseline.maxIdleTimeM2.isValidNonNegative() &&

                baseline.avgIdleEvents.isValidNonNegative() &&
                baseline.stdIdleEvents.isValidNonNegative() &&
                baseline.idleEventsM2.isValidNonNegative() &&

                baseline.avgDeleteRatio.isFinite() &&
                baseline.avgDeleteRatio in 0.0..1.0 &&
                baseline.stdDeleteRatio.isValidNonNegative() &&
                baseline.deleteRatioM2.isValidNonNegative() &&

                baseline.avgLongPauses.isValidNonNegative() &&
                baseline.stdLongPauses.isValidNonNegative() &&
                baseline.longPausesM2.isValidNonNegative()
    }


    private fun Map<String, Any>.doubleValue(
        key: String
    ): Double? {

        return (
                this[key] as? Number
                )?.toDouble()
    }


    private fun Map<String, Any>.intValue(
        key: String
    ): Int? {

        return (
                this[key] as? Number
                )?.toInt()
    }


    private fun Map<String, Any>.longValue(
        key: String
    ): Long? {

        return (
                this[key] as? Number
                )?.toLong()
    }


    private fun Double.isValidNonNegative():
            Boolean {

        return isFinite() &&
                this >= 0.0
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

        // Publish the updated navigation behavior immediately.
        updateOverallFormBehaviorResult()
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


    /*
 * Publishes the current form-behavior snapshot.
 *
 * The snapshot combines:
 * 1. the newest live comparison of the currently focused field;
 * 2. the recent step-navigation result.
 *
 * Completed field comparisons are not queued, retained, or published.
 */
    private fun updateOverallFormBehaviorResult() {

        lastOverallFormBehaviorResult =
            FormBehaviorScoreAggregator.aggregate(
                fieldComparisonResult = currentFieldComparisonResult,
                stepNavigationResult = lastStepNavigationResult
            )

        val formScore0To4 = when {
            lastOverallFormBehaviorResult.score >= 75 -> 4
            lastOverallFormBehaviorResult.score >= 60 -> 3
            lastOverallFormBehaviorResult.score >= 30 -> 2
            lastOverallFormBehaviorResult.score >= 15 -> 1
            else -> 0
        }

        DistressScoringManager.updateFormBehaviorScore(formScore0To4)

        Log.d(
            "FORM_OVERALL_BEHAVIOR",
            """
        Overall form behavior score = ${lastOverallFormBehaviorResult.score}
        Overall form behavior level = ${lastOverallFormBehaviorResult.level}
        Top contributor = ${lastOverallFormBehaviorResult.topContributor}

        Current focused field = $currentFocusedFieldId
        Has live field result = ${currentFieldComparisonResult != null}

        Live field behavior score = ${lastOverallFormBehaviorResult.fieldBehaviorScore}
        Step navigation score = ${lastOverallFormBehaviorResult.stepNavigationScore}

        Converted form score 0-4 = $formScore0To4
        Should suggest help = ${lastOverallFormBehaviorResult.shouldSuggestHelp}
        """.trimIndent()
        )
    }


    private fun evaluateActiveField(fieldId: String) {
        // Only the currently focused field may publish a live comparison.
        if (currentFocusedFieldId != fieldId) {
            return
        }

        val baseline = currentBaseline ?: return
        val session = activeSessions[fieldId] ?: return

        val sample = buildSample(
            session = session,
            focusEndTimeMs = SystemClock.elapsedRealtime()
        )

        // Avoid evaluating an extremely short interaction.
        if (sample.dwellTimeMs < 500L) {
            return
        }

        val comparisonResult = FormBehaviorBaselineComparator.compare(
            sample = sample,
            baseline = baseline
        )

        /*
         * Replace the previous live result.
         *
         * There is no queue: only the latest comparison of the active field
         * is retained.
         */
        currentFieldComparisonResult = comparisonResult

        Log.d(
            "FORM_LIVE_COMPARISON",
            """
        Field = ${comparisonResult.fieldId}
        Live score = ${comparisonResult.score}
        Live level = ${comparisonResult.level}
        Top contributor = ${comparisonResult.topContributor}
        """.trimIndent()
        )

        updateOverallFormBehaviorResult()
    }

}