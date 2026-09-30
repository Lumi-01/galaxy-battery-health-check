package kr.local.galaxybattery

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil

class PowerGraphView(context: Context) : View(context) {
    private var samples: List<ChargePower.Sample> = emptyList()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    private var selected: ChargePower.Sample? = null
    var onSelection: ((ChargePower.Sample) -> Unit)? = null
    fun setSamples(values: List<ChargePower.Sample>) {
        if (samples == values) return
        samples = values
        selected = selected?.takeIf { values.contains(it) }
        contentDescription = "전력 그래프, ${values.size}개 측정. 양수는 배터리로 유입, 음수는 방전 전력."
        invalidate()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = 44f * density; val right = width - 12f * density
        val top = 16f * density; val bottom = height - 30f * density
        if (right <= left || bottom <= top) return
        val valid = samples.mapNotNull { it.watts() }
        val maximum = maxOf(5.0, ceil((valid.maxOrNull() ?: 0.0) / 5) * 5)
        val minimum = minOf(0.0, kotlin.math.floor((valid.minOrNull() ?: 0.0) / 5) * 5)
        val start = samples.firstOrNull()?.time ?: 0L
        val end = maxOf(start + 1000, samples.lastOrNull()?.time ?: 1000L)
        fun x(time: Long) = left + ((time - start).toDouble() / (end - start) * (right - left)).toFloat()
        fun y(watts: Double) = bottom - ((watts - minimum) / (maximum - minimum) * (bottom - top)).toFloat()
        paint.textSize = 10f * resources.displayMetrics.scaledDensity
        paint.strokeWidth = density
        for (step in 0..4) {
            val value = minimum + (maximum - minimum) * step / 4.0
            paint.color = Color.rgb(44, 58, 69)
            canvas.drawLine(left, y(value), right, y(value), paint)
            paint.color = Color.rgb(148, 167, 181)
            canvas.drawText(String.format(Locale.US, "%.0fW", value), 0f, y(value) + 4 * density, paint)
        }
        paint.color = Color.rgb(148, 167, 181)
        if (samples.isNotEmpty()) {
            val format = SimpleDateFormat("HH:mm", Locale.KOREA)
            canvas.drawText(format.format(Date(start)), left, height - 6 * density, paint)
            val last = format.format(Date(end))
            canvas.drawText(last, right - paint.measureText(last), height - 6 * density, paint)
        }
        if (valid.isEmpty()) {
            paint.textSize = 13f * resources.displayMetrics.scaledDensity
            val text = if (samples.isEmpty()) "측정하면 그래프가 여기에 나타나요" else "기기에서 유효한 전류 값을 제공하지 않아요"
            canvas.drawText(text, left + 8 * density, (top + bottom) / 2, paint)
            return
        }
        paint.strokeWidth = 2.5f * density
        paint.strokeCap = Paint.Cap.ROUND
        samples.zipWithNext().forEach { (a, b) ->
            val aw = a.watts(); val bw = b.watts()
            if (aw != null && bw != null && b.time - a.time in 1..15000) {
                paint.color = if (bw >= 0) Color.rgb(115, 235, 195) else Color.rgb(115, 178, 246)
                canvas.drawLine(x(a.time), y(aw), x(b.time), y(bw), paint)
            }
        }
        val point = selected ?: samples.lastOrNull { it.watts() != null }
        point?.let { sample -> sample.watts()?.let { w ->
            paint.color = Color.rgb(115, 235, 195)
            if (selected != null) { paint.strokeWidth = density; canvas.drawLine(x(sample.time), top, x(sample.time), bottom, paint) }
            canvas.drawCircle(x(sample.time), y(w), 4f * density, paint)
        } }
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (samples.isEmpty()) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val fraction = ((event.x - 44 * density) / (width - 56 * density)).coerceIn(0f, 1f)
                val target = samples.first().time + ((samples.last().time - samples.first().time) * fraction).toLong()
                samples.minByOrNull { abs(it.time - target) }?.let { selected = it; onSelection?.invoke(it) }
                invalidate(); return true
            }
            MotionEvent.ACTION_UP -> { parent?.requestDisallowInterceptTouchEvent(false); performClick(); return true }
            MotionEvent.ACTION_CANCEL -> { parent?.requestDisallowInterceptTouchEvent(false); return true }
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}
