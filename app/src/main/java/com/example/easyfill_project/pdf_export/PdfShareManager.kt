package com.example.easyfill_project.pdf_export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object PdfShareManager {

    fun openPdf(
        context: Context,
        pdfFile: File
    ) {
        val pdfUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )

        val openIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(
                pdfUri,
                "application/pdf"
            )

            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val chooserIntent = Intent.createChooser(
            openIntent,
            "פתיחת קובץ PDF"
        )

        context.startActivity(chooserIntent)
    }
}