package com.luqman.pdfme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/** One freehand stroke. Points are normalized (0..1) to the page bitmap size. */
data class Stroke(
    val color: Int,
    val widthFraction: Float,
    val points: MutableList<PointF>
)

/** A signature image placed on a page. Rect is normalized (0..1) to the page bitmap size. */
data class SigStamp(
    val bitmap: Bitmap,
    var rect: RectF
)

/**
 * Transparent overlay that sits exactly on top of the rendered page bitmap.
 * Finger draws strokes; when a signature stamp exists and move mode is on,
 * dragging inside the stamp moves it.
 */
class DrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var strokes: MutableList<Stroke> = mutableListOf()
    var stamp: SigStamp? = null
    var drawColor: Int = Color.BLACK
    var moveMode: Boolean = false

    private var currentStroke: Stroke? = null
    private var draggingStamp = false
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f

    private val strokePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    fun setPage(pageStrokes: MutableList<Stroke>, pageStamp: SigStamp?) {
        strokes = pageStrokes
        stamp = pageStamp
        currentStroke = null
        draggingStamp = false
        invalidate()
    }

    fun clearPage() {
        strokes.clear()
        stamp = null
        invalidate()
    }

    private fun stampRectPx(): RectF? {
        val s = stamp ?: return null
        return RectF(
            s.rect.left * width,
            s.rect.top * height,
            s.rect.right * width,
            s.rect.bottom * height
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        val x = event.x
        val y = event.y
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val rect = stampRectPx()
                if (moveMode && rect != null && rect.contains(x, y)) {
                    draggingStamp = true
                    dragOffsetX = x - rect.left
                    dragOffsetY = y - rect.top
                    return true
                }
                if (!moveMode) {
                    currentStroke = Stroke(
                        drawColor,
                        0.008f,
                        mutableListOf(PointF(x / width, y / height))
                    )
                    strokes.add(currentStroke!!)
                    invalidate()
                    return true
                }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (draggingStamp) {
                    val s = stamp ?: return true
                    val wPx = s.rect.width() * width
                    val hPx = s.rect.height() * height
                    val newLeft = (x - dragOffsetX).coerceIn(0f, (width - wPx).coerceAtLeast(0f))
                    val newTop = (y - dragOffsetY).coerceIn(0f, (height - hPx).coerceAtLeast(0f))
                    s.rect = RectF(
                        newLeft / width,
                        newTop / height,
                        (newLeft + wPx) / width,
                        (newTop + hPx) / height
                    )
                    invalidate()
                    return true
                }
                currentStroke?.let {
                    it.points.add(PointF(x / width, y / height))
                    invalidate()
                    return true
                }
                return false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                draggingStamp = false
                currentStroke = null
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (stroke in strokes) {
            if (stroke.points.size < 2) continue
            val path = Path()
            val first = stroke.points[0]
            path.moveTo(first.x * width, first.y * height)
            for (i in 1 until stroke.points.size) {
                val p = stroke.points[i]
                path.lineTo(p.x * width, p.y * height)
            }
            strokePaint.color = stroke.color
            strokePaint.strokeWidth = stroke.widthFraction * width
            canvas.drawPath(path, strokePaint)
        }
        stamp?.let { s ->
            val dest = stampRectPx() ?: return@let
            canvas.drawBitmap(s.bitmap, null, dest, null)
        }
    }
}
