package kr.local.galaxybattery

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.*
import android.graphics.Typeface
import android.util.TypedValue

/** Blur only a captured backdrop; icons and labels remain sharp above it. */
class FrostedNavigation(context: Context, private val source: View, private val palette: AppPalette,
                        private val blurEnabled: Boolean, private val onSelect: (Int) -> Unit) : FrameLayout(context) {
    private val density = resources.displayMetrics.density
    private val backdrop = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_XY }
    private val items = mutableListOf<LinearLayout>()
    private val labels = mutableListOf<TextView>()
    private val icons = mutableListOf<ImageView>()
    private var captured: Bitmap? = null
    private var pending = false
    init {
        val radius = 28 * density
        background = GradientDrawable().apply { setColor(palette.navigation); cornerRadius = radius }
        clipToOutline = true
        elevation = 8 * density
        addView(backdrop, LayoutParams(-1, -1))
        addView(View(context).apply {
            setBackgroundColor(withAlpha(palette.navigation, if (canBlur()) 208 else 255))
        }, LayoutParams(-1, -1))
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(4), dp(4), dp(4), dp(4)) }
        addView(row, LayoutParams(-1, -1))
        val names = arrayOf("배터리 상태", "충전 모니터링", "기록·공유")
        val assets = arrayOf("ic_nav_battery", "ic_nav_monitor", "ic_nav_history")
        for (index in names.indices) {
            val item = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                isClickable = true; isFocusable = true; contentDescription = names[index]
                setPadding(dp(4), dp(3), dp(4), dp(3))
                setOnClickListener { onSelect(index) }
            }
            val icon = ImageView(context).apply {
                setImageResource(resources.getIdentifier(assets[index], "drawable", context.packageName))
                imageTintList = android.content.res.ColorStateList.valueOf(palette.foreground)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            val label = TextView(context).apply {
                text = names[index]; gravity = Gravity.CENTER; setTextColor(palette.foreground)
                setSingleLine(true); setAutoSizeTextTypeUniformWithConfiguration(10, 11, 1, TypedValue.COMPLEX_UNIT_SP)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            item.addView(icon, LinearLayout.LayoutParams(dp(20), dp(20)))
            item.addView(label, LinearLayout.LayoutParams(-1, dp(17)).apply { topMargin = dp(1) })
            row.addView(item, LinearLayout.LayoutParams(0, -1, 1f).apply { leftMargin = dp(2); rightMargin = dp(2) })
            items.add(item); icons.add(icon); labels.add(label)
        }
        if (canBlur()) backdrop.setRenderEffect(RenderEffect.createBlurEffect(16 * density, 16 * density, Shader.TileMode.CLAMP))
        select(0)
    }
    fun select(index: Int) {
        items.forEachIndexed { i, item ->
            item.isSelected = i == index
            item.background = GradientDrawable().apply {
                setColor(if (i == index) palette.selection else Color.TRANSPARENT)
                cornerRadius = 24 * density
            }
            labels[i].setTypeface(Typeface.DEFAULT, if (i == index) Typeface.BOLD else Typeface.NORMAL)
        }
        refreshBackdrop()
    }
    fun refreshBackdrop() {
        if (!canBlur() || pending) return
        pending = true
        postDelayed({ pending = false; captureBackdrop() }, 100)
    }
    private fun captureBackdrop() {
        if (!isAttachedToWindow || width <= 0 || height <= 0 || source.width <= 0) return
        val w = maxOf(1, width / 4); val h = maxOf(1, height / 4)
        val bitmap = captured?.takeIf { it.width == w && it.height == h } ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val origin = IntArray(2); val position = IntArray(2)
        source.getLocationInWindow(origin); getLocationInWindow(position)
        val canvas = Canvas(bitmap)
        canvas.drawColor(palette.background)
        canvas.scale(w.toFloat() / width, h.toFloat() / height)
        canvas.translate((origin[0] - position[0]).toFloat(), (origin[1] - position[1]).toFloat())
        source.draw(canvas)
        captured = bitmap
        backdrop.setImageBitmap(bitmap)
        backdrop.invalidate()
    }
    private fun canBlur() = blurEnabled && Build.VERSION.SDK_INT >= 31
    private fun withAlpha(color: Int, alpha: Int) = (color and 0x00ffffff) or (alpha shl 24)
    private fun dp(value: Int) = (value * density + .5f).toInt()
}
