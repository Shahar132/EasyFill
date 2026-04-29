package com.example.easyfill_project.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController

@Composable
fun AuthScreen(navController: NavHostController) {

    // Stores the email text
    var email by remember { mutableStateOf("") }

    // Stores the password text
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "התחברות",
            fontSize = 32.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("אימייל") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("סיסמה") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))//adding spacing

        Button(
            onClick = {
                // Navigate to home screen after user press login
                //later will be connection to firebase auth
                navController.navigate("app")//navigate to home screen
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("התחבר")
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(
            onClick = {
                // Firebase sign up later
            }
        ) {
            Text("אין לך חשבון? הרשמה")
        }
    }
}