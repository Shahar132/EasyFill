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

//force right to left
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.CompositionLocalProvider

//import
import com.google.firebase.auth.FirebaseAuth

//check email pattern
import android.util.Patterns



@Composable
fun AuthScreen(navController: NavHostController) {

    // Stores the email text
    var email by remember { mutableStateOf("") }

    // Stores the password text
    var password by remember { mutableStateOf("") }

    var emailError by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf("") }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(23.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            Text(
                text = "התחברות",
                fontSize = 32.sp
            )

            Spacer(modifier = Modifier.height(22.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("אימייל") },
                modifier = Modifier.fillMaxWidth()
            )

            if (emailError.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = emailError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("סיסמה") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            if (passwordError.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = passwordError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(20.dp))//adding spacing


            Button(
                onClick = {
                    emailError = ""
                    passwordError = ""

                    var hasError = false

                    if (email.isBlank()) {
                        emailError = "הכנס אימייל"
                        hasError = true
                    } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                        emailError = "אימייל לא תקין"
                        hasError = true
                    }

                    if (password.isBlank()) {
                        passwordError = "הכנס סיסמה"
                        hasError = true
                    }


                    if (hasError) return@Button

                    FirebaseAuth.getInstance()
                        .signInWithEmailAndPassword(email, password)
                        .addOnSuccessListener {
                            navController.navigate("app")
                        }
                        .addOnFailureListener {
                            passwordError =
                                " אימייל או סיסמה לא נכונים \n(במידה ועדכנת כתובת מייל אנא וודא שאישרת במייל שנשלח)"
                        }

                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("התחבר")
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(
                onClick = {
                    // Firebase sign up
                    navController.navigate("register")
                }
            ) {
                Text("אין לך חשבון? הרשמה")
            }
        }
    }
}