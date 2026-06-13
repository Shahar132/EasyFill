package com.example.easyfill_project.chatbot.navigation

import androidx.navigation.NavHostController
import com.example.easyfill_project.chatbot.model.BotAction

object BotNavigationHandler {

    fun handle(
        action: BotAction,
        navController: NavHostController
    ): Boolean {
        val route = when (action) {
            BotAction.OpenHome -> "home"
            BotAction.OpenFormOptions -> "demoFormOptions"
            BotAction.OpenFormsProgress -> "myFormsProgress"
            BotAction.OpenProfile -> "profile"
            BotAction.OpenGuidance -> "Guidance"
            BotAction.OpenUploadPdf -> "uploadPdf"

            BotAction.OpenPersonalSettings -> "Personal Settings"
            BotAction.OpenContrastSettings -> "contrastSettings"
            BotAction.OpenFontSizeSettings -> "fontSizeSettings"
            BotAction.OpenBackgroundSounds -> "backgroundSounds"

            else -> null
        } ?: return false

        navController.navigate(route) {
            launchSingleTop = true
        }

        return true
    }
}