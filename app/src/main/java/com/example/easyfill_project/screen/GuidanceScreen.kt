package com.example.easyfill_project.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp

@Composable
fun GuidanceScreen() {

    // Box = container that lets us center content easily
    Box(
        modifier = Modifier.fillMaxSize(), // take full screen
        contentAlignment = Alignment.Center // center everything inside
    ) {

        // Text displayed in the center of the screen
        Text(
            text = "Guidance Screen",
            fontSize = 22.sp
        )
    }
}