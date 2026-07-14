package com.example.easyfill_project.chatbot.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.easyfill_project.R
import com.example.easyfill_project.chatbot.logic.BotActionMessageProvider
import com.example.easyfill_project.chatbot.logic.BotSuggestion
import com.example.easyfill_project.chatbot.logic.BotSuggestionBuilder
import com.example.easyfill_project.chatbot.model.BotAction
import com.example.easyfill_project.chatbot.model.BotAppState
import com.example.easyfill_project.chatbot.model.DistressSnapshot
import com.example.easyfill_project.distress_scoring.DistressMode

import kotlinx.coroutines.delay


@Composable
fun FloatingChatOverlay(
    modifier: Modifier = Modifier,
    distressSnapshot: DistressSnapshot = DistressSnapshot(),
    distressMode: DistressMode = DistressMode.FORM_FILLING,
    appState: BotAppState = BotAppState(),
    onBotAction: (BotAction) -> Unit = {}
) {
    var isChatOpen by remember {
        mutableStateOf(false)
    }

    val suggestionQueue = remember {
        mutableStateListOf<BotSuggestion>()
    }

    var lastAddedScore by remember {
        mutableStateOf(0)
    }

    var successMessage by remember {
        mutableStateOf<String?>(null)
    }

    val severityLevel = distressSnapshot.globalScore

    LaunchedEffect(
        severityLevel,
        distressMode,
        appState
    ) {
        if (
            severityLevel > 0 &&
            severityLevel != lastAddedScore
        ) {
            val suggestion =
                BotSuggestionBuilder.buildSuggestion(
                    severityLevel = severityLevel,
                    distressMode = distressMode,
                    appState = appState
                )

            if (suggestion != null) {
                suggestionQueue.add(suggestion)
                lastAddedScore = severityLevel
            }
        }

        if (severityLevel == 0) {
            lastAddedScore = 0
        }
    }

    val currentSuggestion =
        suggestionQueue.firstOrNull()

    // Never keep the popup open without an available suggestion.
    LaunchedEffect(currentSuggestion) {
        if (currentSuggestion == null) {
            isChatOpen = false
        }
    }


    // After showing a successful action message,
    // close the popup and remove the completed suggestion.
    LaunchedEffect(successMessage) {
        if (successMessage != null) {
            delay(4000)

            if (suggestionQueue.isNotEmpty()) {
                suggestionQueue.removeAt(0)
            }

            successMessage = null
            isChatOpen = false
            lastAddedScore = 0
        }
    }

    val alertColor =
        when (severityLevel) {
            0 -> Color.Transparent
            1 -> Color(0xFF4CAF50)
            2 -> Color(0xFFE1CC13)
            3 -> Color(0xFFFF5722)
            else -> Color(0xFFB92014)
        }

    val alertText =
        when (severityLevel) {
            0 -> ""
            1 -> "יש לי הצעה קטנה"
            2 -> "\u202Bאפשר לעזור?\u202C"
            3 -> "\u202Bרוצה שאקל עליך\u202C"
            else -> "יש אפשרויות סיוע"
        }

    Box(
        modifier = modifier.size(48.dp),
        contentAlignment = Alignment.Center
    ) {
        // Chatbot icon.
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(CircleShape)
                .background(
                    MaterialTheme.colorScheme.primary
                )
                .clickable {
                    // Open the window only when a suggestion already exists.
                    if (currentSuggestion != null) {
                        isChatOpen = !isChatOpen
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(
                    id = R.drawable.chatbot_icon
                ),
                contentDescription =
                    "פתיחת צ'אטבוט EasyFill",
                modifier = Modifier.size(56.dp)
            )
        }

        // Alert layout — unchanged.
        if (
            !isChatOpen &&
            currentSuggestion != null &&
            severityLevel > 0 &&
            alertText.isNotBlank()
        ) {
            CompositionLocalProvider(
                LocalLayoutDirection provides
                        LayoutDirection.Ltr
            ) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .wrapContentSize(
                            align = Alignment.TopEnd,
                            unbounded = true
                        )
                        .absoluteOffset(
                            x = 0.dp,
                            y = (-26).dp
                        )
                        .zIndex(2f),
                    verticalAlignment =
                        Alignment.CenterVertically,
                    horizontalArrangement =
                        Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 190.dp)
                            .background(
                                color = Color.White,
                                shape =
                                    RoundedCornerShape(12.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = alertColor,
                                shape =
                                    RoundedCornerShape(12.dp)
                            )
                            .padding(
                                horizontal = 8.dp,
                                vertical = 4.dp
                            )
                    ) {
                        Text(
                            text = alertText,
                            color = alertColor,
                            fontSize = 10.sp,
                            fontWeight =
                                FontWeight.Medium,
                            maxLines = 2
                        )
                    }

                    Box(
                        modifier = Modifier
                            .offset(y = 8.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(alertColor),
                        contentAlignment =
                            Alignment.Center
                    ) {
                        Text(
                            text = "!",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight =
                                FontWeight.Bold
                        )
                    }
                }
            }
        }

        DropdownMenu(
            expanded =
                isChatOpen &&
                        currentSuggestion != null,
            onDismissRequest = {
                isChatOpen = false
            },
            modifier = Modifier.width(260.dp),
            offset = DpOffset(
                x = 24.dp,
                y = 8.dp
            ),
            shape = RoundedCornerShape(20.dp),
            containerColor =
                MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp,
            border = BorderStroke(
                2.dp,
                MaterialTheme.colorScheme.secondary
            )
        ) {
            if (currentSuggestion != null) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    if (successMessage != null) {
                        // After an action is completed,
                        // show only the success message.
                        Text(
                            text = successMessage.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        Text(
                            text = currentSuggestion.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(
                            modifier = Modifier.height(8.dp)
                        )

                        // Action buttons.
                        Column(
                            verticalArrangement =
                                Arrangement.spacedBy(4.dp)
                        ) {
                            currentSuggestion.options.forEach { option ->
                                OutlinedButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        onBotAction(option.action)

                                        successMessage =
                                            BotActionMessageProvider.getMessage(
                                                option.action
                                            )
                                    },
                                    border = BorderStroke(
                                        width = 2.dp,
                                        color =
                                            MaterialTheme.colorScheme.secondary
                                    ),
                                    colors =
                                        ButtonDefaults.outlinedButtonColors(
                                            containerColor =
                                                MaterialTheme.colorScheme.primary,
                                            contentColor =
                                                MaterialTheme.colorScheme.secondary
                                        )
                                ) {
                                    Text(
                                        text = option.label,
                                        color =
                                            MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }

                        Spacer(
                            modifier = Modifier.height(4.dp)
                        )

                        // Show "לא עכשיו" only before an action is selected.
                        CompositionLocalProvider(
                            LocalLayoutDirection provides LayoutDirection.Ltr
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (suggestionQueue.isNotEmpty()) {
                                            suggestionQueue.removeAt(0)
                                        }

                                        successMessage = null
                                        isChatOpen = false
                                        lastAddedScore = 0
                                    },
                                    border = BorderStroke(
                                        width = 2.dp,
                                        color =
                                            MaterialTheme.colorScheme.secondary
                                    ),
                                    colors =
                                        ButtonDefaults.outlinedButtonColors(
                                            containerColor =
                                                MaterialTheme.colorScheme.primary,
                                            contentColor =
                                                MaterialTheme.colorScheme.onPrimary
                                        )
                                ) {
                                    CompositionLocalProvider(
                                        LocalLayoutDirection provides
                                                LayoutDirection.Rtl
                                    ) {
                                        Text(
                                            text = "לא עכשיו",
                                            color =
                                                MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}