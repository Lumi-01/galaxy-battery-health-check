package kr.local.galaxybattery

import java.util.Locale

/** Validated telemetry shared by charts and labels; unavailable never means zero. */
class HardwareTelemetry {
    data class Counter(val total: Long, val idle: Long)
    data class Sensor(val name: String, val value: Double, val id: String = name)
    data class Cooling(val name: String, val state: Long, val maximum: Long, val id: String)
    data class Frame(
        val time: Long, val cpu: Map<Int, Double?>, val gpu: Double?,
        val cpuTotal: Double? = null,
        val cpuClockMHz: Map<Int, Double> = emptyMap(), val gpuClockMHz: Double? = null,
        val cpuTemperatures: Map<Int, Double> = emptyMap(),
        val cpuOnline: Map<Int, Boolean?> = emptyMap(), val sensors: List<Sensor> = emptyList(),
        val cpuTemperature: Sensor? = null, val gpuTemperature: Sensor? = null,
        val cooling: List<Cooling> = emptyList()
    )
    var latest = Frame(0L, emptyMap(), null); private set
    var cpuTemperature: Sensor? = null; private set
    var gpuTemperature: Sensor? = null; private set
    private var previous = emptyMap<Int, Counter>()
    private var previousSource = ""
    fun reset() {
        previous = emptyMap(); previousSource = ""; latest = Frame(0L, emptyMap(), null)
        cpuTemperature = null; gpuTemperature = null
    }

