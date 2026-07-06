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

    var showDistressHighlight by remember {
        mutableStateOf(false)
    }

    var showAlertText by remember { mutableStateOf(false) }

    val combinedScore =
        distressSnapshot.touchScore + distressSnapshot.voiceScore

    val severityLevel = when (combinedScore) {
        0 -> 0
        in 1..2 -> 1
        in 3..4 -> 2
        in 5..6 -> 3
        else -> 4
    }


    LaunchedEffect(autoOpenOnDistress) {
        if (autoOpenOnDistress && !hasAutoOpenedForDistress) {
            hasUnreadDistressAlert = true
            showDistressHighlight = true
            hasAutoOpenedForDistress = true

            delay(5000)

            showDistressHighlight = false
        }

        if (!autoOpenOnDistress) {
            hasAutoOpenedForDistress = false
        }
    }


    LaunchedEffect(severityLevel) {
        if (severityLevel > 0) {
            showAlertText = true
            delay(5000)
            showAlertText = false
        } else {
            showAlertText = false
        }
    }

    // בגלל שהאפליקציה RTL:
    // offsetX = 0 אומר צד ימין.
    // offsetX גדול יותר מזיז שמאלה.
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

        val infiniteTransition = rememberInfiniteTransition()

        val distressBorderAlpha by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 450),
                repeatMode = RepeatMode.Reverse
            )
        )


        LaunchedEffect(screenWidthPx, screenHeightPx) {
            if (!initialPositionSet && screenWidthPx > 0f && screenHeightPx > 0f) {
                offsetX = 24f
                offsetY = (screenHeightPx - bubbleSizePx - 120f).coerceAtLeast(0f)
                initialPositionSet = true
            }
        }

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
                        // בכוונה ריק:
                        // זה מונע מלחיצה בתוך הבוט לסגור אותו
                    }
            ) {
                ChatPanel(
                    currentScreen = currentScreen,
                    onClose = { isChatOpen = false },
                    onBotAction = onBotAction,
                    distressSnapshot = distressSnapshot,
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
                .size(width = 190.dp, height = 100.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(bubbleSizeDp)
                    .then(
                        if (showDistressHighlight) {
                            Modifier.border(
                                width = 3.dp,
                                color = Color.Red.copy(alpha = distressBorderAlpha),
                                shape = CircleShape
                            )
                        } else {
                            Modifier
                        }
                    )
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
                            hasUnreadDistressAlert = false
                            showDistressHighlight = false
                        }

                        isChatOpen = !isChatOpen
                    },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.chatbot_icon),
                    contentDescription = "פתיחת צ'אטבוט EasyFill",
                    modifier = Modifier.size(80.dp)
                )
            }


            if (severityLevel > 0 && !isChatOpen)  {


                val alertColor = when (severityLevel) {
                    1 -> Color(0xFF4CAF50) // green
                    2 -> Color(0xFFFFA000) // orange
                    3 -> Color(0xFFFF5722) // deep orange
                    else -> Color.Red
                }

                val alertText = when (severityLevel) {
                    1 -> "יש לי הצעה קטנה"
                    2 -> "אפשר לעזור?"
                    3 -> "רוצה שאקל עליך?"
                    else -> "יש אפשרויות סיוע"
                }

                if (showAlertText) {
                //  text bubble
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(x = 55.dp, y = (-15).dp)
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .border(1.dp, alertColor, RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
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