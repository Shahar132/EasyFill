
package com.example.easyfill_project.form_behavior_analysis

/*
 * Represents the user's accumulated form-behavior baseline.
 *
 * The first baseline is created from the initial 3-4 valid
 * field samples.
 *
 * On later form entries, additional valid field samples are
 * merged into the same fixed-size baseline.
 *
 * No historical FieldBehaviorSample objects are stored in
 * Firebase.
 */
data class FormBehaviorBaseline(

    /*
     * Total number of valid field samples included in the
     * accumulated baseline.
     */
    val sampleCount: Int,

    /*
     * Number of valid form sessions that contributed samples
     * to this baseline.
     */
    val validSessionCount: Int,

    /*
     * Time when the baseline was most recently calculated or
     * updated.
     */
    val calculatedAtMs: Long,


    /*
     * =====================================================
     * DWELL TIME
     * =====================================================
     */

    /*
     * Accumulated average time spent inside a field.
     */
    val avgDwellTimeMs: Double,

    /*
     * Current standard deviation exposed to the existing
     * comparison logic.
     */
    val stdDwellTimeMs: Double,

    /*
     * Accumulated sum of squared differences from the mean.
     *
     * Together with sampleCount, this allows future sessions
     * to update the mean and standard deviation without
     * storing historical samples.
     */
    val dwellTimeM2: Double,


    /*
     * =====================================================
     * THINKING TIME
     * =====================================================
     */

    /*
     * Accumulated average time from field focus until the
     * first edit.
     */
    val avgThinkingTimeMs: Double,

    val stdThinkingTimeMs: Double,

    val thinkingTimeM2: Double,


    /*
     * =====================================================
     * TYPING SPEED
     * =====================================================
     */

    /*
     * Accumulated average typing time per inserted character.
     */
    val avgTypingMsPerInsertedChar: Double,

    val stdTypingMsPerInsertedChar: Double,

    val typingMsPerInsertedCharM2: Double,


    /*
     * =====================================================
     * REVIEW TIME
     * =====================================================
     */

    /*
     * Accumulated average time spent in the field after the
     * final edit.
     */
    val avgReviewTimeMs: Double,

    val stdReviewTimeMs: Double,

    val reviewTimeM2: Double,


    /*
     * =====================================================
     * MAXIMUM IDLE TIME
     * =====================================================
     */

    /*
     * Accumulated average of the longest idle period measured
     * inside each field.
     */
    val avgMaxIdleTimeMs: Double,

    val stdMaxIdleTimeMs: Double,

    val maxIdleTimeM2: Double,


    /*
     * =====================================================
     * IDLE EVENTS
     * =====================================================
     */

    /*
     * Accumulated average number of idle events per field.
     */
    val avgIdleEvents: Double,

    val stdIdleEvents: Double,

    val idleEventsM2: Double,


    /*
     * DELETE RATIO

     */

    /*
     * Accumulated average ratio of deleted characters to all
     * changed characters.
     */
    val avgDeleteRatio: Double,

    val stdDeleteRatio: Double,

    val deleteRatioM2: Double,


    /*
     * LONG PAUSES
     */

    /*
     * Accumulated average number of long pauses while editing
     * one field.
     */
    val avgLongPauses: Double,

    val stdLongPauses: Double,

    val longPausesM2: Double
)