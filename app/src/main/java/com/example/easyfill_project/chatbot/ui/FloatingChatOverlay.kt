package com.example.easyfill_project.chatbot.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape

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

    // Holds the highest unread alert level.
    // The alert level should only increase until the user opens the chat.
    var displayedAlertLevel by remember {
        mutableStateOf(0)
    }

    // Holds the snapshot that caused the highest unread alert.
    // This allows the chat panel to show the correct message even if the current score drops later.
    var unreadDistressSnapshot by remember {
        mutableStateOf<DistressSnapshot?>(null)
    }

    // Holds the highest alert level that the user already opened/read.
    // This prevents showing a lower alert after the user already saw a higher one.
    var acknowledgedAlertLevel by remember {
        mutableStateOf(0)
    }

    val totalDistressScore = distressSnapshot.globalScore

    // Temporary testing scale:
    // 1 = green, 2 = orange, 3 or more = red.
    val alertLevel = when (totalDistressScore) {
        0 -> 0
        1 -> 1
        2 -> 2
        else -> 3
    }

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

    LaunchedEffect(alertLevel) {
        // If the current distress level is 0, do not clear an unread alert.
        // The unread alert should stay until the user opens the chat.
        if (alertLevel == 0) {
            // Reset the acknowledged level only if there is no unread alert waiting.
            // This allows future new alerts to appear after the previous alert was handled.
            if (!hasUnreadDistressAlert && unreadDistressSnapshot == null) {
                acknowledgedAlertLevel = 0
                displayedAlertLevel = 0
            }

            return@LaunchedEffect
        }

        // Show a new alert only if it is higher than:
        // 1. the currently displayed unread alert
        // 2. the highest alert level the user already opened/read
        if (alertLevel > displayedAlertLevel && alertLevel > acknowledgedAlertLevel) {
            displayedAlertLevel = alertLevel
            unreadDistressSnapshot = distressSnapshot

            hasUnreadDistressAlert = true
            showAlertText = true

            delay(5000)

            // Only the small text bubble disappears after 5 seconds.
            // The exclamation mark and the saved chat message stay.
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

        val snapshotForChatPanel = unreadDistressSnapshot ?: distressSnapshot

        if (isChatOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(10f)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        // Close the chat when clicking outside the panel.
                        isChatOpen = false
                        unreadDistressSnapshot = null
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
                        // Empty click handler.
                        // This prevents clicks inside the chat panel from closing it.
                    }
            ) {
                ChatPanel(
                    currentScreen = currentScreen,
                    onClose = {
                        isChatOpen = false
                        unreadDistressSnapshot = null
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
                            // Opening the chat:
                            // The user has seen the unread alert indicator, so remove it.
                            // Do not clear unreadDistressSnapshot here,
                            // because the ChatPanel still needs it to display the correct distress message.
                            acknowledgedAlertLevel = maxOf(
                                acknowledgedAlertLevel,
                                displayedAlertLevel
                            )

                            hasUnreadDistressAlert = false
                            showAlertText = false
                            displayedAlertLevel = 0
                            isChatOpen = true
                        } else {
                            // Closing the chat by clicking the bubble:
                            // Now it is safe to clear the unread distress snapshot.
                            isChatOpen = false
                            unreadDistressSnapshot = null
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
                            .offset(x = -160.dp, y = (-32).dp)
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
                        .offset(x = (-50).dp, y = (-40).dp)
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