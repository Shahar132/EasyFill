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
import com.google.firebase.storage.FirebaseStorage
import com.google.android.gms.tasks.Tasks

//date class for showing the user uploaded files for user
data class UploadedFile(
    val fileId: String = "",
    val fileName: String = "",
    val storagePath: String = ""
)

@Composable
fun ProfileScreen(
    navController: NavHostController,
    onNameUpdated: (String) -> Unit
) {
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }

    var nameStatus by remember { mutableStateOf("") }
    var emailStatus by remember { mutableStateOf("") }
    var passwordStatus by remember { mutableStateOf("") }
    var filesStatus by remember { mutableStateOf("") }

    var showNameField by remember { mutableStateOf(false) }
    var showEmailField by remember { mutableStateOf(false) }
    var showPasswordField by remember { mutableStateOf(false) }

    var newName by remember { mutableStateOf("") }
    var newEmail by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }

    //list of uploaded files
    var uploadedFiles by remember { mutableStateOf<List<UploadedFile>>(emptyList()) }

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

            firestore.collection("users")
                .document(userId)
                .collection("uploadedFiles")
                .get()
                .addOnSuccessListener { snapshot ->
                    uploadedFiles = snapshot.documents.map { doc ->
                        UploadedFile(
                            fileId = doc.getString("fileId") ?: doc.id,
                            fileName = doc.getString("fileName") ?: "",
                            storagePath = doc.getString("storagePath") ?: ""
                        )
                    }
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

        ProfileSectionCard(title = "עריכת פרטי חשבון") {
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
                        TextButton(
                            onClick = {
                                if (userId != null && newName.isNotBlank()) {
                                    firestore.collection("users")
                                        .document(userId)
                                        .update("fullName", newName)
                                        .addOnSuccessListener {
                                            fullName = newName
                                            showNameField = false
                                            nameStatus = "השם עודכן בהצלחה"
                                            onNameUpdated(newName) // updates top bar immediately callback
                                        }
                                } else {
                                    nameStatus = "הכנס שם מלא"
                                }
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
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
                        TextButton(
                            onClick = {
                                if (newEmail.isNotBlank()) {
                                    auth.currentUser?.verifyBeforeUpdateEmail(newEmail)
                                        ?.addOnSuccessListener {
                                            showEmailField = false
                                            emailStatus = "נשלח אימייל אימות לכתובת החדשה, נא לאשר"
                                        }
                                        ?.addOnFailureListener { e ->
                                            emailStatus = "שגיאה בעדכון אימייל: ${e.message}"
                                        }
                                } else {
                                    emailStatus = "הכנס אימייל חדש"
                                }
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
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
                        TextButton(
                            onClick = {
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
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
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
            if (uploadedFiles.isEmpty()) {
                Text(
                    text = "לא הועלו טפסים עדיין",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            uploadedFiles.forEach { file ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = "File",
                        tint = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = file.fileName,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    IconButton(
                        onClick = {
                            if (userId != null) {
                                FirebaseStorage.getInstance()
                                    .reference
                                    .child(file.storagePath)
                                    .delete()
                                    .addOnSuccessListener {
                                        firestore.collection("users")
                                            .document(userId)
                                            .collection("uploadedFiles")
                                            .document(file.fileId)
                                            .delete()
                                            .addOnSuccessListener {
                                                uploadedFiles =
                                                    uploadedFiles.filter { it.fileId != file.fileId }
                                                filesStatus = "הקובץ נמחק בהצלחה"
                                            }
                                    }
                                    .addOnFailureListener { e ->
                                        filesStatus = "שגיאה במחיקת הקובץ: ${e.message}"
                                    }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete file",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (filesStatus.isNotEmpty()) {
                Text(
                    text = filesStatus,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        ProfileSectionCard(title = "אפשרויות יציאה") {
            ProfileActionButton(
                icon = Icons.Default.Logout,
                text = "התנתקות",
                onClick = {
                    auth.signOut()
                    navController.navigate("auth") {
                        popUpTo("app") { inclusive = true }
                    }
                }
            )

            ProfileDeleteButton(
                icon = Icons.Default.Delete,
                text = "מחיקת חשבון",
                onClick = {
                    val currentUser = auth.currentUser
                    val currentUserId = currentUser?.uid

                    if (currentUser != null && currentUserId != null) {
                        val storage = FirebaseStorage.getInstance()
                        val uploadsRef = storage.reference
                            .child("users/$currentUserId/uploads")

                        uploadsRef.listAll().addOnSuccessListener { storageResult ->
                            val deleteStorageTasks = storageResult.items.map { fileRef ->
                                fileRef.delete()
                            }

                            val allStorageDeletedTask =
                                if (deleteStorageTasks.isNotEmpty()) {
                                    Tasks.whenAll(deleteStorageTasks)
                                } else {
                                    Tasks.forResult(null)
                                }

                            allStorageDeletedTask.addOnSuccessListener {
                                val userDocRef = firestore.collection("users").document(currentUserId)

                                userDocRef.collection("uploadedFiles")
                                    .get()
                                    .addOnSuccessListener { snapshot ->
                                        val deleteUploadedFilesTasks =
                                            snapshot.documents.map { document ->
                                                document.reference.delete()
                                            }

                                        val allUploadedFilesDeletedTask =
                                            if (deleteUploadedFilesTasks.isNotEmpty()) {
                                                Tasks.whenAll(deleteUploadedFilesTasks)
                                            } else {
                                                Tasks.forResult(null)
                                            }

                                        allUploadedFilesDeletedTask.addOnSuccessListener {
                                            userDocRef.delete()
                                                .addOnSuccessListener {
                                                    currentUser.delete()
                                                        .addOnSuccessListener {
                                                            navController.navigate("register") {
                                                                popUpTo(0)
                                                            }
                                                        }
                                                        .addOnFailureListener { e ->
                                                            println("Error deleting auth user: ${e.message}")
                                                        }
                                                }
                                                .addOnFailureListener { e ->
                                                    println("Error deleting user document: ${e.message}")
                                                }
                                        }
                                    }
                            }
                        }
                    }
                }
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
        elevation = CardDefaults.cardElevation(14.dp)
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
            tint = MaterialTheme.colorScheme.onSurface //for the icons
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
            containerColor = MaterialTheme.colorScheme.primary, //the background of the button
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        elevation = ButtonDefaults.buttonElevation(3.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = MaterialTheme.colorScheme.onPrimary
        )

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
fun ProfileDeleteButton(
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
            contentDescription = text,
            tint = MaterialTheme.colorScheme.onError
        )

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}