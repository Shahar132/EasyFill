package com.example.easyfill_project.forms_screens

import android.net.Uri
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import java.io.File
import java.util.UUID

data class StoredFormAttachment(
    val attachmentId: String = "",
    val formId: String = "",
    val formTitle: String = "",
    val documentId: String = "",
    val documentTitle: String = "",
    val fileName: String = "",
    val mimeType: String = "",
    val storagePath: String = "",
    val source: String = "",
    val sizeBytes: Long = 0L,
    val pageOrder: Long = 0L,
    val uploadedAt: Timestamp? = null
)

data class DownloadedFormAttachment(
    val attachment: StoredFormAttachment,
    val localFile: File
)

object FormAttachmentsRepository {

    const val SOURCE_FILE_PICKER = "file_picker"
    const val SOURCE_CAMERA = "camera"

    private val auth =
        FirebaseAuth.getInstance()

    private val firestore =
        FirebaseFirestore.getInstance()

    private val storage =
        FirebaseStorage.getInstance()

    /**
     * Adds one new file or photographed page to a logical form document.
     */
    fun uploadAttachment(
        formDefinition: FormDefinition,
        document: RequiredDocument,
        localUri: Uri,
        fileName: String,
        mimeType: String?,
        source: String,
        onSuccess: (StoredFormAttachment) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        saveAttachment(
            formDefinition = formDefinition,
            document = document,
            existingAttachment = null,
            localUri = localUri,
            fileName = fileName,
            mimeType = mimeType,
            source = source,
            onSuccess = onSuccess,
            onFailure = onFailure
        )
    }

    /**
     * Replaces one specific file while preserving its position in the document.
     */
    fun replaceAttachment(
        formDefinition: FormDefinition,
        document: RequiredDocument,
        existingAttachment: StoredFormAttachment,
        localUri: Uri,
        fileName: String,
        mimeType: String?,
        source: String,
        onSuccess: (StoredFormAttachment) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        saveAttachment(
            formDefinition = formDefinition,
            document = document,
            existingAttachment = existingAttachment,
            localUri = localUri,
            fileName = fileName,
            mimeType = mimeType,
            source = source,
            onSuccess = onSuccess,
            onFailure = onFailure
        )
    }

    /**
     * Uploads a new Storage object and then saves its Firestore metadata.
     */
    private fun saveAttachment(
        formDefinition: FormDefinition,
        document: RequiredDocument,
        existingAttachment: StoredFormAttachment?,
        localUri: Uri,
        fileName: String,
        mimeType: String?,
        source: String,
        onSuccess: (StoredFormAttachment) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val uid = auth.currentUser?.uid

        if (uid == null) {
            onFailure(
                IllegalStateException(
                    "לא נמצא משתמש מחובר"
                )
            )
            return
        }

        val attachmentId =
            existingAttachment?.attachmentId
                ?.takeIf { it.isNotBlank() }
                ?: createAttachmentDocumentId(
                    formId = formDefinition.formId,
                    documentId = document.documentId
                )

        val pageOrder =
            existingAttachment?.pageOrder
                ?.takeIf { it > 0L }
                ?: System.currentTimeMillis()

        val previousStoragePath =
            existingAttachment?.storagePath.orEmpty()

        val extension =
            resolveFileExtension(
                fileName = fileName,
                mimeType = mimeType
            )

        val resolvedMimeType =
            resolveMimeType(
                extension = extension,
                mimeType = mimeType
            )

        val storagePath =
            buildStoragePath(
                uid = uid,
                formId = formDefinition.formId,
                documentId = document.documentId,
                attachmentId = attachmentId,
                extension = extension
            )

        val storageReference =
            storage.reference.child(storagePath)

        val firestoreReference =
            firestore.collection("users")
                .document(uid)
                .collection("formAttachments")
                .document(attachmentId)

        val metadata =
            StorageMetadata.Builder()
                .setContentType(resolvedMimeType)
                .build()

        storageReference
            .putFile(localUri, metadata)
            .addOnSuccessListener { uploadSnapshot ->
                val sizeBytes =
                    uploadSnapshot.metadata
                        ?.sizeBytes
                        ?: 0L

                val firestoreData =
                    hashMapOf<String, Any?>(
                        "attachmentId" to attachmentId,
                        "formId" to formDefinition.formId,
                        "formTitle" to formDefinition.title,
                        "documentId" to document.documentId,
                        "documentTitle" to document.title,
                        "fileName" to fileName,
                        "mimeType" to resolvedMimeType,
                        "storagePath" to storagePath,
                        "source" to source,
                        "sizeBytes" to sizeBytes,
                        "pageOrder" to pageOrder,
                        "uploadedAt" to FieldValue.serverTimestamp()
                    )

                firestoreReference
                    .set(firestoreData)
                    .addOnSuccessListener {
                        if (
                            previousStoragePath.isNotBlank() &&
                            previousStoragePath != storagePath
                        ) {
                            storage.reference
                                .child(previousStoragePath)
                                .delete()
                        }

                        onSuccess(
                            StoredFormAttachment(
                                attachmentId = attachmentId,
                                formId = formDefinition.formId,
                                formTitle = formDefinition.title,
                                documentId = document.documentId,
                                documentTitle = document.title,
                                fileName = fileName,
                                mimeType = resolvedMimeType,
                                storagePath = storagePath,
                                source = source,
                                sizeBytes = sizeBytes,
                                pageOrder = pageOrder,
                                uploadedAt = Timestamp.now()
                            )
                        )
                    }
                    .addOnFailureListener { exception ->
                        storageReference.delete()
                        onFailure(exception)
                    }
            }
            .addOnFailureListener { exception ->
                onFailure(exception)
            }
    }

