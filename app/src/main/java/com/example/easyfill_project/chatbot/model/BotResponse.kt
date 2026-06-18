package com.example.easyfill_project.chatbot.model

data class BotResponse(
    val message: String,
    val action: BotAction = BotAction.None,
    val requiresConfirmation: Boolean = false
)