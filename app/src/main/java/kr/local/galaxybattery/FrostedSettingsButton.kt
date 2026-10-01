package kr.local.galaxybattery

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView

/** Floating above the page host, so capturing its backdrop never captures this button. */
class FrostedSettingsButton(context: Context, private val source: View, private val palette: AppPalette,
                            private val blur: Boolean, onClick: () -> Unit) : FrameLayout(context) {
    private val density = resources.displayMetrics.density
    private val backdrop = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_XY }
    private var pending = false
    private var captured: Bitmap? = null
    init {
        val shape = GradientDrawable().apply { setColor(palette.navigation); cornerRadius = 20 * density }
        background = shape; clipToOutline = true; elevation = 3 * density
        addView(backdrop, LayoutParams(-1, -1))
        addView(View(context).apply {
            setBackgroundColor((palette.navigation and 0x00ffffff) or ((if (canBlur()) 180 else 255) shl 24))
        }, LayoutParams(-1, -1))
        addView(ImageView(context).apply {
            setImageResource(resources.getIdentifier("ic_settings", "drawable", context.packageName))
            imageTintList = ColorStateList.valueOf(palette.foreground)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LayoutParams((20 * density).toInt(), (20 * density).toInt(), Gravity.CENTER))
        foreground = RippleDrawable(ColorStateList.valueOf((palette.foreground and 0x00ffffff) or (35 shl 24)), null, shape)
        isClickable = true; isFocusable = true; contentDescription = "설정"
        setOnClickListener { onClick() }
        if (canBlur()) backdrop.setRenderEffect(RenderEffect.createBlurEffect(12 * density, 12 * density, Shader.TileMode.CLAMP))
    }
    fun refreshBackdrop() {
        if (!canBlur() || pending) return
        pending = true
        postDelayed({
            pending = false
            if (isAttachedToWindow && width > 0 && height > 0 && source.width > 0) {
                val w = maxOf(1, width / 2); val h = maxOf(1, height / 2)
                val bitmap = captured?.takeIf { it.width == w && it.height == h } ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val origin = IntArray(2); val position = IntArray(2)
                source.getLocationInWindow(origin); getLocationInWindow(position)
                Canvas(bitmap).apply {
                    drawColor(palette.background)
                    scale(w.toFloat() / width, h.toFloat() / height)
                    translate((origin[0] - position[0]).toFloat(), (origin[1] - position[1]).toFloat())
                    source.draw(this)
                }
                captured = bitmap; backdrop.setImageBitmap(bitmap); backdrop.invalidate()
            }
        }, 100)
    }
    private fun canBlur() = blur && Build.VERSION.SDK_INT >= 31
}
