package kr.local.galaxybattery

import java.util.Locale

/** Android reports net battery current in microamps and battery voltage in millivolts. */
object ChargePower {
    const val MISSING = Int.MIN_VALUE
    @JvmStatic fun watts(currentUa: Int?, voltageMv: Int?): Double? {
        if (currentUa == null || currentUa == MISSING || voltageMv == null || voltageMv !in 1000..20000) return null
        if (currentUa.toLong() !in -30000000L..30000000L) return null
        return currentUa.toDouble() * voltageMv / 1_000_000_000.0
    }
    @JvmStatic fun text(watts: Double?): String = watts?.let { String.format(Locale.US, "%.1f W", it) } ?: "— W"
    @JvmStatic fun chip(watts: Double?): String = watts?.let { String.format(Locale.US, "%.1fW", it) } ?: "—W"
    /** Never take abs(): the live chip must preserve the direction of battery power. */
    @JvmStatic fun liveWatts(sample: Sample?, showCharging: Boolean, showDischarge: Boolean): Double? =
        (if (showCharging) sample?.chargingWatts() else null)
            ?: if (showDischarge) sample?.watts()?.takeIf { it < 0 } else null

    data class Sample @JvmOverloads constructor(val time: Long, val currentUa: Int, val voltageMv: Int, val level: Int,
                      val temperature: Int, val status: Int, val plugged: Int, val thermalStatus: Int = -1) {
        fun watts(): Double? = watts(currentUa, voltageMv)
        fun dischargeWatts(): Double? = watts()?.takeIf { it < 0 }?.let { -it }
        fun chargingWatts(): Double? = watts()?.takeIf { plugged > 0 && (status == 2 || status == 5) && it >= 0 }
    }

    class Stats {
        private val chargingMean = Mean()
        private val dischargeMean = Mean()
        val chargingAverage: Double? get() = chargingMean.average
        val dischargeAverage: Double? get() = dischargeMean.average
        var minimum: Double? = null; private set
        var maximum: Double? = null; private set
        var chargingCount = 0L; private set
        var dischargeCount = 0L; private set
        var dischargeMinimum: Double? = null; private set
        var dischargeMaximum: Double? = null; private set
        fun add(sample: Sample) {
            sample.dischargeWatts()?.let {
                dischargeMean.add(it)
                dischargeCount++
                dischargeMinimum = dischargeMinimum?.let { old -> minOf(old, it) } ?: it
                dischargeMaximum = dischargeMaximum?.let { old -> maxOf(old, it) } ?: it
            }
            sample.chargingWatts()?.let {
                chargingMean.add(it)
                minimum = minimum?.let { old -> minOf(old, it) } ?: it
                maximum = maximum?.let { old -> maxOf(old, it) } ?: it
                chargingCount++
            }
        }
    }

    /** Arithmetic mean of valid samples; restore uses the whole-session count, not the graph tail. */
    class Mean {
        private var count = 0L
        private var sum = 0.0
        val average: Double? get() = if (count > 0) sum / count else null
        fun add(value: Double) { if (value.isFinite() && value >= 0) { sum += value; count++ } }
        fun restore(samples: Long, average: Double?) {
            count = if (samples > 0 && average != null && average.isFinite() && average >= 0) samples else 0
            sum = if (count > 0) average!! * count else 0.0
        }
    }
}
