package com.example.easyfill_project.pdf_export

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.roundToInt

data class PdfAttachment(
    val documentId: String,
    val title: String,
    val uriString: String,
    val fileName: String,
    val mimeType: String?
)

object PdfAttachmentMerger {

    private const val PORTRAIT_PAGE_WIDTH = 595
    private const val PORTRAIT_PAGE_HEIGHT = 842

    private const val PAGE_MARGIN = 24f
    private const val MAX_IMAGE_DIMENSION = 2400

    /**
     * Creates one PDF containing the completed form followed by
     * all selected PDF and image attachments.
     */
    fun createMergedPdf(
        context: Context,
        formPdfFile: File,
        attachments: List<PdfAttachment>
    ): File {
        if (attachments.isEmpty()) {
            return formPdfFile
        }

        val outputDirectory = File(
            context.cacheDir,
            "pdf_exports"
        ).apply {
            mkdirs()
        }

        val outputFile = File(
            outputDirectory,
            "EasyFill_with_attachments_" +
                    "${System.currentTimeMillis()}.pdf"
        )

        val outputDocument = PdfDocument()
        var nextPageNumber = 1

        try {
            nextPageNumber = appendPdfFile(
                outputDocument = outputDocument,
                pdfFile = formPdfFile,
                firstPageNumber = nextPageNumber
            )

            nextPageNumber =
                appendAttachmentsTitlePage(
                    outputDocument = outputDocument,
                    pageNumber = nextPageNumber
                )

            attachments.forEach { attachment ->
                val attachmentUri =
                    Uri.parse(
                        attachment.uriString
                    )

                val isPdf =
                    attachment.mimeType.equals(
                        other = "application/pdf",
                        ignoreCase = true
                    ) ||
                            attachment.fileName.endsWith(
                                suffix = ".pdf",
                                ignoreCase = true
                            )

                val isImage: Boolean =
                    attachment.mimeType
                        ?.startsWith("image/")
                        ?: false

                nextPageNumber = when {
                    isPdf -> {
                        appendPdfUri(
                            context = context,
                            outputDocument =
                                outputDocument,
                            pdfUri = attachmentUri,
                            firstPageNumber =
                                nextPageNumber,
                            documentTitle =
                                attachment.title
                        )
                    }

                    isImage -> {
                        appendImageUri(
                            context = context,
                            outputDocument =
                                outputDocument,
                            imageUri = attachmentUri,
                            pageNumber =
                                nextPageNumber,
                            documentTitle =
                                attachment.title
                        )
                    }

                    else -> {
                        throw IllegalArgumentException(
                            "סוג הקובץ של " +
                                    "${attachment.fileName} " +
                                    "אינו נתמך"
                        )
                    }
                }
            }

            FileOutputStream(outputFile).use {
                    outputStream ->

                outputDocument.writeTo(
                    outputStream
                )
            }

            return outputFile

        } catch (exception: Exception) {
            outputFile.delete()
            throw exception

        } finally {
            outputDocument.close()
        }
    }

    /**
     * Adds the main attachments title page.
     */
    private fun appendAttachmentsTitlePage(
        outputDocument: PdfDocument,
        pageNumber: Int
    ): Int {
        val pageInfo =
            PdfDocument.PageInfo.Builder(
                PORTRAIT_PAGE_WIDTH,
                PORTRAIT_PAGE_HEIGHT,
                pageNumber
            ).create()

        val page =
            outputDocument.startPage(
                pageInfo
            )

        page.canvas.drawColor(Color.WHITE)

        val titlePaint = TextPaint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {
            color = Color.BLACK
            textSize = 34f
            typeface =
                Typeface.create(
                    Typeface.DEFAULT,
                    Typeface.BOLD
                )
        }

        val availableWidth =
            (
                    PORTRAIT_PAGE_WIDTH -
                            PAGE_MARGIN * 2
                    ).roundToInt()

        val titleLayout =
            StaticLayout.Builder.obtain(
                "מסמכים מצורפים",
                0,
                "מסמכים מצורפים".length,
                titlePaint,
                availableWidth
            )
                .setAlignment(
                    Layout.Alignment.ALIGN_CENTER
                )
                .setIncludePad(false)
                .build()

        val titleTop =
            (
                    PORTRAIT_PAGE_HEIGHT -
                            titleLayout.height
                    ) / 2f

        page.canvas.save()

        page.canvas.translate(
            PAGE_MARGIN,
            titleTop
        )

        titleLayout.draw(page.canvas)

        page.canvas.restore()

        outputDocument.finishPage(page)

        return pageNumber + 1
    }

    /**
     * Adds every page from a local PDF file.
     */
    private fun appendPdfFile(
        outputDocument: PdfDocument,
        pdfFile: File,
        firstPageNumber: Int
    ): Int {
        ParcelFileDescriptor.open(
            pdfFile,
            ParcelFileDescriptor.MODE_READ_ONLY
        ).use { fileDescriptor ->

            PdfRenderer(fileDescriptor).use {
                    renderer ->

                return appendRendererPages(
                    outputDocument =
                        outputDocument,

                    renderer = renderer,

                    firstPageNumber =
                        firstPageNumber,

                    documentTitle = null
                )
            }
        }
    }

