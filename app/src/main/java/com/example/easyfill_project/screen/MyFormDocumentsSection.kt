package com.example.easyfill_project.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.easyfill_project.forms_screens.FormAttachmentsRepository
import com.example.easyfill_project.forms_screens.StoredFormAttachment


import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color

private data class StoredDocumentGroup(
    val formId: String,
    val formTitle: String,
    val documentId: String,
    val documentTitle: String,
    val attachments: List<StoredFormAttachment>
)

/**
 * Shows every logical document uploaded by the user and deletes it as one unit.
 */
@Composable
fun MyFormDocumentsSection() {
    var attachments by remember {
        mutableStateOf<List<StoredFormAttachment>>(emptyList())
    }

    var isLoading by remember { mutableStateOf(true) }
    var statusMessage by remember { mutableStateOf("") }

    var deletingDocumentKeys by remember {
        mutableStateOf<Set<String>>(emptySet())
    }

    var documentPendingDeletion by remember {
        mutableStateOf<StoredDocumentGroup?>(null)
    }

    /**
     * Loads every attachment uploaded by the current user.
     */
    fun loadDocuments() {
        isLoading = true
        statusMessage = ""

        FormAttachmentsRepository.loadAllAttachments(
            onSuccess = { loadedAttachments ->
                attachments = loadedAttachments
                isLoading = false
            },
            onFailure = { exception ->
                isLoading = false
                statusMessage =
                    "טעינת המסמכים נכשלה: ${
                        exception.message ?: "שגיאה לא ידועה"
                    }"
            }
        )
    }

    LaunchedEffect(Unit) {
        loadDocuments()
    }

    val documentGroups = attachments
        .groupBy { attachment ->
            attachment.formId to attachment.documentId
        }
        .map { (_, documentAttachments) ->
            val firstAttachment = documentAttachments.first()

            StoredDocumentGroup(
                formId = firstAttachment.formId,
                formTitle = firstAttachment.formTitle.ifBlank {
                    firstAttachment.formId.ifBlank { "טופס" }
                },
                documentId = firstAttachment.documentId,
                documentTitle = firstAttachment.documentTitle.ifBlank {
                    "מסמך מצורף"
                },
                attachments = documentAttachments.sortedBy {
                    it.pageOrder
                }
            )
        }
        .sortedWith(
            compareBy<StoredDocumentGroup> { it.formTitle }
                .thenBy { it.documentTitle }
        )

    val documentsByForm = documentGroups.groupBy {
        it.formId to it.formTitle
    }

    /**
     * Deletes the complete logical document from Storage and Firestore.
     */
    fun deleteDocument(group: StoredDocumentGroup) {
        val documentKey = "${group.formId}__${group.documentId}"

        if (documentKey in deletingDocumentKeys) {
            return
        }

        deletingDocumentKeys = deletingDocumentKeys + documentKey
        statusMessage = ""

        FormAttachmentsRepository.deleteDocumentAttachments(
            formId = group.formId,
            documentId = group.documentId,
            onSuccess = {
                attachments = attachments.filterNot { attachment ->
                    attachment.formId == group.formId &&
                            attachment.documentId == group.documentId
                }

                deletingDocumentKeys =
                    deletingDocumentKeys - documentKey

                statusMessage =
                    "המסמך וכל הקבצים שלו נמחקו בהצלחה"
            },
            onFailure = { exception ->
                deletingDocumentKeys =
                    deletingDocumentKeys - documentKey

                statusMessage =
                    "מחיקת המסמך נכשלה: ${
                        exception.message ?: "שגיאה לא ידועה"
                    }"
            }
        )
    }

    ProfileSectionCard(title = "המסמכים שלי") {
        when {
            isLoading -> {
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

            documentGroups.isEmpty() -> {
                Text(
                    text = "לא צורפו מסמכים לטפסים עדיין",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            else -> {
                documentsByForm.forEach { (formKey, groups) ->
                    Text(
                        text = formKey.second,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    groups.forEach { group ->
                        val documentKey =
                            "${group.formId}__${group.documentId}"

                        val isDeleting =
                            documentKey in deletingDocumentKeys

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = group.documentTitle,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Text(
                                    text = if (group.attachments.size == 1) {
                                        "קובץ אחד צורף"
                                    } else {
                                        "${group.attachments.size} קבצים או צילומים צורפו"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                        .copy(alpha = 0.75f)
                                )

                                group.attachments.forEachIndexed {
                                        index,
                                        attachment ->
                                    Text(
                                        text =
                                            accountAttachmentDisplayName(
                                                attachment = attachment,
                                                index = index
                                            ),
                                        style =
                                            MaterialTheme.typography.bodySmall,
                                        color =
                                            MaterialTheme.colorScheme.onSurface
                                                .copy(alpha = 0.68f)
                                    )
                                }
                            }

                            if (isDeleting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                IconButton(
                                    onClick = {
                                        documentPendingDeletion = group
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "מחיקת המסמך",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.secondary
                            .copy(alpha = 0.45f)
                    )
                }
            }
        }

        if (statusMessage.isNotEmpty()) {
            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }

    documentPendingDeletion?.let { group ->
        AlertDialog(
            onDismissRequest = {
                documentPendingDeletion = null
            },

            containerColor =
                MaterialTheme.colorScheme.surface,

            titleContentColor =
                MaterialTheme.colorScheme.onSurface,

            textContentColor =
                MaterialTheme.colorScheme.onSurface,

            title = {
                Text(
                    text = "מחיקת מסמך",
                    style =
                        MaterialTheme.typography.titleLarge,
                    color =
                        MaterialTheme.colorScheme.onSurface
                )
            },

            text = {
                Text(
                    text =
                        "האם למחוק את \"${group.documentTitle}\"?\n\n" +
                                "כל ${group.attachments.size} הקבצים והתמונות " +
                                "ששייכים למסמך יימחקו ולא יהיה ניתן לשחזר אותם.",
                    style =
                        MaterialTheme.typography.bodyLarge,
                    color =
                        MaterialTheme.colorScheme.onSurface
                )
            },

            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),

                    // Keeps the buttons on opposite physical sides.
                    horizontalArrangement =
                        Arrangement.Absolute.SpaceBetween,

                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            documentPendingDeletion = null
                            deleteDocument(group)
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
                            text = "מחיקת הכול",
                            color =
                                MaterialTheme.colorScheme.onError
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            documentPendingDeletion = null
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
}

/**
 * Returns a readable name without exposing temporary camera-file names.
 */
private fun accountAttachmentDisplayName(
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

    val itemLabel =
        if (isPdf) {
            "קובץ ${index + 1}"
        } else {
            "עמוד ${index + 1}"
        }

    return when {
        attachment.source ==
                FormAttachmentsRepository.SOURCE_CAMERA -> {
            "$itemLabel – צילום מהמצלמה"
        }

        attachment.fileName.isBlank() -> {
            itemLabel
        }

        else -> {
            "$itemLabel – ${attachment.fileName}"
        }
    }
}