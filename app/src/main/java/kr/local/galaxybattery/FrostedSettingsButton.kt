package kr.local.galaxybattery

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView

/** Floating above the page host, excluding the sharp icon from the live blur. */
class FrostedSettingsButton(context: Context, private val source: View, private val palette: AppPalette,
                            private val blur: Boolean, onClick: () -> Unit) : FrameLayout(context) {
    private val density = resources.displayMetrics.density
    private val backdrop = FrostedBackdrop(context, source, palette, blur, 12f)
    init {
        val shape = GradientDrawable().apply { setColor(Color.TRANSPARENT); cornerRadius = 24 * density
            setStroke(maxOf(1, density.toInt()), (palette.foreground and 0x00ffffff) or (36 shl 24)) }
        background = shape; clipToOutline = true; elevation = 2 * density
        addView(backdrop, LayoutParams(-1, -1))
        addView(ImageView(context).apply {
            setImageResource(resources.getIdentifier("ic_settings", "drawable", context.packageName))
            imageTintList = ColorStateList.valueOf(palette.foreground)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LayoutParams((20 * density).toInt(), (20 * density).toInt(), Gravity.CENTER))
        foreground = RippleDrawable(ColorStateList.valueOf((palette.foreground and 0x00ffffff) or (35 shl 24)), shape, GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = 24 * density })
        isClickable = true; isFocusable = true; contentDescription = "설정"
        setOnClickListener { onClick() }
    }
    fun refreshBackdrop() = backdrop.refresh()
}
