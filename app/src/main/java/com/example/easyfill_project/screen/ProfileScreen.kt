package com.example.easyfill_project.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.easyfill_project.forms_screens.FormAttachmentsRepository
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

// Represents an original form file uploaded by the user.
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
    var accountStatus by remember { mutableStateOf("") }

    var showNameField by remember { mutableStateOf(false) }
    var showEmailField by remember { mutableStateOf(false) }
    var showPasswordField by remember { mutableStateOf(false) }

    var newName by remember { mutableStateOf("") }
    var newEmail by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }

    // Stores the original form files uploaded by the user.
    var uploadedFiles by remember {
        mutableStateOf<List<UploadedFile>>(emptyList())
    }

    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()
    val userId = auth.currentUser?.uid

    // Loads the user's personal information and original uploaded forms.
    LaunchedEffect(userId) {
        if (userId == null) {
            return@LaunchedEffect
        }

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
                uploadedFiles = snapshot.documents.map { document ->
                    UploadedFile(
                        fileId = document.getString("fileId") ?: document.id,
                        fileName = document.getString("fileName") ?: "",
                        storagePath = document.getString("storagePath") ?: ""
                    )
                }
            }
            .addOnFailureListener { exception ->
                filesStatus =
                    "טעינת הטפסים נכשלה: ${exception.message ?: "שגיאה לא ידועה"}"
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
            ProfileInfoRow(
                icon = Icons.Default.Person,
                label = "שם מלא",
                value = fullName
            )

            ProfileInfoRow(
                icon = Icons.Default.Email,
                label = "אימייל",
                value = email
            )
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
                                            onNameUpdated(newName)
                                        }
                                        .addOnFailureListener { exception ->
                                            nameStatus =
                                                "שגיאה בעדכון השם: ${exception.message ?: "שגיאה לא ידועה"}"
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
                                    auth.currentUser
                                        ?.verifyBeforeUpdateEmail(newEmail)
                                        ?.addOnSuccessListener {
                                            showEmailField = false
                                            emailStatus =
                                                "נשלח אימייל אימות לכתובת החדשה, נא לאשר"
                                        }
                                        ?.addOnFailureListener { exception ->
                                            emailStatus =
                                                "שגיאה בעדכון אימייל: ${exception.message ?: "שגיאה לא ידועה"}"
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
                                    auth.currentUser
                                        ?.updatePassword(newPassword)
                                        ?.addOnSuccessListener {
                                            newPassword = ""
                                            showPasswordField = false
                                            passwordStatus = "הסיסמה עודכנה בהצלחה"
                                        }
                                        ?.addOnFailureListener { exception ->
                                            passwordStatus =
                                                "שגיאה בעדכון סיסמה: ${exception.message ?: "שגיאה לא ידועה"}"
                                        }
                                } else {
                                    passwordStatus =
                                        "הסיסמה חייבת להכיל לפחות 6 תווים"
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
                        contentDescription = null,
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
                            if (userId == null) {
                                return@IconButton
                            }

                            // Deletes Firestore metadata after the Storage file.
                            fun deleteUploadedFileMetadata() {
                                firestore.collection("users")
                                    .document(userId)
                                    .collection("uploadedFiles")
                                    .document(file.fileId)
                                    .delete()
                                    .addOnSuccessListener {
                                        uploadedFiles = uploadedFiles.filterNot {
                                            it.fileId == file.fileId
                                        }
                                        filesStatus = "הקובץ נמחק בהצלחה"
                                    }
                                    .addOnFailureListener { exception ->
                                        filesStatus =
                                            "שגיאה במחיקת המידע: ${exception.message ?: "שגיאה לא ידועה"}"
                                    }
                            }

                            if (file.storagePath.isBlank()) {
                                deleteUploadedFileMetadata()
                            } else {
                                FirebaseStorage.getInstance()
                                    .reference
                                    .child(file.storagePath)
                                    .delete()
                                    .addOnSuccessListener {
                                        deleteUploadedFileMetadata()
                                    }
                                    .addOnFailureListener { exception ->
                                        filesStatus =
                                            "שגיאה במחיקת הקובץ: ${exception.message ?: "שגיאה לא ידועה"}"
                                    }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "מחיקת טופס",
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

        // Shows supporting documents grouped by form and logical document.
        MyFormDocumentsSection()

        Spacer(modifier = Modifier.height(20.dp))

        ProfileSectionCard(title = "אפשרויות יציאה") {
            ProfileActionButton(
                icon = Icons.Default.Logout,
                text = "התנתקות",
                onClick = {
                    auth.signOut()
                    navController.navigate("auth") {
                        popUpTo("app") {
                            inclusive = true
                        }
                    }
                }
            )

            ProfileDeleteButton(
                icon = Icons.Default.Delete,
                text = "מחיקת חשבון",
                onClick = {
                    val currentUser = auth.currentUser
                    val currentUserId = currentUser?.uid

                    if (currentUser == null || currentUserId == null) {
                        accountStatus = "לא נמצא משתמש מחובר"
                        return@ProfileDeleteButton
                    }

                    accountStatus = "מוחק את החשבון..."

                    // Deletes all supporting documents before deleting the account.
                    deleteAllFormAttachments(
                        onSuccess = {
                            val storage = FirebaseStorage.getInstance()
                            val uploadsReference = storage.reference
                                .child("users/$currentUserId/uploads")

                            uploadsReference.listAll()
                                .addOnSuccessListener { storageResult ->
                                    val deleteStorageTasks = storageResult.items.map {
                                            fileReference -> fileReference.delete()
                                    }

                                    val allStorageDeletedTask =
                                        if (deleteStorageTasks.isNotEmpty()) {
                                            Tasks.whenAll(deleteStorageTasks)
                                        } else {
                                            Tasks.forResult(null)
                                        }

                                    allStorageDeletedTask
                                        .addOnSuccessListener {
                                            val userDocumentReference =
                                                firestore.collection("users")
                                                    .document(currentUserId)

                                            userDocumentReference
                                                .collection("uploadedFiles")
                                                .get()
                                                .addOnSuccessListener { snapshot ->
                                                    val deleteMetadataTasks =
                                                        snapshot.documents.map { document ->
                                                            document.reference.delete()
                                                        }

                                                    val allMetadataDeletedTask =
                                                        if (deleteMetadataTasks.isNotEmpty()) {
                                                            Tasks.whenAll(deleteMetadataTasks)
                                                        } else {
                                                            Tasks.forResult(null)
                                                        }

                                                    allMetadataDeletedTask
                                                        .addOnSuccessListener {
                                                            userDocumentReference.delete()
                                                                .addOnSuccessListener {
                                                                    currentUser.delete()
                                                                        .addOnSuccessListener {
                                                                            navController.navigate(
                                                                                "register"
                                                                            ) {
                                                                                popUpTo(0)
                                                                            }
                                                                        }
                                                                        .addOnFailureListener {
                                                                                exception ->
                                                                            accountStatus =
                                                                                "מחיקת המשתמש נכשלה: ${exception.message ?: "שגיאה לא ידועה"}"
                                                                        }
                                                                }
                                                                .addOnFailureListener {
                                                                        exception ->
                                                                    accountStatus =
                                                                        "מחיקת נתוני המשתמש נכשלה: ${exception.message ?: "שגיאה לא ידועה"}"
                                                                }
                                                        }
                                                        .addOnFailureListener { exception ->
                                                            accountStatus =
                                                                "מחיקת נתוני הטפסים נכשלה: ${exception.message ?: "שגיאה לא ידועה"}"
                                                        }
                                                }
                                                .addOnFailureListener { exception ->
                                                    accountStatus =
                                                        "טעינת נתוני הטפסים נכשלה: ${exception.message ?: "שגיאה לא ידועה"}"
                                                }
                                        }
                                        .addOnFailureListener { exception ->
                                            accountStatus =
                                                "מחיקת קובצי הטפסים נכשלה: ${exception.message ?: "שגיאה לא ידועה"}"
                                        }
                                }
                                .addOnFailureListener { exception ->
                                    accountStatus =
                                        "טעינת קובצי הטפסים נכשלה: ${exception.message ?: "שגיאה לא ידועה"}"
                                }
                        },
                        onFailure = { exception ->
                            accountStatus =
                                "מחיקת המסמכים נכשלה: ${exception.message ?: "שגיאה לא ידועה"}"
                        }
                    )
                }
            )

            if (accountStatus.isNotEmpty()) {
                Text(
                    text = accountStatus,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * Deletes every logical form document before account deletion.
 */
private fun deleteAllFormAttachments(
    onSuccess: () -> Unit,
    onFailure: (Exception) -> Unit
) {
    FormAttachmentsRepository.loadAllAttachments(
        onSuccess = { attachments ->
            val documentKeys = attachments
                .map { attachment ->
                    attachment.formId to attachment.documentId
                }
                .distinct()

            deleteDocumentGroupsSequentially(
                documentKeys = documentKeys,
                currentIndex = 0,
                onSuccess = onSuccess,
                onFailure = onFailure
            )
        },
        onFailure = onFailure
    )
}

/**
 * Deletes logical documents sequentially so every physical file is removed.
 */
private fun deleteDocumentGroupsSequentially(
    documentKeys: List<Pair<String, String>>,
    currentIndex: Int,
    onSuccess: () -> Unit,
    onFailure: (Exception) -> Unit
) {
    if (currentIndex >= documentKeys.size) {
        onSuccess()
        return
    }

    val (formId, documentId) = documentKeys[currentIndex]

    FormAttachmentsRepository.deleteDocumentAttachments(
        formId = formId,
        documentId = documentId,
        onSuccess = {
            deleteDocumentGroupsSequentially(
                documentKeys = documentKeys,
                currentIndex = currentIndex + 1,
                onSuccess = onSuccess,
                onFailure = onFailure
            )
        },
        onFailure = onFailure
    )
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
        elevation = CardDefaults.cardElevation(
            defaultElevation = 14.dp
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.secondary
        )
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
    icon: ImageVector,
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
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface
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
    icon: ImageVector,
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
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 3.dp
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
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
    icon: ImageVector,
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
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onError
        )

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}