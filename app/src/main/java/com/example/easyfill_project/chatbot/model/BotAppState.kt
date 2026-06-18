package com.example.easyfill_project.chatbot.model

data class BotAppState(
    val isMusicPlaying: Boolean = false,
    val selectedSound: String = "none",

    val isTtsSpeaking: Boolean = false,
    val autoReadEnabled: Boolean = false,

    val fontSizeMode: String = "NORMAL",
    val contrastMode: String = "DEFAULT"
)