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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore


//screen of register for new user
@Composable
fun RegisterScreen(navController: NavHostController) {

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(23.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "הרשמה",
            fontSize = 32.sp
        )


        Spacer(modifier = Modifier.height(22.dp))


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

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                FirebaseAuth.getInstance()
                    //create in  firebase auth new email and password -> with unique uid
                    .createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener { result ->

                        // Firebase created uid for user
                        val uid = result.user?.uid ?: return@addOnSuccessListener

                        // Save only extra user info  - email and time , NOT password
                        //Auth → gives UID (document)
                        //Firestore → uses SAME UID and save the details (collection)
                        val userData = hashMapOf(
                            "email" to email,
                            "createdAt" to System.currentTimeMillis()
                        )

                        FirebaseFirestore.getInstance()
                            .collection("users")
                            .document(uid)
                            .set(userData)
                            .addOnSuccessListener {
                                navController.navigate("app")
                            }
                            .addOnFailureListener { e ->
                                status = "שגיאה בשמירת משתמש: ${e.message}"
                            }
                    }
                    .addOnFailureListener { e ->
                        status = "שגיאה בהרשמה: הסיסמה חייבת להכיל לפחות 6 תווים "
                    }

            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("הרשמה")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(status)
    }
}