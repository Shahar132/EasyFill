package com.example.easyfill_project.settings

import com.example.easyfill_project.screen.ContrastMode
import com.example.easyfill_project.screen.FontSizeMode

/**
 * Contains only preferences that the user manually selected
 * from the application's Settings screens.
 *
 * Chatbot changes are temporary and are not written here.
 */
data class UserSettings(
    val contrastMode: ContrastMode = ContrastMode.DEFAULT,
    val fontSizeMode: FontSizeMode = FontSizeMode.NORMAL,
    val selectedSound: String = "none"
)