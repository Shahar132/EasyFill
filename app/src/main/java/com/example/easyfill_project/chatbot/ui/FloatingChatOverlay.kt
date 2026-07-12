package com.example.easyfill_project.chatbot.ui

import androidx.compose.foundation.Image

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.easyfill_project.R
import com.example.easyfill_project.chatbot.logic.BotActionMessageProvider
import com.example.easyfill_project.chatbot.logic.BotSuggestion
import com.example.easyfill_project.chatbot.logic.BotSuggestionBuilder
import com.example.easyfill_project.chatbot.model.BotAction
import com.example.easyfill_project.chatbot.model.BotAppState
import com.example.easyfill_project.chatbot.model.DistressSnapshot
import com.example.easyfill_project.distress_scoring.DistressMode


//This file now only handles UI
@Composable
fun FloatingChatOverlay(
    modifier: Modifier = Modifier,
    distressSnapshot: DistressSnapshot = DistressSnapshot(),

    // Tells the suggestion builder whether the user is recording
    // or filling the form by typing.
    distressMode: DistressMode = DistressMode.FORM_FILLING,
    appState: BotAppState = BotAppState(),
    onBotAction: (BotAction) -> Unit = {}
) {
    // true = popup card is open, false = only icon is shown.
    var isChatOpen by remember { mutableStateOf(false) }

    // Queue of suggestions waiting for answer.
    val suggestionQueue = remember { mutableStateListOf<BotSuggestion>() }

    // Prevents adding same score suggestion again and again.
    var lastAddedScore by remember { mutableStateOf(0) }

    // true after user clicked an option or "לא עכשיו".
    var userAnsweredCurrentSuggestion by remember { mutableStateOf(false) }

    // Message shown after action succeeds.
    var successMessage by remember { mutableStateOf<String?>(null) }

    // Final distress score from DistressScoringManager.
    val severityLevel = distressSnapshot.globalScore

    // Runs when score/app state changes.
    LaunchedEffect(severityLevel, distressMode, appState) {
        // Add suggestion only when score is positive and new.
        if (severityLevel > 0 && severityLevel != lastAddedScore) {
            val suggestion = BotSuggestionBuilder.buildSuggestion(
                severityLevel = severityLevel,
                distressMode = distressMode,
                appState = appState
            )

            if (suggestion != null) {
                suggestionQueue.add(suggestion)
                lastAddedScore = severityLevel
            }
        }

        // If distress returns to 0, allow future suggestions again.
        if (severityLevel == 0) {
            lastAddedScore = 0
        }
    }

    // Show only first waiting suggestion.
    val currentSuggestion = suggestionQueue.firstOrNull()


// Alert color is based only on the total distress score.
    val alertColor = when (severityLevel) {
        0 -> Color.Transparent
        1 -> Color(0xFF4CAF50) // green - low
        2 -> Color(0xFFFFC107) // yellow - medium
        3 -> Color(0xFFFF9800) // orange - high
        else -> Color(0xFFF44336) // red - very high
    }


    // Small message shown above the chatbot icon.
    val alertText = when (severityLevel) {
        0 -> ""
        1 -> "יש לי הצעה קטנה"
        2 -> "אפשר לעזור?"
        3 -> "רוצה שאקל עליך?"
        else -> "יש אפשרויות סיוע"
    }

    // Full-screen overlay container.
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 16.dp, bottom = 24.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        Column(horizontalAlignment = Alignment.Start) {

            // Show card only if icon opened and suggestion exists.
            if (isChatOpen && currentSuggestion != null) {
                Card(
                    modifier = Modifier.width(300.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFF1ECF4)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {

                        // If user already chose action, show result message.
                        if (successMessage != null) {
                            Text(
                                text = successMessage ?: "",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            // Show original suggestion.
                            Text(
                                text = currentSuggestion.message,
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Show all option buttons.
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                currentSuggestion.options.forEach { option ->
                                    Button(
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = {
                                            // Perform selected action.
                                            onBotAction(option.action)

                                            // Show matching success message.
                                            successMessage =
                                                BotActionMessageProvider.getMessage(option.action)

                                            // Mark answered.
                                            userAnsweredCurrentSuggestion = true
                                        }
                                    ) {
                                        Text(option.label)
                                    }
                                }

                                // User declines suggestion.
                                OutlinedButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        successMessage = "אין בעיה, לא ביצעתי שינוי."
                                        userAnsweredCurrentSuggestion = true
                                    }
                                ) {
                                    Text("לא עכשיו")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Close button.
                        TextButton(
                            onClick = {
                                // Remove suggestion only after answer.
                                if (userAnsweredCurrentSuggestion) {
                                    suggestionQueue.removeAt(0)
                                    userAnsweredCurrentSuggestion = false
                                    successMessage = null
                                }

                                // Close popup.
                                isChatOpen = false
                            }
                        ) {
                            Text("סגור")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            // Show small alert bubble only when card is closed.
            if (!isChatOpen && currentSuggestion != null) {
                Box(
                    modifier = Modifier
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .border(1.dp, alertColor, RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = alertText,
                        color = alertColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
            }

            // Fixed chatbot icon.
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
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

                // Show ! badge if there is pending suggestion.
                if (currentSuggestion != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
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
}