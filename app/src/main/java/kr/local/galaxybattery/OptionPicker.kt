package kr.local.galaxybattery

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.*

/** Rounded, theme-aware popup used for theme and interval choices. */
class OptionPicker(context: Context, private val palette: AppPalette, private val options: List<String>, initial: Int) : LinearLayout(context) {
    var selectedItemPosition = initial.coerceIn(0, options.lastIndex); private set
    private val value = TextView(context)
    private fun dp(number: Int) = (number * resources.displayMetrics.density + .5f).toInt()
    init {
        orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), 0, dp(12), 0)
        background = GradientDrawable().apply { setColor(palette.navigation); cornerRadius = dp(18).toFloat() }
        value.text = options[selectedItemPosition]; value.textSize = 15f; value.setTextColor(palette.foreground)
        addView(value, LayoutParams(0, -2, 1f))
        addView(TextView(context).apply { text = "⌄"; textSize = 23f; setTextColor(palette.muted); importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }, LayoutParams(-2, -2))
        contentDescription = "선택: ${value.text}"; isFocusable = true
        setOnClickListener { showOptions() }
    }
    private fun showOptions() {
        val entries = LinearLayout(context).apply { orientation = VERTICAL; setPadding(dp(8), dp(8), dp(8), dp(8)) }
        val scroll = ScrollView(context).apply { addView(entries) }
        val popup = PopupWindow(scroll, width.coerceAtLeast(dp(220)), dp(options.size * 48 + 16).coerceAtMost(dp(336)), true).apply {
            setBackgroundDrawable(GradientDrawable().apply { setColor(palette.card); cornerRadius = dp(24).toFloat() })
            elevation = dp(8).toFloat(); isOutsideTouchable = true
        }
        options.forEachIndexed { index, label ->
            entries.addView(TextView(context).apply {
                text = label; textSize = 16f; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), 0, dp(16), 0); setTextColor(if (index == selectedItemPosition) palette.accent else palette.foreground)
                if (index == selectedItemPosition) { setTypeface(Typeface.DEFAULT, Typeface.BOLD); isSelected = true }
                isFocusable = true; setOnClickListener {
                    selectedItemPosition = index; value.text = label; this@OptionPicker.contentDescription = "선택: $label"; popup.dismiss()
                }
            }, LayoutParams(-1, dp(48)))
        }
        popup.showAsDropDown(this, 0, dp(6))
    }
}
