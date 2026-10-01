package kr.local.galaxybattery

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.TextView
import android.view.Gravity

/** Samsung-style pill visuals, with native checked state and switch accessibility. */
class OneUiSwitch(context: Context, private val palette: AppPalette) : CompoundButton(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    init { buttonDrawable = null; background = null; isFocusable = true; isClickable = true }
    override fun setChecked(checked: Boolean) {
        super.setChecked(checked)
        // No stateful Drawable exists to invalidate this custom Canvas artwork.
        // Redraw after row taps, native switch taps and restored checked states.
        invalidate()
    }
    override fun getAccessibilityClassName(): CharSequence = "android.widget.Switch"
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(resolveSize((56 * density).toInt(), widthMeasureSpec), resolveSize((48 * density).toInt(), heightMeasureSpec))
    }
    override fun onDraw(canvas: Canvas) {
        val left = (width - 50 * density) / 2f
        val top = (height - 30 * density) / 2f
        val dark = Color.red(palette.background) < 100
        paint.color = if (isChecked) Color.parseColor(if (dark) "#7AA8FF" else "#387AFF") else Color.parseColor(if (dark) "#656970" else "#A6A8AD")
        paint.alpha = if (isEnabled) 255 else 110
        canvas.drawRoundRect(left, top, left + 50 * density, top + 30 * density, 15 * density, 15 * density, paint)
        paint.color = Color.WHITE; paint.alpha = if (isEnabled) 255 else 150
        val end = if (isChecked) 35 else 15
        val center = left + (if (layoutDirection == LAYOUT_DIRECTION_RTL) 50 - end else end) * density
        canvas.drawCircle(center, top + 15 * density, 12 * density, paint)
    }
}

/** Whole-row tap target; labels remain independent from the compact switch artwork. */
class OneUiToggle(context: Context, palette: AppPalette, label: String, description: String,
                  checked: Boolean, changed: ((Boolean) -> Unit)? = null) : LinearLayout(context) {
    val control = OneUiSwitch(context, palette)
    var isChecked: Boolean
        get() = control.isChecked
        set(value) { control.isChecked = value }
    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
    init {
        orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(64); setPadding(0, dp(6), 0, dp(6))
        val labels = LinearLayout(context).apply { orientation = VERTICAL; setPadding(0, 0, dp(12), 0) }
        labels.addView(TextView(context).apply { text = label; textSize = 15f; setTextColor(palette.foreground) })
        if (description.isNotBlank()) labels.addView(TextView(context).apply {
            text = description; textSize = 12f; setTextColor(palette.muted); setPadding(0, dp(4), 0, 0)
        })
        addView(labels, LayoutParams(0, -2, 1f)); addView(control, LayoutParams(dp(56), dp(48)))
        control.contentDescription = label; control.isChecked = checked
        control.setOnCheckedChangeListener { _, value -> changed?.invoke(value) }
        setOnClickListener { control.toggle() }
    }
}
