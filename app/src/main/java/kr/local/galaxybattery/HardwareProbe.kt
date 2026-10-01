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
        read("/proc/stat")?.lineSequence()?.filter { it.matches(Regex("cpu(?:[0-9]+)?\\s+.*")) }
            ?.take(129)?.forEach { append("stat|$it\n") }
        val cpus = try { File("/sys/devices/system/cpu").listFiles()?.filter { it.name.matches(Regex("cpu[0-9]+")) } } catch (_: Exception) { null }
        cpus?.take(128)?.sortedBy { it.name.removePrefix("cpu").toInt() }?.forEach {
            append("core|${it.name.removePrefix("cpu")}|${read("${it.path}/online") ?: "unknown"}\n")
            // CPUFreq exports kHz. Prefer hardware feedback, then the policy's
            // current requested clock; never substitute the maximum frequency.
            val frequency = listOf("cpuinfo_cur_freq", "scaling_cur_freq").firstNotNullOfOrNull { attribute ->
                read("${it.path}/cpufreq/$attribute")?.toLongOrNull()?.takeIf { value -> value in 1..10000000 }
            }
            if (frequency != null) append("freq|${it.name.removePrefix("cpu")}|${frequency / 1000.0}\n")
        }
        val zones = try { File("/sys/class/thermal").listFiles() } catch (_: Exception) { null }
        zones?.filter { it.name.matches(Regex("thermal_zone[0-9]+")) }?.take(256)?.forEach { zone ->
            val type = read("${zone.path}/type")?.take(80) ?: return@forEach
            val raw = read("${zone.path}/temp")?.toLongOrNull()
            // Retain zone identity: different physical sensors may share a name.
            if (raw != null && raw in -40000..150000) append("temp|${type.replace('|', '_').replace('\n', ' ')}|${raw / 1000.0}|${zone.name}\n")
        }
        val percent = read("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage")?.toDoubleOrNull()
        val busyFields = read("/sys/class/kgsl/kgsl-3d0/gpubusy")?.split(Regex("\\s+"))
        val busy = busyFields?.takeIf { it.size == 2 }?.mapNotNull { it.toLongOrNull() }
        val gpu = percent?.takeIf { it in 0.0..100.0 } ?: busy?.takeIf {
            it.size == 2 && it[1] > 0 && it[0] in 0..it[1]
        }?.let { it[0] * 100.0 / it[1] }
        if (gpu != null) append("gpu|$gpu\n")
        // KGSL and devfreq clock attributes use Hz, independently of CPUFreq.
        val gpuFrequency = listOf("/sys/class/kgsl/kgsl-3d0/gpuclk", "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq")
            .firstNotNullOfOrNull { read(it)?.toLongOrNull()?.takeIf { value -> value in 1..10000000000L } }
        if (gpuFrequency != null) append("gpufreq|${gpuFrequency / 1000000.0}\n")
    }

    /** Thermal-only polling avoids sampling CPU/GPU charts at the thermal interval. */
    fun readCooling(): String = buildString {
        val devices = try { File("/sys/class/thermal").listFiles() } catch (_: Exception) { null }
        devices?.filter { it.name.matches(Regex("cooling_device[0-9]+")) }?.take(64)?.forEach { device ->
            val type = read("${device.path}/type")?.take(80) ?: return@forEach
            // Only named CPU/GPU/frequency devices are relevant here; a generic
            // fan or battery cooling state must not be labelled CPU throttling.
            if (!Regex("cpu|gpu|cpufreq|devfreq", RegexOption.IGNORE_CASE).containsMatchIn(type)) return@forEach
            val state = read("${device.path}/cur_state")?.toLongOrNull()
            val maximum = read("${device.path}/max_state")?.toLongOrNull()
            if (state != null && maximum != null && maximum > 0 && state in 0..maximum)
                append("cool|${type.replace('|', '_').replace('\n', ' ')}|$state|$maximum|${device.name}\n")
        }
    }
}