    /**
     * Loads every saved file for one form and groups them by logical document.
     */
    fun loadFormAttachments(
        formId: String,
        onSuccess: (
            Map<String, List<StoredFormAttachment>>
        ) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val uid = auth.currentUser?.uid

        if (uid == null) {
            onFailure(
                IllegalStateException(
                    "לא נמצא משתמש מחובר"
                )
            )
            return
        }

        firestore.collection("users")
            .document(uid)
            .collection("formAttachments")
            .whereEqualTo("formId", formId)
            .get()
            .addOnSuccessListener { snapshot ->
                val attachments =
                    snapshot.documents
                        .mapNotNull(::toStoredAttachment)
                        .groupBy { attachment ->
                            attachment.documentId
                        }
                        .mapValues { (_, files) ->
                            files.sortedWith(
                                compareBy<StoredFormAttachment> {
                                    it.pageOrder
                                }.thenBy {
                                    it.uploadedAt?.seconds ?: 0L
                                }
                            )
                        }

                onSuccess(attachments)
            }
            .addOnFailureListener { exception ->
                onFailure(exception)
            }
    }

    /**
     * Loads every attachment uploaded by the current user.
     */
    fun loadAllAttachments(
        onSuccess: (
            List<StoredFormAttachment>
        ) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val uid = auth.currentUser?.uid

        if (uid == null) {
            onFailure(
                IllegalStateException(
                    "לא נמצא משתמש מחובר"
                )
            )
            return
        }

        firestore.collection("users")
            .document(uid)
            .collection("formAttachments")
            .get()
            .addOnSuccessListener { snapshot ->
                val attachments =
                    snapshot.documents
                        .mapNotNull(::toStoredAttachment)
                        .sortedWith(
                            compareByDescending<StoredFormAttachment> {
                                it.uploadedAt?.seconds ?: 0L
                            }.thenByDescending {
                                it.pageOrder
                            }
                        )

                onSuccess(attachments)
            }
            .addOnFailureListener { exception ->
                onFailure(exception)
            }
    }

