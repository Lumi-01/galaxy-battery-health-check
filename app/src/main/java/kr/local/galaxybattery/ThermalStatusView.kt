package kr.local.galaxybattery

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Show the OS stage and headroom first. Diagnostic timestamps and kernel signals can expand. */
class ThermalStatusView(context: Context, private val palette: AppPalette) : LinearLayout(context) {
    private val state = TextView(context)
    private val load = TextView(context)
    private val signals = TextView(context)
    private val updated = TextView(context)
    private val diagnostics = LinearLayout(context).apply { orientation = VERTICAL; visibility = GONE }
    init {
        orientation = VERTICAL
        addView(state.apply { textSize = 18f; setTextColor(palette.foreground); setTypeface(Typeface.DEFAULT, Typeface.BOLD) })
        addView(load.apply { textSize = 13f; setTextColor(palette.muted); setPadding(0, dp(10), 0, dp(4)) })
        addView(TextView(context).apply { text = "심한 쓰로틀링 기준 1.0 · 10초마다 확인"; textSize = 12f; setTextColor(palette.muted) })
        addView(signals.apply { textSize = 12f; setTextColor(palette.muted); setPadding(0, dp(6), 0, 0) })
        addView(TextView(context).apply {
            text = "0단계도 실제 클럭 제한이 있을 수 있어요"; textSize = 11f; setTextColor(palette.muted); setPadding(0, dp(8), 0, 0)
        })
        addView(TextView(context).apply {
            text = "상세 정보"; textSize = 13f; setTextColor(palette.accent)
            setPadding(0, dp(12), 0, dp(8)); minimumHeight = dp(48); gravity = android.view.Gravity.CENTER_VERTICAL
            setOnClickListener { diagnostics.visibility = if (diagnostics.visibility == GONE) VISIBLE else GONE; text = if (diagnostics.visibility == VISIBLE) "상세 정보 접기" else "상세 정보" }
        })
        diagnostics.addView(updated.apply { textSize = 12f; setTextColor(palette.muted) })
        addView(diagnostics)
        setStatus(-1)
    }
    fun setStatus(status: Int, headroom: Float? = null, cooling: List<HardwareTelemetry.Cooling> = emptyList(),
                  statusTime: Long = 0L, coolingSource: String = "", coolingTime: Long = 0L, headroomTime: Long = 0L) {
        state.text = ThermalStatus.label(status)
        state.setTextColor(when (status) {
            1, 2 -> if (Color.red(palette.background) < 100) Color.rgb(255,205,112) else Color.rgb(125,83,0)
            3 -> if (Color.red(palette.background) < 100) Color.rgb(255,174,122) else Color.rgb(160,65,0)
            4, 5, 6 -> if (Color.red(palette.background) < 100) Color.rgb(255,146,154) else Color.rgb(172,30,49)
            0 -> palette.accent
            else -> palette.muted
        })
        load.text = headroom?.let { String.format(Locale.US, "발열 부하 %.2f\n%s", it, ThermalStatus.headroomWarning(it)) }
            ?: "발열 부하 확인 불가"
        val active = cooling.filter { it.state > 0 }
        signals.text = when {
            cooling.isEmpty() -> "추가 쓰로틀링 신호 확인 불가"
            active.isEmpty() -> "추가 쓰로틀링 신호 없음"
            else -> "추가 쓰로틀링 신호 ${active.size}개 작동"
        }
        fun time(value: Long) = if (value > 0) SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date(value)) else "확인 중"
        updated.text = "OS ${time(statusTime)}\n발열 부하 ${if (headroom == null) "확인 불가" else time(headroomTime)}\n추가 신호 ${time(coolingTime)}\n출처 ${coolingSource.ifBlank { "확인 중" }}" +
            if (active.isNotEmpty()) "\n" + active.take(3).joinToString("\n") { "${it.name} ${it.state}/${it.maximum}" } else ""
        contentDescription = "${state.text}, OS 보고 단계, ${load.text}, ${signals.text}"
    }
    private fun dp(value: Int) = AppUi.dp(context, value)
}
