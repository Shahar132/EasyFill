//package com.example.easyfill_project.chatbot.ui
//
//import androidx.compose.foundation.Image
//import androidx.compose.foundation.background
//import androidx.compose.foundation.border
//import androidx.compose.foundation.clickable
//import androidx.compose.foundation.gestures.detectDragGestures
//import androidx.compose.foundation.interaction.MutableInteractionSource
//import androidx.compose.foundation.layout.Box
//import androidx.compose.foundation.layout.BoxWithConstraints
//import androidx.compose.foundation.layout.fillMaxSize
//import androidx.compose.foundation.layout.offset
//import androidx.compose.foundation.layout.size
//import androidx.compose.foundation.shape.CircleShape
//import androidx.compose.material3.MaterialTheme
//import androidx.compose.material3.Text
//import androidx.compose.runtime.Composable
//import androidx.compose.runtime.LaunchedEffect
//import androidx.compose.runtime.getValue
//import androidx.compose.runtime.mutableFloatStateOf
//import androidx.compose.runtime.mutableStateOf
//import androidx.compose.runtime.remember
//import androidx.compose.runtime.setValue
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.draw.clip
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.input.pointer.pointerInput
//import androidx.compose.ui.platform.LocalDensity
//import androidx.compose.ui.res.painterResource
//import androidx.compose.ui.text.font.FontWeight
//import androidx.compose.ui.unit.IntOffset
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.unit.sp
//import androidx.compose.ui.zIndex
//import com.example.easyfill_project.R
//import com.example.easyfill_project.chatbot.model.BotAction
//import com.example.easyfill_project.chatbot.model.BotAppState
//import com.example.easyfill_project.chatbot.model.DistressSnapshot
//import kotlinx.coroutines.delay
//import kotlin.math.roundToInt
//import androidx.compose.animation.core.RepeatMode
//import androidx.compose.animation.core.animateFloat
//import androidx.compose.animation.core.infiniteRepeatable
//import androidx.compose.animation.core.rememberInfiniteTransition
//import androidx.compose.animation.core.tween
//import androidx.compose.foundation.layout.padding
//import androidx.compose.foundation.shape.RoundedCornerShape
//import android.util.Log
//import kotlin.math.max
//
//@Composable
//fun FloatingChatOverlay(
//    currentScreen: String,
//    modifier: Modifier = Modifier,
//    onBotAction: (BotAction) -> Unit = {},
//    autoOpenOnDistress: Boolean = false,
//    distressSnapshot: DistressSnapshot = DistressSnapshot(),
//    appState: BotAppState = BotAppState()
//) {
//    var isChatOpen by remember { mutableStateOf(false) }
//
//    var hasAutoOpenedForDistress by remember {
//        mutableStateOf(false)
//    }
//
//    var hasUnreadDistressAlert by remember {
//        mutableStateOf(false)
//    }
//
//    var showAlertText by remember {
//        mutableStateOf(false)
//    }
//
//    // Holds the highest unread alert level.
//    // The alert level should only increase until the user opens the chat.
//    var displayedAlertLevel by remember {
//        mutableStateOf(0)
//    }
//
//    // Holds the snapshot that caused the highest unread alert.
//    // This allows the chat panel to show the correct message even if the current score drops later.
//    var unreadDistressSnapshot by remember {
//        mutableStateOf<DistressSnapshot?>(null)
//    }
//
//    // Holds the highest alert level that the user already opened/read.
//    // This prevents showing a lower alert after the user already saw a higher one.
//    var acknowledgedAlertLevel by remember {
//        mutableStateOf(0)
//    }
//
//    // Holds the distress snapshot that should be shown inside the open chat panel.
//    // It is separated from unreadDistressSnapshot so we can clear the floating alert
//    // without keeping the message forever inside the panel.
//    var panelDistressSnapshot by remember {
//        mutableStateOf<DistressSnapshot?>(null)
//    }
//
//    // Marks whether we are still inside the same active distress cycle.
//    // A new cycle starts only after the total score goes back to 0 and rises again.
//    var distressCycleActive by remember {
//        mutableStateOf(false)
//    }
//
//    val totalDistressScore = distressSnapshot.globalScore
//
//    // Temporary testing scale:
//    // 1 = green, 2 = orange, 3 or more = red.
//    val alertLevel = when (totalDistressScore) {
//        0 -> 0
//        1 -> 1
//        2 -> 2
//        else -> 3
//    }
//
//
//
////    LaunchedEffect(autoOpenOnDistress) {
////        if (autoOpenOnDistress && !hasAutoOpenedForDistress) {
////            hasUnreadDistressAlert = true
////            showAlertText = true
////            hasAutoOpenedForDistress = true
////
////            delay(5000)
////
////            showAlertText = false
////        }
////
////        if (!autoOpenOnDistress) {
////            hasAutoOpenedForDistress = false
////        }
////    }
//
//    LaunchedEffect(
//        alertLevel,
//        totalDistressScore,
//        distressSnapshot.touchScore,
//        distressSnapshot.voiceScore,
//        distressSnapshot.faceScore,
//        distressSnapshot.formBehaviorScore,
//        isChatOpen
//    ) {
//        // Do not show floating alerts while the chat panel is open.
//        if (isChatOpen) {
//            showAlertText = false
//            return@LaunchedEffect
//        }
//
//        // If distress is back to 0, the current distress cycle ended.
//        // This allows a future new detection to show an alert again.
//        if (alertLevel == 0) {
//            distressCycleActive = false
//
//            if (!hasUnreadDistressAlert && unreadDistressSnapshot == null) {
//                acknowledgedAlertLevel = 0
//                displayedAlertLevel = 0
//            }
//
//            return@LaunchedEffect
//        }
//
//        val isNewDistressCycle = !distressCycleActive
//
//        val isUpgrade =
//            alertLevel > displayedAlertLevel &&
//                    alertLevel > acknowledgedAlertLevel
//
//        // Show alert if this is a new distress cycle,
//        // or if the current distress got worse.
//        // Do not show downgrade alerts.
//        if (isNewDistressCycle || isUpgrade) {
//            distressCycleActive = true
//
//            displayedAlertLevel = alertLevel
//            unreadDistressSnapshot = distressSnapshot
//
//            hasUnreadDistressAlert = true
//            showAlertText = true
//
//            delay(5000)
//
//            showAlertText = false
//        }
//    }
//    LaunchedEffect(alertLevel) {
//        // If the current distress level is 0, do not clear an unread alert.
//        // The unread alert should stay until the user opens the chat.
//        if (alertLevel == 0) {
//            // Reset the acknowledged level only if there is no unread alert waiting.
//            // This allows future new alerts to appear after the previous alert was handled.
//            if (!hasUnreadDistressAlert && unreadDistressSnapshot == null) {
//                acknowledgedAlertLevel = 0
//                displayedAlertLevel = 0
//            }
//
//            return@LaunchedEffect
//        }
//
//        // Show a new alert only if it is higher than:
//        // 1. the currently displayed unread alert
//        // 2. the highest alert level the user already opened/read
//        if (alertLevel > displayedAlertLevel && alertLevel > acknowledgedAlertLevel) {
//            displayedAlertLevel = alertLevel
//            unreadDistressSnapshot = distressSnapshot
//
//            hasUnreadDistressAlert = true
//            showAlertText = true
//
//            delay(5000)
//
//            // Only the small text bubble disappears after 5 seconds.
//            // The exclamation mark and the saved chat message stay.
//            showAlertText = false
//        }
//    }
//
//    // RTL behavior:
//    // offsetX = 0 means the right side of the screen.
//    // A larger offsetX moves the bubble to the left.
//    var offsetX by remember { mutableFloatStateOf(24f) }
//    var offsetY by remember { mutableFloatStateOf(250f) }
//
//    var initialPositionSet by remember {
//        mutableStateOf(false)
//    }
//
//    BoxWithConstraints(
//        modifier = modifier.fillMaxSize()
//    ) {
//        val density = LocalDensity.current
//
//        val bubbleSizeDp = 64.dp
//        val bubbleSizePx = with(density) { bubbleSizeDp.toPx() }
//
//        val panelWidthDp = 320.dp
//        val panelHeightDp = 330.dp
//
//        val panelWidthPx = with(density) { panelWidthDp.toPx() }
//        val panelHeightPx = with(density) { panelHeightDp.toPx() }
//
//        val screenWidthPx = with(density) { maxWidth.toPx() }
//        val screenHeightPx = with(density) { maxHeight.toPx() }
//
//        LaunchedEffect(screenWidthPx, screenHeightPx) {
//            if (!initialPositionSet && screenWidthPx > 0f && screenHeightPx > 0f) {
//                offsetX = 24f
//                offsetY = (screenHeightPx - bubbleSizePx - 120f).coerceAtLeast(0f)
//                initialPositionSet = true
//            }
//        }
//
//       // val snapshotForChatPanel = unreadDistressSnapshot ?: distressSnapshot
//        val snapshotForChatPanel = panelDistressSnapshot ?: DistressSnapshot()
//
//        if (isChatOpen) {
//            Box(
//                modifier = Modifier
//                    .fillMaxSize()
//                    .zIndex(10f)
//                    .clickable(
//                        indication = null,
//                        interactionSource = remember { MutableInteractionSource() }
//                    ) {
//                        // Close the chat when clicking outside the panel.
//                        isChatOpen = false
//                        unreadDistressSnapshot = null
//                    }
//            )
//
//            val panelX = offsetX.coerceIn(
//                0f,
//                (screenWidthPx - panelWidthPx).coerceAtLeast(0f)
//            )
//
//            val panelY = if (offsetY + bubbleSizePx + panelHeightPx + 12f <= screenHeightPx) {
//                offsetY + bubbleSizePx + 12f
//            } else {
//                offsetY - panelHeightPx - 12f
//            }.coerceIn(
//                0f,
//                (screenHeightPx - panelHeightPx).coerceAtLeast(0f)
//            )
//
//            Box(
//                modifier = Modifier
//                    .offset {
//                        IntOffset(
//                            panelX.roundToInt(),
//                            panelY.roundToInt()
//                        )
//                    }
//                    .zIndex(20f)
//                    .clickable(
//                        indication = null,
//                        interactionSource = remember { MutableInteractionSource() }
//                    ) {
//                        // Empty click handler.
//                        // This prevents clicks inside the chat panel from closing it.
//                    }
//            ) {
//                ChatPanel(
//                    currentScreen = currentScreen,
//                    onClose = {
//                        isChatOpen = false
//                        unreadDistressSnapshot = null
//                    },
//                    onBotAction = onBotAction,
//                    distressSnapshot = snapshotForChatPanel,
//                    appState = appState
//                )
//            }
//        }
//
//        Box(
//            modifier = Modifier
//                .offset {
//                    IntOffset(
//                        offsetX.roundToInt(),
//                        offsetY.roundToInt()
//                    )
//                }
//                .zIndex(30f)
//                .size(width = 220.dp, height = 130.dp),
//            contentAlignment = Alignment.Center
//        ) {
//            Box(
//                modifier = Modifier
//                    .size(bubbleSizeDp)
//                    .clip(CircleShape)
//                    .background(MaterialTheme.colorScheme.primary)
//                    .pointerInput(Unit) {
//                        detectDragGestures { change, dragAmount ->
//                            change.consume()
//
//                            val newX = offsetX - dragAmount.x
//                            val newY = offsetY + dragAmount.y
//
//                            offsetX = newX.coerceIn(
//                                0f,
//                                screenWidthPx - bubbleSizePx
//                            )
//
//                            offsetY = newY.coerceIn(
//                                0f,
//                                screenHeightPx - bubbleSizePx
//                            )
//                        }
//                    }
//                    .clickable {
//                        if (!isChatOpen) {
//                            // Save the unread alert for the panel to show once.
//                            panelDistressSnapshot = unreadDistressSnapshot
//
//                            // Mark the current alert as read.
//                            acknowledgedAlertLevel = maxOf(
//                                acknowledgedAlertLevel,
//                                displayedAlertLevel
//                            )
//
//                            // Clear the floating alert.
//                            hasUnreadDistressAlert = false
//                            showAlertText = false
//                            displayedAlertLevel = 0
//                            unreadDistressSnapshot = null
//
//                            isChatOpen = true
//                        } else {
//                            isChatOpen = false
//                            panelDistressSnapshot = null
//                        }
//                    },
//                contentAlignment = Alignment.Center
//            ) {
//                Image(
//                    painter = painterResource(id = R.drawable.chatbot_icon),
//                    contentDescription = "פתיחת צ'אטבוט EasyFill",
//                    modifier = Modifier.size(80.dp)
//                )
//            }
//
//            if (hasUnreadDistressAlert && displayedAlertLevel > 0 && !isChatOpen) {
//                val alertColor = when (displayedAlertLevel) {
//                    1 -> Color(0xFF4CAF50)
//                    2 -> Color(0xFFFFA000)
//                    else -> Color.Red
//                }
//
//                val alertText = when (displayedAlertLevel) {
//                    1 -> "יש לי הצעה קטנה"
//                    2 -> "אפשר לעזור?"
//                    else -> "רוצה שאקל עליך?"
//                }
//
//                if (showAlertText) {
//                    Box(
//                        modifier = Modifier
//                            .align(Alignment.Center)
//                            .offset(x = -160.dp, y = (-32).dp)
//                            .background(Color.White, RoundedCornerShape(12.dp))
//                            .border(1.dp, alertColor, RoundedCornerShape(12.dp))
//                            .padding(horizontal = 10.dp, vertical = 5.dp),
//                        contentAlignment = Alignment.Center
//                    ) {
//                        Text(
//                            text = alertText,
//                            color = alertColor,
//                            fontSize = 10.sp,
//                            fontWeight = FontWeight.Medium
//                        )
//                    }
//                }
//
//                Box(
//                    modifier = Modifier
//                        .align(Alignment.Center)
//                        .offset(x = (-50).dp, y = (-40).dp)
//                        .size(22.dp)
//                        .clip(CircleShape)
//                        .background(alertColor),
//                    contentAlignment = Alignment.Center
//                ) {
//                    Text(
//                        text = "!",
//                        color = Color.White,
//                        fontSize = 12.sp,
//                        fontWeight = FontWeight.Bold
//                    )
//                }
//            }
//        }
//    }
//}





