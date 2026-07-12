package com.example.easyfill_project.pdf_export

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import java.io.OutputStream

internal object HousingAssistancePdfRenderer {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val PAGE_MARGIN = 48

    private const val CONTENT_TOP = 62f
    private const val CONTENT_BOTTOM = 785f

    private sealed interface DrawCommand

    private data class TextCommand(
        val layout: StaticLayout,
        val y: Float
    ) : DrawCommand

    private data class DividerCommand(
        val y: Float
    ) : DrawCommand

    private data class SectionHeaderCommand(
        val layout: StaticLayout,
        val y: Float,
        val height: Float
    ) : DrawCommand

    private data class PagePlan(
        val commands: MutableList<DrawCommand> = mutableListOf(),
        var currentY: Float = CONTENT_TOP
    )

    fun render(
        firebaseFields: Map<String, String>,
        creationDate: String,
        outputStream: OutputStream
    ) {
        val availableWidth =
            PAGE_WIDTH - (PAGE_MARGIN * 2)

        val fullContentHeight =
            CONTENT_BOTTOM - CONTENT_TOP

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

        val notePaint = TextPaint().apply {
            textSize = 13f
            typeface = Typeface.create(
                Typeface.DEFAULT,
                Typeface.NORMAL
            )
            isAntiAlias = true
            alpha = 180
        }

        val headerPaint = TextPaint().apply {
            textSize = 12f
            typeface = Typeface.create(
                Typeface.DEFAULT,
                Typeface.BOLD
            )
            isAntiAlias = true
            alpha = 180
        }

        val footerPaint = TextPaint().apply {
            textSize = 11f
            typeface = Typeface.create(
                Typeface.DEFAULT,
                Typeface.NORMAL
            )
            isAntiAlias = true
            alpha = 180
        }

        fun containsHebrew(text: String): Boolean {
            return text.any { character ->
                character in '\u0590'..'\u05FF'
            }
        }

        fun createTextLayout(
            text: String,
            paint: TextPaint,
            width: Int = availableWidth
        ): StaticLayout {

            val hasHebrew = containsHebrew(text)

            val direction = if (hasHebrew) {
                TextDirectionHeuristics.RTL
            } else {
                TextDirectionHeuristics.LTR
            }

            val alignment = if (hasHebrew) {
                Layout.Alignment.ALIGN_NORMAL
            } else {
                Layout.Alignment.ALIGN_OPPOSITE
            }

            return StaticLayout.Builder
                .obtain(
                    text,
                    0,
                    text.length,
                    paint,
                    width
                )
                .setAlignment(alignment)
                .setTextDirection(direction)
                .setIncludePad(false)
                .setLineSpacing(4f, 1f)
                .build()
        }

        val visibleSections =
            HousingAssistancePdfSchema.sections.mapNotNull { section ->

                val conditionalKey =
                    section.showWhenSelectedKey

                if (
                    conditionalKey != null &&
                    !isSelectedOption(
                        firebaseFields[conditionalKey].orEmpty()
                    )
                ) {
                    return@mapNotNull null
                }

                val visibleFields =
                    section.fields.filter { field ->

                        val value =
                            firebaseFields[field.firebaseKey]
                                .orEmpty()

                        when (field.displayType) {

                            PdfFieldDisplayType.TEXT -> {
                                value.isNotBlank() ||
                                        field.isRequired
                            }

                            PdfFieldDisplayType.SELECTED_OPTION -> {
                                isSelectedOption(value)
                            }
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

        val pages = mutableListOf(PagePlan())
        var currentPage = pages.last()

        fun startNewPage() {
            currentPage = PagePlan()
            pages.add(currentPage)
        }

        fun remainingHeight(): Float {
            return CONTENT_BOTTOM - currentPage.currentY
        }

        fun ensureSpace(requiredHeight: Float) {
            if (remainingHeight() < requiredHeight) {
                startNewPage()
            }
        }

        fun addFixedLayout(
            layout: StaticLayout,
            spacingAfter: Float
        ) {
            ensureSpace(layout.height.toFloat())

            currentPage.commands.add(
                TextCommand(
                    layout = layout,
                    y = currentPage.currentY
                )
            )

            currentPage.currentY +=
                layout.height + spacingAfter
        }

        fun addFlowingText(
            originalText: String,
            paint: TextPaint,
            spacingAfter: Float
        ) {
            var remainingText = originalText

            while (remainingText.isNotEmpty()) {

                val layout = createTextLayout(
                    text = remainingText,
                    paint = paint
                )

                if (layout.height <= remainingHeight()) {

                    currentPage.commands.add(
                        TextCommand(
                            layout = layout,
                            y = currentPage.currentY
                        )
                    )

                    currentPage.currentY +=
                        layout.height + spacingAfter

                    remainingText = ""
                    continue
                }

                if (
                    remainingHeight() <
                    paint.textSize + 8f
                ) {
                    startNewPage()
                    continue
                }

                var fittingLineCount = 0

                for (
                lineIndex in 0 until layout.lineCount
                ) {
                    if (
                        layout
                            .getLineBottom(lineIndex)
                            .toFloat() <= remainingHeight()
                    ) {
                        fittingLineCount++
                    } else {
                        break
                    }
                }

                if (fittingLineCount == 0) {
                    startNewPage()
                    continue
                }

                val endIndex = layout.getLineEnd(
                    fittingLineCount - 1
                )

                val pageText = remainingText
                    .substring(0, endIndex)
                    .trimEnd()

                if (pageText.isEmpty()) {
                    startNewPage()
                    continue
                }

                val pageLayout = createTextLayout(
                    text = pageText,
                    paint = paint
                )

                currentPage.commands.add(
                    TextCommand(
                        layout = pageLayout,
                        y = currentPage.currentY
                    )
                )

                currentPage.currentY +=
                    pageLayout.height

                remainingText = remainingText
                    .substring(endIndex)
                    .trimStart()

                if (remainingText.isNotEmpty()) {
                    startNewPage()
                }
            }
        }

        fun addDivider(
            spacingAfter: Float = 12f
        ) {
            if (remainingHeight() >= 2f) {

                currentPage.commands.add(
                    DividerCommand(
                        y = currentPage.currentY
                    )
                )

                currentPage.currentY += spacingAfter
            }
        }

        fun estimateFieldHeight(
            field: PdfFieldDefinition
        ): Float {

            val storedValue =
                firebaseFields[field.firebaseKey]
                    .orEmpty()

            return when (field.displayType) {

                PdfFieldDisplayType.TEXT -> {

                    val displayedValue =
                        if (
                            storedValue.isBlank() &&
                            field.isRequired
                        ) {
                            "לא מולא"
                        } else {
                            storedValue
                        }

                    val labelLayout = createTextLayout(
                        text = field.displayName,
                        paint = fieldLabelPaint
                    )

                    val valueLayout = createTextLayout(
                        text = displayedValue,
                        paint = fieldValuePaint
                    )

                    labelLayout.height +
                            valueLayout.height +
                            30f
                }

                PdfFieldDisplayType.SELECTED_OPTION -> {

                    val optionLayout = createTextLayout(
                        text = "• ${field.displayName}",
                        paint = fieldValuePaint
                    )

                    optionLayout.height + 20f
                }
            }
        }

        val documentTitleLayout =
            createTextLayout(
                text = "טופס בקשה לסיוע בדיור",
                paint = documentTitlePaint
            )

        addFixedLayout(
            layout = documentTitleLayout,
            spacingAfter = 12f
        )

        val noteLayout = createTextLayout(
            text = "המסמך מציג שדות שמולאו בלבד. " +
                    "שדות חובה חסרים מסומנים כ״לא מולא״.",
            paint = notePaint
        )

        addFixedLayout(
            layout = noteLayout,
            spacingAfter = 28f
        )

        visibleSections.forEach {
                (section, visibleFields) ->

            val sectionTitleLayout =
                createTextLayout(
                    text = section.title,
                    paint = sectionTitlePaint,
                    width = availableWidth - 24
                )

            val sectionHeaderHeight =
                sectionTitleLayout.height + 22f

            val estimatedFieldsHeight =
                visibleFields.sumOf { field ->
                    estimateFieldHeight(field)
                        .toDouble()
                }.toFloat()

            val estimatedSectionHeight =
                sectionHeaderHeight +
                        estimatedFieldsHeight +
                        36f

            if (
                estimatedSectionHeight <=
                fullContentHeight &&
                estimatedSectionHeight >
                remainingHeight()
            ) {
                startNewPage()
            }

            val firstFieldHeight =
                visibleFields
                    .firstOrNull()
                    ?.let(::estimateFieldHeight)
                    ?: 0f

            ensureSpace(
                sectionHeaderHeight +
                        firstFieldHeight +
                        20f
            )

            currentPage.commands.add(
                SectionHeaderCommand(
                    layout = sectionTitleLayout,
                    y = currentPage.currentY,
                    height = sectionHeaderHeight
                )
            )

            currentPage.currentY +=
                sectionHeaderHeight + 18f

            visibleFields.forEach { field ->

                val storedValue =
                    firebaseFields[field.firebaseKey]
                        .orEmpty()

                when (field.displayType) {

                    PdfFieldDisplayType.TEXT -> {

                        val displayedValue =
                            if (
                                storedValue.isBlank() &&
                                field.isRequired
                            ) {
                                "לא מולא"
                            } else {
                                storedValue
                            }

                        val labelLayout =
                            createTextLayout(
                                text = field.displayName,
                                paint = fieldLabelPaint
                            )

                        val valueLayout =
                            createTextLayout(
                                text = displayedValue,
                                paint = fieldValuePaint
                            )

                        val firstValueLineHeight =
                            if (valueLayout.lineCount > 0) {
                                valueLayout
                                    .getLineBottom(0)
                                    .toFloat()
                            } else {
                                valueLayout.height.toFloat()
                            }

                        ensureSpace(
                            labelLayout.height +
                                    firstValueLineHeight +
                                    10f
                        )

                        addFixedLayout(
                            layout = labelLayout,
                            spacingAfter = 3f
                        )

                        addFlowingText(
                            originalText = displayedValue,
                            paint = fieldValuePaint,
                            spacingAfter = 10f
                        )

                        addDivider()
                    }

                    PdfFieldDisplayType.SELECTED_OPTION -> {

                        val optionLayout =
                            createTextLayout(
                                text = "• ${field.displayName}",
                                paint = fieldValuePaint
                            )

                        addFixedLayout(
                            layout = optionLayout,
                            spacingAfter = 10f
                        )

                        addDivider()
                    }
                }
            }

            currentPage.currentY += 14f
        }

        drawPdf(
            pages = pages,
            creationDate = creationDate,
            outputStream = outputStream,
            createTextLayout = ::createTextLayout,
            headerPaint = headerPaint,
            footerPaint = footerPaint
        )
    }

    private fun drawPdf(
        pages: List<PagePlan>,
        creationDate: String,
        outputStream: OutputStream,
        createTextLayout: (
            text: String,
            paint: TextPaint,
            width: Int
        ) -> StaticLayout,
        headerPaint: TextPaint,
        footerPaint: TextPaint
    ) {
        val pdfDocument = PdfDocument()

        val dividerPaint = Paint().apply {
            strokeWidth = 1f
            alpha = 45
            isAntiAlias = true
        }

        val sectionBackgroundPaint = Paint().apply {
            color = Color.rgb(
                238,
                242,
                246
            )
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val sectionAccentPaint = Paint().apply {
            color = Color.rgb(
                75,
                95,
                115
            )
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val availableWidth =
            PAGE_WIDTH - (PAGE_MARGIN * 2)

        try {
            val totalPages = pages.size

            pages.forEachIndexed { index, pagePlan ->

                val pageNumber = index + 1

                val pageInfo =
                    PdfDocument.PageInfo.Builder(
                        PAGE_WIDTH,
                        PAGE_HEIGHT,
                        pageNumber
                    ).create()

                val page =
                    pdfDocument.startPage(pageInfo)

                val canvas = page.canvas

                val headerLayout =
                    createTextLayout(
                        "EasyFill — בקשה לסיוע בדיור",
                        headerPaint,
                        availableWidth
                    )

                canvas.save()

                canvas.translate(
                    PAGE_MARGIN.toFloat(),
                    24f
                )

                headerLayout.draw(canvas)

                canvas.restore()

                pagePlan.commands.forEach { command ->

                    when (command) {

                        is TextCommand -> {

                            canvas.save()

                            canvas.translate(
                                PAGE_MARGIN.toFloat(),
                                command.y
                            )

                            command.layout.draw(canvas)

                            canvas.restore()
                        }

                        is DividerCommand -> {

                            canvas.drawLine(
                                PAGE_MARGIN.toFloat(),
                                command.y,
                                (
                                        PAGE_WIDTH -
                                                PAGE_MARGIN
                                        ).toFloat(),
                                command.y,
                                dividerPaint
                            )
                        }

                        is SectionHeaderCommand -> {

                            val left =
                                PAGE_MARGIN.toFloat()

                            val right =
                                (
                                        PAGE_WIDTH -
                                                PAGE_MARGIN
                                        ).toFloat()

                            val top = command.y

                            val bottom =
                                command.y +
                                        command.height

                            canvas.drawRoundRect(
                                left,
                                top,
                                right,
                                bottom,
                                10f,
                                10f,
                                sectionBackgroundPaint
                            )

                            canvas.drawRect(
                                right - 6f,
                                top,
                                right,
                                bottom,
                                sectionAccentPaint
                            )

                            val textY =
                                top +
                                        (
                                                command.height -
                                                        command.layout.height
                                                ) / 2f

                            canvas.save()

                            canvas.translate(
                                left,
                                textY
                            )

                            command.layout.draw(canvas)

                            canvas.restore()
                        }
                    }
                }

                val footerText =
                    "עמוד $pageNumber מתוך $totalPages" +
                            "  |  נוצר בתאריך $creationDate"

                val footerLayout =
                    createTextLayout(
                        footerText,
                        footerPaint,
                        availableWidth
                    )

                canvas.save()

                canvas.translate(
                    PAGE_MARGIN.toFloat(),
                    808f
                )

                footerLayout.draw(canvas)

                canvas.restore()

                pdfDocument.finishPage(page)
            }

            pdfDocument.writeTo(outputStream)

        } finally {
            pdfDocument.close()
        }
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