    /**
     * Adds every page from a downloaded or selected PDF.
     */
    private fun appendPdfUri(
        context: Context,
        outputDocument: PdfDocument,
        pdfUri: Uri,
        firstPageNumber: Int,
        documentTitle: String
    ): Int {
        openPdfFileDescriptor(
            context = context,
            uri = pdfUri
        ).use { fileDescriptor ->

            PdfRenderer(fileDescriptor).use {
                    renderer ->

                return appendRendererPages(
                    outputDocument =
                        outputDocument,

                    renderer = renderer,

                    firstPageNumber =
                        firstPageNumber,

                    documentTitle =
                        documentTitle
                )
            }
        }
    }

    /**
     * Renders each source PDF page into the output PDF.
     */
    private fun appendRendererPages(
        outputDocument: PdfDocument,
        renderer: PdfRenderer,
        firstPageNumber: Int,
        documentTitle: String?
    ): Int {
        var pageNumber =
            firstPageNumber

        for (
        index in 0 until renderer.pageCount
        ) {
            renderer.openPage(index).use {
                    sourcePage ->

                val longestSide =
                    maxOf(
                        sourcePage.width,
                        sourcePage.height
                    ).coerceAtLeast(1)

                val renderScale =
                    minOf(
                        2f,
                        1800f /
                                longestSide.toFloat()
                    ).coerceAtLeast(0.5f)

                val bitmapWidth =
                    (
                            sourcePage.width *
                                    renderScale
                            )
                        .roundToInt()
                        .coerceAtLeast(1)

                val bitmapHeight =
                    (
                            sourcePage.height *
                                    renderScale
                            )
                        .roundToInt()
                        .coerceAtLeast(1)

                val bitmap =
                    Bitmap.createBitmap(
                        bitmapWidth,
                        bitmapHeight,
                        Bitmap.Config.ARGB_8888
                    )

                bitmap.eraseColor(Color.WHITE)

                val renderMatrix =
                    Matrix().apply {
                        setScale(
                            renderScale,
                            renderScale
                        )
                    }

                sourcePage.render(
                    bitmap,
                    null,
                    renderMatrix,
                    PdfRenderer.Page
                        .RENDER_MODE_FOR_PRINT
                )

                pageNumber =
                    appendBitmapPage(
                        outputDocument =
                            outputDocument,

                        bitmap = bitmap,

                        pageNumber =
                            pageNumber,

                        documentTitle =
                            if (index == 0) {
                                documentTitle
                            } else {
                                null
                            }
                    )

                bitmap.recycle()
            }
        }

        return pageNumber
    }

