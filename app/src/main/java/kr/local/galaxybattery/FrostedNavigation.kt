package kr.local.galaxybattery

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.widget.*
import android.graphics.Typeface
import android.util.TypedValue

/** Live GPU backdrop; icons and labels remain sharp above it. */
class FrostedNavigation(context: Context, private val source: View, private val palette: AppPalette,
                        private val blurEnabled: Boolean, private val onSelect: (Int) -> Unit) : FrameLayout(context) {
    private val density = resources.displayMetrics.density
    private val backdrop = FrostedBackdrop(context, source, palette, blurEnabled, 16f)
    private val items = mutableListOf<LinearLayout>()
    private val labels = mutableListOf<TextView>()
    private val icons = mutableListOf<ImageView>()
    init {
        val radius = 28 * density
        background = GradientDrawable().apply { setColor(Color.TRANSPARENT); cornerRadius = radius }
        foreground = LiquidGlassEdge(density, 28f, Color.red(palette.background) < 100)
        clipToOutline = true
        elevation = 2 * density
        addView(backdrop, LayoutParams(-1, -1))
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(6), dp(6), dp(6), dp(6)) }
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
        select(0)
    }
    fun select(index: Int) {
        items.forEachIndexed { i, item ->
            item.isSelected = i == index
            val shape = GradientDrawable().apply {
                setColor(if (i == index) (palette.selection and 0xFFFFFF) or (190 shl 24) else Color.TRANSPARENT)
                cornerRadius = 24 * density
            }
            item.background = RippleDrawable(ColorStateList.valueOf((palette.accent and 0xFFFFFF) or (36 shl 24)), shape, null)
            val color = palette.foreground
            labels[i].setTextColor(color); icons[i].imageTintList = ColorStateList.valueOf(color)
            labels[i].setTypeface(Typeface.DEFAULT, if (i == index) Typeface.BOLD else Typeface.NORMAL)
        }
        refreshBackdrop()
    }
    fun refreshBackdrop() = backdrop.refresh()
    private fun dp(value: Int) = (value * density + .5f).toInt()
}
