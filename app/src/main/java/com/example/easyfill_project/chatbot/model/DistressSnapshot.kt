package com.example.easyfill_project.chatbot.model

data class DistressSnapshot(
    val globalScore: Int = 0,
    val semanticTextScore: Int = 0,
    val faceScore: Int = 0,
    val voiceScore: Int = 0,
    val touchScore: Int = 0,
    val formBehaviorScore: Int = 0
)