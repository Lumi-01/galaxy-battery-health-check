package kr.local.galaxybattery

import android.graphics.*
import android.graphics.drawable.Drawable

/** A static optical rim over the live backdrop; no idle animations or bitmap copies. */
class LiquidGlassEdge(private val density: Float, private val radiusDp: Float, private val dark: Boolean) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var opacity = 255
    override fun draw(canvas: Canvas) {
        val b = bounds
        rect.set(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat())
        val radius = radiusDp * density
        // Soft top light over a clearer lower half creates depth without covering text.
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(0f, rect.top, rect.right * .65f, rect.bottom,
            intArrayOf(if (dark) 0x20FFFFFF else 0x65FFFFFF, 0x06FFFFFF, if (dark) 0x0CFFFFFF else 0x18FFFFFF),
            floatArrayOf(0f, .48f, 1f), Shader.TileMode.CLAMP)
        paint.alpha = opacity
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.2f * density
        rect.inset(paint.strokeWidth / 2, paint.strokeWidth / 2)
        paint.shader = LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
            intArrayOf(if (dark) 0x88FFFFFF.toInt() else 0xEDFFFFFF.toInt(), if (dark) 0x16FFFFFF else 0x26677A8D,
                if (dark) 0x42FFFFFF else 0xACFFFFFF.toInt()), floatArrayOf(0f, .6f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect, radius, radius, paint)
        rect.inset(1.2f * density, 1.2f * density)
        paint.strokeWidth = .6f * density
        paint.shader = LinearGradient(0f, rect.top, 0f, rect.bottom,
            intArrayOf(if (dark) 0x26FFFFFF else 0x90FFFFFF.toInt(), 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.shader = null
    }
    override fun setAlpha(alpha: Int) { opacity = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
