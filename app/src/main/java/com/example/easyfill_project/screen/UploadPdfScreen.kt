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
import com.google.firebase.firestore.FirebaseFirestore
import android.provider.OpenableColumns
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavHostController

//import for azure
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit


@Composable
fun UploadPdfScreen(navController: NavHostController) {

    var selectedPdfUri by remember { mutableStateOf<Uri?>(null) }
    var uploadStatus by remember { mutableStateOf("לא נבחר קובץ") }
    var fileName by remember { mutableStateOf<String?>(null) }
    var fileSize by remember { mutableStateOf<Long?>(null) }

    var isUploading by remember { mutableStateOf(false) }// true while Firebase is uploading
    var uploadProgress by remember { mutableStateOf(0) }// upload percent: 0-100

    val context = LocalContext.current

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
        // Uri changes because it’s a temporary access reference, not a file path
    ) { uri ->
        selectedPdfUri = uri

        if (uri != null) {
            uploadStatus = "נבחר קובץ PDF"

            // Extract file name
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)

                if (it.moveToFirst()) {
                    if (nameIndex != -1) {
                        fileName = it.getString(nameIndex)
                    }

                    if (sizeIndex != -1) {
                        fileSize = it.getLong(sizeIndex)
                    }
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

        Spacer(modifier = Modifier.height(60.dp))


        Button(
            enabled = !isUploading,
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

        if (isUploading) {//Show progress under status text
            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { uploadProgress / 100f },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "$uploadProgress%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

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

        Spacer(modifier = Modifier.height(45.dp))


        Button(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),

            enabled = selectedPdfUri != null && !isUploading,

            onClick = {
                isUploading = true
                uploadProgress = 0
                uploadStatus = "מעלה קובץ..."

                selectedPdfUri?.let { uri ->

                    uploadPdfToFirebaseStorage(
                        pdfUri = uri,
                        fileName = fileName,
                        fileSize = fileSize,

                        onProgress = { progress ->
                            uploadProgress = progress
                            uploadStatus = "מעלה קובץ... $progress%"
                        },

                        //  UPDATED SUCCESS (WITH AZURE CALL)
                        onSuccess = { uploadedFileId ->

                            uploadStatus = "הקובץ הועלה בהצלחה, מתחיל חילוץ נתונים..."

                            callAzureExtraction(
                                fileId = uploadedFileId,

                                onSuccess = {
                                    uploadStatus = "החילוץ הסתיים בהצלחה"
                                    isUploading = false
                                    uploadProgress = 0

                                    selectedPdfUri = null
                                    fileName = null
                                    fileSize = null
                                },

                                onError = { error ->
                                    uploadStatus = "שגיאה בחילוץ: $error"
                                    isUploading = false
                                    uploadProgress = 0
                                }
                            )
                        },

                        onError = { error ->
                            uploadStatus = "שגיאה: $error"
                            isUploading = false
                            uploadProgress = 0
                        }
                    )
                }
            },

            colors = ButtonDefaults.buttonColors(
                containerColor = if (selectedPdfUri != null)
                    MaterialTheme.colorScheme.surface
                else
                    Color.LightGray,

                contentColor = MaterialTheme.colorScheme.onSurface
            )
        )
        {
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

        Spacer(modifier = Modifier.height(50.dp))


        OutlinedButton(
            enabled = !isUploading,//not shown button until finished extracting
            onClick = { navController.navigate("speechDemo") }, // navigate to demo screen
            modifier = Modifier
                .wrapContentWidth(Alignment.End)
                .padding(top = 24.dp),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "מעבר"
                )

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = "מעבר למילוי הטופס", // updated text
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

    }
}

// This function uploads the selected PDF to Firebase Storage
fun uploadPdfToFirebaseStorage(
    pdfUri: Uri,          // the selected PDF file from the phone
    fileName: String?,    // original file name, like "טופס.pdf"
    fileSize: Long?,      // file size in bytes
    onProgress: (Int) -> Unit,
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit
) {
    // current user id for the uid the key
    val userId = FirebaseAuth.getInstance().currentUser?.uid
    //in case user not logged in then cant upload files
    if (userId == null) {
        onError("המשתמש לא מחובר")
        return
    }

    // Firestore = saves data about the file
    val firestore = FirebaseFirestore.getInstance()


    // STEP 1: check Firestore if this user already uploaded
    // a file with same name AND same size
    firestore.collection("users")
        .document(userId)
        .collection("uploadedFiles")
        .whereEqualTo("fileName", fileName)
        .whereEqualTo("fileSize", fileSize)
        .get()
        .addOnSuccessListener { documents ->

            // If Firestore found a match, block upload
            if (!documents.isEmpty) {
                onError("הקובץ הזה כבר הועלה בעבר")
                return@addOnSuccessListener
            }

            // STEP 2: no duplicate found, create unique id
            val fileId = UUID.randomUUID().toString()

            // Storage path for the real PDF file
            val storagePath = "users/$userId/uploads/$fileId.pdf"

            // Firebase Storage = saves the actual PDF
            val storageRef = FirebaseStorage.getInstance()
                .reference
                .child(storagePath)

            // STEP 3: upload PDF to Storage
            storageRef.putFile(pdfUri)//start upload
                .addOnProgressListener { taskSnapshot ->//While the file is uploading, keep telling me how much is done
                    val totalBytes = taskSnapshot.totalByteCount
                    val transferredBytes = taskSnapshot.bytesTransferred

                    val progress = if (totalBytes > 0) {
                        ((100.0 * transferredBytes) / totalBytes).toInt()
                    } else {
                        0
                    }

                    onProgress(progress)
                }
                .addOnSuccessListener {//tell me when finished

                    // STEP 4: save file info in Firestore
                    val fileData = hashMapOf(
                        "fileId" to fileId,
                        "fileName" to fileName,
                        "fileSize" to fileSize,
                        "storagePath" to storagePath,
                        "uploadedAt" to System.currentTimeMillis()
                    )

                    firestore.collection("users")
                        .document(userId)
                        .collection("uploadedFiles")
                        .document(fileId)
                        .set(fileData)
                        .addOnSuccessListener {
                            onSuccess(fileId)
                        }
                        .addOnFailureListener { exception ->
                            onError("שגיאה בשמירת פרטי הקובץ: ${exception.message}")
                        }
                }
                .addOnFailureListener { exception ->
                    onError("שגיאה בהעלאה: ${exception.message}")
                }
        }
        .addOnFailureListener { exception ->
            onError("שגיאה בבדיקת כפילות: ${exception.message}")
        }
}

fun callAzureExtraction(
    fileId: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val user = FirebaseAuth.getInstance().currentUser

    if (user == null) {
        onError("המשתמש לא מחובר")
        return
    }

    user.getIdToken(true)
        .addOnSuccessListener { result ->
            val idToken = result.token

            if (idToken == null) {
                onError("לא נמצא טוקן משתמש")
                return@addOnSuccessListener
            }
            //call the Cloud Run function
            val url =
                "https://process-pdf-azure-968227768801.europe-west1.run.app?fileId=$fileId"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $idToken")
                .get()
                .build()

            val client = OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(180, TimeUnit.SECONDS)
                .writeTimeout(180, TimeUnit.SECONDS)
                .build()

            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    onError("שגיאה בחילוץ Azure: ${e.message}")
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    if (response.isSuccessful) {
                        onSuccess()
                    } else {
                        onError("Azure failed: ${response.code}")
                    }
                }
            })
        }
        .addOnFailureListener {
            onError("שגיאה בקבלת טוקן משתמש: ${it.message}")
        }
}