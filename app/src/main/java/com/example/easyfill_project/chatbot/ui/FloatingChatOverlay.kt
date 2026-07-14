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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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


import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.widthIn

/**
 * Displays the chatbot icon beside the current form-section headline.
 *
 * The component always reserves only 48.dp of layout space.
 * The alert and opened card are drawn outside that fixed area,
 * so they do not cause the section headline to move to another line.
 */
@Composable
fun FloatingChatOverlay(
    modifier: Modifier = Modifier,
    distressSnapshot: DistressSnapshot = DistressSnapshot(),

    // Tells the suggestion builder whether the user is currently
    // filling a form or using another distress-analysis mode.
    distressMode: DistressMode = DistressMode.FORM_FILLING,

    // Current app settings used when creating chatbot suggestions.
    appState: BotAppState = BotAppState(),

    // Sends the selected chatbot action back to AppNavigation.
    onBotAction: (BotAction) -> Unit = {}
) {
    // True when the suggestion card is open.
    var isChatOpen by remember {
        mutableStateOf(false)
    }

    // Stores suggestions waiting for the user.
    val suggestionQueue = remember {
        mutableStateListOf<BotSuggestion>()
    }

    // Prevents repeatedly adding a suggestion for the same score.
    var lastAddedScore by remember {
        mutableStateOf(0)
    }

    // True after the user selects an option or presses "לא עכשיו".
    var userAnsweredCurrentSuggestion by remember {
        mutableStateOf(false)
    }

    // Message shown after an action is selected.
    var successMessage by remember {
        mutableStateOf<String?>(null)
    }

    // Current total distress level.
    val severityLevel = distressSnapshot.globalScore

    /**
     * Creates a new suggestion when:
     * - the distress score is greater than zero;
     * - the score changed;
     * - the suggestion builder returned a valid suggestion.
     */
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

        // Allows the same distress level to create a future
        // suggestion after the score first returns to zero.
        if (severityLevel == 0) {
            lastAddedScore = 0
        }
    }

    // Only the first waiting suggestion is currently displayed.
    val currentSuggestion =
        suggestionQueue.firstOrNull()

    // Alert color according to the current distress level.
    val alertColor =
        when (severityLevel) {
            0 -> Color.Transparent
            1 -> Color(0xFF4CAF50) // Green
            2 -> Color(0xFFE1CC13) // Yellow
            3 -> Color(0xFFFF5722) // Orange
            else -> Color(0xFFB92014) // Red
        }

    // Short alert text displayed above the chatbot.
    val alertText =
        when (severityLevel) {
            0 -> ""
            1 -> "יש לי הצעה קטנה"
            2 -> "\u202Bאפשר לעזור?\u202C"
            3 -> "\u202Bרוצה שאקל עליך\u202C"
            else -> "יש אפשרויות סיוע"
        }

    /**
     * Fixed-size chatbot container.
     *
     * It always occupies exactly 48.dp, even when the alert
     * or full suggestion card is visible.
     */// The chatbot always reserves only 48.dp.
// The alert and card can draw outside this area
// without changing the section headline width.
    Box(
        modifier = modifier.size(48.dp),
        contentAlignment = Alignment.Center
    ) {
        // Chatbot icon.
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable {
                    isChatOpen = !isChatOpen
                },
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(
                    id = R.drawable.chatbot_icon
                ),
                contentDescription = "פתיחת צ'אטבוט EasyFill",
                modifier = Modifier.size(56.dp)
            )
        }

        /*
         * Alert above the chatbot.
         *
         * The Row uses LTR only for positioning:
         * - the text is placed first, on the left;
         * - the ! badge stays fixed on the right;
         * - longer text expands toward the left.
         */
        if (
            !isChatOpen &&
            currentSuggestion != null
        ) {
            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Ltr
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
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement =
                        Arrangement.spacedBy(6.dp)
                ) {
                    // Alert message expands toward the left.
                    Box(
                        modifier = Modifier
                            .widthIn(max = 190.dp)
                            .background(
                                color = Color.White,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = alertColor,
                                shape = RoundedCornerShape(12.dp)
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
                            fontWeight = FontWeight.Medium,
                            maxLines = 2
                        )
                    }

                    // ! badge stays on the right,
                    // directly above the chatbot.
                    Box(
                        modifier = Modifier
                            .offset(y = 8.dp)
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

        /*
         * Open chatbot card.
         *
         * It is also unbounded, so opening it does not
         * change the width of the section headline.
         */
        if (
            isChatOpen &&
            currentSuggestion != null
        ) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .wrapContentSize(
                        align = Alignment.TopEnd,
                        unbounded = true
                    )
                    .absoluteOffset(
                        x = 0.dp,
                        y = 56.dp
                    )
                    .width(300.dp)
                    .zIndex(3f),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFF1ECF4)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    if (successMessage != null) {
                        Text(
                            text = successMessage.orEmpty(),
                            style =
                                MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Text(
                            text = currentSuggestion.message,
                            style =
                                MaterialTheme.typography.bodyMedium
                        )

                        Spacer(
                            modifier = Modifier.height(16.dp)
                        )

                        Column(
                            verticalArrangement =
                                Arrangement.spacedBy(8.dp)
                        ) {
                            currentSuggestion.options.forEach { option ->
                                Button(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        onBotAction(option.action)

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
                                modifier = Modifier.fillMaxWidth(),
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
                        modifier = Modifier.height(8.dp)
                    )

                    TextButton(
                        onClick = {
                            if (
                                userAnsweredCurrentSuggestion &&
                                suggestionQueue.isNotEmpty()
                            ) {
                                suggestionQueue.removeAt(0)
                                userAnsweredCurrentSuggestion = false
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