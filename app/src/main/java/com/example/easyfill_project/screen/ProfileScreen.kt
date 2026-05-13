package com.example.easyfill_project.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun ProfileScreen(navController: NavHostController) {
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }

    var nameStatus by remember { mutableStateOf("") }
    var emailStatus by remember { mutableStateOf("") }
    var passwordStatus by remember { mutableStateOf("") }

    var showNameField by remember { mutableStateOf(false) }
    var showEmailField by remember { mutableStateOf(false) }
    var showPasswordField by remember { mutableStateOf(false) }

    var newName by remember { mutableStateOf("") }
    var newEmail by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }

    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()
    val userId = auth.currentUser?.uid

    LaunchedEffect(Unit) {
        if (userId != null) {
            firestore.collection("users")
                .document(userId)
                .get()
                .addOnSuccessListener { document ->
                    fullName = document.getString("fullName") ?: ""
                    email = document.getString("email") ?: ""
                }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = "ניהול חשבון",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(28.dp))

        ProfileSectionCard(title = "פרטים אישיים") {
            ProfileInfoRow(Icons.Default.Person, "שם מלא", fullName)
            ProfileInfoRow(Icons.Default.Email, "אימייל", email)
        }

        Spacer(modifier = Modifier.height(20.dp))

        ProfileSectionCard(title = "ניהול חשבון") {

            ProfileActionButton(
                icon = Icons.Default.Person,
                text = "שינוי שם מלא",
                onClick = {
                    showNameField = !showNameField
                    newName = fullName
                    nameStatus = ""
                }
            )

            if (showNameField) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("שם מלא חדש") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        TextButton(onClick = {
                            if (userId != null && newName.isNotBlank()) {
                                firestore.collection("users")
                                    .document(userId)
                                    .update("fullName", newName)
                                    .addOnSuccessListener {
                                        fullName = newName
                                        showNameField = false
                                        nameStatus = "השם עודכן בהצלחה"
                                    }
                            } else {
                                nameStatus = "הכנס שם מלא"
                            }
                        }) {
                            Text("שמור")
                        }
                    }
                )
            }

            if (nameStatus.isNotEmpty()) {
                Text(
                    text = nameStatus,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            ProfileActionButton(
                icon = Icons.Default.Email,
                text = "שינוי אימייל",
                onClick = {
                    showEmailField = !showEmailField
                    newEmail = email
                    emailStatus = ""
                }
            )

            if (showEmailField) {
                OutlinedTextField(
                    value = newEmail,
                    onValueChange = { newEmail = it },
                    label = { Text("אימייל חדש") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        TextButton(onClick = {
                            if (newEmail.isNotBlank()) {
                                auth.currentUser?.verifyBeforeUpdateEmail(newEmail)
                                    ?.addOnSuccessListener {
                                        showEmailField = false
                                        emailStatus =
                                            "נשלח אימייל אימות לכתובת החדשה, נא לאשר"
                                    }
                                    ?.addOnFailureListener { e ->
                                        emailStatus = "שגיאה בעדכון אימייל: ${e.message}"
                                    }
                            } else {
                                emailStatus = "הכנס אימייל חדש"
                            }
                        }) {
                            Text("שמור")
                        }
                    }
                )
            }

            if (emailStatus.isNotEmpty()) {
                Text(
                    text = emailStatus,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            ProfileActionButton(
                icon = Icons.Default.Lock,
                text = "שינוי סיסמה",
                onClick = {
                    showPasswordField = !showPasswordField
                    passwordStatus = ""
                }
            )

            if (showPasswordField) {
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("סיסמה חדשה") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        TextButton(onClick = {
                            if (newPassword.length >= 6) {
                                auth.currentUser?.updatePassword(newPassword)
                                    ?.addOnSuccessListener {
                                        newPassword = ""
                                        showPasswordField = false
                                        passwordStatus = "הסיסמה עודכנה בהצלחה"
                                    }
                                    ?.addOnFailureListener {
                                        passwordStatus = "שגיאה בעדכון סיסמה"
                                    }
                            } else {
                                passwordStatus = "הסיסמה חייבת להכיל לפחות 6 תווים"
                            }
                        }) {
                            Text("שמור")
                        }
                    }
                )
            }

            if (passwordStatus.isNotEmpty()) {
                Text(
                    text = passwordStatus,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        ProfileSectionCard(title = "ניהול טפסים") {
            ProfileActionButton(
                icon = Icons.Default.Description,
                text = "מחיקת טפסים שהועלו",
                onClick = {}
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        ProfileSectionCard(title = "אזור מסוכן") {
            ProfileActionButton(
                icon = Icons.Default.Logout,
                text = "התנתקות",
                onClick = {
                    auth.signOut()
                    navController.navigate("auth")
                }
            )

            ProfileDangerButton(
                icon = Icons.Default.Delete,
                text = "מחיקת חשבון",
                onClick = {}
            )
        }
    }
}

@Composable
fun ProfileSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(6.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(14.dp))

            content()
        }
    }
}

@Composable
fun ProfileInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun ProfileActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Button(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .height(58.dp),
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground
        ),
        elevation = ButtonDefaults.buttonElevation(3.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text
        )

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
fun ProfileDangerButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Button(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .height(58.dp),
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text
        )

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}