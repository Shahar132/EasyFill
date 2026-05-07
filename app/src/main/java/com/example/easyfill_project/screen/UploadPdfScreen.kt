package com.example.easyfill_project.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Upload
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign

@Composable
fun UploadPdfScreen() {

    var selectedPdfUri by remember { mutableStateOf<Uri?>(null) }
    var uploadStatus by remember { mutableStateOf("לא נבחר קובץ") }
    var fileName by remember { mutableStateOf<String?>(null) }


    val context = LocalContext.current

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        selectedPdfUri = uri

        if (uri != null) {
            uploadStatus = "נבחר קובץ PDF"

            // 👇 Extract file name
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (it.moveToFirst() && nameIndex != -1) {
                    fileName = it.getString(nameIndex)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(35.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "בחירת מסמך להעלאה",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = "ניתן להעלות קובץ כדי שנוכל לעזור לך\nבמילוי הטופס" ,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(120.dp))


        Button(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            onClick = {
                pdfPickerLauncher.launch("application/pdf")
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surface, // background
                contentColor = MaterialTheme.colorScheme.onSurface   // text + icon
            ),
            shape = RoundedCornerShape(20.dp), // rounded corners
            elevation = ButtonDefaults.buttonElevation(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Choose file",
                modifier = Modifier.size(28.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "בחר קובץ PDF להעלאה",
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = uploadStatus,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        fileName?.let {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = " $it",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(90.dp))


        Button(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            enabled = selectedPdfUri != null,
            onClick = {
                selectedPdfUri?.let { uri ->
                    uploadPdfToFirebaseStorage(
                        pdfUri = uri,
                        onSuccess = {
                            uploadStatus = "הקובץ הועלה בהצלחה"
                        },
                        onError = { error ->
                            uploadStatus = "שגיאה בהעלאה: $error"
                        }
                    )
                }
            }
        ) {
            Icon(
                imageVector = Icons.Default.Upload,
                contentDescription = "Upload file"
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "סיום והעלאת הקובץ",
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

    }
}

// This function uploads the selected PDF to Firebase Storage
fun uploadPdfToFirebaseStorage(
    pdfUri: Uri, // The PDF file location on the phone
    onSuccess: () -> Unit, // What to do if upload works
    onError: (String) -> Unit // What to do if upload fails
) {
    // Try to get current logged-in Firebase user ID.
    // If no user is logged in, use "demoUser" for now.
    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "demoUser"

    // Create random unique file name so files do not replace each other
    val fileId = UUID.randomUUID().toString()

    // Create the place/path in Firebase Storage where the PDF will be saved
    val storageRef = FirebaseStorage.getInstance()
        .reference
        .child("users/$userId/uploads/$fileId.pdf")

    // Upload the PDF file from the phone to Firebase Storage
    storageRef.putFile(pdfUri)
        .addOnSuccessListener {
            // Runs when upload finished successfully
            onSuccess()
        }
        .addOnFailureListener { exception ->
            // Runs if upload failed, and sends the error message back to the screen
            onError(exception.message ?: "Unknown error")
        }
}