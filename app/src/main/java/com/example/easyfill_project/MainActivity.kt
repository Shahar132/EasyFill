package com.example.easyfill_project

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

// Compose background and layout components
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape

// Compose Material 3 UI components
import androidx.compose.material3.*

// Compose basics
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

// Styling tools
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Rounded button shape
import androidx.compose.foundation.shape.RoundedCornerShape

// Your app theme
import com.example.easyfill_project.ui.theme.EasyFill_ProjectTheme

//navigation
import androidx.navigation.compose.*
import androidx.navigation.NavHostController
import com.example.easyfill_project.screen.AuthScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Makes the app draw behind system bars for a modern full-screen look
        enableEdgeToEdge()

        // setContent replaces XML layouts in Compose
        setContent {

            // Applies your app theme: colors, typography, Material style
            EasyFill_ProjectTheme {
                AppNavigation()

            }
        }
    }
}


@Composable
fun AppNavigation() {

    // NavController = manages screen navigation
    val navController = rememberNavController()

    // NavHost = defines all screens
    NavHost(
        navController = navController,
        startDestination = "main" // first screen
    ) {

        //Calls our main opening screen
        composable("main") {
            EasyFillMainScreen(navController)
        }

        // Second screen (Firebase auth placeholder for now)
        composable("auth") {
            AuthScreen()
        }
    }
}

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
                    color = Color(0xFF6C63FF)
                )

                // Small space between title and subtitle
                Spacer(modifier = Modifier.height(8.dp))

                // Hebrew subtitle
                Text(
                    text = "מילוי קל",
                    fontSize = 22.sp,
                    color = Color(0xFF6C63FF)
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
                        containerColor = Color(0xFFFF6F61)
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

