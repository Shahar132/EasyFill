package com.example.easyfill_project.screen

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

fun getContrastColorScheme(mode: ContrastMode): ColorScheme {
    return when (mode) {

        ContrastMode.DEFAULT -> lightColorScheme(//default blue shaded
            background = Color.White,
            surface = Color(0xFFCAEAFC),//background of components that sit ON the screen - >cards
            primary = Color.White,//primary for selected items in menu
            onBackground = Color.Black,//text color that appears ON the background
            onSurface = Color.Black,//text will be black
            onPrimary = Color.Black ,
            secondary = Color(0xFF0B78D0), // blue


            // Disabled button background
            tertiary = Color(0xFF386B8F) ,
             // Background of StatusCard
            surfaceVariant = Color(0xFFB0C4E3),

            // Text inside StatusCard
            onSurfaceVariant = Color.Black,
            // Validation message and validation border.
            error = Color(0xFFB3261E),

            // Text displayed on an error-colored background.
            onError = Color.White,


        )

        ContrastMode.HIGH -> lightColorScheme(//black and white
            background = Color.Black,
            surface = Color(0xFF2A2929),//background of components that sit ON the screen
            primary = Color.White,
            onBackground = Color.White,// text color that appears ON the background
            onSurface = Color.White,//the text will be white
            onPrimary = Color.Black,
            secondary = Color(0xFF5B5757), // dark grey

            // Disabled button background
            tertiary = Color(0xFF989494),



            surfaceVariant = Color(0xFF949292),
            onSurfaceVariant = Color.White,

            // Validation message and validation border.
            error = Color(0xFFFFD600),

            // Text displayed on an error-colored background.
            onError = Color.Black,

            )

        ContrastMode.LOW -> lightColorScheme(//light purple shades
            background = Color.White,
            surface = Color(0xFFD2C4F3),//background of components that sit ON the screen
            primary = Color.White,
            onBackground = Color.Black,
            onSurface = Color.Black,
            onPrimary = Color.Black,
            secondary = Color(0xFF673AB7), // dark purple

            // Disabled button background
            tertiary = Color(0xFF703F92),

            surfaceVariant = Color(0xFFDECFF1),
            onSurfaceVariant = Color.Black,

            // Validation message and validation border.
            error = Color(0xFFB3261E),

            // Text displayed on an error-colored background.
            onError = Color.White,

            )
    }
}