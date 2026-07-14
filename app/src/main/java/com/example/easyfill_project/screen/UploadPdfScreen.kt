//package com.example.easyfill_project.screen
//
//import android.content.Context
//import android.net.Uri
//import androidx.activity.compose.rememberLauncherForActivityResult
//import androidx.activity.result.contract.ActivityResultContracts
//import androidx.compose.foundation.layout.*
//import androidx.compose.foundation.shape.RoundedCornerShape
//import androidx.compose.material3.*
//import androidx.compose.runtime.*
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.unit.dp
//import com.google.firebase.auth.FirebaseAuth
//import com.google.firebase.storage.FirebaseStorage
//import java.util.UUID
//
//import androidx.compose.material.icons.Icons
//import androidx.compose.material.icons.filled.Add
//import androidx.compose.material.icons.filled.KeyboardArrowUp
//import androidx.compose.material.icons.filled.Upload
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.platform.LocalContext
//import androidx.compose.ui.text.style.TextAlign
//import com.google.firebase.firestore.FirebaseFirestore
//import android.provider.OpenableColumns
//import androidx.compose.foundation.BorderStroke
//import androidx.compose.material.icons.filled.ArrowForward
//import androidx.compose.ui.graphics.Color
//import androidx.navigation.NavHostController
//
////import for azure
//import okhttp3.OkHttpClient
//import okhttp3.Request
//import java.io.IOException
//import java.util.concurrent.TimeUnit
//import androidx.core.content.edit
//
//
//import androidx.compose.foundation.background
//import androidx.compose.foundation.rememberScrollState
//import androidx.compose.foundation.verticalScroll
//import androidx.compose.material3.CardDefaults
//import androidx.compose.material3.ElevatedCard
//import androidx.compose.material3.Surface
//
//
//
////// This function uploads the selected PDF to Firebase Storage
//
//
//fun uploadPdfToFirebaseStorage(
//    context: Context,
//    pdfUri: Uri,          // the selected PDF file from the phone
//    fileName: String?,    // original file name, like "טופס.pdf"
//    fileSize: Long?,      // file size in bytes
//    onProgress: (Int) -> Unit,
//    onSuccess: (String) -> Unit,
//    onError: (String) -> Unit
//) {
//    // current user id for the uid the key
//    val userId = FirebaseAuth.getInstance().currentUser?.uid
//    //in case user not logged in then cant upload files
//    if (userId == null) {
//        onError("המשתמש לא מחובר")
//        return
//    }
//
//    // Firestore = saves data about the file
//    val firestore = FirebaseFirestore.getInstance()
//
//
//    // STEP 1: check Firestore if this user already uploaded
//    // a file with same name AND same size
//    firestore.collection("users")
//        .document(userId)
//        .collection("uploadedFiles")
//        .whereEqualTo("fileName", fileName)
//        .whereEqualTo("fileSize", fileSize)
//        .get()
//        .addOnSuccessListener { documents ->
//
//            // If Firestore found a match, block upload
//            if (!documents.isEmpty) {
//                onError("הקובץ הזה כבר הועלה בעבר")
//                return@addOnSuccessListener
//            }
//
//            // STEP 2: no duplicate found, create unique id
//            val fileId = UUID.randomUUID().toString()
//
//            // Storage path for the real PDF file
//            val storagePath = "users/$userId/uploads/$fileId.pdf"
//
//            // Firebase Storage = saves the actual PDF
//            val storageRef = FirebaseStorage.getInstance()
//                .reference
//                .child(storagePath)
//
//            // STEP 3: upload PDF to Storage
//            storageRef.putFile(pdfUri)//start upload
//                .addOnProgressListener { taskSnapshot ->//While the file is uploading, keep telling me how much is done
//                    val totalBytes = taskSnapshot.totalByteCount
//                    val transferredBytes = taskSnapshot.bytesTransferred
//
//                    val progress = if (totalBytes > 0) {
//                        ((100.0 * transferredBytes) / totalBytes).toInt()
//                    } else {
//                        0
//                    }
//
//                    onProgress(progress)
//                }
//                .addOnSuccessListener {//tell me when finished
//
//                    // STEP 4: save file info in Firestore
//                    val fileData = hashMapOf(
//                        "fileId" to fileId,
//                        "fileName" to fileName,
//                        "fileSize" to fileSize,
//                        "storagePath" to storagePath,
//                        "uploadedAt" to System.currentTimeMillis()
//                    )
//
//                    firestore.collection("users")
//                        .document(userId)
//                        .collection("uploadedFiles")
//                        .document(fileId)
//                        .set(fileData)
//                        .addOnSuccessListener {
//                            onSuccess(fileId)
//                        }
//                        .addOnFailureListener { exception ->
//                            onError("שגיאה בשמירת פרטי הקובץ: ${exception.message}")
//                        }
//                }
//                .addOnFailureListener { exception ->
//                    onError("שגיאה בהעלאה: ${exception.message}")
//                }
//        }
//        .addOnFailureListener { exception ->
//            onError("שגיאה בבדיקת כפילות: ${exception.message}")
//        }
//}
//
//fun callAzureExtraction(
//    fileId: String,
//    onSuccess: () -> Unit,
//    onError: (String) -> Unit
//) {
//    val user = FirebaseAuth.getInstance().currentUser
//
//    if (user == null) {
//        onError("המשתמש לא מחובר")
//        return
//    }
//
//    user.getIdToken(true)
//        .addOnSuccessListener { result ->
//            val idToken = result.token
//
//            if (idToken == null) {
//                onError("לא נמצא טוקן משתמש")
//                return@addOnSuccessListener
//            }
//            //call the Cloud Run function
//            val url =
//                "https://process-pdf-azure-968227768801.europe-west1.run.app?fileId=$fileId"
//
//            val request = Request.Builder()
//                .url(url)
//                .addHeader("Authorization", "Bearer $idToken")
//                .get()
//                .build()
//
//            val client = OkHttpClient.Builder()
//                .connectTimeout(60, TimeUnit.SECONDS)
//                .readTimeout(180, TimeUnit.SECONDS)
//                .writeTimeout(180, TimeUnit.SECONDS)
//                .build()
//
//            client.newCall(request).enqueue(object : okhttp3.Callback {
//                override fun onFailure(call: okhttp3.Call, e: IOException) {
//                    onError("שגיאה בחילוץ Azure: ${e.message}")
//                }
//
//                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
//                    if (response.isSuccessful) {
//                        onSuccess()
//                    } else {
//                        onError("Azure failed: ${response.code}")
//                    }
//                }
//            })
//        }
//        .addOnFailureListener {
//            onError("שגיאה בקבלת טוקן משתמש: ${it.message}")
//        }
//}
//
//
//@Composable
//fun UploadPdfScreen(navController: NavHostController) {
//
//    var selectedPdfUri by remember { mutableStateOf<Uri?>(null) }
//    var uploadStatus by remember { mutableStateOf("לא נבחר קובץ") }
//    var fileName by remember { mutableStateOf<String?>(null) }
//    var fileSize by remember { mutableStateOf<Long?>(null) }
//
//    var isUploading by remember { mutableStateOf(false) }
//    var uploadProgress by remember { mutableStateOf(0) }
//
//    val context = LocalContext.current
//    val scrollState = rememberScrollState()
//
//    val pdfPickerLauncher = rememberLauncherForActivityResult(
//        contract = ActivityResultContracts.GetContent()
//    ) { uri ->
//        selectedPdfUri = uri
//
//        if (uri != null) {
//            uploadStatus = "נבחר קובץ PDF"
//
//            val cursor = context.contentResolver.query(uri, null, null, null, null)
//            cursor?.use {
//                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
//                val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
//
//                if (it.moveToFirst()) {
//                    if (nameIndex != -1) {
//                        fileName = it.getString(nameIndex)
//                    }
//
//                    if (sizeIndex != -1) {
//                        fileSize = it.getLong(sizeIndex)
//                    }
//                }
//            }
//        } else {
//            uploadStatus = "לא נבחר קובץ"
//            fileName = null
//            fileSize = null
//        }
//    }
//
//    Box(
//        modifier = Modifier
//            .fillMaxSize()
//            .background(MaterialTheme.colorScheme.background)
//    ) {
//        Column(
//            modifier = Modifier
//                .fillMaxSize()
//                .verticalScroll(scrollState)
//                .padding(horizontal = 24.dp, vertical = 28.dp),
//            horizontalAlignment = Alignment.CenterHorizontally
//        ) {
//
//            Spacer(modifier = Modifier.height(12.dp))
//
//            Text(
//                text = "העלאת מסמך",
//                style = MaterialTheme.typography.headlineLarge,
//                color = MaterialTheme.colorScheme.onBackground,
//                textAlign = TextAlign.Center
//            )
//
//            Spacer(modifier = Modifier.height(10.dp))
//
//            Text(
//                text = "אם יש לך מסמך PDF מלא, אפשר להעלות אותו כדי שנזהה ממנו פרטים שיעזרו במילוי הטפסים. זה לא חובה, אפשר להמשיך גם בלי קובץ.",
//                style = MaterialTheme.typography.bodyLarge,
//                color = MaterialTheme.colorScheme.onBackground,
//                textAlign = TextAlign.Center,
//                modifier = Modifier.fillMaxWidth()
//            )
//
//            Spacer(modifier = Modifier.height(32.dp))
//
//
//            val uploadCardShape = RoundedCornerShape(22.dp)
//
//            Card(
//                modifier = Modifier.fillMaxWidth(),
//                shape = uploadCardShape,
//                elevation = CardDefaults.cardElevation(
//                    defaultElevation = 6.dp
//                ),
//                border = BorderStroke(
//                    width = 1.dp,
//                    color = MaterialTheme.colorScheme.secondary
//                ),
//                colors = CardDefaults.cardColors(
//                    containerColor = MaterialTheme.colorScheme.surface
//                )
//            ) {
//                Column(
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .padding(22.dp),
//                    horizontalAlignment = Alignment.CenterHorizontally
//                ) {
//
//                    Spacer(modifier = Modifier.height(4.dp))
//
//                    Button(
//                        enabled = !isUploading,
//                        onClick = {
//                            pdfPickerLauncher.launch("application/pdf")
//                        },
//                        modifier = Modifier
//                            .fillMaxWidth()
//                            .height(58.dp),
//                        shape = RoundedCornerShape(18.dp),
//                        colors = ButtonDefaults.buttonColors(
//                            containerColor = MaterialTheme.colorScheme.primary,
//                            contentColor = MaterialTheme.colorScheme.onPrimary,
//                            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
//                            disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
//                        )
//                    ) {
//                        Icon(
//                            imageVector = Icons.Default.Add,
//                            contentDescription = "Choose file",
//                            modifier = Modifier.size(24.dp)
//                        )
//
//                        Spacer(modifier = Modifier.width(10.dp))
//
//                        Text(
//                            text = if (selectedPdfUri == null) "בחר קובץ PDF" else "בחר קובץ אחר",
//                            style = MaterialTheme.typography.bodyLarge
//                        )
//                    }
//
//                    if (fileName != null) {
//                        Spacer(modifier = Modifier.height(18.dp))
//
//                        SelectedFileCard(
//                            fileName = fileName ?: "",
//                            fileSize = fileSize
//                        )
//                    }
//
//                    Spacer(modifier = Modifier.height(18.dp))
//
//                    StatusCard(
//                        status = uploadStatus,
//                        isUploading = isUploading,
//                        progress = uploadProgress
//                    )
//
//                    Spacer(modifier = Modifier.height(24.dp))
//
//                    Button(
//                        enabled = selectedPdfUri != null && !isUploading,
//                        onClick = {
//                            isUploading = true
//                            uploadProgress = 0
//                            uploadStatus = "מעלה קובץ..."
//
//                            selectedPdfUri?.let { uri ->
//
//                                uploadPdfToFirebaseStorage(
//                                    context = context,
//                                    pdfUri = uri,
//                                    fileName = fileName,
//                                    fileSize = fileSize,
//
//                                    onProgress = { progress: Int ->
//                                        uploadProgress = progress
//                                        uploadStatus = "מעלה קובץ... $progress%"
//                                    },
//
//                                    onSuccess = { uploadedFileId: String ->
//
//                                        uploadStatus = "הקובץ הועלה בהצלחה, מתחיל חילוץ נתונים..."
//
//                                        callAzureExtraction(
//                                            fileId = uploadedFileId,
//
//                                            onSuccess = {
//                                                uploadStatus = "החילוץ הסתיים בהצלחה"
//                                                isUploading = false
//                                                uploadProgress = 0
//
//                                                selectedPdfUri = null
//                                                fileName = null
//                                                fileSize = null
//                                            },
//
//                                            onError = { error: String ->
//                                                uploadStatus = "שגיאה בחילוץ: $error"
//                                                isUploading = false
//                                                uploadProgress = 0
//                                            }
//                                        )
//                                    },
//
//                                    onError = { error: String ->
//                                        uploadStatus = "שגיאה: $error"
//                                        isUploading = false
//                                        uploadProgress = 0
//                                    }
//                                )
//                            }
//                        },
//                        modifier = Modifier
//                            .fillMaxWidth()
//                            .height(58.dp),
//                        shape = RoundedCornerShape(18.dp),
//                        colors = ButtonDefaults.buttonColors(
//                            containerColor = if (selectedPdfUri != null)
//                                MaterialTheme.colorScheme.secondary
//                            else
//                                MaterialTheme.colorScheme.surfaceVariant,
//
//                            contentColor = if (selectedPdfUri != null)
//                                MaterialTheme.colorScheme.onSecondary
//                            else
//                                MaterialTheme.colorScheme.onSurfaceVariant
//                        )
//                    ) {
//                        Text(
//                            text = "סיום והעלאת הקובץ",
//                            style = MaterialTheme.typography.bodyLarge
//                        )
//                    }
//                }
//            }
//
//            Spacer(modifier = Modifier.height(20.dp))
//
//            OutlinedButton(
//                enabled = !isUploading,
//                onClick = { navController.navigate("demoFormOptions") },
//                modifier = Modifier.fillMaxWidth(),
//                shape = RoundedCornerShape(18.dp),
//                border = BorderStroke(
//                    width = 1.5.dp,
//                    color = MaterialTheme.colorScheme.secondary
//                ),
//                colors = ButtonDefaults.outlinedButtonColors(
//                    containerColor = MaterialTheme.colorScheme.surface,
//                    contentColor = MaterialTheme.colorScheme.onSurface
//                )
//            ) {
//                Row(
//                    modifier = Modifier.padding(vertical = 6.dp),
//                    verticalAlignment = Alignment.CenterVertically,
//                    horizontalArrangement = Arrangement.Center
//                ) {
//                    Icon(
//                        imageVector = Icons.Default.ArrowForward,
//                        contentDescription = "מעבר"
//                    )
//
//                    Spacer(modifier = Modifier.width(8.dp))
//
//                    Text(
//                        text = "המשך לבחירת טופס ללא העלאה",
//                        style = MaterialTheme.typography.bodyLarge
//                    )
//                }
//            }
//
//            Spacer(modifier = Modifier.height(20.dp))
//        }
//    }
//
//}
//
//
//
//
//@Composable
//private fun SelectedFileCard(
//    fileName: String,
//    fileSize: Long?
//) {
//    Surface(
//        modifier = Modifier.fillMaxWidth(),
//        shape = RoundedCornerShape(18.dp),
//        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
//        border = BorderStroke(
//            width = 1.dp,
//            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
//        )
//    ) {
//        Column(
//            modifier = Modifier.padding(16.dp),
//            horizontalAlignment = Alignment.CenterHorizontally
//        ) {
//            Text(
//                text = "הקובץ שנבחר",
//                style = MaterialTheme.typography.labelLarge,
//                color = MaterialTheme.colorScheme.primary,
//                textAlign = TextAlign.Center
//            )
//
//            Spacer(modifier = Modifier.height(6.dp))
//
//            Text(
//                text = fileName,
//                style = MaterialTheme.typography.bodyLarge,
//                color = MaterialTheme.colorScheme.onSurface,
//                textAlign = TextAlign.Center,
//                modifier = Modifier.fillMaxWidth()
//            )
//
//            fileSize?.let {
//                Spacer(modifier = Modifier.height(4.dp))
//
//                Text(
//                    text = formatFileSize(it),
//                    style = MaterialTheme.typography.bodySmall,
//                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
//                    textAlign = TextAlign.Center
//                )
//            }
//        }
//    }
//}
//
//@Composable
//private fun StatusCard(
//    status: String,
//    isUploading: Boolean,
//    progress: Int
//) {
//    Surface(
//        modifier = Modifier.fillMaxWidth(),
//        shape = RoundedCornerShape(18.dp),
//        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
//    ) {
//        Column(
//            modifier = Modifier.padding(16.dp),
//            horizontalAlignment = Alignment.CenterHorizontally
//        ) {
//            Text(
//                text = status,
//                style = MaterialTheme.typography.bodyMedium,
//                color = MaterialTheme.colorScheme.onSurfaceVariant,
//                textAlign = TextAlign.Center,
//                modifier = Modifier.fillMaxWidth()
//            )
//
//            if (isUploading) {
//                Spacer(modifier = Modifier.height(12.dp))
//
//                LinearProgressIndicator(
//                    progress = { progress / 100f },
//                    modifier = Modifier.fillMaxWidth(),
//                    color = MaterialTheme.colorScheme.primary,
//                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
//                )
//
//                Spacer(modifier = Modifier.height(8.dp))
//
//                Text(
//                    text = "$progress%",
//                    style = MaterialTheme.typography.labelMedium,
//                    color = MaterialTheme.colorScheme.primary
//                )
//            }
//        }
//    }
//}
//
//private fun formatFileSize(sizeInBytes: Long): String {
//    val sizeInKb = sizeInBytes / 1024.0
//    val sizeInMb = sizeInKb / 1024.0
//
//    return if (sizeInMb >= 1) {
//        String.format("%.2f MB", sizeInMb)
//    } else {
//        String.format("%.0f KB", sizeInKb)
//    }
//}
//




