package com.example.easyfill_project.chatbot.model

data class BotContext(
    val currentScreen: String,
    val distressSnapshot: DistressSnapshot = DistressSnapshot(),
    val appState: BotAppState = BotAppState()
)