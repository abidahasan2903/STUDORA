package com.example.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class ScanFilter(val label: String) {
    ORIGINAL("Original"),
    ENHANCED("Enhanced Doc"),
    GRAYSCALE("Grayscale"),
    BLACK_AND_WHITE("B&W Scan")
}

data class NormalizedCropRect(
    val left: Float = 0.04f,
    val top: Float = 0.04f,
    val right: Float = 0.96f,
    val bottom: Float = 0.96f
)

object PdfUtils {

    suspend fun getPdfPageCount(file: File): Result<Int> = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) return@withContext Result.failure(IOException("PDF file does not exist."))
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    Result.success(renderer.pageCount)
                }
            }
        } catch (e: Exception) {
            Result.failure(IOException("Invalid or unreadable PDF file: ${e.localizedMessage}", e))
        }
    }

    suspend fun renderPdfPageToBitmap(
        file: File,
        pageIndex: Int,
        targetWidthPx: Int = 1240
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) return@withContext Result.failure(IOException("PDF file not found."))
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (pageIndex !in 0 until renderer.pageCount) {
                        return@withContext Result.failure(IndexOutOfBoundsException("Page $pageIndex out of bounds."))
                    }
                    renderer.openPage(pageIndex).use { page ->
                        val ratio = page.height.toFloat() / max(1, page.width)
                        val width = targetWidthPx.coerceIn(300, 2200)
                        val height = (width * ratio).roundToInt().coerceAtLeast(300)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)
                        canvas.drawColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        Result.success(bitmap)
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun renderPdfRangeToBitmaps(
        file: File,
        startPage: Int,
        endPageExclusive: Int,
        targetWidthPx: Int = 1024
    ): Result<List<Bitmap>> = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) return@withContext Result.failure(IOException("PDF file not found."))
            val bitmaps = mutableListOf<Bitmap>()
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val safeEnd = min(endPageExclusive, renderer.pageCount)
                    for (i in startPage until safeEnd) {
                        renderer.openPage(i).use { page ->
                            val ratio = page.height.toFloat() / max(1, page.width)
                            val width = targetWidthPx.coerceIn(400, 1400)
                            val height = (width * ratio).roundToInt().coerceAtLeast(400)
                            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            Canvas(bmp).drawColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmaps.add(bmp)
                        }
                    }
                }
            }
            Result.success(bitmaps)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loadOrientedBitmap(file: File, maxDimension: Int = 2000): Result<Bitmap> = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) return@withContext Result.failure(IOException("Image file not found."))
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val maxRaw = max(bounds.outWidth, bounds.outHeight)
            var sampleSize = 1
            while (maxRaw / sampleSize > maxDimension) {
                sampleSize *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val raw = BitmapFactory.decodeFile(file.absolutePath, opts)
                ?: return@withContext Result.failure(IOException("Failed to decode image."))

            val rotation = try {
                val exif = ExifInterface(file.absolutePath)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } catch (_: Exception) {
                0f
            }
            if (rotation == 0f) {
                Result.success(raw)
            } else {
                val matrix = Matrix().apply { postRotate(rotation) }
                val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
                if (rotated != raw) raw.recycle()
                Result.success(rotated)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun applyCropAndFilter(
        source: Bitmap,
        cropRect: NormalizedCropRect,
        filter: ScanFilter
    ): Bitmap {
        val leftPx = (cropRect.left.coerceIn(0f, 0.9f) * source.width).roundToInt()
        val topPx = (cropRect.top.coerceIn(0f, 0.9f) * source.height).roundToInt()
        val rightPx = (cropRect.right.coerceIn(cropRect.left + 0.08f, 1f) * source.width).roundToInt()
        val bottomPx = (cropRect.bottom.coerceIn(cropRect.top + 0.08f, 1f) * source.height).roundToInt()

        val cropW = max(20, rightPx - leftPx)
        val cropH = max(20, bottomPx - topPx)
        val cropped = Bitmap.createBitmap(source, leftPx, topPx, cropW, cropH)

        if (filter == ScanFilter.ORIGINAL) return cropped

        val result = Bitmap.createBitmap(cropped.width, cropped.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cm = ColorMatrix()

        when (filter) {
            ScanFilter.ORIGINAL -> Unit
            ScanFilter.ENHANCED -> {
                // Boost contrast & slight brightness for crisp printed documents
                val contrast = 1.35f
                val translate = (-0.5f * contrast + 0.5f) * 255f + 14f
                cm.set(
                    floatArrayOf(
                        contrast, 0f, 0f, 0f, translate,
                        0f, contrast, 0f, 0f, translate,
                        0f, 0f, contrast, 0f, translate,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }
            ScanFilter.GRAYSCALE -> {
                cm.setSaturation(0f)
                val contrast = ColorMatrix(
                    floatArrayOf(
                        1.2f, 0f, 0f, 0f, -12f,
                        0f, 1.2f, 0f, 0f, -12f,
                        0f, 0f, 1.2f, 0f, -12f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                cm.postConcat(contrast)
            }
            ScanFilter.BLACK_AND_WHITE -> {
                cm.setSaturation(0f)
                val highContrast = ColorMatrix(
                    floatArrayOf(
                        2.6f, 0f, 0f, 0f, -190f,
                        0f, 2.6f, 0f, 0f, -190f,
                        0f, 0f, 2.6f, 0f, -190f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                cm.postConcat(highContrast)
            }
        }

        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(cropped, 0f, 0f, paint)
        cropped.recycle()
        return result
    }

    /**
     * Suggests document edges automatically based on brightness gradient from borders.
     */
    fun suggestDocumentBounds(bitmap: Bitmap): NormalizedCropRect {
        return try {
            val w = bitmap.width
            val h = bitmap.height
            if (w < 60 || h < 60) return NormalizedCropRect()
            // Sample brightness along middle crossbars to detect dark background -> bright paper transition
            val stepX = max(1, w / 60)
            val stepY = max(1, h / 60)
            fun luma(x: Int, y: Int): Int {
                val p = bitmap.getPixel(x.coerceIn(0, w - 1), y.coerceIn(0, h - 1))
                return (Color.red(p) * 3 + Color.green(p) * 6 + Color.blue(p)) / 10
            }
            var left = 0.03f
            var right = 0.97f
            var top = 0.03f
            var bottom = 0.97f

            val midY = h / 2
            val midX = w / 2
            val avgCenter = luma(midX, midY)
            if (avgCenter > 110) {
                val threshold = avgCenter - 35
                for (x in 0 until w / 3 step stepX) {
                    if (luma(x, midY) >= threshold) {
                        left = (x.toFloat() / w).coerceIn(0.02f, 0.25f)
                        break
                    }
                }
                for (x in w - 1 downTo (2 * w) / 3 step stepX) {
                    if (luma(x, midY) >= threshold) {
                        right = (x.toFloat() / w).coerceIn(0.75f, 0.98f)
                        break
                    }
                }
                for (y in 0 until h / 3 step stepY) {
                    if (luma(midX, y) >= threshold) {
                        top = (y.toFloat() / h).coerceIn(0.02f, 0.25f)
                        break
                    }
                }
                for (y in h - 1 downTo (2 * h) / 3 step stepY) {
                    if (luma(midX, y) >= threshold) {
                        bottom = (y.toFloat() / h).coerceIn(0.75f, 0.98f)
                        break
                    }
                }
            }
            NormalizedCropRect(left, top, right, bottom)
        } catch (_: Exception) {
            NormalizedCropRect()
        }
    }

    suspend fun createPdfFromImages(
        imageFiles: List<File>,
        outputPdfFile: File
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (imageFiles.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("No pages to export to PDF."))
        }
        val pdfDocument = PdfDocument()
        try {
            var writtenPages = 0
            // Standard A4-ish high-res points (1240 x 1754 at 150 DPI)
            val pageWidth = 1240
            val pageHeight = 1754

            imageFiles.forEachIndexed { index, imgFile ->
                val bmpResult = loadOrientedBitmap(imgFile, maxDimension = 1800)
                val bmp = bmpResult.getOrElse {
                    throw IOException("Failed to load scanned page ${index + 1}: ${it.message}")
                }
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas
                canvas.drawColor(Color.WHITE)

                val margin = 36f
                val availW = pageWidth - margin * 2
                val availH = pageHeight - margin * 2
                val scale = min(availW / bmp.width.toFloat(), availH / bmp.height.toFloat())
                val drawW = bmp.width * scale
                val drawH = bmp.height * scale
                val left = (pageWidth - drawW) / 2f
                val top = (pageHeight - drawH) / 2f

                val destRect = android.graphics.RectF(left, top, left + drawW, top + drawH)
                canvas.drawBitmap(bmp, null, destRect, Paint(Paint.FILTER_BITMAP_FLAG))
                pdfDocument.finishPage(page)
                bmp.recycle()
                writtenPages++
            }

            outputPdfFile.parentFile?.mkdirs()
            FileOutputStream(outputPdfFile).use { out ->
                pdfDocument.writeTo(out)
                out.flush()
            }
            if (!outputPdfFile.exists() || outputPdfFile.length() == 0L) {
                outputPdfFile.delete()
                return@withContext Result.failure(IOException("Generated PDF file was empty."))
            }
            Result.success(writtenPages)
        } catch (e: Exception) {
            outputPdfFile.delete()
            Result.failure(e)
        } finally {
            pdfDocument.close()
        }
    }
}
