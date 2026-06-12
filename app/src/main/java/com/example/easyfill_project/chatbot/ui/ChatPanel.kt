package com.example.easyfill_project.chatbot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.easyfill_project.chatbot.logic.ChatBotManager
import com.example.easyfill_project.chatbot.model.BotAction
import com.example.easyfill_project.chatbot.model.BotAppState
import com.example.easyfill_project.chatbot.model.BotContext
import com.example.easyfill_project.chatbot.model.BotResponse
import com.example.easyfill_project.chatbot.model.ChatMessage
import com.example.easyfill_project.chatbot.model.DistressSnapshot

@Composable
fun ChatPanel(
    currentScreen: String,
    onClose: () -> Unit,
    onBotAction: (BotAction) -> Unit = {},
    distressSnapshot: DistressSnapshot = DistressSnapshot(),
    appState: BotAppState = BotAppState(),
    modifier: Modifier = Modifier
) {
    var message by remember { mutableStateOf("") }

    var pendingAction by remember {
        mutableStateOf<BotAction?>(null)
    }

    var hasShownDistressSuggestion by remember(currentScreen) {
        mutableStateOf(false)
    }

    val chatBotManager = remember {
        ChatBotManager()
    }

    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                text = "שלום, אני העוזר של EasyFill. איך אפשר לעזור?",
                isFromUser = false
            )
        )
    }

    val botContext = BotContext(
        currentScreen = currentScreen,
        distressSnapshot = distressSnapshot,
        appState = appState
    )

    fun handleBotResponse(botResponse: BotResponse) {
        messages.add(
            ChatMessage(
                text = botResponse.message,
                isFromUser = false
            )
        )

        if (botResponse.action != BotAction.None) {
            if (botResponse.requiresConfirmation) {
                pendingAction = botResponse.action
            } else {
                onBotAction(botResponse.action)
            }
        }
    }

    LaunchedEffect(currentScreen) {
        if (!hasShownDistressSuggestion) {
            val distressSuggestion = chatBotManager.getDistressSuggestion(botContext)

            if (distressSuggestion != null) {
                handleBotResponse(distressSuggestion)
                hasShownDistressSuggestion = true
            }
        }
    }

    Card(
        modifier = modifier
            .width(330.dp)
            .height(420.dp),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Text(
                text = "EasyFill Assistant",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "מסך נוכחי: $currentScreen",
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { chatMessage ->
                    ChatMessageBubble(chatMessage = chatMessage)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text("כתוב הודעה...")
                },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    val userText = message.trim()

                    if (userText.isNotEmpty()) {
                        messages.add(
                            ChatMessage(
                                text = userText,
                                isFromUser = true
                            )
                        )

                        val currentPendingAction = pendingAction

                        if (currentPendingAction != null && isUserApproval(userText)) {
                            onBotAction(currentPendingAction)

                            messages.add(
                                ChatMessage(
                                    text = getActionExecutedMessage(currentPendingAction),
                                    isFromUser = false
                                )
                            )

                            pendingAction = null
                            message = ""
                            return@Button
                        }

                        if (currentPendingAction != null && isUserRejection(userText)) {
                            messages.add(
                                ChatMessage(
                                    text = "אין בעיה, לא ביצעתי את הפעולה.",
                                    isFromUser = false
                                )
                            )

                            pendingAction = null
                            message = ""
                            return@Button
                        }

                        val botResponse = chatBotManager.getResponse(
                            userMessage = userText,
                            context = botContext
                        )

                        handleBotResponse(botResponse)

                        message = ""
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("שלח")
            }

            Spacer(modifier = Modifier.height(6.dp))

            Button(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("סגור")
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(
    chatMessage: ChatMessage
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (chatMessage.isFromUser) {
            Alignment.CenterEnd
        } else {
            Alignment.CenterStart
        }
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = if (chatMessage.isFromUser) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier.fillMaxWidth(0.78f)
        ) {
            Text(
                text = chatMessage.text,
                modifier = Modifier.padding(10.dp),
                color = if (chatMessage.isFromUser) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private fun isUserApproval(text: String): Boolean {
    val cleanText = text.trim().lowercase()

    return cleanText == "כן" ||
            cleanText == "כן בבקשה" ||
            cleanText == "אפשר" ||
            cleanText == "בסדר" ||
            cleanText == "אוקי" ||
            cleanText == "יאללה" ||
            cleanText == "מאשר" ||
            cleanText == "תאשר"
}

private fun isUserRejection(text: String): Boolean {
    val cleanText = text.trim().lowercase()

    return cleanText == "לא" ||
            cleanText == "לא תודה" ||
            cleanText == "עזוב" ||
            cleanText == "לא עכשיו" ||
            cleanText == "ביטול" ||
            cleanText == "אל תפעיל" ||
            cleanText == "אל תפתח"
}

private fun getActionExecutedMessage(action: BotAction): String {
    return when (action) {
        BotAction.ReadAloud -> {
            "בסדר, הפעלתי הקראה."
        }

        BotAction.StopReading -> {
            "בסדר, עצרתי את ההקראה."
        }

        BotAction.EnableAutoRead -> {
            "הפעלתי הקראה אוטומטית לכל מסך."
        }

        BotAction.DisableAutoRead -> {
            "כיביתי הקראה אוטומטית."
        }

        BotAction.OpenPersonalSettings -> {
            "פתחתי את מסך ההתאמה האישית."
        }

        BotAction.OpenContrastSettings -> {
            "פתחתי את הגדרות הצבעים."
        }

        BotAction.OpenFontSizeSettings -> {
            "פתחתי את הגדרות גודל הטקסט."
        }

        BotAction.OpenBackgroundSounds -> {
            "פתחתי את מסך צלילי הרקע."
        }

        is BotAction.PlaySound -> {
            "הפעלתי ${action.option.displayName}."
        }

        BotAction.StopBackgroundMusic -> {
            "עצרתי את מוזיקת הרקע."
        }

        is BotAction.SetContrast -> {
            "שיניתי את הצבעים ל${action.option.displayName}."
        }

        is BotAction.SetFontSize -> {
            "שיניתי את גודל הטקסט ל${action.option.displayName}."
        }

        BotAction.None -> {
            ""
        }
    }
}