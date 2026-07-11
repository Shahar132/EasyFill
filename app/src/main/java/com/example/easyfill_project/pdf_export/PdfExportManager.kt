package com.example.easyfill_project.pdf_export

import android.content.Context
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import java.io.File
import java.io.FileOutputStream

object PdfExportManager {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val PAGE_MARGIN = 48

    fun createHousingAssistancePdf(
        context: Context,
        firebaseFields: Map<String, String>
    ): File {

        require(firebaseFields.isNotEmpty()) {
            "לא התקבלו שדות ליצירת PDF"
        }

        val pdfDocument = PdfDocument()

        var pageNumber = 1

        var page = pdfDocument.startPage(
            createPageInfo(pageNumber)
        )

        var canvas = page.canvas
        var currentY = PAGE_MARGIN.toFloat()

        val availableWidth = PAGE_WIDTH - (PAGE_MARGIN * 2)

        val documentTitlePaint = TextPaint().apply {
            textSize = 24f
            typeface = Typeface.create(
                Typeface.DEFAULT,
                Typeface.BOLD
            )
            isAntiAlias = true
        }

        val sectionTitlePaint = TextPaint().apply {
            textSize = 20f
            typeface = Typeface.create(
                Typeface.DEFAULT,
                Typeface.BOLD
            )
            isAntiAlias = true
        }

        val bodyPaint = TextPaint().apply {
            textSize = 16f
            typeface = Typeface.create(
                Typeface.DEFAULT,
                Typeface.NORMAL
            )
            isAntiAlias = true
        }

        fun createTextLayout(
            text: String,
            paint: TextPaint,
            alignment: Layout.Alignment
        ): StaticLayout {
            return StaticLayout.Builder
                .obtain(
                    text,
                    0,
                    text.length,
                    paint,
                    availableWidth
                )
                .setAlignment(alignment)
                .setTextDirection(TextDirectionHeuristics.RTL)
                .setIncludePad(false)
                .setLineSpacing(4f, 1f)
                .build()
        }

        fun startNewPage() {
            pdfDocument.finishPage(page)

            pageNumber++

            page = pdfDocument.startPage(
                createPageInfo(pageNumber)
            )

            canvas = page.canvas
            currentY = PAGE_MARGIN.toFloat()
        }

        fun drawTextBlock(
            text: String,
            paint: TextPaint,
            spacingAfter: Float,
            alignment: Layout.Alignment =
                Layout.Alignment.ALIGN_OPPOSITE
        ) {
            var layout = createTextLayout(
                text = text,
                paint = paint,
                alignment = alignment
            )

            val bottomLimit = PAGE_HEIGHT - PAGE_MARGIN

            if (currentY + layout.height > bottomLimit) {
                startNewPage()

                layout = createTextLayout(
                    text = text,
                    paint = paint,
                    alignment = alignment
                )
            }

            canvas.save()

            canvas.translate(
                PAGE_MARGIN.toFloat(),
                currentY
            )

            layout.draw(canvas)

            canvas.restore()

            currentY += layout.height + spacingAfter
        }

        try {
            drawTextBlock(
                text = "טופס בקשה לסיוע בדיור",
                paint = documentTitlePaint,
                spacingAfter = 32f,
                alignment = Layout.Alignment.ALIGN_CENTER
            )

            HousingAssistancePdfSchema.sections.forEach { section ->

                val visibleFields = section.fields.filter { field ->
                    val value = firebaseFields[
                        field.firebaseKey
                    ].orEmpty()

                    when (field.displayType) {
                        PdfFieldDisplayType.TEXT ->
                            value.isNotBlank()

                        PdfFieldDisplayType.SELECTED_OPTION ->
                            isSelectedOption(value)
                    }
                }

                if (visibleFields.isNotEmpty()) {

                    drawTextBlock(
                        text = section.title,
                        paint = sectionTitlePaint,
                        spacingAfter = 16f
                    )

                    visibleFields.forEach { field ->

                        val value = firebaseFields[
                            field.firebaseKey
                        ].orEmpty()

                        val textToDraw = when (
                            field.displayType
                        ) {
                            PdfFieldDisplayType.TEXT -> {
                                "${field.displayName}: $value"
                            }

                            PdfFieldDisplayType.SELECTED_OPTION -> {
                                "• ${field.displayName}"
                            }
                        }

                        drawTextBlock(
                            text = textToDraw,
                            paint = bodyPaint,
                            spacingAfter = 12f
                        )
                    }

                    currentY += 16f
                }
            }

            pdfDocument.finishPage(page)

            val exportFolder = File(
                context.cacheDir,
                "pdf_exports"
            )

            if (!exportFolder.exists()) {
                val folderCreated = exportFolder.mkdirs()

                if (!folderCreated && !exportFolder.exists()) {
                    throw IllegalStateException(
                        "לא ניתן ליצור תיקייה עבור קובץ ה-PDF"
                    )
                }
            }

            val outputFile = File(
                exportFolder,
                "housing_assistance_${System.currentTimeMillis()}.pdf"
            )

            FileOutputStream(outputFile).use { outputStream ->
                pdfDocument.writeTo(outputStream)
            }

            return outputFile

        } finally {
            pdfDocument.close()
        }
    }

    private fun createPageInfo(
        pageNumber: Int
    ): PdfDocument.PageInfo {
        return PdfDocument.PageInfo.Builder(
            PAGE_WIDTH,
            PAGE_HEIGHT,
            pageNumber
        ).create()
    }

    private fun isSelectedOption(
        value: String
    ): Boolean {
        return value.isNotBlank() &&
                !value.equals(
                    other = "false",
                    ignoreCase = true
                )
    }
}