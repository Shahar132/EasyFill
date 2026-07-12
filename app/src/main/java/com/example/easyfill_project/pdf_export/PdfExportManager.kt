package com.example.easyfill_project.pdf_export

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfExportManager {

    fun createHousingAssistancePdf(
        context: Context,
        firebaseFields: Map<String, String>
    ): File {

        require(firebaseFields.isNotEmpty()) {
            "לא התקבלו שדות ליצירת PDF"
        }

        val locale = Locale.forLanguageTag("he-IL")
        val currentDate = Date()

        val creationDate = SimpleDateFormat(
            "dd-MM-yyyy HH:mm",
            locale
        ).format(currentDate)

        val fileDate = SimpleDateFormat(
            "dd-MM-yyyy_HH-mm",
            locale
        ).format(currentDate)

        val exportFolder = prepareExportFolder(context)

        deleteOldPdfFiles(exportFolder)

        val outputFile = File(
            exportFolder,
            "בקשה_לסיוע_בדיור_$fileDate.pdf"
        )

        try {
            FileOutputStream(outputFile).use { outputStream ->
                HousingAssistancePdfRenderer.render(
                    firebaseFields = firebaseFields,
                    creationDate = creationDate,
                    outputStream = outputStream
                )
            }
        } catch (exception: Exception) {
            outputFile.delete()
            throw exception
        }

        return outputFile
    }

    private fun prepareExportFolder(
        context: Context
    ): File {

        val exportFolder = File(
            context.cacheDir,
            "pdf_exports"
        )

        if (!exportFolder.exists()) {
            val created = exportFolder.mkdirs()

            if (!created && !exportFolder.exists()) {
                throw IllegalStateException(
                    "לא ניתן ליצור תיקייה עבור קובץ ה-PDF"
                )
            }
        }

        return exportFolder
    }

    private fun deleteOldPdfFiles(
        exportFolder: File
    ) {
        exportFolder
            .listFiles()
            ?.filter { file ->
                file.isFile &&
                        file.extension.equals(
                            other = "pdf",
                            ignoreCase = true
                        )
            }
            ?.forEach { oldPdf ->
                oldPdf.delete()
            }
    }
}