    @JvmOverloads fun describe(raw: String, source: String, time: Long = System.currentTimeMillis()): String {
        if (source != previousSource) previous = emptyMap()
        previousSource = source
        val current = mutableMapOf<Int, Counter>()
        val cores = sortedMapOf<Int, String>()
        val clocks = sortedMapOf<Int, Double>()
        val sensors = linkedMapOf<String, Sensor>()
        val cooling = linkedMapOf<String, Cooling>()
        var gpu: Double? = null
        var gpuClock: Double? = null
        raw.take(65536).lineSequence().forEach { line ->
            val fields = line.split('|')
            when (fields.firstOrNull()) {
                "stat" -> {
                    val parts = fields.getOrNull(1)?.trim()?.split(Regex("\\s+")) ?: return@forEach
                    val label = parts.firstOrNull() ?: return@forEach
                    if (!label.matches(Regex("cpu(?:[0-9]+)?"))) return@forEach
                    val id = if (label == "cpu") -1 else label.removePrefix("cpu").toIntOrNull() ?: return@forEach
                    // guest is already included in user/nice; malformed columns
                    // invalidate the row rather than shifting idle/iowait indices.
                    val columns = parts.drop(1).take(8)
                    val values = columns.mapNotNull { it.toLongOrNull() }
                    if (id in -1..127 && values.size == columns.size && values.size >= 4 && values.all { it in 0..Long.MAX_VALUE / 8 }) {
                        current[id] = Counter(values.sum(), values[3] + values.getOrElse(4) { 0 })
                        if (id >= 0) cores.putIfAbsent(id, "unknown")
                    }
                }
                "core" -> coreId(fields.getOrNull(1))?.let { cores[it] = fields.getOrNull(2) ?: "unknown" }
                "freq" -> coreId(fields.getOrNull(1))?.let { id ->
                    number(fields.getOrNull(2), 0.001..10000.0)?.let { clocks[id] = it; cores.putIfAbsent(id, "unknown") }
                }
                "temp" -> if (fields.size in 3..4 && fields[1].isNotBlank()) {
                    number(fields[2], -40.0..150.0)?.let {
                        val name = fields[1].take(80)
                        val id = fields.getOrNull(3)?.takeIf { value -> value.isNotBlank() }?.take(80) ?: name
                        sensors[id] = Sensor(name, it, id)
                    }
                }
                "gpu" -> gpu = number(fields.getOrNull(1), 0.0..100.0)
                "gpufreq" -> gpuClock = number(fields.getOrNull(1), 0.001..10000.0)
                "cool" -> if (fields.size == 5 && fields[1].isNotBlank()) {
                    val state = fields[2].toLongOrNull(); val maximum = fields[3].toLongOrNull()
                    if (state != null && maximum != null && maximum > 0 && state in 0..maximum)
                        cooling[fields[4]] = Cooling(fields[1].take(80), state, maximum, fields[4].take(80))
                }
            }
        }
        val temperatures = sensors.values.toList()
        cpuTemperature = representative(temperatures, "cpu")
        gpuTemperature = representative(temperatures, "gpu")
        val usages = cores.mapValues { (id, online) -> if (online == "0") null else usage(id, current) }
        val coreTemperatures = cores.keys.mapNotNull { id ->
            temperatures.firstOrNull { coreTemperatureId(it.name) == id }?.let { id to it.value }
        }.toMap()
        // Prefer the kernel's aggregate. Fallback requires complete, valid deltas
        // from every online core and weights them by measured CPU time.
        val aggregate = if (current.containsKey(-1)) usage(-1, current) else aggregate(cores, current)
        val online = cores.mapValues { (_, value) -> when (value) { "0" -> false; "1" -> true; else -> null } }
        latest = Frame(time, usages, gpu, aggregate, clocks, gpuClock, coreTemperatures, online,
            temperatures, cpuTemperature, gpuTemperature, cooling.values.toList())
        val previousCounters = previous
        previous = current
        return buildString {
            append("CPU 코어별 사용률 · 온도\n")
            if (cores.isEmpty()) append("확인 불가 · 기기 접근 제한\n")
            cores.forEach { (id, state) ->
                val label = if (state == "0") "오프라인" else usages[id]?.let { format(it) + "%" }
                    ?: if (current[id] != null && previousCounters[id] == null) "다음 측정 대기" else "확인 불가"
                append("CPU $id   $label   ·   ${coreTemperatures[id]?.let { format(it) + "°C" } ?: "온도 확인 불가"}\n")
            }
            append("\nGPU 전체 사용률   ${gpu?.let { format(it) + "%" } ?: "확인 불가"}\n")
            append("GPU 코어별 사용률 · 온도   기기에서 미제공\n")
            append("\nCPU·GPU·SoC 온도 센서\n")
            if (temperatures.isEmpty()) append("확인 불가 · 기기 접근 제한\n")
            temperatures.forEach { append("${it.name}   ${format(it.value)}°C\n") }
            append("\n출처: $source · 화면을 보는 동안 갱신")
        }
    }
    private fun delta(id: Int, current: Map<Int, Counter>): Pair<Long, Long>? {
        val before = previous[id] ?: return null
        val now = current[id] ?: return null
        val total = now.total - before.total
        val idle = now.idle - before.idle
        return if (total > 0 && idle in 0..total) total to idle else null
    }
    private fun usage(id: Int, current: Map<Int, Counter>): Double? =
        delta(id, current)?.let { (total, idle) -> (total - idle) * 100.0 / total }
    private fun aggregate(cores: Map<Int, String>, current: Map<Int, Counter>): Double? {
        val online = cores.filterValues { it != "0" }.keys
        if (online.isEmpty()) return null
        val deltas = online.map { delta(it, current) ?: return null }
        val total = deltas.sumOf { it.first.toDouble() }
        return deltas.sumOf { (it.first - it.second).toDouble() } * 100.0 / total
    }
    private fun coreId(value: String?) = value?.toIntOrNull()?.takeIf { it in 0..127 }
    private fun number(value: String?, range: ClosedFloatingPointRange<Double>) =
        value?.toDoubleOrNull()?.takeIf { it.isFinite() && it in range }
    private fun coreTemperatureId(name: String): Int? =
        Regex("(?:cpu[-_]?|core)([0-9]+)(?:[-_](?:therm|thermal))?", RegexOption.IGNORE_CASE)
            .matchEntire(name)?.groupValues?.get(1)?.toIntOrNull()
    private fun format(value: Double) = String.format(Locale.US, "%.1f", value)
    private fun representative(values: List<Sensor>, kind: String): Sensor? {
        val candidates = values.filter {
            val name = it.name.lowercase(Locale.US)
            if (kind == "gpu") name.contains("gpu") && !name.contains("cpu")
            else !name.contains("gpu") && (name.contains("cpu") || coreTemperatureId(name) != null)
        }
        val whole = candidates.filter {
            it.name.lowercase(Locale.US) in listOf(kind, "$kind-therm", "${kind}_therm", "$kind-thermal", "${kind}_thermal")
        }
        return (whole.ifEmpty { candidates }).maxByOrNull { it.value }
    }
}
