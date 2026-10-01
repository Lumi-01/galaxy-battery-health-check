package kr.local.galaxybattery

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/** A compact thermal summary; severity is named and numbered, never conveyed by color alone. */
class ThermalStatusView(context: Context, private val palette: AppPalette) : LinearLayout(context) {
    private val state = TextView(context)
    private val badge = TextView(context)
    init {
        orientation = VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12))
        background = GradientDrawable().apply {
            setColor((palette.selection and 0x00ffffff) or (115 shl 24)); cornerRadius = dp(18).toFloat()
        }
        val row = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        val names = LinearLayout(context).apply { orientation = VERTICAL }
        names.addView(TextView(context).apply { text = "쓰로틀링"; textSize = 12f; setTextColor(palette.muted) })
        names.addView(state.apply {
            textSize = 17f; setTextColor(palette.foreground); setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(0, dp(3), 0, 0)
        })
        row.addView(names, LayoutParams(0, -2, 1f))
        row.addView(badge.apply {
            textSize = 13f; gravity = Gravity.CENTER; setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }, LayoutParams(-2, -2).apply { leftMargin = dp(10) })
        addView(row)
        addView(TextView(context).apply {
            text = "기기 전체 기준 · 충전 제한 원인과는 다를 수 있어요."
            textSize = 11f; setTextColor(palette.muted); setPadding(0, dp(8), 0, 0)
        })
        setStatus(-1)
    }
    fun setStatus(status: Int) {
        val known = status in 0..6
        state.text = ThermalStatus.label(status).substringBefore(" ·")
        badge.text = if (known) "${status}단계" else "—"
        val color = when (status) {
            1, 2 -> if (Color.red(palette.background) < 100) Color.rgb(255,205,112) else Color.rgb(125,83,0)
            3 -> if (Color.red(palette.background) < 100) Color.rgb(255,174,122) else Color.rgb(160,65,0)
            4, 5, 6 -> if (Color.red(palette.background) < 100) Color.rgb(255,146,154) else Color.rgb(172,30,49)
            0 -> palette.accent
            else -> palette.muted
        }
        badge.setTextColor(color)
        badge.background = GradientDrawable().apply {
            setColor((color and 0x00ffffff) or (28 shl 24)); cornerRadius = dp(18).toFloat()
        }
        contentDescription = "쓰로틀링, ${ThermalStatus.label(status)}"
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
}
