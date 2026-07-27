package com.example.easyfill_project.forms_screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.easyfill_project.pdf_export.PdfAttachment
import com.example.easyfill_project.pdf_export.PdfAttachmentMerger
import com.example.easyfill_project.pdf_export.PdfShareManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.Color
import java.io.File

import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId

/**
 * Displays the common validation, attachment and PDF flow for every form.
 */
@Composable
fun GeneralFormSummarySection(
    formDefinition: FormDefinition,
    formData: Map<String, String>,
    validateForm: (Map<String, String>) -> List<FormIssue>,
    createFormPdf: suspend (Context, Map<String, String>) -> File,
    onValidationIssuesFound: (List<FormIssue>) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isCreatingPdf by remember { mutableStateOf(false) }
    var isLoadingAttachments by remember(formDefinition.formId) {
        mutableStateOf(true)
    }

    var pendingValidationIssues by remember {
        mutableStateOf<List<FormIssue>>(emptyList())
    }

    var showValidationDialog by remember { mutableStateOf(false) }
    var showDocumentsDialog by remember { mutableStateOf(false) }
    var showMissingDocumentsDialog by remember { mutableStateOf(false) }

    var selectedDocumentForPicker by remember {
        mutableStateOf<RequiredDocument?>(null)
    }

    var selectedDocumentForCamera by remember {
        mutableStateOf<RequiredDocument?>(null)
    }

    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCameraFileName by remember { mutableStateOf<String?>(null) }

    var attachmentPendingDeletion by remember {
        mutableStateOf<StoredFormAttachment?>(null)
    }

    var storedAttachments by remember(formDefinition.formId) {
        mutableStateOf<Map<String, List<StoredFormAttachment>>>(emptyMap())
    }

    var uploadingDocumentIds by remember {
        mutableStateOf<Set<String>>(emptySet())
    }

    var deletingAttachmentIds by remember {
        mutableStateOf<Set<String>>(emptySet())
    }

    /**
     * Adds one file or photographed page to the selected logical document.
     */
    fun uploadDocument(
        document: RequiredDocument,
        uri: Uri,
        fileName: String,
        mimeType: String?,
        source: String
    ) {
        if (document.documentId in uploadingDocumentIds) {
            return
        }

        uploadingDocumentIds = uploadingDocumentIds + document.documentId

        FormAttachmentsRepository.uploadAttachment(
            formDefinition = formDefinition,
            document = document,
            localUri = uri,
            fileName = fileName,
            mimeType = mimeType,
            source = source,
            onSuccess = { uploadedAttachment ->
                val updatedFiles = (
                        storedAttachments[document.documentId].orEmpty() +
                                uploadedAttachment
                        ).sortedBy { it.pageOrder }

                storedAttachments = storedAttachments +
                        (document.documentId to updatedFiles)

                uploadingDocumentIds =
                    uploadingDocumentIds - document.documentId

                Toast.makeText(
                    context,
                    "הקובץ נוסף למסמך",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onFailure = { exception ->
                uploadingDocumentIds =
                    uploadingDocumentIds - document.documentId

                Log.e(
                    "FormAttachments",
                    "Failed to upload form attachment",
                    exception
                )

                Toast.makeText(
                    context,
                    "שמירת הקובץ נכשלה: ${
                        exception.message ?: "שגיאה לא ידועה"
                    }",
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    /**
     * Deletes one photographed page or selected file from Firebase.
     */
    fun deleteSingleAttachment(
        attachment: StoredFormAttachment
    ) {
        if (
            attachment.attachmentId.isBlank() ||
            attachment.attachmentId in deletingAttachmentIds
        ) {
            return
        }

        deletingAttachmentIds =
            deletingAttachmentIds + attachment.attachmentId

        FormAttachmentsRepository.deleteAttachment(
            attachment = attachment,

            onSuccess = {
                val remainingFiles =
                    storedAttachments[attachment.documentId]
                        .orEmpty()
                        .filterNot { currentAttachment ->
                            currentAttachment.attachmentId ==
                                    attachment.attachmentId
                        }

                storedAttachments =
                    if (remainingFiles.isEmpty()) {
                        storedAttachments - attachment.documentId
                    } else {
                        storedAttachments +
                                (
                                        attachment.documentId to
                                                remainingFiles.sortedBy {
                                                    it.pageOrder
                                                }
                                        )
                    }

                deletingAttachmentIds =
                    deletingAttachmentIds - attachment.attachmentId

                Toast.makeText(
                    context,
                    "העמוד נמחק",
                    Toast.LENGTH_SHORT
                ).show()
            },

            onFailure = { exception ->
                deletingAttachmentIds =
                    deletingAttachmentIds - attachment.attachmentId

                Log.e(
                    "FormAttachments",
                    "Failed to delete one form attachment",
                    exception
                )

                Toast.makeText(
                    context,
                    "מחיקת העמוד נכשלה: ${
                        exception.message ?: "שגיאה לא ידועה"
                    }",
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { selectedUri: Uri? ->
        val requiredDocument = selectedDocumentForPicker
        selectedDocumentForPicker = null

        if (selectedUri == null || requiredDocument == null) {
            return@rememberLauncherForActivityResult
        }

        runCatching {
            context.contentResolver.takePersistableUriPermission(
                selectedUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        uploadDocument(
            document = requiredDocument,
            uri = selectedUri,
            fileName = getSelectedFileName(context, selectedUri),
            mimeType = context.contentResolver.getType(selectedUri),
            source = FormAttachmentsRepository.SOURCE_FILE_PICKER
        )
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { photoSaved ->
        val requiredDocument = selectedDocumentForCamera
        val cameraUri = pendingCameraUri
        val cameraFileName = pendingCameraFileName

        Log.d(
            "DocumentCamera",
            "Camera result received. Photo saved: $photoSaved"
        )

        if (
            photoSaved &&
            requiredDocument != null &&
            cameraUri != null &&
            cameraFileName != null
        ) {
            val nextPhotoNumber =
                storedAttachments[requiredDocument.documentId]
                    .orEmpty()
                    .size + 1

            uploadDocument(
                document = requiredDocument,
                uri = cameraUri,

                // Stores a readable name instead of Android's long
                // temporary camera-file name.
                fileName = "צילום $nextPhotoNumber.jpg",

                mimeType = "image/jpeg",
                source = FormAttachmentsRepository.SOURCE_CAMERA
            )
        }

        selectedDocumentForCamera = null
        pendingCameraUri = null
        pendingCameraFileName = null
    }

    /**
     * Loads attachments saved during earlier app sessions.
     */
    LaunchedEffect(formDefinition.formId) {
        isLoadingAttachments = true

        FormAttachmentsRepository.loadFormAttachments(
            formId = formDefinition.formId,
            onSuccess = { attachments ->
                storedAttachments = attachments
                isLoadingAttachments = false
            },
            onFailure = { exception ->
                isLoadingAttachments = false

                Log.e(
                    "FormAttachments",
                    "Failed to load form attachments",
                    exception
                )

                Toast.makeText(
                    context,
                    "טעינת המסמכים נכשלה: ${
                        exception.message ?: "שגיאה לא ידועה"
                    }",
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    val relevantDocuments = remember(formDefinition, formData) {
        FormDocumentRequirementsResolver.getRelevantDocuments(
            formDefinition = formDefinition,
            formData = formData
        )
    }

    val hasConfiguredDocuments = remember(formDefinition) {
        FormDocumentRequirementsResolver.hasConfiguredDocuments(
            formDefinition = formDefinition
        )
    }

    val missingRequiredDocuments = relevantDocuments.filter { document ->
        document.isRequired &&
                storedAttachments[document.documentId].orEmpty().isEmpty()
    }

    val relevantStoredAttachments = relevantDocuments.flatMap { document ->
        storedAttachments[document.documentId]
            .orEmpty()
            .sortedBy { it.pageOrder }
    }

    val missingFieldsCount = pendingValidationIssues.count {
        it.issueType == FormIssueType.MISSING
    }

    val invalidFieldsCount = pendingValidationIssues.count {
        it.issueType == FormIssueType.INVALID
    }

    val validationSummary = when {
        missingFieldsCount > 0 && invalidFieldsCount > 0 ->
            "נמצאו $missingFieldsCount שדות חסרים " +
                    "ו־$invalidFieldsCount שדות שאינם תקינים."

        missingFieldsCount > 0 ->
            "נמצאו $missingFieldsCount שדות חסרים."

        invalidFieldsCount > 0 ->
            "נמצאו $invalidFieldsCount שדות שאינם תקינים."

        else -> "נמצאו שדות שדורשים בדיקה."
    }

    /**
     * Opens the camera with a full-resolution temporary output file.
     */
    fun photographDocument(document: RequiredDocument) {
        try {
            val (cameraUri, cameraFileName) =
                createCameraImageUri(context)

            selectedDocumentForCamera = document
            pendingCameraUri = cameraUri
            pendingCameraFileName = cameraFileName
            cameraLauncher.launch(cameraUri)
        } catch (exception: Exception) {
            Log.e(
                "DocumentCamera",
                "Failed to open camera for document attachment",
                exception
            )

            Toast.makeText(
                context,
                "פתיחת המצלמה נכשלה: ${
                    exception.message ?: "שגיאה לא ידועה"
                }",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Creates the form PDF and appends every relevant saved attachment.
     */
    fun createPdf() {
        if (isCreatingPdf) {
            return
        }

        isCreatingPdf = true
        val attachmentsSnapshot = relevantStoredAttachments.toList()

        coroutineScope.launch {
            val formPdfFile = try {
                withContext(Dispatchers.IO) {
                    createFormPdf(
                        context.applicationContext,
                        formData
                    )
                }
            } catch (exception: Exception) {
                isCreatingPdf = false

                Toast.makeText(
                    context,
                    "יצירת הקובץ נכשלה: ${
                        exception.message ?: "שגיאה לא ידועה"
                    }",
                    Toast.LENGTH_LONG
                ).show()

                return@launch
            }

            if (attachmentsSnapshot.isEmpty()) {
                PdfShareManager.openPdf(
                    context = context,
                    pdfFile = formPdfFile
                )

                isCreatingPdf = false
                return@launch
            }

            val downloadDirectory = File(
                context.cacheDir,
                "downloaded_form_attachments/${formDefinition.formId}"
            ).apply {
                deleteRecursively()
                mkdirs()
            }

            FormAttachmentsRepository.downloadAttachments(
                attachments = attachmentsSnapshot,
                destinationDirectory = downloadDirectory,
                onSuccess = { downloadedAttachments ->
                    coroutineScope.launch {
                        try {
                            val totalFilesPerDocument = downloadedAttachments
                                .groupingBy { it.attachment.documentId }
                                .eachCount()

                            val currentFileNumber = mutableMapOf<String, Int>()

                            val mergeAttachments = downloadedAttachments.map {
                                    downloaded ->
                                val attachment = downloaded.attachment
                                val totalFiles = totalFilesPerDocument[
                                    attachment.documentId
                                ] ?: 1

                                val fileNumber = (
                                        currentFileNumber[attachment.documentId] ?: 0
                                        ) + 1

                                currentFileNumber[attachment.documentId] =
                                    fileNumber

                                val displayedTitle = if (totalFiles > 1) {
                                    "${attachment.documentTitle} – " +
                                            "חלק $fileNumber מתוך $totalFiles"
                                } else {
                                    attachment.documentTitle
                                }

                                PdfAttachment(
                                    documentId = attachment.documentId,
                                    title = displayedTitle,
                                    uriString = Uri.fromFile(
                                        downloaded.localFile
                                    ).toString(),
                                    fileName = attachment.fileName,
                                    mimeType = attachment.mimeType
                                )
                            }

                            val finalPdfFile = withContext(Dispatchers.IO) {
                                PdfAttachmentMerger.createMergedPdf(
                                    context = context.applicationContext,
                                    formPdfFile = formPdfFile,
                                    attachments = mergeAttachments
                                )
                            }

                            PdfShareManager.openPdf(
                                context = context,
                                pdfFile = finalPdfFile
                            )
                        } catch (exception: Exception) {
                            Log.e(
                                "PdfAttachments",
                                "Failed to merge attachments",
                                exception
                            )

                            Toast.makeText(
                                context,
                                "צירוף המסמכים לקובץ נכשל: ${
                                    exception.message ?: "שגיאה לא ידועה"
                                }",
                                Toast.LENGTH_LONG
                            ).show()
                        } finally {
                            isCreatingPdf = false
                        }
                    }
                },
                onFailure = { exception ->
                    isCreatingPdf = false

                    Log.e(
                        "PdfAttachments",
                        "Failed to download attachments",
                        exception
                    )

                    Toast.makeText(
                        context,
                        "הורדת המסמכים נכשלה: ${
                            exception.message ?: "שגיאה לא ידועה"
                        }",
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }
    }

    val isAttachmentOperationRunning =
        uploadingDocumentIds.isNotEmpty() ||
                deletingAttachmentIds.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 28.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
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
                    .padding(horizontal = 22.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .background(
                            color = MaterialTheme.colorScheme.secondary,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = "יצירת מסמך PDF",
                        modifier = Modifier.size(46.dp),
                        tint = MaterialTheme.colorScheme.onSecondary
                    )
                }

                Text(
                    text = "בדיקת הטופס ויצירת קובץ",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Text(
                    text =
                        "לפני יצירת הקובץ המערכת תבדוק שכל השדות " +
                                "הנדרשים מולאו ושהפרטים שהוזנו תקינים.",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                OutlinedButton(
                    onClick = { showDocumentsDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 54.dp),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(
                        width = 1.5.dp,
                        color = MaterialTheme.colorScheme.secondary
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onBackground
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = "צירוף מסמכים לטופס",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Button(
                    enabled =
                        !isCreatingPdf &&
                                !isLoadingAttachments &&
                                !isAttachmentOperationRunning,
                    onClick = {
                        val formIssues = validateForm(formData)

                        if (formIssues.isNotEmpty()) {
                            pendingValidationIssues = formIssues
                            showValidationDialog = true
                            return@Button
                        }

                        if (missingRequiredDocuments.isNotEmpty()) {
                            showMissingDocumentsDialog = true
                            return@Button
                        }

                        createPdf()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 54.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    )
                ) {
                    when {
                        isLoadingAttachments -> {
                            SummaryProgressContent("טוען מסמכים...")
                        }

                        isCreatingPdf -> {
                            SummaryProgressContent("יוצר קובץ...")
                        }

                        isAttachmentOperationRunning -> {
                            SummaryProgressContent("שומר מסמכים...")
                        }

                        else -> {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            Text(
                                text = "יצירת קובץ PDF",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDocumentsDialog) {
        AlertDialog(
            onDismissRequest = { showDocumentsDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            textContentColor = MaterialTheme.colorScheme.onBackground,
            title = {
                Text(
                    text = if (relevantDocuments.isNotEmpty()) {
                        "מסמכים שיש לצרף"
                    } else {
                        "אין מסמכים נדרשים"
                    },
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                when {
                    isLoadingAttachments -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )

                            Spacer(modifier = Modifier.width(12.dp))
                            Text("טוען מסמכים...")
                        }
                    }

                    relevantDocuments.isEmpty() -> {
                        Text(
                            text = if (hasConfiguredDocuments) {
                                "לא נדרשים מסמכים לצירוף עבור האפשרויות " +
                                        "שנבחרו בטופס זה."
                            } else {
                                "בטופס זה לא הוגדרה רשימת מסמכים לצירוף."
                            },
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }

                    else -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 500.dp)
                                .semantics {
                                    testTagsAsResourceId = true
                                }
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text =
                                    "אפשר לבחור PDF או תמונה, ולצלם כמה " +
                                            "עמודים שצריך עבור אותו מסמך.",
                                style = MaterialTheme.typography.bodyLarge
                            )

                            relevantDocuments.forEach { document ->
                                val accessibilityIdPrefix =
                                    "form_${formDefinition.formId}_" +
                                            "document_${document.documentId}"

                                val files = storedAttachments[
                                    document.documentId
                                ].orEmpty().sortedBy { it.pageOrder }

                                val isUploading = document.documentId in
                                        uploadingDocumentIds

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    elevation = CardDefaults.cardElevation(
                                        defaultElevation = 10.dp
                                    ),
                                    border = BorderStroke(
                                        width = 2.dp,
                                        color = MaterialTheme.colorScheme.secondary
                                    ),
                                    colors = CardDefaults.cardColors(
                                        containerColor =
                                            MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalArrangement =
                                            Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = document.title,
                                            style =
                                                MaterialTheme.typography.titleMedium
                                        )

                                        document.note?.let { note ->
                                            Text(
                                                text = note,
                                                style =
                                                    MaterialTheme.typography.bodyMedium
                                            )
                                        }

                                        Text(
                                            text = if (document.isRequired) {
                                                "מסמך חובה"
                                            } else {
                                                "מסמך מותנה"
                                            },
                                            modifier = Modifier
                                                .testTag(
                                                    "${accessibilityIdPrefix}_requirement"
                                                )
                                                .clearAndSetSemantics {
                                                    contentDescription =
                                                        "${document.title}, " +
                                                                if (
                                                                    document.isRequired
                                                                ) {
                                                                    "מסמך חובה"
                                                                } else {
                                                                    "מסמך מותנה"
                                                                }
                                                },
                                            style = MaterialTheme.typography.bodySmall
                                        )

                                        if (isUploading) {
                                            AttachmentOperationRow(
                                                text = "מעלה ושומר קובץ נוסף..."
                                            )
                                        }

                                        if (files.isEmpty()) {
                                            Text(
                                                text = "לא צורף מסמך",
                                                modifier = Modifier
                                                    .testTag(
                                                        "${accessibilityIdPrefix}_empty_status"
                                                    )
                                                    .clearAndSetSemantics {
                                                        contentDescription =
                                                            "${document.title}, " +
                                                                    "לא צורף מסמך"
                                                    }
                                            )
                                        } else {
                                            Text(
                                                text = if (files.size == 1) {
                                                    "צורף קובץ או צילום אחד"
                                                } else {
                                                    "צורפו ${files.size} קבצים או צילומים"
                                                },
                                                style =
                                                    MaterialTheme.typography.bodyMedium
                                            )

                                            files.forEachIndexed {
                                                    index,
                                                    attachment ->

                                                val isDeletingAttachment =
                                                    attachment.attachmentId in
                                                            deletingAttachmentIds

                                                Surface(
                                                    modifier =
                                                        Modifier.fillMaxWidth(),
                                                    shape =
                                                        RoundedCornerShape(12.dp),
                                                    color =
                                                        MaterialTheme
                                                            .colorScheme
                                                            .primary,
                                                    contentColor =
                                                        MaterialTheme
                                                            .colorScheme
                                                            .onPrimary,
                                                    border = BorderStroke(
                                                        width = 1.5.dp,
                                                        color =
                                                            MaterialTheme
                                                                .colorScheme
                                                                .secondary
                                                    ),
                                                    shadowElevation = 3.dp
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(
                                                                horizontal = 12.dp,
                                                                vertical = 10.dp
                                                            ),
                                                        verticalAlignment =
                                                            Alignment.CenterVertically
                                                    ) {
                                                        Column(
                                                            modifier =
                                                                Modifier.weight(1f)
                                                        ) {
                                                            Text(
                                                                text =
                                                                    attachmentDisplayLabel(
                                                                        attachment =
                                                                            attachment,
                                                                        index = index
                                                                    ),
                                                                style =
                                                                    MaterialTheme
                                                                        .typography
                                                                        .bodyLarge,
                                                                color =
                                                                    MaterialTheme
                                                                        .colorScheme
                                                                        .onPrimary
                                                            )

                                                            Text(
                                                                text =
                                                                    attachmentDisplayName(
                                                                        attachment =
                                                                            attachment,
                                                                        index = index
                                                                    ),
                                                                modifier =
                                                                    Modifier.fillMaxWidth(),
                                                                style =
                                                                    MaterialTheme
                                                                        .typography
                                                                        .bodySmall,
                                                                maxLines = 2,
                                                                overflow =
                                                                    TextOverflow.Ellipsis,
                                                                color =
                                                                    MaterialTheme
                                                                        .colorScheme
                                                                        .onPrimary
                                                            )
                                                        }

                                                        Spacer(
                                                            modifier =
                                                                Modifier.width(8.dp)
                                                        )

                                                        if (isDeletingAttachment) {
                                                            CircularProgressIndicator(
                                                                modifier =
                                                                    Modifier.size(22.dp),
                                                                strokeWidth = 2.dp,
                                                                color =
                                                                    MaterialTheme
                                                                        .colorScheme
                                                                        .onPrimary
                                                            )
                                                        } else {
                                                            IconButton(
                                                                enabled =
                                                                    !isUploading,
                                                                onClick = {
                                                                    attachmentPendingDeletion =
                                                                        attachment
                                                                }
                                                            ) {
                                                                Icon(
                                                                    imageVector =
                                                                        Icons.Default.Delete,
                                                                    contentDescription =
                                                                        "מחיקת העמוד",
                                                                    tint =
                                                                        MaterialTheme
                                                                            .colorScheme
                                                                            .error
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        Row(
                                            modifier =
                                                Modifier.fillMaxWidth(),
                                            horizontalArrangement =
                                                Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                enabled =
                                                    !isUploading &&
                                                            deletingAttachmentIds
                                                                .isEmpty(),
                                                onClick = {
                                                    selectedDocumentForPicker =
                                                        document

                                                    documentPicker.launch(
                                                        arrayOf(
                                                            "application/pdf",
                                                            "image/*"
                                                        )
                                                    )
                                                },
                                                modifier =
                                                    Modifier
                                                        .weight(1f)
                                                        .heightIn(min = 48.dp)
                                                        .testTag(
                                                            "${accessibilityIdPrefix}_file_button"
                                                        )
                                                        .semantics {
                                                            contentDescription =
                                                                if (files.isEmpty()) {
                                                                    "בחירת קובץ עבור " +
                                                                            document.title
                                                                } else {
                                                                    "הוספת קובץ עבור " +
                                                                            document.title
                                                                }
                                                        },
                                                shape =
                                                    RoundedCornerShape(14.dp),
                                                border = BorderStroke(
                                                    width = 1.5.dp,
                                                    color =
                                                        MaterialTheme
                                                            .colorScheme
                                                            .secondary
                                                ),
                                                colors =
                                                    ButtonDefaults
                                                        .outlinedButtonColors(
                                                            containerColor =
                                                                MaterialTheme
                                                                    .colorScheme
                                                                    .primary,
                                                            contentColor =
                                                                MaterialTheme
                                                                    .colorScheme
                                                                    .onPrimary,
                                                            disabledContainerColor =
                                                                MaterialTheme
                                                                    .colorScheme
                                                                    .primary
                                                                    .copy(alpha = 0.35f),
                                                            disabledContentColor =
                                                                MaterialTheme
                                                                    .colorScheme
                                                                    .onPrimary
                                                                    .copy(alpha = 0.7f)
                                                        )
                                            ) {
                                                Text(
                                                    text = if (files.isEmpty()) {
                                                        "בחירת קובץ"
                                                    } else {
                                                        "הוספת קובץ"
                                                    },
                                                    modifier =
                                                        Modifier.clearAndSetSemantics { },
                                                    textAlign = TextAlign.Center,
                                                    color =
                                                        MaterialTheme
                                                            .colorScheme
                                                            .onPrimary
                                                )
                                            }

                                            OutlinedButton(
                                                enabled =
                                                    !isUploading &&
                                                            deletingAttachmentIds
                                                                .isEmpty(),
                                                onClick = {
                                                    photographDocument(document)
                                                },
                                                modifier =
                                                    Modifier
                                                        .weight(1f)
                                                        .heightIn(min = 48.dp)
                                                        .testTag(
                                                            "${accessibilityIdPrefix}_camera_button"
                                                        )
                                                        .semantics {
                                                            contentDescription =
                                                                if (files.isEmpty()) {
                                                                    "צילום עכשיו עבור " +
                                                                            document.title
                                                                } else {
                                                                    "צילום נוסף עבור " +
                                                                            document.title
                                                                }
                                                        },
                                                shape =
                                                    RoundedCornerShape(14.dp),
                                                border = BorderStroke(
                                                    width = 1.5.dp,
                                                    color =
                                                        MaterialTheme
                                                            .colorScheme
                                                            .secondary
                                                ),
                                                colors =
                                                    ButtonDefaults
                                                        .outlinedButtonColors(
                                                            containerColor =
                                                                MaterialTheme
                                                                    .colorScheme
                                                                    .primary,
                                                            contentColor =
                                                                MaterialTheme
                                                                    .colorScheme
                                                                    .onPrimary,
                                                            disabledContainerColor =
                                                                MaterialTheme
                                                                    .colorScheme
                                                                    .primary
                                                                    .copy(alpha = 0.35f),
                                                            disabledContentColor =
                                                                MaterialTheme
                                                                    .colorScheme
                                                                    .onPrimary
                                                                    .copy(alpha = 0.7f)
                                                        )
                                            ) {
                                                Text(
                                                    text = if (files.isEmpty()) {
                                                        "צילום עכשיו"
                                                    } else {
                                                        "צילום נוסף"
                                                    },
                                                    modifier =
                                                        Modifier.clearAndSetSemantics { },
                                                    textAlign = TextAlign.Center,
                                                    color =
                                                        MaterialTheme
                                                            .colorScheme
                                                            .onPrimary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                OutlinedButton(
                    onClick = { showDocumentsDialog = false },
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        width = 1.5.dp,
                        color = MaterialTheme.colorScheme.secondary
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = "סגור",
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        )
    }

    attachmentPendingDeletion?.let { attachment ->
        val currentFiles =
            storedAttachments[attachment.documentId]
                .orEmpty()
                .sortedBy { it.pageOrder }

        val attachmentIndex =
            currentFiles.indexOfFirst { currentAttachment ->
                currentAttachment.attachmentId ==
                        attachment.attachmentId
            }.coerceAtLeast(0)

        val attachmentLabel =
            attachmentDisplayLabel(
                attachment = attachment,
                index = attachmentIndex
            )

        AlertDialog(
            onDismissRequest = {
                attachmentPendingDeletion = null
            },

            // Keeps the dialog background consistent with the app cards.
            containerColor =
                MaterialTheme.colorScheme.surface,

            titleContentColor =
                MaterialTheme.colorScheme.onSurface,

            textContentColor =
                MaterialTheme.colorScheme.onSurface,

            title = {
                Text(
                    text = "מחיקת $attachmentLabel",
                    style =
                        MaterialTheme.typography.titleLarge,
                    color =
                        MaterialTheme.colorScheme.onSurface
                )
            },

            text = {
                Text(
                    text =
                        "האם למחוק את $attachmentLabel?\n\n" +
                                "רק הקובץ או הצילום הזה יימחק. " +
                                "שאר עמודי המסמך יישארו שמורים.",
                    style =
                        MaterialTheme.typography.bodyLarge,
                    color =
                        MaterialTheme.colorScheme.onSurface
                )
            },

            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),

                    // Places one button on each physical side.
                    horizontalArrangement =
                        Arrangement.Absolute.SpaceBetween,

                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            attachmentPendingDeletion = null
                            deleteSingleAttachment(attachment)
                        },

                        modifier =
                            Modifier.heightIn(min = 48.dp),

                        shape =
                            RoundedCornerShape(14.dp),

                        border = BorderStroke(
                            width = 1.5.dp,
                            color = Color.Black
                        ),

                        colors =
                            ButtonDefaults.outlinedButtonColors(
                                containerColor =
                                    MaterialTheme.colorScheme.error,

                                contentColor =
                                    MaterialTheme.colorScheme.onError
                            )
                    ) {
                        Text(
                            text = "מחיקה",
                            color =
                                MaterialTheme.colorScheme.onError
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            attachmentPendingDeletion = null
                        },

                        modifier =
                            Modifier.heightIn(min = 48.dp),

                        shape =
                            RoundedCornerShape(14.dp),

                        border = BorderStroke(
                            width = 1.5.dp,
                            color = Color.Black
                        ),

                        colors =
                            ButtonDefaults.outlinedButtonColors(
                                containerColor =
                                    MaterialTheme.colorScheme.primary,

                                contentColor =
                                    MaterialTheme.colorScheme.onPrimary
                            )
                    ) {
                        Text(
                            text = "ביטול",
                            color =
                                MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        )
    }

    if (showMissingDocumentsDialog) {
        AlertDialog(
            onDismissRequest = { showMissingDocumentsDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = "יש מסמכים שטרם צורפו",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("המסמכים הבאים עדיין לא צורפו:")

                    missingRequiredDocuments.forEach { document ->
                        Text("• ${document.title}")
                    }

                    Text(
                        "ניתן לצרף אותם כעת או ליצור את הקובץ בכל זאת."
                    )
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            showMissingDocumentsDialog = false
                            showDocumentsDialog = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(
                            width = 1.5.dp,
                            color =
                                MaterialTheme.colorScheme.secondary
                        ),
                        colors =
                            ButtonDefaults.outlinedButtonColors(
                                containerColor =
                                    MaterialTheme.colorScheme.primary,

                                contentColor =
                                    MaterialTheme.colorScheme.onPrimary
                            )
                    ) {
                        Text(
                            text = "מעבר לצירוף מסמכים",
                            color =
                                MaterialTheme.colorScheme.onPrimary
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            showMissingDocumentsDialog = false
                            createPdf()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(
                            width = 1.5.dp,
                            color =
                                MaterialTheme.colorScheme.secondary
                        ),
                        colors =
                            ButtonDefaults.outlinedButtonColors(
                                containerColor =
                                    MaterialTheme.colorScheme.primary,

                                contentColor =
                                    MaterialTheme.colorScheme.onPrimary
                            )
                    ) {
                        Text(
                            text = "יצירת הקובץ בכל זאת",
                            color =
                                MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        )
    }

    if (showValidationDialog) {
        AlertDialog(
            onDismissRequest = { showValidationDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = "נדרשת השלמת שדות",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text =
                        "$validationSummary\n" +
                                "אפשר לעבור לטופס ולתקן את השדות המסומנים."
                )
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.Absolute.SpaceBetween
                ) {
                    OutlinedButton(
                        onClick = {
                            showValidationDialog = false
                            onValidationIssuesFound(
                                pendingValidationIssues
                            )
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(
                            width = 1.5.dp,
                            color =
                                MaterialTheme.colorScheme.secondary
                        ),
                        colors =
                            ButtonDefaults.outlinedButtonColors(
                                containerColor =
                                    MaterialTheme.colorScheme.primary,

                                contentColor =
                                    MaterialTheme.colorScheme.onPrimary
                            )
                    ) {
                        Text(
                            text = "מעבר לשדות",
                            color =
                                MaterialTheme.colorScheme.onPrimary
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            showValidationDialog = false
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(
                            width = 1.5.dp,
                            color =
                                MaterialTheme.colorScheme.secondary
                        ),
                        colors =
                            ButtonDefaults.outlinedButtonColors(
                                containerColor =
                                    MaterialTheme.colorScheme.primary,

                                contentColor =
                                    MaterialTheme.colorScheme.onPrimary
                            )
                    ) {
                        Text(
                            text = "סגור",
                            color =
                                MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        )
    }
}

/**
 * Displays progress inside the main PDF action button.
 */
@Composable
private fun SummaryProgressContent(text: String) {
    CircularProgressIndicator(
        modifier = Modifier.size(20.dp),
        strokeWidth = 2.dp,
        color = MaterialTheme.colorScheme.onSecondary
    )

    Spacer(modifier = Modifier.width(10.dp))
    Text(text)
}

/**
 * Displays progress for one attachment operation.
 */
@Composable
private fun AttachmentOperationRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp
        )

        Spacer(modifier = Modifier.width(10.dp))
        Text(text)
    }
}

/**
 * Returns a short logical label for one attached PDF or image.
 */
private fun attachmentDisplayLabel(
    attachment: StoredFormAttachment,
    index: Int
): String {
    val isPdf =
        attachment.mimeType.equals(
            other = "application/pdf",
            ignoreCase = true
        ) ||
                attachment.fileName.endsWith(
                    suffix = ".pdf",
                    ignoreCase = true
                )

    return if (isPdf) {
        "קובץ ${index + 1}"
    } else {
        "עמוד ${index + 1}"
    }
}

/**
 * Hides temporary Android camera names and keeps selected-file names readable.
 */
private fun attachmentDisplayName(
    attachment: StoredFormAttachment,
    index: Int
): String {
    return when {
        attachment.source ==
                FormAttachmentsRepository.SOURCE_CAMERA -> {
            "צילום "
        }

        attachment.fileName.isBlank() -> {
            "קובץ ${index + 1}"
        }

        else -> {
            attachment.fileName
        }
    }
}

/**
 * Returns the display name of a selected file.
 */
private fun getSelectedFileName(
    context: Context,
    uri: Uri
): String {
    context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null
    )?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(
            OpenableColumns.DISPLAY_NAME
        )

        if (nameIndex >= 0 && cursor.moveToFirst()) {
            return cursor.getString(nameIndex)
        }
    }

    return uri.lastPathSegment ?: "מסמך מצורף"
}

/**
 * Creates a temporary image URI for a full-resolution camera photo.
 */
private fun createCameraImageUri(
    context: Context
): Pair<Uri, String> {
    val attachmentsDirectory = File(
        context.cacheDir,
        "form_attachments"
    ).apply {
        mkdirs()
    }

    val imageFile = File.createTempFile(
        "document_${System.currentTimeMillis()}_",
        ".jpg",
        attachmentsDirectory
    )

    val imageUri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        imageFile
    )

    return imageUri to imageFile.name
}