    /**
     * Deletes one physical attachment from Storage and Firestore.
     */
    fun deleteAttachment(
        attachment: StoredFormAttachment,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val uid = auth.currentUser?.uid

        if (uid == null) {
            onFailure(
                IllegalStateException(
                    "לא נמצא משתמש מחובר"
                )
            )
            return
        }

        val firestoreDocumentId =
            attachment.attachmentId.ifBlank {
                createLegacyAttachmentDocumentId(
                    formId = attachment.formId,
                    documentId = attachment.documentId
                )
            }

        val firestoreReference =
            firestore.collection("users")
                .document(uid)
                .collection("formAttachments")
                .document(firestoreDocumentId)

        fun deleteFirestoreMetadata() {
            firestoreReference.delete()
                .addOnSuccessListener {
                    onSuccess()
                }
                .addOnFailureListener { exception ->
                    onFailure(exception)
                }
        }

        if (attachment.storagePath.isBlank()) {
            deleteFirestoreMetadata()
            return
        }

        storage.reference
            .child(attachment.storagePath)
            .delete()
            .addOnSuccessListener {
                deleteFirestoreMetadata()
            }
            .addOnFailureListener { exception ->
                val storageException =
                    exception as? StorageException

                if (
                    storageException?.errorCode ==
                    StorageException.ERROR_OBJECT_NOT_FOUND
                ) {
                    deleteFirestoreMetadata()
                } else {
                    onFailure(exception)
                }
            }
    }

    /**
     * Deletes every file belonging to one logical form document.
     */
    fun deleteDocumentAttachments(
        formId: String,
        documentId: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val uid = auth.currentUser?.uid

        if (uid == null) {
            onFailure(
                IllegalStateException(
                    "לא נמצא משתמש מחובר"
                )
            )
            return
        }

        firestore.collection("users")
            .document(uid)
            .collection("formAttachments")
            .whereEqualTo("formId", formId)
            .get()
            .addOnSuccessListener { snapshot ->
                val attachments =
                    snapshot.documents
                        .mapNotNull(::toStoredAttachment)
                        .filter { attachment ->
                            attachment.documentId == documentId
                        }

                deleteNextAttachment(
                    attachments = attachments,
                    currentIndex = 0,
                    onSuccess = onSuccess,
                    onFailure = onFailure
                )
            }
            .addOnFailureListener { exception ->
                onFailure(exception)
            }
    }

    /**
     * Keeps compatibility with code that previously deleted one document.
     */
    fun deleteAttachment(
        formId: String,
        documentId: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        deleteDocumentAttachments(
            formId = formId,
            documentId = documentId,
            onSuccess = onSuccess,
            onFailure = onFailure
        )
    }

    /**
     * Downloads saved attachments to a temporary local directory.
     */
    fun downloadAttachments(
        attachments: List<StoredFormAttachment>,
        destinationDirectory: File,
        onSuccess: (
            List<DownloadedFormAttachment>
        ) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        destinationDirectory.mkdirs()

        if (attachments.isEmpty()) {
            onSuccess(emptyList())
            return
        }

        val downloadedAttachments =
            mutableListOf<DownloadedFormAttachment>()

        downloadNextAttachment(
            attachments = attachments,
            currentIndex = 0,
            destinationDirectory = destinationDirectory,
            downloadedAttachments = downloadedAttachments,
            onSuccess = onSuccess,
            onFailure = onFailure
        )
    }

    /**
     * Downloads attachments sequentially to preserve their document order.
     */
    private fun downloadNextAttachment(
        attachments: List<StoredFormAttachment>,
        currentIndex: Int,
        destinationDirectory: File,
        downloadedAttachments: MutableList<DownloadedFormAttachment>,
        onSuccess: (
            List<DownloadedFormAttachment>
        ) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (currentIndex >= attachments.size) {
            onSuccess(downloadedAttachments.toList())
            return
        }

        val attachment = attachments[currentIndex]

        val extension =
            resolveFileExtension(
                fileName = attachment.fileName,
                mimeType = attachment.mimeType
            )

        val localFile =
            File(
                destinationDirectory,
                "${sanitizePathSegment(attachment.attachmentId)}_" +
                        "${System.currentTimeMillis()}.$extension"
            )

        storage.reference
            .child(attachment.storagePath)
            .getFile(localFile)
            .addOnSuccessListener {
                downloadedAttachments.add(
                    DownloadedFormAttachment(
                        attachment = attachment,
                        localFile = localFile
                    )
                )

                downloadNextAttachment(
                    attachments = attachments,
                    currentIndex = currentIndex + 1,
                    destinationDirectory = destinationDirectory,
                    downloadedAttachments = downloadedAttachments,
                    onSuccess = onSuccess,
                    onFailure = onFailure
                )
            }
            .addOnFailureListener { exception ->
                localFile.delete()
                onFailure(exception)
            }
    }

