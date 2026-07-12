package com.example.easyfill_project.pdf_export

import android.content.Context
import android.graphics.Paint
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

        /*
         * מכינים מראש רק את החלקים והשדות שבאמת מכילים מידע.
         */
        val visibleSections = HousingAssistancePdfSchema.sections.mapNotNull { section ->

            val visibleFields = section.fields.filter { field ->
                val value = firebaseFields[field.firebaseKey].orEmpty()

                when (field.displayType) {
                    PdfFieldDisplayType.TEXT ->
                        value.isNotBlank()

                    PdfFieldDisplayType.SELECTED_OPTION ->
                        isSelectedOption(value)
                }
            }

            if (visibleFields.isEmpty()) {
                null
            } else {
                section to visibleFields
            }
        }

        require(visibleSections.isNotEmpty()) {
            "לא נמצאו שדות מתאימים ליצירת PDF"
        }

        val pdfDocument = PdfDocument()

        var pageNumber = 1
        var page = pdfDocument.startPage(
            createPageInfo(pageNumber)
        )

        var canvas = page.canvas
        var currentY = PAGE_MARGIN.toFloat()

        val availableWidth = PAGE_WIDTH - (PAGE_MARGIN * 2)
        val bottomLimit = PAGE_HEIGHT - PAGE_MARGIN
        val usablePageHeight = PAGE_HEIGHT - (PAGE_MARGIN * 2)

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

        val fieldLabelPaint = TextPaint().apply {
            textSize = 15f
            typeface = Typeface.create(
                Typeface.DEFAULT,
                Typeface.BOLD
            )
            isAntiAlias = true
        }

        val fieldValuePaint = TextPaint().apply {
            textSize = 16f
            typeface = Typeface.create(
                Typeface.DEFAULT,
                Typeface.NORMAL
            )
            isAntiAlias = true
        }

        val selectedOptionPaint = TextPaint().apply {
            textSize = 16f
            typeface = Typeface.create(
                Typeface.DEFAULT,
                Typeface.NORMAL
            )
            isAntiAlias = true
        }

        val dividerPaint = Paint().apply {
            strokeWidth = 1f
            alpha = 50
            isAntiAlias = true
        }

        fun containsHebrew(text: String): Boolean {
            return text.any { character ->
                character in '\u0590'..'\u05FF'
            }
        }

        fun createTextLayout(
            text: String,
            paint: TextPaint,
            isValue: Boolean = false
        ): StaticLayout {

            val isHebrewText = containsHebrew(text)

            val textDirection = if (isHebrewText) {
                TextDirectionHeuristics.RTL
            } else {
                TextDirectionHeuristics.LTR
            }

            val alignment = if (isHebrewText) {
                // תחילת שורת RTL היא הצד הימני
                Layout.Alignment.ALIGN_NORMAL
            } else {
                // סוף שורת LTR הוא הצד הימני
                Layout.Alignment.ALIGN_OPPOSITE
            }

            return StaticLayout.Builder
                .obtain(
                    text,
                    0,
                    text.length,
                    paint,
                    availableWidth
                )
                .setAlignment(alignment)
                .setTextDirection(textDirection)
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

        fun drawLayout(
            layout: StaticLayout,
            spacingAfter: Float
        ) {
            if (currentY + layout.height > bottomLimit) {
                startNewPage()
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

        fun estimateFieldHeight(
            field: PdfFieldDefinition
        ): Int {

            val value = firebaseFields[
                field.firebaseKey
            ].orEmpty()

            return when (field.displayType) {

                PdfFieldDisplayType.TEXT -> {
                    val labelLayout = createTextLayout(
                        text = field.displayName,
                        paint = fieldLabelPaint
                    )

                    val valueLayout = createTextLayout(
                        text = value,
                        paint = fieldValuePaint,
                        isValue = true
                    )

                    labelLayout.height +
                            valueLayout.height +
                            18
                }

                PdfFieldDisplayType.SELECTED_OPTION -> {
                    val selectedLayout = createTextLayout(
                        text = "• ${field.displayName}",
                        paint = selectedOptionPaint
                    )

                    selectedLayout.height + 14
                }
            }
        }

        try {
            val documentTitleLayout = createTextLayout(
                text = "טופס בקשה לסיוע בדיור",
                paint = documentTitlePaint
            )

            drawLayout(
                layout = documentTitleLayout,
                spacingAfter = 32f
            )

            visibleSections.forEach { (section, visibleFields) ->

                val sectionTitleLayout = createTextLayout(
                    text = section.title,
                    paint = sectionTitlePaint
                )

                val fieldsHeight = visibleFields.sumOf { field ->
                    estimateFieldHeight(field)
                }

                val estimatedSectionHeight =
                    sectionTitleLayout.height +
                            fieldsHeight +
                            42

                val firstFieldHeight = visibleFields
                    .firstOrNull()
                    ?.let(::estimateFieldHeight)
                    ?: 0

                /*
                 * אם כל החלק יכול להיכנס בעמוד אחד,
                 * אבל אין לו מקום בעמוד הנוכחי —
                 * מעבירים את כולו לעמוד הבא.
                 */
                if (
                    estimatedSectionHeight <= usablePageHeight &&
                    currentY + estimatedSectionHeight > bottomLimit
                ) {
                    startNewPage()
                }

                /*
                 * אם החלק גדול מעמוד שלם,
                 * לפחות לא משאירים כותרת לבדה בתחתית העמוד.
                 */
                if (
                    currentY +
                    sectionTitleLayout.height +
                    firstFieldHeight +
                    24 > bottomLimit
                ) {
                    startNewPage()
                }

                drawLayout(
                    layout = sectionTitleLayout,
                    spacingAfter = 8f
                )

                canvas.drawLine(
                    PAGE_MARGIN.toFloat(),
                    currentY,
                    (PAGE_WIDTH - PAGE_MARGIN).toFloat(),
                    currentY,
                    dividerPaint
                )

                currentY += 14f

                visibleFields.forEach { field ->

                    val value = firebaseFields[
                        field.firebaseKey
                    ].orEmpty()

                    when (field.displayType) {

                        PdfFieldDisplayType.TEXT -> {

                            val labelLayout = createTextLayout(
                                text = field.displayName,
                                paint = fieldLabelPaint
                            )

                            val valueLayout = createTextLayout(
                                text = value,
                                paint = fieldValuePaint,
                                isValue = true
                            )

                            drawLayout(
                                layout = labelLayout,
                                spacingAfter = 2f
                            )

                            drawLayout(
                                layout = valueLayout,
                                spacingAfter = 14f
                            )
                        }

                        PdfFieldDisplayType.SELECTED_OPTION -> {

                            val selectedLayout = createTextLayout(
                                text = "• ${field.displayName}",
                                paint = selectedOptionPaint
                            )

                            drawLayout(
                                layout = selectedLayout,
                                spacingAfter = 12f
                            )
                        }
                    }
                }

                currentY += 18f
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