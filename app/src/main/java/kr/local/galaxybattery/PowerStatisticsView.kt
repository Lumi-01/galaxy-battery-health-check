package kr.local.galaxybattery

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/** Shared charging/discharging sections for preview, live recording and saved history. */
class PowerStatisticsView(context: Context, private val palette: AppPalette) : LinearLayout(context) {
    private val charging: Pair<TextView, TextView>
    private val discharge: Pair<TextView, TextView>
    init {
        orientation = VERTICAL
        charging = section("충전 전력", palette.accent)
        discharge = section("방전 전력", palette.negative)
        setValues(null, null, null, null)
    }
    private fun section(title: String, color: Int): Pair<TextView, TextView> {
        val panel = LinearLayout(context).apply {
            orientation = VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply { setColor((color and 0xFFFFFF) or (14 shl 24)); cornerRadius = dp(18).toFloat() }
        }
        addView(panel, LayoutParams(-1, -2).apply { if (childCount > 0) topMargin = dp(10) })
        panel.addView(TextView(context).apply { text = title; textSize = 14f; setTextColor(color); setTypeface(Typeface.DEFAULT, Typeface.BOLD) })
        fun metric(label: String): TextView {
            val row = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(30) }
            panel.addView(row, LayoutParams(-1, -2))
            row.addView(TextView(context).apply { text = label; textSize = 13f; setTextColor(palette.muted) }, LayoutParams(-2, -2).apply { marginEnd = dp(12) })
            return TextView(context).apply {
                setTextColor(color); setTypeface(Typeface.DEFAULT, Typeface.BOLD); textSize = 18f; gravity = Gravity.END
                setSingleLine(true); setHorizontallyScrolling(false)
                setAutoSizeTextTypeUniformWithConfiguration(12, 18, 1, TypedValue.COMPLEX_UNIT_SP)
                row.addView(this, LayoutParams(0, -2, 1f))
            }
        }
        return metric("평균") to metric("최대")
    }
    fun setValues(chargeMean: Double?, chargePeak: Double?, dischargeMean: Double?, dischargePeak: Double?) {
        charging.first.text = ChargePower.text(chargeMean); charging.second.text = ChargePower.text(chargePeak)
        // Statistics use the discharge magnitude; the graph and live chip keep its negative sign.
        discharge.first.text = ChargePower.text(dischargeMean); discharge.second.text = ChargePower.text(dischargePeak)
    }
    private fun dp(value: Int) = AppUi.dp(context, value)
}
