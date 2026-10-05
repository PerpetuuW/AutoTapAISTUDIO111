package com.example.autotap.infrastructure.overlay.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import androidx.core.graphics.toColorInt

class MaskPreviewView(context: Context) : View(context) {

    private var sourceBitmap: Bitmap? = null
    private var isMaskMode: Boolean = false
    private var isCircleShape: Boolean = false

    private val dm = context.resources.displayMetrics
    private fun dpF(v: Float): Float = v * dm.density

    private val checkerPaint = Paint().apply {
        val tileSize = maxOf(2, dpF(10f).toInt())
        val half = tileSize / 2
        val tileBmp = Bitmap.createBitmap(tileSize, tileSize, Bitmap.Config.ARGB_8888)
        val cLight = "#281142".toColorInt()
        val cDark = "#10051C".toColorInt()
        for (y in 0 until tileSize) {
            for (x in 0 until tileSize) {
                val isLight = ((x < half && y < half) || (x >= half && y >= half))
                tileBmp.setPixel(x, y, if (isLight) cLight else cDark)
            }
        }
        shader = BitmapShader(tileBmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpF(1.5f)
        color = "#C084FC".toColorInt()
    }

    private val cleanBitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private val contourGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpF(1.5f)
        color = "#00F5D4".toColorInt()
    }

    private val srcRect = Rect()
    private val dstRect = RectF()
    private val clipPath = Path()
    private val outlinePath = Path()

    fun bind(bitmap: Bitmap?, isMask: Boolean, isCircle: Boolean = false) {
        sourceBitmap = bitmap
        isMaskMode = isMask
        isCircleShape = isCircle
        borderPaint.color = if (isMask) "#00F5D4".toColorInt() else "#C084FC".toColorInt()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        if (w <= 0f || h <= 0f) return

        clipPath.rewind()
        if (isCircleShape) {
            clipPath.addOval(0f, 0f, w, h, Path.Direction.CW)
        } else {
            clipPath.addRoundRect(0f, 0f, w, h, dpF(6f), dpF(6f), Path.Direction.CW)
        }

        canvas.save()
        canvas.clipPath(clipPath)

        canvas.drawRect(0f, 0f, w, h, checkerPaint)

        sourceBitmap?.let { bmp ->
            if (!bmp.isRecycled && bmp.width > 0 && bmp.height > 0) {
                srcRect.set(0, 0, bmp.width, bmp.height)
                val pad = dpF(3f)
                val scale = minOf((w - pad * 2) / bmp.width.toFloat(), (h - pad * 2) / bmp.height.toFloat())
                val drawW = bmp.width * scale
                val drawH = bmp.height * scale
                val dx = (w - drawW) / 2f
                val dy = (h - drawH) / 2f
                dstRect.set(dx, dy, dx + drawW, dy + drawH)

                canvas.drawBitmap(bmp, srcRect, dstRect, cleanBitmapPaint)

                if (isMaskMode) {
                    outlinePath.rewind()
                    if (isCircleShape) {
                        outlinePath.addOval(dstRect, Path.Direction.CW)
                    } else {
                        outlinePath.addRoundRect(dstRect, dpF(3f), dpF(3f), Path.Direction.CW)
                    }
                    canvas.drawPath(outlinePath, contourGlowPaint)
                }
            }
        }
        canvas.restore()

        if (isCircleShape) {
            canvas.drawOval(dpF(1f), dpF(1f), w - dpF(1f), h - dpF(1f), borderPaint)
        } else {
            canvas.drawRoundRect(dpF(1f), dpF(1f), w - dpF(1f), h - dpF(1f), dpF(6f), dpF(6f), borderPaint)
        }
    }
}
