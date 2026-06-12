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
import com.example.easyfill_project.chatbot.model.BotContext
import com.example.easyfill_project.chatbot.model.BotResponse
import com.example.easyfill_project.chatbot.model.ChatMessage
import com.example.easyfill_project.chatbot.model.DistressSnapshot
import com.example.easyfill_project.chatbot.model.BotAppState

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

    // כרגע זה Context לבדיקה בלבד.
    // בעתיד במקום DistressSnapshot ידני נקבל את זה מ-DistressManager.
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

        if (botResponse.action != BotAction.NONE) {
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

    return cleanText.contains("כן") ||
            cleanText.contains("אפשר") ||
            cleanText.contains("בסדר") ||
            cleanText.contains("תפעיל") ||
            cleanText.contains("פתח") ||
            cleanText.contains("יאללה") ||
            cleanText.contains("אוקי")
}

private fun isUserRejection(text: String): Boolean {
    val cleanText = text.trim().lowercase()

    return cleanText.contains("לא") ||
            cleanText.contains("עזוב") ||
            cleanText.contains("לא עכשיו") ||
            cleanText.contains("ביטול") ||
            cleanText.contains("אל תפעיל") ||
            cleanText.contains("אל תפתח")
}

private fun getActionExecutedMessage(action: BotAction): String {
    return when (action) {
        BotAction.READ_ALOUD -> "בסדר, הפעלתי הקראה."
        BotAction.STOP_READING -> "בסדר, עצרתי את ההקראה."

        BotAction.ENABLE_AUTO_READ -> "הפעלתי הקראה אוטומטית לכל מסך."
        BotAction.DISABLE_AUTO_READ -> "כיביתי הקראה אוטומטית."

        BotAction.OPEN_PERSONAL_SETTINGS -> "פתחתי את מסך ההתאמה האישית."
        BotAction.OPEN_CONTRAST_SETTINGS -> "פתחתי את הגדרות הניגודיות."
        BotAction.OPEN_FONT_SIZE_SETTINGS -> "פתחתי את הגדרות גודל הטקסט."
        BotAction.OPEN_BACKGROUND_SOUNDS -> "פתחתי את מסך צלילי הרקע."

        BotAction.PLAY_NATURE_SOUND -> "הפעלתי צלילי טבע מרגיעים."
        BotAction.PLAY_CALM_MUSIC -> "הפעלתי מוזיקה למדיטציה."
        BotAction.PLAY_INSTRUMENT_SOUND -> "הפעלתי צלילי נגינה מרגיעים."
        BotAction.STOP_BACKGROUND_MUSIC -> "עצרתי את מוזיקת הרקע."

        BotAction.SET_CONTRAST_DEFAULT -> "העברתי את הצבעים למצב רגיל."
        BotAction.SET_CONTRAST_HIGH -> "הפעלתי מצב ניגודיות גבוהה."
        BotAction.SET_CONTRAST_LOW -> "הפעלתי צבעים רגועים."

        BotAction.SET_FONT_SMALL -> "שיניתי את גודל הטקסט לקטן."
        BotAction.SET_FONT_NORMAL -> "שיניתי את גודל הטקסט לרגיל."
        BotAction.SET_FONT_LARGE -> "שיניתי את גודל הטקסט לגדול."

        BotAction.NONE -> ""
    }
}