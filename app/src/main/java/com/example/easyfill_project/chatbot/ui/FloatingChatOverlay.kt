package com.example.easyfill_project.chatbot.ui

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
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

    var userAnsweredCurrentSuggestion by remember {
        mutableStateOf(false)
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
                    isChatOpen = !isChatOpen
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
            currentSuggestion != null
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

        /*
         * DropdownMenu uses a popup window.
         * It is therefore displayed above the form fields.
         */
        DropdownMenu(
            expanded =
                isChatOpen &&
                        currentSuggestion != null,
            onDismissRequest = {
                isChatOpen = false
            },
            modifier = Modifier.width(270.dp),
            offset = DpOffset(
                x = 24.dp,
                y = 8.dp
            ),
            shape = RoundedCornerShape(20.dp),
            containerColor = Color(0xFFF1ECF4),
            shadowElevation = 12.dp
        ) {
            if (currentSuggestion != null) {
                Column(
                    modifier = Modifier.padding(10.dp)
                ) {
                    if (successMessage != null) {
                        Text(
                            text =
                                successMessage.orEmpty(),
                            style =
                                MaterialTheme.typography
                                    .bodyMedium
                        )
                    } else {
                        Text(
                            text =
                                currentSuggestion.message,
                            style =
                                MaterialTheme.typography
                                    .bodyMedium
                        )

                        Spacer(
                            modifier =
                                Modifier.height(8.dp)
                        )

                        Column(
                            verticalArrangement =
                                Arrangement.spacedBy(4.dp)
                        ) {
                            currentSuggestion.options
                                .forEach { option ->
                                    Button(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .height(40.dp),
                                        onClick = {
                                            onBotAction(
                                                option.action
                                            )

                                            successMessage =
                                                BotActionMessageProvider
                                                    .getMessage(
                                                        option.action
                                                    )

                                            userAnsweredCurrentSuggestion =
                                                true
                                        }
                                    ) {
                                        Text(option.label)
                                    }
                                }

                            OutlinedButton(
                                modifier =
                                    Modifier.fillMaxWidth()
                                .height(40.dp),
                                onClick = {
                                    successMessage =
                                        "אין בעיה, לא ביצעתי שינוי."

                                    userAnsweredCurrentSuggestion =
                                        true
                                }
                            ) {
                                Text("לא עכשיו")
                            }
                        }
                    }

                    Spacer(
                        modifier = Modifier.height(4.dp)
                    )

                    TextButton(
                        onClick = {
                            if (
                                userAnsweredCurrentSuggestion &&
                                suggestionQueue.isNotEmpty()
                            ) {
                                suggestionQueue.removeAt(0)

                                userAnsweredCurrentSuggestion =
                                    false

                                successMessage = null
                            }

                            isChatOpen = false
                        }
                    ) {
                        Text("סגור")
                    }
                }
            }
        }
    }
}