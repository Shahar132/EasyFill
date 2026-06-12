package com.example.easyfill_project.chatbot.model

data class BotResponse(
    val message: String,
    val action: BotAction = BotAction.NONE,
    val requiresConfirmation: Boolean = false
)