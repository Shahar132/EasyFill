package com.example.easyfill_project.screen

import android.util.Patterns
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FirebaseFirestore

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration

import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

@Composable
fun RegisterScreen(navController: NavHostController) {

    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    var fullNameError by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(23.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("הרשמה", fontSize = 32.sp)

            Spacer(modifier = Modifier.height(22.dp))

            OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it },
                label = { Text("שם פרטי ושם משפחה") },
                modifier = Modifier.fillMaxWidth()
            )

            if (fullNameError.isNotEmpty()) {
                Text(
                    text = fullNameError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("אימייל") },
                modifier = Modifier.fillMaxWidth()
            )

            if (emailError.isNotEmpty()) {
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
                Text(
                    text = passwordError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription =
                            if (isLoading) {
                                "מתבצעת הרשמה לחשבון"
                            } else {
                                "יצירת חשבון חדש"
                            }
                    },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1E1796),
                    contentColor = Color.White,
                    disabledContainerColor =
                        Color(0xFF1E1796).copy(alpha = 0.45f),
                    disabledContentColor =
                        Color.White.copy(alpha = 0.75f)
                ),
                onClick = {
                    fullNameError = ""
                    emailError = ""
                    passwordError = ""
                    status = ""

                    val trimmedFullName = fullName.trim()
                    val trimmedEmail = email.trim()

                    var hasError = false

                    if (trimmedFullName.isBlank()) {
                        fullNameError =
                            "הכנס שם פרטי ושם משפחה"
                        hasError = true
                    }

                    if (trimmedEmail.isBlank()) {
                        emailError = "הכנס אימייל"
                        hasError = true
                    } else if (
                        !Patterns.EMAIL_ADDRESS
                            .matcher(trimmedEmail)
                            .matches()
                    ) {
                        emailError =
                            "האימייל שהוזן אינו תקין"
                        hasError = true
                    }

                    if (password.isBlank()) {
                        passwordError = "הכנס סיסמה"
                        hasError = true
                    } else if (password.length < 6) {
                        passwordError =
                            "הסיסמה חייבת להכיל לפחות 6 תווים"
                        hasError = true
                    }

                    if (hasError) return@Button

                    isLoading = true

                    FirebaseAuth.getInstance()
                        .createUserWithEmailAndPassword(
                            trimmedEmail,
                            password
                        )
                        .addOnSuccessListener { result ->
                            val uid = result.user?.uid

                            if (uid == null) {
                                isLoading = false
                                status = "שגיאה ביצירת משתמש"
                                return@addOnSuccessListener
                            }

                            val userData = hashMapOf(
                                "email" to trimmedEmail,
                                "fullName" to trimmedFullName,
                                "createdAt" to
                                        System.currentTimeMillis()
                            )

                            FirebaseFirestore.getInstance()
                                .collection("users")
                                .document(uid)
                                .set(userData)
                                .addOnSuccessListener {
                                    isLoading = false
                                    navController.navigate("app")
                                }
                                .addOnFailureListener { e ->
                                    isLoading = false
                                    status =
                                        "שגיאה בשמירת משתמש: " +
                                                "${e.message}"
                                }
                        }
                        .addOnFailureListener { e ->
                            isLoading = false

                            if (
                                e is FirebaseAuthUserCollisionException
                            ) {
                                emailError =
                                    "אימייל זה כבר קיים במערכת,\n" +
                                            "יש לך חשבון קיים חזור/י " +
                                            "לדף ההתחברות"
                            } else {
                                status =
                                    "שגיאה בהרשמה: ${e.message}"
                            }
                        }
                }
            ) {
                Text(
                    text =
                        if (isLoading) {
                            "נרשם..."
                        } else {
                            "הרשמה"
                        },

                    // Prevents the visible text from creating another
                    // accessibility description inside the button.
                    modifier = Modifier.clearAndSetSemantics { }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(
                onClick = {
                    navController.navigate("auth")
                },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color(0xFF1E1796)
                )
            ) {
                Text(
                    text = buildAnnotatedString {
                        append("כבר יש לך חשבון? ")

                        pushStyle(
                            SpanStyle(
                                color = Color(0xFF1E1796),
                                textDecoration =
                                    TextDecoration.Underline
                            )
                        )

                        append("התחברות")
                        pop()
                    },
                    color = Color(0xFF1E1796)
                )
            }

            if (status.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = status,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}