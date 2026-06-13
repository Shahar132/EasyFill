package com.example.easyfill_project.chatbot.semantic

import com.example.easyfill_project.chatbot.model.BotIntent

data class IntentMatch(
    val intent: BotIntent,
    val score: Float,
    val matchedText: String
)