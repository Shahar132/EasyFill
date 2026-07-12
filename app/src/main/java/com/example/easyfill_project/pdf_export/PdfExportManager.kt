package com.example.easyfill_project.pdf_export

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfExportManager {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val PAGE_MARGIN = 48

    /*
     * משאירים מקום קבוע לכותרת העליונה
     * ולמספר העמוד בתחתית.
     */
    private const val CONTENT_TOP = 62f
    private const val CONTENT_BOTTOM = 785f

    /*
     * פקודות הציור נשמרות קודם בזיכרון.
     * לאחר שאנחנו יודעים כמה עמודים נוצרו,
     * מציירים את ה-PDF בפועל.
     */
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

    fun createHousingAssistancePdf(
        context: Context,
        firebaseFields: Map<String, String>
    ): File {

        require(firebaseFields.isNotEmpty()) {
            "לא התקבלו שדות ליצירת PDF"
        }

        val locale = Locale.forLanguageTag("he-IL")

        val creationDate = SimpleDateFormat(
            "dd-MM-yyyy HH:mm",
            locale
        ).format(Date())

        val fileDate = SimpleDateFormat(
            "dd-MM-yyyy_HH-mm",
            locale
        ).format(Date())

        val availableWidth =
            PAGE_WIDTH - (PAGE_MARGIN * 2)

        val fullContentHeight =
            CONTENT_BOTTOM - CONTENT_TOP

        /*
         * עיצוב הכותרת הראשית.
         */
        val documentTitlePaint = TextPaint().apply {
            textSize = 24f
            typeface = Typeface.create(
                Typeface.DEFAULT,
                Typeface.BOLD
            )
            isAntiAlias = true
        }

        /*
         * עיצוב כותרות החלקים:
         * פרטים אישיים, כתובת, הכנסות וכדומה.
         */
        val sectionTitlePaint = TextPaint().apply {
            textSize = 20f
            typeface = Typeface.create(
                Typeface.DEFAULT,
                Typeface.BOLD
            )
            isAntiAlias = true
        }

        /*
         * שם השדה, לדוגמה: שם פרטי.
         */
        val fieldLabelPaint = TextPaint().apply {
            textSize = 15f
            typeface = Typeface.create(
                Typeface.DEFAULT,
                Typeface.BOLD
            )
            isAntiAlias = true
        }

        /*
         * הערך של השדה.
         */
        val fieldValuePaint = TextPaint().apply {
            textSize = 16f
            typeface = Typeface.create(
                Typeface.DEFAULT,
                Typeface.NORMAL
            )
            isAntiAlias = true
        }

        /*
         * ההודעה הקטנה בראש המסמך.
         */
        val notePaint = TextPaint().apply {
            textSize = 13f
            typeface = Typeface.create(
                Typeface.DEFAULT,
                Typeface.NORMAL
            )
            isAntiAlias = true
            alpha = 180
        }

        /*
         * הכותרת העליונה שמופיעה בכל עמוד.
         */
        val headerPaint = TextPaint().apply {
            textSize = 12f
            typeface = Typeface.create(
                Typeface.DEFAULT,
                Typeface.BOLD
            )
            isAntiAlias = true
            alpha = 180
        }

        /*
         * מספר העמוד ותאריך היצירה בתחתית.
         */
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

        /*
         * יוצר פריסת טקסט:
         *
         * עברית מוצגת בכיוון RTL.
         * מספרים, מיילים ואנגלית מוצגים בכיוון LTR.
         * כל הטקסט נשאר מיושר לצד ימין.
         */
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

        /*
         * מסננים מראש חלקים ושדות שלא צריכים להופיע.
         */
        val visibleSections =
            HousingAssistancePdfSchema.sections.mapNotNull { section ->

                val conditionalKey =
                    section.showWhenSelectedKey

                /*
                 * לדוגמה:
                 * פרטי דירה בשכירות יוצגו רק כאשר
                 * המשתמש בחר סיוע בשכר דירה.
                 */
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

        /*
         * תכנון העמודים לפני הציור.
         */
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

        /*
         * מאפשר לטקסט ארוך לעבור לעמוד הבא
         * בלי להיחתך.
         */
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

                /*
                 * כל הטקסט נכנס בשטח שנותר.
                 */
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

                /*
                 * אין מספיק מקום אפילו לשורה אחת.
                 */
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

        /*
         * הערכת הגובה של שדה לפני הציור.
         */
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

        /*
         * הכותרת הראשית.
         */
        val documentTitleLayout =
            createTextLayout(
                text = "טופס בקשה לסיוע בדיור",
                paint = documentTitlePaint
            )

        addFixedLayout(
            layout = documentTitleLayout,
            spacingAfter = 12f
        )

        /*
         * הסבר קצר על תוכן הקובץ.
         */
        val noteLayout = createTextLayout(
            text = "המסמך מציג שדות שמולאו בלבד. " +
                    "שדות חובה חסרים מסומנים כ״לא מולא״.",
            paint = notePaint
        )

        addFixedLayout(
            layout = noteLayout,
            spacingAfter = 28f
        )

        /*
         * מעבר על כל חלקי הטופס.
         */
        visibleSections.forEach {
                (section, visibleFields) ->

            val sectionTitleLayout =
                createTextLayout(
                    text = section.title,
                    paint = sectionTitlePaint,
                    width = availableWidth - 24
                )

            /*
             * הגובה הכולל של פס כותרת החלק.
             */
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

            /*
             * אם כל החלק יכול להיכנס בעמוד אחד,
             * אבל אין מספיק מקום בעמוד הנוכחי,
             * מעבירים את כולו לעמוד הבא.
             */
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

            /*
             * לא משאירים את כותרת החלק לבד
             * בתחתית העמוד.
             */
            ensureSpace(
                sectionHeaderHeight +
                        firstFieldHeight +
                        20f
            )

            /*
             * מוסיפים פקודה מיוחדת לציור
             * כותרת החלק עם רקע.
             */
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

                        /*
                         * שם השדה ולפחות השורה הראשונה
                         * של הערך נשארים באותו עמוד.
                         */
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

        /*
         * עכשיו מציירים בפועל את כל העמודים.
         */
        val pdfDocument = PdfDocument()

        val dividerPaint = Paint().apply {
            strokeWidth = 1f
            alpha = 45
            isAntiAlias = true
        }

        /*
         * רקע עדין לכותרות החלקים.
         */
        val sectionBackgroundPaint = Paint().apply {
            color = Color.rgb(
                238,
                242,
                246
            )
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        /*
         * פס הדגשה בצד ימין של כותרת החלק.
         */
        val sectionAccentPaint = Paint().apply {
            color = Color.rgb(
                75,
                95,
                115
            )
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        try {
            val totalPages = pages.size

            pages.forEachIndexed {
                    index,
                    pagePlan ->

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

                /*
                 * כותרת קבועה בראש כל עמוד.
                 */
                val headerLayout =
                    createTextLayout(
                        text = "EasyFill — בקשה לסיוע בדיור",
                        paint = headerPaint
                    )

                canvas.save()

                canvas.translate(
                    PAGE_MARGIN.toFloat(),
                    24f
                )

                headerLayout.draw(canvas)

                canvas.restore()

                /*
                 * ציור כל הפקודות שתוכננו לעמוד.
                 */
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

                            /*
                             * מלבן מעוגל עם רקע עדין.
                             */
                            canvas.drawRoundRect(
                                left,
                                top,
                                right,
                                bottom,
                                10f,
                                10f,
                                sectionBackgroundPaint
                            )

                            /*
                             * פס אנכי בצד ימין.
                             */
                            canvas.drawRect(
                                right - 6f,
                                top,
                                right,
                                bottom,
                                sectionAccentPaint
                            )

                            /*
                             * ממרכזים את הטקסט
                             * בתוך פס הכותרת.
                             */
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

                /*
                 * מספר העמוד ותאריך יצירה.
                 */
                val footerText =
                    "עמוד $pageNumber מתוך $totalPages" +
                            "  |  נוצר בתאריך $creationDate"

                val footerLayout =
                    createTextLayout(
                        text = footerText,
                        paint = footerPaint
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

            /*
             * יצירת תיקיית הקבצים הזמניים.
             */
            val exportFolder = File(
                context.cacheDir,
                "pdf_exports"
            )

            if (!exportFolder.exists()) {

                val created =
                    exportFolder.mkdirs()

                if (
                    !created &&
                    !exportFolder.exists()
                ) {
                    throw IllegalStateException(
                        "לא ניתן ליצור תיקייה " +
                                "עבור קובץ ה-PDF"
                    )
                }
            }

            /*
             * מחיקת קובצי PDF זמניים קודמים.
             */
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

            /*
             * שם ברור לקובץ החדש.
             */
            val outputFile = File(
                exportFolder,
                "בקשה_לסיוע_בדיור_$fileDate.pdf"
            )

            FileOutputStream(outputFile).use {
                    outputStream ->

                pdfDocument.writeTo(outputStream)
            }

            return outputFile

        } finally {
            pdfDocument.close()
        }
    }

    /*
     * בודק האם אפשרות Checkbox נבחרה.
     */
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