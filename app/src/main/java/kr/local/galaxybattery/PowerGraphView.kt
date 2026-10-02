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

class PowerGraphView(context: Context, private val palette: AppPalette = AppPalette.forDark(AppSettings(context).isDark(context))) : View(context) {
    private var samples: List<ChargePower.Sample> = emptyList()
    private var screenEvents: List<ScreenTimeline.Event> = emptyList()
    private var observedEnd = 0L
    private var preview = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    private var selected: ChargePower.Sample? = null
    private var touchX = 0f
    private var touchY = 0f
    var onSelection: ((ChargePower.Sample) -> Unit)? = null
    @JvmOverloads fun setScreenEvents(values: List<ScreenTimeline.Event>, end: Long = 0L, isPreview: Boolean = false) {
        if (screenEvents == values && observedEnd == end && preview == isPreview) return
        screenEvents = values; observedEnd = end; preview = isPreview; invalidate()
    }
    private fun endTime(start: Long) = maxOf(start + 1000, samples.lastOrNull()?.time ?: 0L,
        screenEvents.maxOfOrNull { it.time } ?: 0L, observedEnd)
    fun setSamples(values: List<ChargePower.Sample>) {
        if (samples == values) return
        samples = values
        selected = selected?.takeIf { values.contains(it) }
        contentDescription = "전력 그래프. 양수는 충전, 음수는 방전. 회색은 화면 꺼짐 구간."
        invalidate()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = 44f * density; val right = width - 12f * density
        val top = 24f * density; val bottom = height - 30f * density
        if (right <= left || bottom <= top) return
        val valid = samples.mapNotNull { it.watts() }
        val maximum = maxOf(5.0, ceil((valid.maxOrNull() ?: 0.0) / 5) * 5)
        val minimum = minOf(0.0, kotlin.math.floor((valid.minOrNull() ?: 0.0) / 5) * 5)
        val start = samples.firstOrNull()?.time ?: 0L
        val end = endTime(start)
        fun x(time: Long) = left + ((time - start).toDouble() / (end - start) * (right - left)).toFloat()
        fun y(watts: Double) = bottom - ((watts - minimum) / (maximum - minimum) * (bottom - top)).toFloat()
        val off = ScreenTimeline.intervals(screenEvents, start, end)
        paint.color = (palette.muted and 0xFFFFFF) or (30 shl 24)
        off.forEach { canvas.drawRect(x(it.start), top, x(it.end), bottom, paint) }
        paint.textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 10f, resources.displayMetrics)
        if (off.isNotEmpty()) {
            paint.color = palette.muted
            canvas.drawRoundRect(left, 3 * density, left + 8 * density, 11 * density, 2 * density, 2 * density, paint)
            canvas.drawText("화면 꺼짐 구간", left + 14 * density, 12 * density, paint)
        }
        paint.strokeWidth = density
        for (step in 0..4) {
            val value = minimum + (maximum - minimum) * step / 4.0
            paint.color = palette.grid
            canvas.drawLine(left, y(value), right, y(value), paint)
            paint.color = palette.muted
            canvas.drawText(String.format(Locale.US, "%.0fW", value), 0f, y(value) + 4 * density, paint)
        }
        paint.color = palette.muted
        if (samples.isNotEmpty()) {
            val format = SimpleDateFormat("HH:mm", Locale.KOREA)
            canvas.drawText(format.format(Date(start)), left, height - 6 * density, paint)
            val last = format.format(Date(end))
            canvas.drawText(last, right - paint.measureText(last), height - 6 * density, paint)
        }
        if (valid.isEmpty()) {
            paint.textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 13f, resources.displayMetrics)
            val text = if (samples.isEmpty()) "측정하면 그래프가 여기에 나타나요" else "기기에서 유효한 전류 값을 제공하지 않아요"
            canvas.drawText(text, left + 8 * density, (top + bottom) / 2, paint)
            return
        }
        paint.strokeWidth = 2.5f * density
        paint.strokeCap = Paint.Cap.ROUND
        val gap = RefreshPolicy.graphGapMs(samples.map { it.time })
        samples.zipWithNext().forEach { (a, b) ->
            val aw = a.watts(); val bw = b.watts()
            // Preview polling pauses with the Activity. Do not interpolate across
            // an unmeasured screen-off interval; recordings keep their real samples.
            val unmeasured = preview && off.any { it.start < b.time && it.end > a.time }
            if (aw != null && bw != null && b.time - a.time in 1..gap && !unmeasured) {
                paint.color = if (bw >= 0) palette.accent else palette.negative
                canvas.drawLine(x(a.time), y(aw), x(b.time), y(bw), paint)
            }
        }
        val point = selected ?: samples.lastOrNull { it.watts() != null }
        point?.let { sample -> sample.watts()?.let { w ->
            paint.color = palette.accent
            if (selected != null) { paint.strokeWidth = density; canvas.drawLine(x(sample.time), top, x(sample.time), bottom, paint) }
            canvas.drawCircle(x(sample.time), y(w), 4f * density, paint)
        } }
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (samples.isEmpty()) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    touchX = event.x; touchY = event.y
                } else if (abs(event.x - touchX) > abs(event.y - touchY)) {
                    // Horizontal scrubbing selects samples; vertical swipes can scroll the page.
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                val fraction = ((event.x - 44 * density) / (width - 56 * density)).coerceIn(0f, 1f)
                val target = samples.first().time + ((endTime(samples.first().time) - samples.first().time) * fraction).toLong()
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
