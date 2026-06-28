package com.example.easyfill_project.chatbot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.easyfill_project.chatbot.intent.Model2VecIntentDetector
import com.example.easyfill_project.chatbot.logic.ChatBotManager
import com.example.easyfill_project.chatbot.model.BotAction
import com.example.easyfill_project.chatbot.model.BotAppState
import com.example.easyfill_project.chatbot.model.BotContext
import com.example.easyfill_project.chatbot.model.BotResponse
import com.example.easyfill_project.chatbot.model.ChatMessage
import com.example.easyfill_project.chatbot.model.DistressSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon

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

    val context = LocalContext.current

    val semanticIntentDetector = remember {
        Model2VecIntentDetector(context)
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            semanticIntentDetector.load()
        }
    }

    val chatBotManager = remember {
        ChatBotManager(
            intentDetector = semanticIntentDetector
        )
    }

    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                text = "שלום, אני העוזר של EasyFill. איך אפשר לעזור?",
                isFromUser = false
            )
        )
    }

    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    // Build the current bot context from the screen, distress snapshot, and app state.
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

    fun sendCurrentMessage() {
        val userText = message.trim()

        if (userText.isEmpty()) {
            return
        }

        messages.add(
            ChatMessage(
                text = userText,
                isFromUser = true
            )
        )

        val currentPendingAction = pendingAction

        // If the bot is waiting for confirmation and the user approves, execute the pending action.
        if (currentPendingAction != null && isUserApproval(userText)) {

            if (currentPendingAction != BotAction.ShowEmergencyContacts) {
                onBotAction(currentPendingAction)
            }

            messages.add(
                ChatMessage(
                    text = getActionExecutedMessage(currentPendingAction),
                    isFromUser = false
                )
            )

            pendingAction = null
            message = ""
            return
        }

        // If the bot is waiting for confirmation and the user rejects, cancel the pending action.
        if (currentPendingAction != null && isUserRejection(userText)) {
            messages.add(
                ChatMessage(
                    text = "אין בעיה, לא ביצעתי את הפעולה.",
                    isFromUser = false
                )
            )

            pendingAction = null
            message = ""
            return
        }

        // If there was a pending action but the user wrote something else,
        // clear the pending action so a later "yes" will not trigger an old action.
        if (currentPendingAction != null) {
            pendingAction = null
        }

        val botResponse = chatBotManager.getResponse(
            userMessage = userText,
            context = botContext
        )

        handleBotResponse(botResponse)

        message = ""
    }

    // Stores the last distress state shown inside the chat.
    // This is better than only storing the global score because the dominant source can change
    // even when the total score stays the same.
    var lastShownDistressKey by remember {
        mutableStateOf<String?>(null)
    }

    // Stores the index of the distress message inside the chat.
    // This allows us to replace the old distress message instead of adding many messages.
    var distressMessageIndex by remember {
        mutableStateOf<Int?>(null)
    }

    LaunchedEffect(
        distressSnapshot.globalScore,
        distressSnapshot.touchScore,
        distressSnapshot.voiceScore,
        distressSnapshot.faceScore,
        distressSnapshot.semanticTextScore,
        distressSnapshot.formBehaviorScore
    ) {
        val currentGlobalScore = distressSnapshot.globalScore

        // If there is no distress, reset the stored distress message state.
        if (currentGlobalScore == 0) {
            lastShownDistressKey = null
            distressMessageIndex = null
            return@LaunchedEffect
        }

        // A unique key that represents the current distress state.
        // If any score changes, this key changes.
        val currentDistressKey =
            "${distressSnapshot.globalScore}-" +
                    "${distressSnapshot.touchScore}-" +
                    "${distressSnapshot.voiceScore}-" +
                    "${distressSnapshot.faceScore}-" +
                    "${distressSnapshot.semanticTextScore}-" +
                    "${distressSnapshot.formBehaviorScore}"

        // Do not show the same distress message again if nothing changed.
        if (currentDistressKey == lastShownDistressKey) {
            return@LaunchedEffect
        }

        val distressSuggestion = chatBotManager.getDistressSuggestion(botContext)
            ?: return@LaunchedEffect

        val index = distressMessageIndex

        if (index != null && index in messages.indices) {
            // Replace the previous distress message.
            messages[index] = ChatMessage(
                text = distressSuggestion.message,
                isFromUser = false
            )

            listState.animateScrollToItem(index)
        } else {
            // Add the first distress message to the chat.
            messages.add(
                ChatMessage(
                    text = distressSuggestion.message,
                    isFromUser = false
                )
            )

            distressMessageIndex = messages.lastIndex
        }

        // Automatic distress messages usually should not force the user to respond.
        // Still, this supports actions if a future BotResponse includes one.
        if (distressSuggestion.action != BotAction.None) {
            if (distressSuggestion.requiresConfirmation) {
                pendingAction = distressSuggestion.action
            } else {
                onBotAction(distressSuggestion.action)
            }
        }

        lastShownDistressKey = currentDistressKey
    }

    Card(
        modifier = modifier
            .width(320.dp)
            .height(330.dp),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF1ECF4)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "EasyFill Assistant",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "מסך נוכחי: $currentScreen",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(32.dp)
                ) {
                    Text(
                        text = "×",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { chatMessage ->
                    ChatMessageBubble(chatMessage = chatMessage)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            CompactChatInput(
                message = message,
                onMessageChange = { message = it },
                onSend = {
                    sendCurrentMessage()
                },
                onMicClick = {
                    // STT / voice input will be connected here later.
                }
            )
        }
    }
}

