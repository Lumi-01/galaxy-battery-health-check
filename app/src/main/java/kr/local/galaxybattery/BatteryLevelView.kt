package kr.local.galaxybattery

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo

/** Rounded charge indicator. Unknown readings stay empty and are announced as unavailable. */
class BatteryLevelView(context: Context, private val palette: AppPalette) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()
    private var level: Int? = null

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = "배터리 잔량 확인 중"
    }

    fun setLevel(value: Int?) {
        val next = value?.takeIf { it in 0..100 }
        if (next == level) return
        level = next
        contentDescription = next?.let { "배터리 잔량 $it%" } ?: "배터리 잔량 확인 불가"
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        val radius = height / 2f
        bounds.set(0f, 0f, width.toFloat(), height.toFloat())
        paint.shader = null
        paint.color = palette.actionSurface
        canvas.drawRoundRect(bounds, radius, radius, paint)
        val amount = level ?: return
        if (amount == 0) return
        val filled = width * amount / 100f
        // Amber is reserved for low charge; the normal fill stays mint in both themes.
        val low = amount <= 15
        paint.shader = LinearGradient(0f, 0f, filled, 0f,
            if (low) Color.parseColor("#F4CC86") else palette.batteryStart,
            if (low) Color.parseColor("#ECA14B") else palette.batteryEnd, Shader.TileMode.CLAMP)
        bounds.right = filled
        canvas.drawRoundRect(bounds, radius, radius, paint)
        paint.shader = null
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.ProgressBar"
        level?.let {
            info.rangeInfo = AccessibilityNodeInfo.RangeInfo.obtain(
                AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT, 0f, 100f, it.toFloat())
        }
    }
}
