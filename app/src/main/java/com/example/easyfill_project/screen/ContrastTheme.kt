package com.example.easyfill_project.screen

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

fun getContrastColorScheme(mode: ContrastMode): ColorScheme {
    return when (mode) {

        ContrastMode.DEFAULT -> lightColorScheme(//default
            background = Color.White,
            surface = Color(0xFFCAEAFC),//background of components that sit ON the screen - >cards
            primary = Color.White,
            onBackground = Color.Black,//text color that appears ON the background
            onSurface = Color.Black,//text will be black
            onPrimary = Color.Black
        )

        ContrastMode.HIGH -> lightColorScheme(//black and white
            background = Color.Black,
            surface = Color(0xFF2A2929),//background of components that sit ON the screen
            primary = Color.White,
            onBackground = Color.White,// text color that appears ON the background
            onSurface = Color.White,//the text will be white
            onPrimary = Color.Black
        )

        ContrastMode.LOW -> lightColorScheme(//light purple shades
            background = Color.White,
            surface = Color(0xFFD2C4F3),//background of components that sit ON the screen
            primary = Color.White,
            onBackground = Color.Black,
            onSurface = Color.Black,
            onPrimary = Color.Black
        )
    }
}