package kr.local.galaxybattery

import java.util.Locale

/** CPU deltas exclude guest counters, which are already included in user/nice. */
class HardwareTelemetry {
    data class Counter(val total: Long, val idle: Long)
    private var previous = emptyMap<Int, Counter>()
    private var previousSource = ""
    fun reset() { previous = emptyMap(); previousSource = "" }

    fun describe(raw: String, source: String): String {
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
                    val values = parts.drop(1).take(8).mapNotNull { it.toLongOrNull() }
                    if (id in 0..127 && values.size >= 4 && values.all { it >= 0 && it <= Long.MAX_VALUE / 8 }) {
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
        val result = buildString {
            append("CPU 코어별 사용률 · 온도\n")
            if (cores.isEmpty()) append("확인 불가 · 기기 접근 제한\n")
            cores.forEach { (id, online) ->
                val before = previous[id]; val now = current[id]
                val delta = if (before != null && now != null) now.total - before.total else 0
                val idle = if (before != null && now != null) now.idle - before.idle else -1
                val usage = if (online != "0" && delta > 0 && idle in 0..delta) (delta - idle) * 100.0 / delta else null
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
        return result
    }
    private fun format(value: Double) = String.format(Locale.US, "%.1f", value)
}
