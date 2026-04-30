package com.example.easyfill_project

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
// Your app theme
import com.example.easyfill_project.ui.theme.EasyFill_ProjectTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Makes the app draw behind system bars for a modern full-screen look
        enableEdgeToEdge()

        // setContent replaces XML layouts in Compose
        setContent {

            // Applies your app theme: colors, typography, Material style
            EasyFill_ProjectTheme {
                AppNavigation()// call the main function that contains navigations to all screens

            }
        }
    }
}



