package com.luqman.pdfme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/** Simple finger-signature pad. Returns a tightly cropped bitmap, or null if empty. */
class SignatureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val path = Path()
    private var hasInk = false

    private val paint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    fun clear() {
        path.reset()
        hasInk = false
        invalidate()
    }

    fun getBitmap(): Bitmap? {
        if (!hasInk) return null
        val bounds = RectF()
        path.computeBounds(bounds, true)
        val pad = 24f
        val left = (bounds.left - pad).coerceAtLeast(0f)
        val top = (bounds.top - pad).coerceAtLeast(0f)
        val right = (bounds.right + pad).coerceAtMost(width.toFloat())
        val bottom = (bounds.bottom + pad).coerceAtMost(height.toFloat())
        val w = (right - left).toInt().coerceAtLeast(1)
        val h = (bottom - top).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.translate(-left, -top)
        canvas.drawPath(path, paint)
        return bmp
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                path.moveTo(event.x, event.y)
                hasInk = true
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                path.lineTo(event.x, event.y)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawPath(path, paint)
    }
}