@Composable
private fun CompactChatInput(
    message: String,
    onMessageChange: (String) -> Unit,
    onSend: () -> Unit,
    onMicClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(28.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        ) {
            if (message.isBlank()) {
                Text(
                    text = "כתוב הודעה...",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            BasicTextField(
                value = message,
                onValueChange = onMessageChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(
                    onSend = {
                        onSend()
                    }
                )
            )
        }

        IconButton(
            onClick = onMicClick,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = "קלט קולי",
                tint = Color.DarkGray
            )
        }

        IconButton(
            onClick = onSend,
            modifier = Modifier
                .size(40.dp)
                .background(Color.Black, CircleShape)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "שלח",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
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
            "הפעלתי את ההקראה."
        }

        BotAction.StopReading -> {
            "עצרתי את ההקראה."
        }

        BotAction.EnableAutoRead -> {
            "הפעלתי הקראה אוטומטית."
        }

        BotAction.DisableAutoRead -> {
            "כיביתי הקראה אוטומטית."
        }

        BotAction.OpenHome -> {
            "העברתי אותך לדף הבית."
        }

        BotAction.OpenFormOptions -> {
            "העברתי אותך למסך בחירת הטפסים."
        }

        BotAction.OpenFormsProgress -> {
            "העברתי אותך למסך התקדמות הטפסים."
        }

        BotAction.OpenProfile -> {
            "פתחתי את הפרופיל האישי."
        }

        BotAction.OpenGuidance -> {
            "פתחתי את מדריך המשתמש."
        }

        BotAction.OpenUploadPdf -> {
            "פתחתי את מסך העלאת הטפסים."
        }

        BotAction.OpenPersonalSettings -> {
            "פתחתי את מסך ההתאמה האישית."
        }

        BotAction.OpenContrastSettings -> {
            "פתחתי את הגדרות הצבעים והניגודיות."
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
            "כיביתי את מוזיקת הרקע."
        }

        is BotAction.SetContrast -> {
            "שיניתי את הצבעים ל${action.option.displayName}."
        }

        is BotAction.SetFontSize -> {
            "שיניתי את גודל הטקסט ל${action.option.displayName}."
        }

        BotAction.ShowEmergencyContacts -> {
            """
אפשר לפנות לעזרה דרך:

• אדם קרוב שאת/ה סומך/ת עליו
• מוקד חירום מקומי במקרה של סכנה מיידית
• עובד/ת סוציאלי/ת, יועץ/ת או רופא/ה

בנוסף, אני יכול לעזור לך עכשיו בתוך האפליקציה:
• להגדיל טקסט
• להפעיל הקראה
• לשנות צבעים למצב רגוע יותר
• לפתוח התאמה אישית

אפשר לכתוב לי למשל: "תגדיל טקסט" או "תפעיל הקראה".
""".trimIndent()
        }

        BotAction.None -> {
            ""
        }

    }
}