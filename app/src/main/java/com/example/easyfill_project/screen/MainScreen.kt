package com.example.easyfill_project.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController


// @Composable means this function creates UI
@Composable
fun EasyFillMainScreen(navController: NavHostController) {

    // Scaffold is the main page structure in Compose
    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->

        // Box is like a container that can hold content and background
        Box(
            modifier = Modifier
                .fillMaxSize() // makes the Box take the full screen
                .padding(innerPadding) // keeps content away from system bars
                .background(
                    // Creates a vertical gradient background
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFBFE9FF), // light blue color
                            Color(0xFFFFE6A7)  // light yellow color
                        )
                    )
                )
                .padding(24.dp) // inner space from screen edges
        ) {

            // Column places items vertically one under another
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // Empty vertical space from the top
                Spacer(modifier = Modifier.height(100.dp))


                // Logo circle
                Box(
                    modifier = Modifier
                        .size(90.dp) // circle size
                        .background(
                            color = Color(0xFFFFE08A), // soft yellow
                            shape = CircleShape // makes it a circle
                        ),
                    contentAlignment = Alignment.Center // centers the icon inside
                ) {
                    Text(
                        text = "✏️",
                        fontSize = 42.sp
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))



                // Main title text
                Text(
                    text = "EasyFill",
                    fontSize = 38.sp,
                    color = Color(0xFF1E1796)
                )

                // Small space between title and subtitle
                Spacer(modifier = Modifier.height(8.dp))

                // Hebrew subtitle
                Text(
                    text = "מילוי קל",
                    fontSize = 22.sp,
                    color = Color(0xFF1E1796)
                )

                // Space before paragraph
                Spacer(modifier = Modifier.height(40.dp))

                // Hebrew description paragraph
                Text(
                    text = "שלום, אני כאן לעזור לך\nבמילוי טפסים בצורה נוחה,\nברורה, ומהירה בקצב שלך.",
                    fontSize = 18.sp,
                    color = Color(0xFF4A4A4A),
                    textAlign = TextAlign.Center,
                    lineHeight = 28.sp
                )

                // Takes all empty space and pushes the button to the bottom
                Spacer(modifier = Modifier.weight(1f))

                // Bottom button
                Button(
                    onClick = {
                        // connection to auth screen
                        navController.navigate("auth")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(55.dp),
                    shape = RoundedCornerShape(30.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFEB1400
                        )
                    )
                ) {
                    // Text inside the button
                    Text(
                        text = "המשך להתחברות",
                        fontSize = 18.sp,
                        color = Color.White
                    )
                }

                // Space under the button
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}