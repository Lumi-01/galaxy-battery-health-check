package kr.local.galaxybattery

import java.util.Locale

/** CPU deltas exclude guest counters, which are already included in user/nice. */
class HardwareTelemetry {
    data class Counter(val total: Long, val idle: Long)
    data class Frame(val time: Long, val cpu: Map<Int, Double?>, val gpu: Double?)
    data class Sensor(val name: String, val value: Double)
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
        val temperatures = mutableListOf<Pair<String, Double>>()
        var gpu: Double? = null
        raw.take(65536).lineSequence().forEach { line ->
            val fields = line.split('|')
            when (fields.firstOrNull()) {
                "stat" -> {
                    val parts = fields.getOrNull(1)?.trim()?.split(Regex("\\s+")) ?: return@forEach
                    val id = parts.firstOrNull()?.removePrefix("cpu")?.toIntOrNull() ?: return@forEach
                    // Reject the complete row if a column is malformed; removing a
                    // bad column shifts idle/iowait and can fabricate a usage value.
                    val counters = parts.drop(1).take(8)
                    val values = counters.mapNotNull { it.toLongOrNull() }
                    if (id in 0..127 && values.size == counters.size && values.size >= 4 && values.all { it >= 0 && it <= Long.MAX_VALUE / 8 }) {
                        current[id] = Counter(values.sum(), values[3] + values.getOrElse(4) { 0 })
                        cores.putIfAbsent(id, "unknown")
                    }
                }
                "core" -> fields.getOrNull(1)?.toIntOrNull()?.takeIf { it in 0..127 }?.let {
                    cores[it] = fields.getOrNull(2) ?: "unknown"
                }
                "temp" -> if (fields.size == 3) fields[2].toDoubleOrNull()?.takeIf { it.isFinite() && it in -40.0..150.0 }?.let {
                    temperatures.add(fields[1] to it)
                }
                "gpu" -> gpu = fields.getOrNull(1)?.toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..100.0 }
            }
        }
        val usages = sortedMapOf<Int, Double?>()
        cpuTemperature = representative(temperatures, "cpu")
        gpuTemperature = representative(temperatures, "gpu")
        val result = buildString {
            append("CPU 코어별 사용률 · 온도\n")
            if (cores.isEmpty()) append("확인 불가 · 기기 접근 제한\n")
            cores.forEach { (id, online) ->
                val before = previous[id]; val now = current[id]
                val delta = if (before != null && now != null) now.total - before.total else 0
                val idle = if (before != null && now != null) now.idle - before.idle else -1
                val usage = if (online != "0" && delta > 0 && idle in 0..delta) (delta - idle) * 100.0 / delta else null
                usages[id] = usage
                // Only explicit cpuN/coreN labels map to a core. Cluster/SoC sensors
                // remain separate instead of repeating one temperature for every core.
                val temp = temperatures.firstOrNull { it.first.lowercase(Locale.US) in listOf("cpu$id", "cpu-$id", "cpu_${id}", "core$id") }?.second
                append("CPU $id   ${if (online == "0") "오프라인" else usage?.let { format(it) + "%" } ?: if (now != null && before == null) "다음 측정 대기" else "확인 불가"}   ·   ${temp?.let { format(it) + "°C" } ?: "온도 확인 불가"}\n")
            }
            append("\nGPU 전체 사용률   ${gpu?.let { format(it) + "%" } ?: "확인 불가"}\n")
            append("GPU 코어별 사용률 · 온도   기기에서 미제공\n")
            append("\nCPU·GPU·SoC 온도 센서\n")
            if (temperatures.isEmpty()) append("확인 불가 · 기기 접근 제한\n")
            temperatures.take(32).forEach { (name, value) -> append("$name   ${format(value)}°C\n") }
            append("\n출처: $source · 화면을 보는 동안 갱신")
        }
        previous = current
        latest = Frame(time, usages.toMap(), gpu)
        return result
    }
    private fun format(value: Double) = String.format(Locale.US, "%.1f", value)
    private fun representative(values: List<Pair<String, Double>>, kind: String): Sensor? {
        val candidates = values.filter { (name, _) ->
            val normalized = name.lowercase(Locale.US)
            if (kind == "gpu") normalized.contains("gpu") && !normalized.contains("cpu")
            else !normalized.contains("gpu") && (normalized.contains("cpu") || normalized.matches(Regex("core[0-9]+")))
        }
        // Prefer an explicitly named whole-device sensor. Otherwise show the
        // hottest named sensor, with its name, without inventing an average.
        val whole = candidates.filter { (name, _) ->
            name.lowercase(Locale.US) in listOf(kind, "$kind-therm", "${kind}_therm", "$kind-thermal", "${kind}_thermal")
        }
        return (whole.ifEmpty { candidates }).maxByOrNull { it.second }?.let { Sensor(it.first, it.second) }
    }
}
