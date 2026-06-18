package com.example.easyfill_project.chatbot.intent

import com.example.easyfill_project.chatbot.model.BotIntent

interface IntentDetector {
    fun detectIntent(userMessage: String): BotIntent
}