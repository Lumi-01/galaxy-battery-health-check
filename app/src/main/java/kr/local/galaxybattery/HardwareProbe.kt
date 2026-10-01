package kr.local.galaxybattery

import java.io.File

/** Fixed, read-only kernel paths. Used locally and by the authorized Shizuku service. */
object HardwareProbe {
    private fun read(path: String): String? = try {
        File(path).bufferedReader().use { reader ->
            val buffer = CharArray(32768)
            var count = 0
            while (count < buffer.size) {
                val n = reader.read(buffer, count, buffer.size - count)
                if (n < 0) break
                count += n
            }
            String(buffer, 0, count).trim()
        }
    } catch (_: Exception) { null }

    fun readSnapshot(): String = buildString {
        read("/proc/stat")?.lineSequence()?.filter { it.matches(Regex("cpu[0-9]+ .*")) }
            ?.take(128)?.forEach { append("stat|$it\n") }
        val cpus = try { File("/sys/devices/system/cpu").listFiles()?.filter { it.name.matches(Regex("cpu[0-9]+")) } } catch (_: Exception) { null }
        cpus?.take(128)?.sortedBy { it.name.removePrefix("cpu").toInt() }?.forEach {
            append("core|${it.name.removePrefix("cpu")}|${read("${it.path}/online") ?: "unknown"}\n")
        }
        val zones = try { File("/sys/class/thermal").listFiles() } catch (_: Exception) { null }
        zones?.filter { it.name.matches(Regex("thermal_zone[0-9]+")) }?.take(256)?.forEach { zone ->
            val type = read("${zone.path}/type")?.take(80) ?: return@forEach
            if (Regex("cpu|gpu|soc|cluster|core|tsens", RegexOption.IGNORE_CASE).containsMatchIn(type)) {
                val raw = read("${zone.path}/temp")?.toLongOrNull()
                // Linux thermal ABI uses millidegrees Celsius, including subzero values.
                if (raw != null && raw in -40000..150000) append("temp|${type.replace('|', '_').replace('\n', ' ')}|${raw / 1000.0}\n")
            }
        }
        val percent = read("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage")?.toDoubleOrNull()
        val busy = read("/sys/class/kgsl/kgsl-3d0/gpubusy")?.split(Regex("\\s+"))?.mapNotNull { it.toLongOrNull() }
        val gpu = percent?.takeIf { it in 0.0..100.0 } ?: busy?.takeIf {
            it.size == 2 && it[1] > 0 && it[0] in 0..it[1]
        }?.let { it[0] * 100.0 / it[1] }
        if (gpu != null) append("gpu|$gpu\n")
    }
}
