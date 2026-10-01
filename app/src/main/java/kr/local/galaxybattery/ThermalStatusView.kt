package kr.local.galaxybattery

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.Date

/** A compact thermal summary; severity is named and numbered, never conveyed by color alone. */
class ThermalStatusView(context: Context, private val palette: AppPalette) : LinearLayout(context) {
    private val state = TextView(context)
    private val badge = TextView(context)
    private val load = TextView(context)
    private val signals = TextView(context)
    private val updated = TextView(context)
    init {
        orientation = VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12))
        background = GradientDrawable().apply {
            setColor((palette.selection and 0x00ffffff) or (115 shl 24)); cornerRadius = dp(18).toFloat()
        }
        val row = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        val names = LinearLayout(context).apply { orientation = VERTICAL }
        names.addView(TextView(context).apply { text = "열 제한 · 쓰로틀링"; textSize = 12f; setTextColor(palette.muted) })
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
        addView(load.apply { textSize = 12f; setTextColor(palette.muted); setPadding(0, dp(10), 0, 0) })
        addView(signals.apply { textSize = 11f; setTextColor(palette.muted); setPadding(0, dp(5), 0, 0) })
        addView(updated.apply { textSize = 11f; setTextColor(palette.muted); setPadding(0, dp(5), 0, 0) })
        addView(TextView(context).apply {
            text = "OS 단계와 실제 클럭 제한은 다를 수 있어요. 0단계만으로 쓰로틀링 없음을 확정하지 않아요."
            textSize = 11f; setTextColor(palette.muted); setPadding(0, dp(8), 0, 0)
        })
        setStatus(-1)
    }
    fun setStatus(status: Int, headroom: Float? = null, cooling: List<HardwareTelemetry.Cooling> = emptyList(),
                  statusTime: Long = 0L, coolingSource: String = "", coolingTime: Long = 0L, headroomTime: Long = 0L) {
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
        load.text = headroom?.let { String.format(Locale.US, "열 부하 %.2f · %s\n심한 제한 기준 1.0 · 10초마다 확인", it, ThermalStatus.headroomWarning(it)) }
            ?: "열 부하 · 기기에서 제공하지 않아요"
        val active = cooling.filter { it.state > 0 }
        signals.text = when {
            cooling.isEmpty() -> "추가 제한 신호 · 확인 불가"
            active.isEmpty() -> "추가 제한 신호 · 작동 중인 항목 없음"
            else -> "추가 제한 신호 · ${active.size}개 작동\n" + active.take(3).joinToString(" · ") { "${it.name} ${it.state}/${it.maximum}" }
        }
        fun time(value: Long) = if (value > 0) SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date(value)) else "확인 중"
        updated.text = "OS ${time(statusTime)} · 열 부하 ${if (headroom == null) "확인 불가" else time(headroomTime)}\n제한 신호 ${time(coolingTime)} · ${coolingSource.ifBlank { "확인 중" }} · 10초마다 확인"
        contentDescription = "쓰로틀링, ${ThermalStatus.label(status)}, ${load.text}, ${signals.text}"
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
}
