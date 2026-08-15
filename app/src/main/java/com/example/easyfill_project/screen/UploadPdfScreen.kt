
package com.example.easyfill_project.screen

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Represents the current stage of the PDF process.
 *
 * IDLE:
 * No upload or extraction is currently running.
 *
 * UPLOADING:
 * The PDF is being uploaded to Firebase Storage.
 *
 * EXTRACTING:
 * The PDF was uploaded, and Azure is extracting its data.
 *
 * SUCCESS:
 * Upload and extraction finished successfully.
 *
 * ERROR:
 * Upload or extraction failed.
 */
private enum class PdfProcessState {
    IDLE,
    UPLOADING,
    EXTRACTING,
    SUCCESS,
    ERROR
}

/**
 * Screen that allows the user to:
 *
 * 1. Select a PDF file.
 * 2. Upload it to Firebase Storage.
 * 3. Save its metadata in Firestore.
 * 4. Call Azure extraction.
 * 5. Continue to the form-selection screen.
 */
@Composable
fun UploadPdfScreen(
    navController: NavHostController
) {
    // Temporary Android URI of the selected PDF.
    var selectedPdfUri by remember {
        mutableStateOf<Uri?>(null)
    }

    // Message displayed in the status card.
    var uploadStatus by remember {
        mutableStateOf("לא נבחר קובץ")
    }

    // Original file name displayed to the user.
    var fileName by remember {
        mutableStateOf<String?>(null)
    }

    // Original file size in bytes.
    var fileSize by remember {
        mutableStateOf<Long?>(null)
    }

    // Exact Firebase upload percentage between 0 and 100.
    var uploadProgress by remember {
        mutableIntStateOf(0)
    }

    // Controls which status UI should currently be displayed.
    var processState by remember {
        mutableStateOf(PdfProcessState.IDLE)
    }

    // Android context is required for the file picker and Firebase upload.
    val context = LocalContext.current

    // Allows the screen to scroll on smaller devices.
    val scrollState = rememberScrollState()

    // True during Firebase upload or Azure extraction.
    //
    // While processing, file-selection and navigation buttons are disabled.
    val isProcessing =
        processState == PdfProcessState.UPLOADING ||
                processState == PdfProcessState.EXTRACTING

    /**
     * Opens the Android file picker.
     *
     * GetContent returns a temporary URI for the selected PDF.
     */
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->

        // Save the selected URI.
        selectedPdfUri = uri

        if (uri != null) {
            // Reset the old status when a new file is selected.
            uploadStatus = "נבחר קובץ PDF"
            uploadProgress = 0
            processState = PdfProcessState.IDLE

            // Clear old metadata before reading the new file.
            fileName = null
            fileSize = null

            // Read the display name and size from Android.
            context.contentResolver
                .query(
                    uri,
                    null,
                    null,
                    null,
                    null
                )
                ?.use { cursor ->

                    val nameIndex =
                        cursor.getColumnIndex(
                            OpenableColumns.DISPLAY_NAME
                        )

                    val sizeIndex =
                        cursor.getColumnIndex(
                            OpenableColumns.SIZE
                        )

                    if (cursor.moveToFirst()) {
                        if (nameIndex != -1) {
                            fileName = cursor.getString(nameIndex)
                        }

                        if (sizeIndex != -1) {
                            fileSize = cursor.getLong(sizeIndex)
                        }
                    }
                }

        } else {
            // The user closed the picker without selecting a PDF.
            selectedPdfUri = null
            fileName = null
            fileSize = null
            uploadProgress = 0
            uploadStatus = "לא נבחר קובץ"
            processState = PdfProcessState.IDLE
        }
    }

    // Main full-screen container.
    Box(
        modifier = Modifier
            .fillMaxSize()

            // Your existing screen background color.
            .background(
                MaterialTheme.colorScheme.background
            )
    ) {
        // Scrollable screen content.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(
                    horizontal = 24.dp,
                    vertical = 28.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Main screen title.
            Text(
                text = "העלאת מסמך",
                style = MaterialTheme.typography.headlineLarge,

                // Your existing title color.
                color = MaterialTheme.colorScheme.onBackground,

                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Explains why uploading is useful and that it is optional.
            Text(
                text = "אם יש לך מסמך PDF מלא, אפשר להעלות אותו כדי שנזהה ממנו פרטים שיעזרו במילוי הטפסים. זה לא חובה, אפשר להמשיך גם בלי קובץ.",
                style = MaterialTheme.typography.bodyLarge,

                // Your existing explanation color.
                color = MaterialTheme.colorScheme.onBackground,

                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Main upload card shape.
            val uploadCardShape = RoundedCornerShape(22.dp)

            // Groups the file selection, status and upload controls.
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = uploadCardShape,

                // Your existing card elevation.
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 6.dp
                ),

                // Your existing card border color.
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.secondary
                ),

                // Your existing card background color.
                colors = CardDefaults.cardColors(
                    containerColor =
                        MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    horizontalAlignment =
                        Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(4.dp))

                    /**
                     * Button for selecting or replacing the PDF.
                     *
                     * Disabled while upload or extraction is active.
                     */
                    Button(
                        enabled = !isProcessing,
                        onClick = {
                            pdfPickerLauncher.launch(
                                "application/pdf"
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp),
                        shape = RoundedCornerShape(18.dp),

                        // Your existing choose-file button colors.
                        colors = ButtonDefaults.buttonColors(
                            containerColor =
                                MaterialTheme.colorScheme.primary,

                            contentColor =
                                MaterialTheme.colorScheme.onPrimary,

                            disabledContainerColor =
                                MaterialTheme.colorScheme.primary
                                    .copy(alpha = 0.35f),

                            disabledContentColor =
                                MaterialTheme.colorScheme.onPrimary
                                    .copy(alpha = 0.7f)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Choose file",
                            modifier = Modifier.size(24.dp)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Text(
                            text =
                                if (selectedPdfUri == null) {
                                    "בחר קובץ PDF"
                                } else {
                                    "בחר קובץ אחר"
                                },
                            style =
                                MaterialTheme.typography.bodyLarge
                        )
                    }

                    /**
                     * Show selected-file information only when
                     * a file name was successfully read.
                     */
                    if (fileName != null) {
                        Spacer(modifier = Modifier.height(18.dp))

                        SelectedFileCard(
                            fileName = fileName.orEmpty(),
                            fileSize = fileSize
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    /**
                     * Displays:
                     *
                     * - current status text,
                     * - Firebase upload percentage,
                     * - Azure extraction loading animation.
                     */
                    StatusCard(
                        status = uploadStatus,
                        processState = processState,
                        progress = uploadProgress
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    /**
                     * Starts the complete process:
                     *
                     * Firebase upload
                     * → Firestore metadata saving
                     * → Azure extraction.
                     */
                    Button(
                        enabled =
                            selectedPdfUri != null &&
                                    !isProcessing,

                        onClick = {
                            // Stop immediately if no URI is available.
                            val pdfUri =
                                selectedPdfUri
                                    ?: return@Button

                            // Begin the Firebase upload state.
                            processState =
                                PdfProcessState.UPLOADING

                            uploadProgress = 0
                            uploadStatus = "מעלה קובץ..."

                            uploadPdfToFirebaseStorage(
                                context = context,
                                pdfUri = pdfUri,
                                fileName = fileName,
                                fileSize = fileSize,

                                /**
                                 * Firebase provides exact upload progress.
                                 *
                                 * This updates both the percentage text
                                 * and the linear progress bar.
                                 */
                                onProgress = { progress ->
                                    uploadProgress =
                                        progress.coerceIn(0, 100)

                                    uploadStatus =
                                        "מעלה קובץ... ${uploadProgress}%"
                                },

                                /**
                                 * Firebase Storage upload and Firestore
                                 * metadata saving both finished.
                                 *
                                 * Now Azure extraction begins.
                                 */
                                onSuccess = { uploadedFileId ->

                                    // Change the UI from a percentage bar
                                    // to the circular Azure loading animation.
                                    processState =
                                        PdfProcessState.EXTRACTING

                                    uploadProgress = 100

                                    uploadStatus =
                                        "הקובץ הועלה בהצלחה, מתחיל חילוץ נתונים..."

                                    callAzureExtraction(
                                        fileId = uploadedFileId,

                                        /**
                                         * Azure completed successfully.
                                         */
                                        onSuccess = {
                                            processState =
                                                PdfProcessState.SUCCESS

                                            uploadStatus =
                                                "החילוץ הסתיים בהצלחה. הנתונים מוכנים לשימוש בטפסים."

                                            uploadProgress = 0

                                            // Clear the selected file because
                                            // processing is complete.
                                            selectedPdfUri = null
                                            fileName = null
                                            fileSize = null
                                        },

                                        /**
                                         * The file was uploaded, but Azure
                                         * extraction failed.
                                         */
                                        onError = { error ->
                                            processState =
                                                PdfProcessState.ERROR

                                            uploadStatus =
                                                "שגיאה בחילוץ הנתונים: $error"

                                            uploadProgress = 0
                                        }
                                    )
                                },

                                /**
                                 * Firebase upload, duplicate checking,
                                 * or Firestore metadata saving failed.
                                 */
                                onError = { error ->
                                    processState =
                                        PdfProcessState.ERROR

                                    uploadStatus =
                                        "שגיאה בהעלאת הקובץ: $error"

                                    uploadProgress = 0
                                }
                            )
                        },

                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp),

                        shape = RoundedCornerShape(18.dp),

                        // Your existing upload-button colors.
                        colors = ButtonDefaults.buttonColors(
                            // Used when the button is enabled:
                            // a file is selected and processing is not active.
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary,

                            // Used when the button is disabled:
                            // no file selected, or upload/extraction is running.
                            disabledContainerColor =
                                MaterialTheme.colorScheme.tertiary,

                            disabledContentColor =
                                MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Upload,
                            contentDescription = "Upload file"
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        // Button wording changes according to the process.
                        Text(
                            text = when (processState) {
                                PdfProcessState.UPLOADING ->
                                    "מעלה את הקובץ..."

                                PdfProcessState.EXTRACTING ->
                                    "מחלץ נתונים..."

                                else ->
                                    "סיום והעלאת הקובץ"
                            },
                            style =
                                MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            /**
             * Allows navigation without uploading a PDF.
             *
             * It is disabled while Firebase or Azure is still processing,
             * so the operation is not interrupted accidentally.
             */
            OutlinedButton(
                enabled = !isProcessing,
                onClick = {
                    navController.navigate(
                        "demoFormOptions"
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),

                // Your existing outlined-button border color.
                border = BorderStroke(
                    width = 1.5.dp,
                    color = MaterialTheme.colorScheme.secondary
                ),

                // Your existing outlined-button colors.
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor =
                        MaterialTheme.colorScheme.surface,

                    contentColor =
                        MaterialTheme.colorScheme.onSurface
                )
            ) {
                Row(
                    modifier =
                        Modifier.padding(vertical = 6.dp),
                    verticalAlignment =
                        Alignment.CenterVertically,
                    horizontalArrangement =
                        Arrangement.Center
                ) {
                    Text(
                        text =
                            "המשך לבחירת טופס ללא העלאה",
                        style =
                            MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    Icon(
                        imageVector =
                            Icons.Default.ArrowBack,
                        contentDescription = "מעבר"
                    )

                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

/**
 * Displays the selected PDF information.
 *
 * It shows:
 * - file name,
 * - file size in KB or MB.
 */
@Composable
private fun SelectedFileCard(
    fileName: String,
    fileSize: Long?
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),

        // Your existing selected-file background color.
        color =
            MaterialTheme.colorScheme.primary
                .copy(alpha = 0.08f),

        // Your existing selected-file border color.
        border = BorderStroke(
            width = 1.dp,
            color =
                MaterialTheme.colorScheme.primary
                    .copy(alpha = 0.18f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {
            Text(
                text = "הקובץ שנבחר",
                style =
                    MaterialTheme.typography.labelLarge,

                // Your existing selected-file title color.
                color =
                    MaterialTheme.colorScheme.onSurface,

                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = fileName,
                style =
                    MaterialTheme.typography.bodyLarge,

                // Your existing file-name color.
                color =
                    MaterialTheme.colorScheme.onSurface,

                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            fileSize?.let { size ->
                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = formatFileSize(size),
                    style =
                        MaterialTheme.typography.bodySmall,

                    // Your existing file-size color.
                    color =
                        MaterialTheme.colorScheme.onSurface
                            .copy(alpha = 0.65f),

                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Displays the current PDF-processing information.
 *
 * IDLE:
 * Shows only the current message.
 *
 * UPLOADING:
 * Shows the exact upload percentage and a linear progress bar.
 *
 * EXTRACTING:
 * Shows a circular loading animation because Azure does not
 * return an exact percentage.
 *
 * SUCCESS:
 * Shows the final success message.
 *
 * ERROR:
 * Shows the error message.
 */
@Composable
private fun StatusCard(
    status: String,
    processState: PdfProcessState,
    progress: Int
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),

        // Your existing status-card background color.
        color =
            MaterialTheme.colorScheme.surfaceVariant
                .copy(alpha = 0.65f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {
            // Main status message.
            Text(
                text = status,
                style =
                    MaterialTheme.typography.bodyMedium,

                // Your existing status-text color.
                color =
                    MaterialTheme.colorScheme.onSurfaceVariant,

                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            when (processState) {
                /**
                 * Firebase upload state.
                 *
                 * Shows exact progress from 0 to 100.
                 */
                PdfProcessState.UPLOADING -> {
                    Spacer(modifier = Modifier.height(12.dp))

                    LinearProgressIndicator(
                        progress = {
                            progress.coerceIn(0, 100) / 100f
                        },
                        modifier = Modifier.fillMaxWidth(),

                        // Your existing progress-bar color.
                        color =
                            MaterialTheme.colorScheme.primary,

                        // Your existing progress-track color.
                        trackColor =
                            MaterialTheme.colorScheme.primary
                                .copy(alpha = 0.18f)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text =
                            "${progress.coerceIn(0, 100)}%",
                        style =
                            MaterialTheme.typography.labelMedium,

                        // Your existing percentage color.
                        color =
                            MaterialTheme.colorScheme.primary
                    )
                }

                /**
                 * Azure extraction state.
                 *
                 * Azure does not return an exact percentage,
                 * so an indeterminate circular animation is shown.
                 */
                PdfProcessState.EXTRACTING -> {
                    Spacer(modifier = Modifier.height(14.dp))

                    CircularProgressIndicator(
                        modifier = Modifier.size(42.dp),

                        // Uses the same theme primary color
                        // as your upload progress bar.
                        color =
                            MaterialTheme.colorScheme.primary,

                        // Uses your existing themed track style.
                        trackColor =
                            MaterialTheme.colorScheme.primary
                                .copy(alpha = 0.18f)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text =
                            "הפעולה עשויה להימשך מספר רגעים",
                        style =
                            MaterialTheme.typography.bodySmall,

                        // Your existing helper-text color.
                        color =
                            MaterialTheme.colorScheme.onSurfaceVariant,

                        textAlign = TextAlign.Center
                    )
                }

                // IDLE, SUCCESS and ERROR display only the status message.
                else -> Unit
            }
        }
    }
}

/**
 * Uploads the selected PDF to Firebase Storage.
 *
 * Process:
 *
 * 1. Checks whether the same file name and size already exist.
 * 2. Creates a unique file ID.
 * 3. Uploads the real PDF to Firebase Storage.
 * 4. Saves file metadata in Firestore.
 * 5. Returns the generated file ID through onSuccess.
 */
fun uploadPdfToFirebaseStorage(
    context: Context,
    pdfUri: Uri,
    fileName: String?,
    fileSize: Long?,
    onProgress: (Int) -> Unit,
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit
) {
    // Get the currently logged-in user's ID.
    val userId =
        FirebaseAuth.getInstance()
            .currentUser
            ?.uid

    // Upload is not allowed without a logged-in user.
    if (userId == null) {
        onError("המשתמש לא מחובר")
        return
    }

    // Firestore stores the metadata about the uploaded PDF.
    val firestore =
        FirebaseFirestore.getInstance()

    // Check whether the same name and size were uploaded before.
    firestore.collection("users")
        .document(userId)
        .collection("uploadedFiles")
        .whereEqualTo("fileName", fileName)
        .whereEqualTo("fileSize", fileSize)
        .get()
        .addOnSuccessListener { documents ->

            // Prevent uploading the same file twice.
            if (!documents.isEmpty) {
                onError("הקובץ הזה כבר הועלה בעבר")
                return@addOnSuccessListener
            }

            // Generate a unique identifier for this PDF.
            val fileId =
                UUID.randomUUID().toString()

            // Firebase Storage location of the actual file.
            val storagePath =
                "users/$userId/uploads/$fileId.pdf"

            val storageRef =
                FirebaseStorage.getInstance()
                    .reference
                    .child(storagePath)

            // Start uploading the real PDF.
            storageRef.putFile(pdfUri)
                .addOnProgressListener { taskSnapshot ->

                    val totalBytes =
                        taskSnapshot.totalByteCount

                    val transferredBytes =
                        taskSnapshot.bytesTransferred

                    // Calculate the percentage uploaded.
                    val progress =
                        if (totalBytes > 0) {
                            (
                                    100.0 *
                                            transferredBytes /
                                            totalBytes
                                    ).toInt()
                        } else {
                            0
                        }

                    onProgress(
                        progress.coerceIn(0, 100)
                    )
                }
                .addOnSuccessListener {
                    // Metadata that will be saved in Firestore.
                    val fileData = hashMapOf(
                        "fileId" to fileId,
                        "fileName" to fileName,
                        "fileSize" to fileSize,
                        "storagePath" to storagePath,
                        "uploadedAt" to
                                System.currentTimeMillis()
                    )

                    firestore.collection("users")
                        .document(userId)
                        .collection("uploadedFiles")
                        .document(fileId)
                        .set(fileData)
                        .addOnSuccessListener {
                            // Return the ID needed for Azure extraction.
                            onSuccess(fileId)
                        }
                        .addOnFailureListener { exception ->
                            onError(
                                "שגיאה בשמירת פרטי הקובץ: " +
                                        "${exception.message}"
                            )
                        }
                }
                .addOnFailureListener { exception ->
                    onError(
                        "שגיאה בהעלאה: " +
                                "${exception.message}"
                    )
                }
        }
        .addOnFailureListener { exception ->
            onError(
                "שגיאה בבדיקת כפילות: " +
                        "${exception.message}"
            )
        }
}

/**
 * Calls the Cloud Run endpoint that performs Azure extraction.
 *
 * OkHttp callbacks run on a background thread.
 *
 * Compose state must be updated from the Android main thread,
 * so Handler posts onSuccess or onError back to the main thread.
 */
fun callAzureExtraction(
    fileId: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    // Get the current Firebase user.
    val user =
        FirebaseAuth.getInstance().currentUser

    if (user == null) {
        onError("המשתמש לא מחובר")
        return
    }

    // Used to return OkHttp results to the main Android thread.
    val mainHandler =
        Handler(Looper.getMainLooper())

    // Request a fresh Firebase authentication token.
    user.getIdToken(true)
        .addOnSuccessListener { result ->

            val idToken = result.token

            if (idToken == null) {
                onError("לא נמצא טוקן משתמש")
                return@addOnSuccessListener
            }

            // Cloud Run endpoint with the uploaded PDF ID.
            val url =
                "https://process-pdf-azure-968227768801.europe-west1.run.app" +
                        "?fileId=$fileId"

            // Build the authenticated HTTP request.
            val request =
                Request.Builder()
                    .url(url)
                    .addHeader(
                        "Authorization",
                        "Bearer $idToken"
                    )
                    .get()
                    .build()

            // HTTP client with longer extraction timeouts.
            val client =
                OkHttpClient.Builder()
                    .connectTimeout(
                        60,
                        TimeUnit.SECONDS
                    )
                    .readTimeout(
                        180,
                        TimeUnit.SECONDS
                    )
                    .writeTimeout(
                        180,
                        TimeUnit.SECONDS
                    )
                    .build()

            client.newCall(request)
                .enqueue(
                    object : Callback {

                        // Network request failed.
                        override fun onFailure(
                            call: Call,
                            e: IOException
                        ) {
                            mainHandler.post {
                                onError(
                                    "שגיאה בחילוץ Azure: " +
                                            "${e.message}"
                                )
                            }
                        }

                        // Cloud Run returned a response.
                        override fun onResponse(
                            call: Call,
                            response: Response
                        ) {
                            response.use {
                                mainHandler.post {
                                    if (response.isSuccessful) {
                                        onSuccess()
                                    } else {
                                        onError(
                                            "Azure failed: " +
                                                    "${response.code}"
                                        )
                                    }
                                }
                            }
                        }
                    }
                )
        }
        .addOnFailureListener { exception ->
            onError(
                "שגיאה בקבלת טוקן משתמש: " +
                        "${exception.message}"
            )
        }
}

/**
 * Converts a file size from bytes into readable KB or MB text.
 */
private fun formatFileSize(
    sizeInBytes: Long
): String {
    val sizeInKb =
        sizeInBytes / 1024.0

    val sizeInMb =
        sizeInKb / 1024.0

    return if (sizeInMb >= 1) {
        String.format("%.2f MB", sizeInMb)
    } else {
        String.format("%.0f KB", sizeInKb)
    }
}