package com.example.easyfill_project.chatbot.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.easyfill_project.R
import com.example.easyfill_project.chatbot.model.BotAction
import com.example.easyfill_project.chatbot.model.BotAppState
import com.example.easyfill_project.chatbot.model.DistressSnapshot
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

    LaunchedEffect(autoOpenOnDistress) {
        if (autoOpenOnDistress && !hasAutoOpenedForDistress) {
            isChatOpen = true
            hasAutoOpenedForDistress = true
        }

        if (!autoOpenOnDistress) {
            hasAutoOpenedForDistress = false
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

        val panelWidthDp = 330.dp
        val panelHeightDp = 360.dp

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

        if (isChatOpen) {
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

            ChatPanel(
                currentScreen = currentScreen,
                onClose = { isChatOpen = false },
                onBotAction = onBotAction,
                distressSnapshot = distressSnapshot,
                appState = appState,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            panelX.roundToInt(),
                            panelY.roundToInt()
                        )
                    }
                    .zIndex(20f)
            )
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
    }
}