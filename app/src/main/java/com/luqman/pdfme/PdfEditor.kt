package com.luqman.pdfme

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import java.io.File

/** Edits applied to one page: freehand strokes plus an optional signature stamp. */
data class OverlayData(
    val strokes: List<Stroke>,
    val stamp: SigStamp?
)

/** PDF manipulation via PDFBox-Android. All functions read [input] and write [output]. */
object PdfEditor {

    fun pageCount(file: File): Int {
        PDDocument.load(file).use { return it.numberOfPages }
    }

    /** Bakes strokes + signature stamps into the pages as a transparent overlay image. */
    fun bakeOverlays(
        input: File,
        output: File,
        overlays: Map<Int, OverlayData>,
        bitmapWidth: Int
    ) {
        PDDocument.load(input).use { doc ->
            for ((pageIndex, data) in overlays) {
                if (pageIndex >= doc.numberOfPages) continue
                if (data.strokes.isEmpty() && data.stamp == null) continue
                val page = doc.getPage(pageIndex)
                val pageW = page.mediaBox.width
                val pageH = page.mediaBox.height
                val bw = bitmapWidth
                val bh = (bitmapWidth * pageH / pageW).toInt().coerceAtLeast(1)
                val overlay = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(overlay)
                val paint = Paint().apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    isAntiAlias = true
                }
                for (stroke in data.strokes) {
                    if (stroke.points.size < 2) continue
                    val path = android.graphics.Path()
                    val first = stroke.points[0]
                    path.moveTo(first.x * bw, first.y * bh)
                    for (i in 1 until stroke.points.size) {
                        val p = stroke.points[i]
                        path.lineTo(p.x * bw, p.y * bh)
                    }
                    paint.color = stroke.color
                    paint.strokeWidth = stroke.widthFraction * bw
                    canvas.drawPath(path, paint)
                }
                data.stamp?.let { s ->
                    val dest = RectF(
                        s.rect.left * bw,
                        s.rect.top * bh,
                        s.rect.right * bw,
                        s.rect.bottom * bh
                    )
                    canvas.drawBitmap(s.bitmap, null, dest, null)
                }
                val image = LosslessFactory.createFromImage(doc, overlay)
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                    stream.drawImage(image, 0f, 0f, pageW, pageH)
                }
                overlay.recycle()
            }
            doc.save(output)
        }
    }

    fun deletePages(input: File, output: File, toDelete: Set<Int>) {
        PDDocument.load(input).use { doc ->
            for (index in toDelete.sortedDescending()) {
                if (index < doc.numberOfPages) doc.removePage(index)
            }
            doc.save(output)
        }
    }

    fun rotatePage(input: File, output: File, pageIndex: Int) {
        PDDocument.load(input).use { doc ->
            if (pageIndex < doc.numberOfPages) {
                val page = doc.getPage(pageIndex)
                page.rotation = (page.rotation + 90) % 360
            }
            doc.save(output)
        }
    }
}