    /**
     * Converts one selected or downloaded image into a PDF page.
     */
    private fun appendImageUri(
        context: Context,
        outputDocument: PdfDocument,
        imageUri: Uri,
        pageNumber: Int,
        documentTitle: String
    ): Int {
        val bitmap =
            decodeSampledBitmap(
                context = context,
                imageUri = imageUri
            )

        try {
            return appendBitmapPage(
                outputDocument =
                    outputDocument,

                bitmap = bitmap,

                pageNumber =
                    pageNumber,

                documentTitle =
                    documentTitle
            )

        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Adds one bitmap page and optionally shows a document title above it.
     */
    private fun appendBitmapPage(
        outputDocument: PdfDocument,
        bitmap: Bitmap,
        pageNumber: Int,
        documentTitle: String?
    ): Int {
        val isLandscape =
            bitmap.width > bitmap.height

        val pageWidth =
            if (isLandscape) {
                PORTRAIT_PAGE_HEIGHT
            } else {
                PORTRAIT_PAGE_WIDTH
            }

        val pageHeight =
            if (isLandscape) {
                PORTRAIT_PAGE_WIDTH
            } else {
                PORTRAIT_PAGE_HEIGHT
            }

        val pageInfo =
            PdfDocument.PageInfo.Builder(
                pageWidth,
                pageHeight,
                pageNumber
            ).create()

        val outputPage =
            outputDocument.startPage(
                pageInfo
            )

        val canvas =
            outputPage.canvas

        canvas.drawColor(Color.WHITE)

        val titleLayout =
            documentTitle?.let { title ->
                createDocumentTitleLayout(
                    title = title,
                    availableWidth =
                        (
                                pageWidth -
                                        PAGE_MARGIN * 2
                                ).roundToInt()
                )
            }

        val titleAreaHeight =
            if (titleLayout != null) {
                maxOf(
                    72f,
                    titleLayout.height + 30f
                )
            } else {
                0f
            }

        if (titleLayout != null) {
            canvas.save()

            canvas.translate(
                PAGE_MARGIN,
                PAGE_MARGIN
            )

            titleLayout.draw(canvas)

            canvas.restore()

            val dividerPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color =
                        Color.rgb(
                            120,
                            120,
                            120
                        )

                    strokeWidth = 1f
                }

            val dividerY =
                PAGE_MARGIN +
                        titleAreaHeight -
                        12f

            canvas.drawLine(
                PAGE_MARGIN,
                dividerY,
                pageWidth - PAGE_MARGIN,
                dividerY,
                dividerPaint
            )
        }

        val availableWidth =
            pageWidth -
                    PAGE_MARGIN * 2

        val availableHeight =
            pageHeight -
                    PAGE_MARGIN * 2 -
                    titleAreaHeight

        val scale =
            minOf(
                availableWidth /
                        bitmap.width.toFloat(),

                availableHeight /
                        bitmap.height.toFloat()
            )

        val drawnWidth =
            bitmap.width * scale

        val drawnHeight =
            bitmap.height * scale

        val left =
            (
                    pageWidth -
                            drawnWidth
                    ) / 2f

        val contentTop =
            PAGE_MARGIN +
                    titleAreaHeight

        val top =
            contentTop +
                    (
                            availableHeight -
                                    drawnHeight
                            ) / 2f

        val destination =
            RectF(
                left,
                top,
                left + drawnWidth,
                top + drawnHeight
            )

        val bitmapPaint =
            Paint(
                Paint.ANTI_ALIAS_FLAG or
                        Paint.FILTER_BITMAP_FLAG
            )

        canvas.drawBitmap(
            bitmap,
            null,
            destination,
            bitmapPaint
        )

        outputDocument.finishPage(
            outputPage
        )

        return pageNumber + 1
    }

    /**
     * Creates a wrapped right-aligned title for one attachment.
     */
    private fun createDocumentTitleLayout(
        title: String,
        availableWidth: Int
    ): StaticLayout {
        val titlePaint =
            TextPaint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                color = Color.BLACK
                textSize = 21f

                typeface =
                    Typeface.create(
                        Typeface.DEFAULT,
                        Typeface.BOLD
                    )
            }

        return StaticLayout.Builder.obtain(
            title,
            0,
            title.length,
            titlePaint,
            availableWidth
        )
            .setAlignment(
                Layout.Alignment.ALIGN_OPPOSITE
            )
            .setIncludePad(false)
            .setMaxLines(2)
            .setEllipsize(
                TextUtils.TruncateAt.END
            )
            .build()
    }

    /**
     * Opens a PDF from either a file URI or a content URI.
     */
    private fun openPdfFileDescriptor(
        context: Context,
        uri: Uri
    ): ParcelFileDescriptor {
        return if (
            uri.scheme ==
            ContentResolver.SCHEME_FILE
        ) {
            val localPath =
                uri.path
                    ?: throw IllegalStateException(
                        "לא נמצא נתיב לקובץ"
                    )

            ParcelFileDescriptor.open(
                File(localPath),
                ParcelFileDescriptor.MODE_READ_ONLY
            )

        } else {
            context.contentResolver
                .openFileDescriptor(
                    uri,
                    "r"
                )
                ?: throw IllegalStateException(
                    "לא ניתן לפתוח את קובץ ה־PDF"
                )
        }
    }

    /**
     * Decodes an image while limiting very large bitmap sizes.
     */
    private fun decodeSampledBitmap(
        context: Context,
        imageUri: Uri
    ): Bitmap {
        val boundsOptions =
            BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

        openImageInputStream(
            context = context,
            uri = imageUri
        ).use { inputStream ->
            BitmapFactory.decodeStream(
                inputStream,
                null,
                boundsOptions
            )
        }

        if (
            boundsOptions.outWidth <= 0 ||
            boundsOptions.outHeight <= 0
        ) {
            throw IllegalStateException(
                "לא ניתן לקרוא את התמונה"
            )
        }

        var sampleSize = 1

        while (
            maxOf(
                boundsOptions.outWidth,
                boundsOptions.outHeight
            ) / sampleSize >
            MAX_IMAGE_DIMENSION
        ) {
            sampleSize *= 2
        }

        val decodeOptions =
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize

                inPreferredConfig =
                    Bitmap.Config.ARGB_8888
            }

        return openImageInputStream(
            context = context,
            uri = imageUri
        ).use { inputStream ->

            BitmapFactory.decodeStream(
                inputStream,
                null,
                decodeOptions
            )
                ?: throw IllegalStateException(
                    "לא ניתן לפתוח את התמונה"
                )
        }
    }

    /**
     * Opens an image from either a file URI or a content URI.
     */
    private fun openImageInputStream(
        context: Context,
        uri: Uri
    ): InputStream {
        return if (
            uri.scheme ==
            ContentResolver.SCHEME_FILE
        ) {
            val localPath =
                uri.path
                    ?: throw IllegalStateException(
                        "לא נמצא נתיב לתמונה"
                    )

            FileInputStream(
                File(localPath)
            )

        } else {
            context.contentResolver
                .openInputStream(uri)
                ?: throw IllegalStateException(
                    "לא ניתן לפתוח את התמונה"
                )
        }
    }
}