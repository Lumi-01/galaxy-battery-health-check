package kr.local.galaxybattery

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Summary first; per-core charts and sensor rows are created only when expanded. */
class HardwareMonitorView(context: Context, private val palette: AppPalette,
                          private val settings: AppSettings, connect: () -> Unit) : LinearLayout(context) {
    private data class GraphBlock(val graph: UsageGraphView, val clock: TextView, val usage: TextView,
                                  val temperature: TextView, val note: TextView)
    private data class SensorRow(val name: TextView, val value: TextView)
    private var frames: List<HardwareTelemetry.Frame> = emptyList()
    private var latest = HardwareTelemetry.Frame(0L, emptyMap(), null)
    private val cpuDetails = LinearLayout(context).apply { orientation = VERTICAL }
    private val coreBlocks = linkedMapOf<Int, GraphBlock>()
    private val sensorDetails = LinearLayout(context).apply { orientation = VERTICAL }
    private val sensorRows = linkedMapOf<String, SensorRow>()
    private var sensorSignature = emptyList<Pair<String, String>>()
    private val cpuSummary: GraphBlock
    private val gpuSummary: GraphBlock
    private val gpuDetails: TextView
    private val batteryTemperature: TextView
    private val cpuTemperature: TextView
    private val gpuTemperature: TextView
    private val footer: TextView

    init {
        orientation = VERTICAL
        val cpu = card()
        cpuSummary = graph(cpu, "CPU", "전체 코어", true)
        divider(cpu)
        cpu.addView(OneUiToggle(context, palette, "CPU 코어별 보기", "각 코어의 그래프와 현재 값", settings.cpuCores) {
            settings.cpuCores = it; renderCores()
        })
        cpu.addView(cpuDetails)

        val gpu = card()
        gpuSummary = graph(gpu, "GPU", "전체 코어", false)
        divider(gpu)
        gpu.addView(OneUiToggle(context, palette, "GPU 코어별 보기", "기기에서 제공하는 정보 확인", settings.gpuCores) {
            settings.gpuCores = it; gpuDetails.visibility = if (it) VISIBLE else GONE
        })
        gpuDetails = label(gpu, "현재 조회 방식은 GPU 전체 사용률과 클럭만 제공해요. GPU 코어별 그래프는 표시할 수 없어요.", 12, palette.muted)
        gpuDetails.visibility = if (settings.gpuCores) VISIBLE else GONE

        val temperatures = card()
        label(temperatures, "온도", 20, palette.foreground, true)
        label(temperatures, "배터리 · CPU · GPU", 12, palette.muted).setPadding(0, dp(3), 0, dp(12))
        batteryTemperature = temperatureRow(temperatures, "배터리")
        cpuTemperature = temperatureRow(temperatures, "CPU")
        gpuTemperature = temperatureRow(temperatures, "GPU")
        divider(temperatures)
        temperatures.addView(OneUiToggle(context, palette, "센서별 온도 보기", "각 센서의 이름과 측정 온도", settings.temperatureSensors) {
            settings.temperatureSensors = it; renderSensors()
        })
        temperatures.addView(sensorDetails)

        footer = label(this, "${settings.hardwareSeconds}초마다 갱신 · 최근 120개 · 화면을 보는 동안 측정", 12, palette.muted)
        footer.setPadding(dp(4), dp(16), dp(4), dp(8))
        val connection = Button(context).apply {
            text = "Shizuku 연결 · 권한 확인"; isAllCaps = false; textSize = 13f
            setTextColor(palette.accent); minHeight = 0; minimumHeight = 0
            background = shape(palette.actionSurface, 24); setOnClickListener { connect() }
        }
        addView(connection, LayoutParams(-1, dp(48)))
        label(this, "읽지 못한 값은 —로 표시해요. Shizuku 연결 후 읽을 수 있는 항목이 늘어날 수 있어요.", 12, palette.muted)
            .setPadding(dp(4), dp(12), dp(4), dp(4))
        renderCores(); renderSensors()
    }

    fun setFrames(values: List<HardwareTelemetry.Frame>, source: String) {
        frames = values; latest = values.lastOrNull() ?: HardwareTelemetry.Frame(0L, emptyMap(), null)
        cpuSummary.graph.setFrames(values); gpuSummary.graph.setFrames(values)
        val clocks = latest.cpuClockMHz.filterKeys { latest.cpuOnline[it] != false }.values.toList()
        cpuSummary.clock.text = clockRange(clocks)
        cpuSummary.usage.text = percent(latest.cpuTotal)
        cpuSummary.temperature.text = temperature(latest.cpuTemperature?.value)
        cpuSummary.note.text = latest.cpuTemperature?.let { "온도 센서 · ${it.name}" } ?: ""
        cpuSummary.note.visibility = if (latest.cpuTemperature != null) VISIBLE else GONE
        gpuSummary.clock.text = frequency(latest.gpuClockMHz)
        gpuSummary.usage.text = percent(latest.gpu)
        gpuSummary.temperature.text = temperature(latest.gpuTemperature?.value)
        gpuSummary.note.text = latest.gpuTemperature?.let { "온도 센서 · ${it.name}" } ?: ""
        gpuSummary.note.visibility = if (latest.gpuTemperature != null) VISIBLE else GONE
        cpuTemperature.text = temperature(latest.cpuTemperature?.value)
        gpuTemperature.text = temperature(latest.gpuTemperature?.value)
        val time = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date(latest.time))
        footer.text = "$source · ${settings.hardwareSeconds}초마다 갱신\n$time 업데이트 · 최근 120개 · 이 화면에서만 측정"
        renderCores(); renderSensors()
    }
    fun setBatteryTemperature(value: Double?) { batteryTemperature.text = temperature(value) }

    private fun renderCores() {
        cpuDetails.visibility = if (settings.cpuCores) VISIBLE else GONE
        if (!settings.cpuCores) return
        val ids = latest.cpu.keys.sorted()
        if (coreBlocks.keys.toList() != ids || cpuDetails.childCount == 0) {
            cpuDetails.removeAllViews(); coreBlocks.clear()
            if (ids.isEmpty()) label(cpuDetails, "코어 정보를 읽을 수 없어요.", 13, palette.muted)
            ids.forEach { id ->
                val container = LinearLayout(context).apply {
                    orientation = VERTICAL; setPadding(dp(14), dp(14), dp(14), dp(14))
                    background = shape(palette.background, 20)
                }
                cpuDetails.addView(container, LayoutParams(-1, -2).apply { topMargin = dp(12) })
                coreBlocks[id] = graph(container, "CPU $id", "코어 ${id + 1}", true, id)
            }
        }
        coreBlocks.forEach { (id, block) ->
            block.graph.setFrames(frames)
            val offline = latest.cpuOnline[id] == false
            block.clock.text = frequency(if (offline) null else latest.cpuClockMHz[id])
            block.usage.text = if (offline) "오프라인" else percent(latest.cpu[id])
            block.temperature.text = temperature(latest.cpuTemperatures[id])
            block.note.text = if (offline) "현재 코어가 꺼져 있어요." else ""
            block.note.visibility = if (offline) VISIBLE else GONE
        }
    }
    private fun renderSensors() {
        sensorDetails.visibility = if (settings.temperatureSensors) VISIBLE else GONE
        if (!settings.temperatureSensors) return
        val sensors = latest.sensors.sortedWith(compareBy({ group(it.name) }, { it.name }, { it.id }))
        val signature = sensors.map { it.id to it.name }
        if (signature != sensorSignature || sensorDetails.childCount == 0) {
            sensorSignature = signature; sensorDetails.removeAllViews(); sensorRows.clear()
            if (sensors.isEmpty()) label(sensorDetails, "현재 읽을 수 있는 개별 온도 센서가 없어요.", 13, palette.muted)
            var previousGroup = -1
            sensors.forEach { sensor ->
                val group = group(sensor.name)
                if (group != previousGroup) {
                    label(sensorDetails, listOf("CPU 센서", "GPU 센서", "기타 센서")[group], 12, palette.accent, true)
                        .setPadding(0, dp(16), 0, dp(6))
                    previousGroup = group
                } else divider(sensorDetails)
                val row = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(58) }
                val titles = LinearLayout(context).apply { orientation = VERTICAL; setPadding(0, 0, dp(12), 0) }
                val name = label(titles, sensor.name, 14, palette.foreground).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
                if (sensor.id != sensor.name) label(titles, sensor.id, 11, palette.muted)
                row.addView(titles, LayoutParams(0, -2, 1f))
                val value = TextView(context).apply { textSize = 16f; setTextColor(palette.foreground); setTypeface(Typeface.DEFAULT, Typeface.BOLD) }
                row.addView(value, LayoutParams(-2, -2)); sensorDetails.addView(row)
                sensorRows[sensor.id] = SensorRow(name, value)
            }
        }
        sensors.forEach { sensorRows[it.id]?.value?.text = temperature(it.value) }
    }
    private fun group(name: String): Int = when {
        name.contains("gpu", true) -> 1
        name.contains("cpu", true) || name.matches(Regex("core[0-9]+", RegexOption.IGNORE_CASE)) -> 0
        else -> 2
    }
    private fun graph(parent: LinearLayout, title: String, subtitle: String, cpu: Boolean, core: Int? = null): GraphBlock {
        label(parent, title, if (core == null) 20 else 16, palette.foreground, true)
        label(parent, subtitle, 12, palette.muted).setPadding(0, dp(2), 0, dp(6))
        val graph = UsageGraphView(context, palette, cpu, core)
        parent.addView(graph, LayoutParams(-1, dp(if (core == null) 120 else 115)).apply { topMargin = dp(4); bottomMargin = dp(12) })
        val metrics = LinearLayout(context).apply { orientation = HORIZONTAL; isBaselineAligned = false }
        parent.addView(metrics)
        fun metric(title: String): TextView {
            val cell = LinearLayout(context).apply { orientation = VERTICAL }
            metrics.addView(cell, LayoutParams(0, -2, 1f))
            label(cell, title, 11, palette.muted)
            return label(cell, "—", if (core == null) 16 else 14, palette.foreground, true).apply {
                maxLines = 2; setPadding(0, dp(4), dp(4), dp(4))
            }
        }
        val clock = metric(if (cpu && core == null) "클럭 범위" else "클럭")
        val usage = metric("사용률"); val temp = metric("온도")
        val note = label(parent, "", 11, palette.muted).apply { visibility = GONE; setPadding(0, dp(6), 0, 0) }
        return GraphBlock(graph, clock, usage, temp, note)
    }
    private fun temperatureRow(parent: LinearLayout, title: String): TextView {
        val row = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(42) }
        row.addView(TextView(context).apply { text = title; textSize = 14f; setTextColor(palette.muted) }, LayoutParams(0, -2, 1f))
        val value = TextView(context).apply { text = "—"; textSize = 18f; setTextColor(palette.foreground); setTypeface(Typeface.DEFAULT, Typeface.BOLD) }
        row.addView(value); parent.addView(row); return value
    }
    private fun card(): LinearLayout = LinearLayout(context).apply {
        orientation = VERTICAL; setPadding(dp(20), dp(18), dp(20), dp(14))
        background = shape(palette.card, 24)
        this@HardwareMonitorView.addView(this, LayoutParams(-1, -2).apply { topMargin = dp(20) })
    }
    private fun divider(parent: LinearLayout) {
        parent.addView(View(context).apply { setBackgroundColor((palette.muted and 0xFFFFFF) or (28 shl 24)); importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO },
            LayoutParams(-1, dp(1)).apply { topMargin = dp(10); bottomMargin = dp(4) })
    }
    private fun label(parent: LinearLayout, title: String, size: Int, color: Int, bold: Boolean = false): TextView = TextView(context).apply {
        text = title; textSize = size.toFloat(); setTextColor(color); includeFontPadding = false
        if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        parent.addView(this, LayoutParams(-1, -2))
    }
    private fun shape(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun percent(value: Double?) = value?.let { String.format(Locale.US, "%.1f%%", it) } ?: "—"
    private fun temperature(value: Double?) = value?.let { String.format(Locale.US, "%.1f°C", it) } ?: "—"
    private fun frequency(value: Double?) = value?.let {
        if (it >= 1000) String.format(Locale.US, "%.2f GHz", it / 1000) else String.format(Locale.US, "%.0f MHz", it)
    } ?: "—"
    private fun clockRange(values: List<Double>): String {
        if (values.isEmpty()) return "—"
        val low = values.min(); val high = values.max()
        if (high - low < .5) return frequency(high)
        return if (high >= 1000) String.format(Locale.US, "%.2f–%.2f\nGHz", low / 1000, high / 1000)
        else String.format(Locale.US, "%.0f–%.0f\nMHz", low, high)
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
}