package com.example.easyfill_project.chatbot.ui

import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.easyfill_project.R
import com.example.easyfill_project.chatbot.model.BotAction
import com.example.easyfill_project.chatbot.model.BotAppState
import com.example.easyfill_project.chatbot.model.DistressSnapshot
import com.example.easyfill_project.distress_scoring.DistressScoringManager
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun FloatingChatOverlay(
    currentScreen: String,
    modifier: Modifier = Modifier,
    onBotAction: (BotAction) -> Unit = {},
    autoOpenOnDistress: Boolean = false,
    distressSnapshot: DistressSnapshot = DistressSnapshot(),
    appState: BotAppState = BotAppState()
) {
    var isChatOpen by remember { mutableStateOf(false) }

    var hasAutoOpenedForDistress by remember {
        mutableStateOf(false)
    }

    var hasUnreadDistressAlert by remember {
        mutableStateOf(false)
    }

    var showAlertText by remember {
        mutableStateOf(false)
    }

    // 0 = none, 1 = green, 2 = orange, 3 = red
    var displayedAlertLevel by remember {
        mutableStateOf(0)
    }

    // Which sources created the current unread alert.
    var displayedAlertSources by remember {
        mutableStateOf<Set<String>>(emptySet())
    }

    // Highest source score inside the current unread alert.
    var displayedAlertSourceScore by remember {
        mutableStateOf(0)
    }

    // Snapshot that caused the unread floating alert.
    var unreadDistressSnapshot by remember {
        mutableStateOf<DistressSnapshot?>(null)
    }

    // Snapshot shown inside the panel while it is open.
    var panelDistressSnapshot by remember {
        mutableStateOf<DistressSnapshot?>(null)
    }

    // Highest score already opened/read by source.
    var acknowledgedScoresBySource by remember {
        mutableStateOf<Map<String, Int>>(emptyMap())
    }

    // Last alert time by source.
    var lastAlertTimeBySource by remember {
        mutableStateOf<Map<String, Long>>(emptyMap())
    }

    // True only when score becomes 0 because we reset after the user read the alert.
    // This prevents clearing cooldown memory by mistake.
    var resetScoresBecauseAlertWasRead by remember {
        mutableStateOf(false)
    }

    // For testing. Later you can change this back to 45_000L.
    val alertCooldownMs = 15_000L

    val totalDistressScore = distressSnapshot.globalScore

    val maxSourceScore = getSourceScores(distressSnapshot)
        .maxOfOrNull { it.score } ?: 0

    val liveAlertLevel = getFloatingAlertLevel(
        totalScore = totalDistressScore,
        maxSourceScore = maxSourceScore
    )

    LaunchedEffect(autoOpenOnDistress) {
        if (autoOpenOnDistress && !hasAutoOpenedForDistress) {
            hasUnreadDistressAlert = true
            showAlertText = true
            hasAutoOpenedForDistress = true

            delay(5000)

            showAlertText = false
        }

        if (!autoOpenOnDistress) {
            hasAutoOpenedForDistress = false
        }
    }

    LaunchedEffect(
        liveAlertLevel,
        totalDistressScore,
        distressSnapshot.touchScore,
        distressSnapshot.voiceScore,
        distressSnapshot.faceScore,
        distressSnapshot.semanticTextScore,
        distressSnapshot.formBehaviorScore,
        isChatOpen
    ) {
        val now = SystemClock.elapsedRealtime()

        if (liveAlertLevel == 0 || totalDistressScore == 0) {
            hasUnreadDistressAlert = false
            showAlertText = false
            displayedAlertLevel = 0
            displayedAlertSources = emptySet()
            displayedAlertSourceScore = 0
            unreadDistressSnapshot = null

            // Do not clear the panel message while the chat is open.
            if (!isChatOpen) {
                panelDistressSnapshot = null
            }

            if (resetScoresBecauseAlertWasRead) {
                // Manual reset after the user read the alert.
                // Keep acknowledgedScoresBySource and lastAlertTimeBySource,
                // so the same source will not jump again before cooldown.
                resetScoresBecauseAlertWasRead = false
            } else {
                // Natural reset: the sensors really went back to 0.
                // Future distress can be treated as a new event.
                acknowledgedScoresBySource = emptyMap()
                lastAlertTimeBySource = emptyMap()
            }

            return@LaunchedEffect
        }

        // If the chat panel is open, do not show a floating alert.
        // But if a new source appears or a source gets worse, show it inside the panel
        // and mark it as already read.
        if (isChatOpen) {
            showAlertText = false

            val candidate = chooseAlertCandidate(
                snapshot = distressSnapshot,
                hasUnreadDistressAlert = false,
                displayedAlertSourceScore = 0,
                acknowledgedScoresBySource = acknowledgedScoresBySource,
                lastAlertTimeBySource = lastAlertTimeBySource,
                cooldownMs = alertCooldownMs,
                nowMs = now
            )

            if (candidate != null) {
                panelDistressSnapshot = candidate.snapshotForPanel

                acknowledgedScoresBySource =
                    acknowledgeSources(
                        currentMap = acknowledgedScoresBySource,
                        sources = candidate.sourcesToAcknowledge,
                        score = candidate.sourceScore
                    )

                lastAlertTimeBySource =
                    updateAlertTimes(
                        currentMap = lastAlertTimeBySource,
                        sources = candidate.sourcesToAcknowledge,
                        nowMs = now
                    )
            }

            return@LaunchedEffect
        }

        val candidate = chooseAlertCandidate(
            snapshot = distressSnapshot,
            hasUnreadDistressAlert = hasUnreadDistressAlert,
            displayedAlertSourceScore = displayedAlertSourceScore,
            acknowledgedScoresBySource = acknowledgedScoresBySource,
            lastAlertTimeBySource = lastAlertTimeBySource,
            cooldownMs = alertCooldownMs,
            nowMs = now
        )

        if (candidate != null) {
            displayedAlertLevel = candidate.alertColorLevel
            displayedAlertSources = candidate.sourcesToAcknowledge
            displayedAlertSourceScore = candidate.sourceScore
            unreadDistressSnapshot = candidate.snapshotForPanel

            hasUnreadDistressAlert = true
            showAlertText = true

            delay(5000)

            showAlertText = false
        }
    }

    // RTL behavior:
    // offsetX = 0 means the right side of the screen.
    // A larger offsetX moves the bubble to the left.
    var offsetX by remember { mutableFloatStateOf(24f) }
    var offsetY by remember { mutableFloatStateOf(250f) }

    var initialPositionSet by remember {
        mutableStateOf(false)
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val density = LocalDensity.current

        val bubbleSizeDp = 64.dp
        val bubbleSizePx = with(density) { bubbleSizeDp.toPx() }

        val panelWidthDp = 320.dp
        val panelHeightDp = 330.dp

        val panelWidthPx = with(density) { panelWidthDp.toPx() }
        val panelHeightPx = with(density) { panelHeightDp.toPx() }

        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }

        LaunchedEffect(screenWidthPx, screenHeightPx) {
            if (!initialPositionSet && screenWidthPx > 0f && screenHeightPx > 0f) {
                offsetX = 24f
                offsetY = (screenHeightPx - bubbleSizePx - 120f).coerceAtLeast(0f)
                initialPositionSet = true
            }
        }

        val snapshotForChatPanel = panelDistressSnapshot ?: DistressSnapshot()

        if (isChatOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(10f)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        isChatOpen = false
                        panelDistressSnapshot = null
                    }
            )

            val panelX = offsetX.coerceIn(
                0f,
                (screenWidthPx - panelWidthPx).coerceAtLeast(0f)
            )

            val panelY = if (offsetY + bubbleSizePx + panelHeightPx + 12f <= screenHeightPx) {
                offsetY + bubbleSizePx + 12f
            } else {
                offsetY - panelHeightPx - 12f
            }.coerceIn(
                0f,
                (screenHeightPx - panelHeightPx).coerceAtLeast(0f)
            )

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            panelX.roundToInt(),
                            panelY.roundToInt()
                        )
                    }
                    .zIndex(20f)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        // Prevent clicks inside the panel from closing it.
                    }
            ) {
                ChatPanel(
                    currentScreen = currentScreen,
                    onClose = {
                        isChatOpen = false
                        panelDistressSnapshot = null
                    },
                    onBotAction = onBotAction,
                    distressSnapshot = snapshotForChatPanel,
                    appState = appState
                )
            }
        }

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        offsetX.roundToInt(),
                        offsetY.roundToInt()
                    )
                }
                .zIndex(30f)
                .size(width = 220.dp, height = 130.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(bubbleSizeDp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()

                            val newX = offsetX - dragAmount.x
                            val newY = offsetY + dragAmount.y

                            offsetX = newX.coerceIn(
                                0f,
                                screenWidthPx - bubbleSizePx
                            )

                            offsetY = newY.coerceIn(
                                0f,
                                screenHeightPx - bubbleSizePx
                            )
                        }
                    }
                    .clickable {
                        if (!isChatOpen) {
                            val now = SystemClock.elapsedRealtime()

                            val snapshotThatWasRead = unreadDistressSnapshot

                            val shouldResetScoresAfterRead =
                                hasUnreadDistressAlert && snapshotThatWasRead != null

                            // Move the unread floating alert into the panel.
                            panelDistressSnapshot = snapshotThatWasRead

                            // Fallback:
                            // If displayedAlertSources was empty for any reason,
                            // derive the sources directly from the snapshot that was read.
                            val sourcesFromSnapshot = snapshotThatWasRead
                                ?.let { snapshot ->
                                    getSourceScores(snapshot)
                                        .filter { it.score > 0 }
                                        .map { it.source }
                                        .toSet()
                                } ?: emptySet()

                            val maxScoreFromSnapshot = snapshotThatWasRead
                                ?.let { snapshot ->
                                    getSourceScores(snapshot)
                                        .filter { it.score > 0 }
                                        .maxOfOrNull { it.score } ?: 0
                                } ?: 0

                            val sourcesToMarkRead =
                                if (displayedAlertSources.isNotEmpty()) {
                                    displayedAlertSources
                                } else {
                                    sourcesFromSnapshot
                                }

                            val scoreToMarkRead =
                                if (displayedAlertSourceScore > 0) {
                                    displayedAlertSourceScore
                                } else {
                                    maxScoreFromSnapshot
                                }

                            // Mark the alert sources as read and save read time.
                            if (sourcesToMarkRead.isNotEmpty() && scoreToMarkRead > 0) {
                                acknowledgedScoresBySource =
                                    acknowledgeSources(
                                        currentMap = acknowledgedScoresBySource,
                                        sources = sourcesToMarkRead,
                                        score = scoreToMarkRead
                                    )

                                lastAlertTimeBySource =
                                    updateAlertTimes(
                                        currentMap = lastAlertTimeBySource,
                                        sources = sourcesToMarkRead,
                                        nowMs = now
                                    )
                            }

                            // Clear floating alert only.
                            hasUnreadDistressAlert = false
                            showAlertText = false
                            displayedAlertLevel = 0
                            displayedAlertSources = emptySet()
                            displayedAlertSourceScore = 0
                            unreadDistressSnapshot = null

                            isChatOpen = true

                            // After the user opened/read the alert,
                            // reset current distress scores only.
                            // This does not reset baseline.
                            if (shouldResetScoresAfterRead) {
                                resetScoresBecauseAlertWasRead = true
                                DistressScoringManager.resetCurrentScoresAfterAlertRead()
                            }
                        } else {
                            isChatOpen = false
                            panelDistressSnapshot = null
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.chatbot_icon),
                    contentDescription = "פתיחת צ'אטבוט EasyFill",
                    modifier = Modifier.size(80.dp)
                )
            }

            if (hasUnreadDistressAlert && displayedAlertLevel > 0 && !isChatOpen) {
                val alertColor = when (displayedAlertLevel) {
                    1 -> Color(0xFF4CAF50)
                    2 -> Color(0xFFFFA000)
                    else -> Color.Red
                }

                val alertText = when (displayedAlertLevel) {
                    1 -> "יש לי הצעה קטנה"
                    2 -> "אפשר לעזור?"
                    else -> "רוצה שאקל עליך?"
                }

                if (showAlertText) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(x = (55).dp, y = (-15).dp)
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .border(1.dp, alertColor, RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = alertText,
                            color = alertColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(x = (-40).dp, y = (-20).dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(alertColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "!",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private data class SourceScore(
    val source: String,
    val score: Int
)

private data class AlertCandidate(
    val sourcesToAcknowledge: Set<String>,
    val sourceScore: Int,
    val alertColorLevel: Int,
    val snapshotForPanel: DistressSnapshot
)

private fun getSourceScores(snapshot: DistressSnapshot): List<SourceScore> {
    return listOf(
        SourceScore("HAND", snapshot.touchScore),
        SourceScore("VOICE", snapshot.voiceScore),
        SourceScore("FACE", snapshot.faceScore),
        SourceScore("TEXT", snapshot.semanticTextScore),
        SourceScore("FORM", snapshot.formBehaviorScore)
    )
}

private fun getFloatingAlertLevel(
    totalScore: Int,
    maxSourceScore: Int
): Int {
    val severityLevel = getDistressSeverityLevel(
        totalScore = totalScore,
        maxSourceScore = maxSourceScore
    )

    return when (severityLevel) {
        0 -> 0
        1 -> 1       // green
        2 -> 2       // orange
        else -> 3    // red
    }
}

private fun getDistressSeverityLevel(
    totalScore: Int,
    maxSourceScore: Int
): Int {
    val levelBySingleSource = when (maxSourceScore) {
        0 -> 0
        1 -> 1
        2 -> 2
        3 -> 3
        else -> 4
    }

    val levelByTotalScore = when (totalScore) {
        0 -> 0
        in 1..2 -> 1
        in 3..4 -> 2
        in 5..6 -> 3
        else -> 4
    }

    return maxOf(levelBySingleSource, levelByTotalScore)
}

private fun chooseAlertCandidate(
    snapshot: DistressSnapshot,
    hasUnreadDistressAlert: Boolean,
    displayedAlertSourceScore: Int,
    acknowledgedScoresBySource: Map<String, Int>,
    lastAlertTimeBySource: Map<String, Long>,
    cooldownMs: Long,
    nowMs: Long
): AlertCandidate? {
    val activeSources = getSourceScores(snapshot)
        .filter { it.score > 0 }

    if (activeSources.isEmpty()) {
        return null
    }

    val eligibleSources = if (hasUnreadDistressAlert) {
        // If there is already an unread alert, replace it only with a stronger source.
        activeSources.filter { sourceScore ->
            sourceScore.score > displayedAlertSourceScore
        }
    } else {
        activeSources.filter { sourceScore ->
            val acknowledgedScore =
                acknowledgedScoresBySource[sourceScore.source] ?: 0

            val lastAlertTime =
                lastAlertTimeBySource[sourceScore.source] ?: 0L

            val cooldownPassed =
                nowMs - lastAlertTime >= cooldownMs

            val isNewOrHigherForThisSource =
                sourceScore.score > acknowledgedScore

            val isSameLevelAfterCooldown =
                acknowledgedScore > 0 &&
                        sourceScore.score == acknowledgedScore &&
                        cooldownPassed

            isNewOrHigherForThisSource || isSameLevelAfterCooldown
        }
    }

    if (eligibleSources.isEmpty()) {
        return null
    }

    val candidateSources = if (hasUnreadDistressAlert) {
        val topScore = eligibleSources.maxOf { it.score }

        eligibleSources
            .filter { it.score == topScore }
            .map { it.source }
            .toSet()
    } else {
        eligibleSources
            .map { it.source }
            .toSet()
    }

    val snapshotForPanel = buildSnapshotForSources(
        snapshot = snapshot,
        sources = candidateSources
    )

    val maxCandidateSourceScore = getSourceScores(snapshotForPanel)
        .maxOfOrNull { it.score } ?: 0

    val alertColorLevel = getFloatingAlertLevel(
        totalScore = snapshotForPanel.globalScore,
        maxSourceScore = maxCandidateSourceScore
    )

    return AlertCandidate(
        sourcesToAcknowledge = candidateSources,
        sourceScore = maxCandidateSourceScore,
        alertColorLevel = alertColorLevel,
        snapshotForPanel = snapshotForPanel
    )
}

private fun buildSnapshotForSources(
    snapshot: DistressSnapshot,
    sources: Set<String>
): DistressSnapshot {
    val textScore = if ("TEXT" in sources) snapshot.semanticTextScore else 0
    val faceScore = if ("FACE" in sources) snapshot.faceScore else 0
    val voiceScore = if ("VOICE" in sources) snapshot.voiceScore else 0
    val handScore = if ("HAND" in sources) snapshot.touchScore else 0
    val formScore = if ("FORM" in sources) snapshot.formBehaviorScore else 0

    val filteredGlobalScore =
        textScore + faceScore + voiceScore + handScore + formScore

    return DistressSnapshot(
        globalScore = filteredGlobalScore,
        semanticTextScore = textScore,
        faceScore = faceScore,
        voiceScore = voiceScore,
        touchScore = handScore,
        formBehaviorScore = formScore
    )
}

private fun acknowledgeSources(
    currentMap: Map<String, Int>,
    sources: Set<String>,
    score: Int
): Map<String, Int> {
    if (sources.isEmpty()) {
        return currentMap
    }

    var updatedMap = currentMap

    sources.forEach { source ->
        val currentScore = updatedMap[source] ?: 0
        updatedMap = updatedMap + (source to maxOf(currentScore, score))
    }

    return updatedMap
}

private fun updateAlertTimes(
    currentMap: Map<String, Long>,
    sources: Set<String>,
    nowMs: Long
): Map<String, Long> {
    if (sources.isEmpty()) {
        return currentMap
    }

    var updatedMap = currentMap

    sources.forEach { source ->
        updatedMap = updatedMap + (source to nowMs)
    }

    return updatedMap
}