package com.example.easyfill_project.screen

import android.content.Context
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
import androidx.core.content.edit


import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Surface



//// This function uploads the selected PDF to Firebase Storage


fun uploadPdfToFirebaseStorage(
    context: Context,
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


@Composable
fun UploadPdfScreen(navController: NavHostController) {

    var selectedPdfUri by remember { mutableStateOf<Uri?>(null) }
    var uploadStatus by remember { mutableStateOf("לא נבחר קובץ") }
    var fileName by remember { mutableStateOf<String?>(null) }
    var fileSize by remember { mutableStateOf<Long?>(null) }

    var isUploading by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableStateOf(0) }

    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        selectedPdfUri = uri

        if (uri != null) {
            uploadStatus = "נבחר קובץ PDF"

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
        } else {
            uploadStatus = "לא נבחר קובץ"
            fileName = null
            fileSize = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "העלאת מסמך",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "אם יש לך מסמך PDF מלא, אפשר להעלות אותו כדי שנזהה ממנו פרטים שיעזרו במילוי הטפסים. זה לא חובה, אפשר להמשיך גם בלי קובץ.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))


            val uploadCardShape = RoundedCornerShape(22.dp)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = uploadCardShape,
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 6.dp
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.secondary
                ),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        enabled = !isUploading,
                        onClick = {
                            pdfPickerLauncher.launch("application/pdf")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                            disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Choose file",
                            modifier = Modifier.size(24.dp)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Text(
                            text = if (selectedPdfUri == null) "בחר קובץ PDF" else "בחר קובץ אחר",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }

                    if (fileName != null) {
                        Spacer(modifier = Modifier.height(18.dp))

                        SelectedFileCard(
                            fileName = fileName ?: "",
                            fileSize = fileSize
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    StatusCard(
                        status = uploadStatus,
                        isUploading = isUploading,
                        progress = uploadProgress
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        enabled = selectedPdfUri != null && !isUploading,
                        onClick = {
                            isUploading = true
                            uploadProgress = 0
                            uploadStatus = "מעלה קובץ..."

                            selectedPdfUri?.let { uri ->

                                uploadPdfToFirebaseStorage(
                                    context = context,
                                    pdfUri = uri,
                                    fileName = fileName,
                                    fileSize = fileSize,

                                    onProgress = { progress: Int ->
                                        uploadProgress = progress
                                        uploadStatus = "מעלה קובץ... $progress%"
                                    },

                                    onSuccess = { uploadedFileId: String ->

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

                                            onError = { error: String ->
                                                uploadStatus = "שגיאה בחילוץ: $error"
                                                isUploading = false
                                                uploadProgress = 0
                                            }
                                        )
                                    },

                                    onError = { error: String ->
                                        uploadStatus = "שגיאה: $error"
                                        isUploading = false
                                        uploadProgress = 0
                                    }
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedPdfUri != null)
                                MaterialTheme.colorScheme.secondary
                            else
                                MaterialTheme.colorScheme.surfaceVariant,

                            contentColor = if (selectedPdfUri != null)
                                MaterialTheme.colorScheme.onSecondary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(
                            text = "סיום והעלאת הקובץ",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedButton(
                enabled = !isUploading,
                onClick = { navController.navigate("demoFormOptions") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(
                    width = 1.5.dp,
                    color = MaterialTheme.colorScheme.secondary
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "מעבר"
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "המשך לבחירת טופס ללא העלאה",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

}




@Composable
private fun SelectedFileCard(
    fileName: String,
    fileSize: Long?
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "הקובץ שנבחר",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = fileName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            fileSize?.let {
                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = formatFileSize(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun StatusCard(
    status: String,
    isUploading: Boolean,
    progress: Int
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            if (isUploading) {
                Spacer(modifier = Modifier.height(12.dp))

                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "$progress%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun formatFileSize(sizeInBytes: Long): String {
    val sizeInKb = sizeInBytes / 1024.0
    val sizeInMb = sizeInKb / 1024.0

    return if (sizeInMb >= 1) {
        String.format("%.2f MB", sizeInMb)
    } else {
        String.format("%.0f KB", sizeInKb)
    }
}