    /**
     * Deletes a list of attachments sequentially.
     */
    private fun deleteNextAttachment(
        attachments: List<StoredFormAttachment>,
        currentIndex: Int,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (currentIndex >= attachments.size) {
            onSuccess()
            return
        }

        deleteAttachment(
            attachment = attachments[currentIndex],
            onSuccess = {
                deleteNextAttachment(
                    attachments = attachments,
                    currentIndex = currentIndex + 1,
                    onSuccess = onSuccess,
                    onFailure = onFailure
                )
            },
            onFailure = onFailure
        )
    }

    /**
     * Converts one Firestore record and supports older attachment records.
     */
    private fun toStoredAttachment(
        snapshot: DocumentSnapshot
    ): StoredFormAttachment? {
        val attachment =
            snapshot.toObject(
                StoredFormAttachment::class.java
            ) ?: return null

        val resolvedOrder =
            attachment.pageOrder.takeIf { it > 0L }
                ?: attachment.uploadedAt
                    ?.toDate()
                    ?.time
                ?: 0L

        return attachment.copy(
            attachmentId =
                attachment.attachmentId.ifBlank {
                    snapshot.id
                },
            pageOrder = resolvedOrder
        )
    }

    /**
     * Creates a unique Firestore record ID for one physical attachment.
     */
    private fun createAttachmentDocumentId(
        formId: String,
        documentId: String
    ): String {
        return "${sanitizePathSegment(formId)}__" +
                "${sanitizePathSegment(documentId)}__" +
                UUID.randomUUID().toString()
    }

    /**
     * Returns the old record ID used before multiple files were supported.
     */
    private fun createLegacyAttachmentDocumentId(
        formId: String,
        documentId: String
    ): String {
        return "${sanitizePathSegment(formId)}__" +
                sanitizePathSegment(documentId)
    }

    /**
     * Creates a unique Storage path for each upload or replacement version.
     */
    private fun buildStoragePath(
        uid: String,
        formId: String,
        documentId: String,
        attachmentId: String,
        extension: String
    ): String {
        return "users/$uid/formAttachments/" +
                "${sanitizePathSegment(formId)}/" +
                "${sanitizePathSegment(documentId)}/" +
                "${sanitizePathSegment(attachmentId)}_" +
                "${System.currentTimeMillis()}.$extension"
    }

    /**
     * Returns a supported extension for PDF and image files.
     */
    private fun resolveFileExtension(
        fileName: String,
        mimeType: String?
    ): String {
        val suppliedExtension =
            fileName.substringAfterLast(
                delimiter = ".",
                missingDelimiterValue = ""
            )
                .lowercase()
                .filter { character ->
                    character.isLetterOrDigit()
                }

        if (
            suppliedExtension in
            setOf(
                "pdf",
                "jpg",
                "jpeg",
                "png"
            )
        ) {
            return suppliedExtension
        }

        return when (mimeType?.lowercase()) {
            "application/pdf" -> "pdf"
            "image/png" -> "png"
            "image/jpeg",
            "image/jpg" -> "jpg"
            else -> "jpg"
        }
    }

    /**
     * Returns the content type saved in Firebase Storage metadata.
     */
    private fun resolveMimeType(
        extension: String,
        mimeType: String?
    ): String {
        if (!mimeType.isNullOrBlank()) {
            return mimeType
        }

        return when (extension) {
            "pdf" -> "application/pdf"
            "png" -> "image/png"
            else -> "image/jpeg"
        }
    }

    /**
     * Makes form and document IDs safe for Firebase paths.
     */
    private fun sanitizePathSegment(
        value: String
    ): String {
        return value.replace(
            regex = Regex("[^A-Za-z0-9_-]"),
            replacement = "_"
        )
    }
}