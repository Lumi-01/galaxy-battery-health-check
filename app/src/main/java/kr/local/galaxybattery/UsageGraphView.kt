package kr.local.galaxybattery

import android.content.Context
import android.graphics.*
import android.util.TypedValue
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Percent charts use typed samples; missing data breaks the line instead of becoming zero. */
class UsageGraphView(context: Context, private val palette: AppPalette, private val cpu: Boolean, private val core: Int? = null) : View(context) {
    private var frames: List<HardwareTelemetry.Frame> = emptyList()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    fun setFrames(values: List<HardwareTelemetry.Frame>) {
        frames = values
        contentDescription = "${if (cpu) core?.let { "CPU $it" } ?: "CPU 전체" else "GPU 전체"} 사용률 그래프, ${values.size}개 측정, 0부터 100퍼센트"
        invalidate()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = 38 * density; val right = width - 8 * density
        val top = 12 * density; val bottom = height - 25 * density
        if (right <= left || bottom <= top) return
        val start = frames.firstOrNull()?.time ?: 0L
        val end = maxOf(start + 1000L, frames.lastOrNull()?.time ?: 1000L)
        fun x(time: Long) = left + ((time - start).toDouble() / (end - start) * (right - left)).toFloat()
        fun y(value: Double) = bottom - (value / 100 * (bottom - top)).toFloat()
        paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10f, resources.displayMetrics)
        paint.strokeWidth = density
        for (value in 0..100 step 50) {
            paint.color = palette.grid; canvas.drawLine(left, y(value.toDouble()), right, y(value.toDouble()), paint)
            paint.color = palette.muted; canvas.drawText("$value%", 0f, y(value.toDouble()) + 3 * density, paint)
        }
        if (frames.isNotEmpty()) {
            val format = SimpleDateFormat("HH:mm:ss", Locale.KOREA)
            paint.color = palette.muted
            canvas.drawText(format.format(Date(start)), left, height - 5 * density, paint)
            val last = format.format(Date(end))
            canvas.drawText(last, right - paint.measureText(last), height - 5 * density, paint)
        }
        val gap = RefreshPolicy.graphGapMs(frames.map { it.time })
        var any = false
        paint.strokeWidth = 2 * density; paint.strokeCap = Paint.Cap.ROUND
        paint.color = if (cpu) core?.let { coreColor(it) } ?: palette.accent else palette.negative
            var previous: HardwareTelemetry.Frame? = null
            var previousValue: Double? = null
            frames.forEach { frame ->
                val value = (when { !cpu -> frame.gpu; core == null -> frame.cpuTotal; else -> frame.cpu[core] })
                    ?.takeIf { it.isFinite() && it in 0.0..100.0 }
                if (value != null) {
                    any = true
                    previous?.let { old -> previousValue?.let { oldValue ->
                        if (frame.time - old.time in 1..gap) canvas.drawLine(x(old.time), y(oldValue), x(frame.time), y(value), paint)
                    } }
                    canvas.drawCircle(x(frame.time), y(value), 1.5f * density, paint)
                }
                previous = frame; previousValue = value
            }
        if (!any) {
            paint.color = palette.muted
            paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics)
            canvas.drawText(if (frames.isEmpty()) "측정 대기 중" else "사용률을 읽을 수 없어요", left + 8 * density, (top + bottom) / 2, paint)
        }
    }
    private fun coreColor(id: Int): Int {
        val dark = Color.red(palette.background) < 100
        return Color.HSVToColor(floatArrayOf((145 + id * 137.5f) % 360, if (dark) .45f else .78f, if (dark) .93f else .64f))
    }
}
