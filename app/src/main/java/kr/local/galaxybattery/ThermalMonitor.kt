package kr.local.galaxybattery

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock

/** OS events are independent of saved power samples; headroom is polled conservatively. */
class ThermalMonitor(context: Activity, private val changed: () -> Unit) {
    private val manager = context.getSystemService(PowerManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var active = false
    private var generation = 0
    private var watcher: Watcher? = null
    var status = -1; private set
    var headroom: Float? = null; private set
    var statusUpdatedAt = 0L; private set
    var headroomUpdatedAt = 0L; private set
    var coolingUpdatedAt = 0L; private set
    var coolingSource = "확인 중"; private set
    var cooling: List<HardwareTelemetry.Cooling> = emptyList(); private set
    private val telemetry = HardwareTelemetry()
    private val reader = HardwareReader(context, thermalOnly = true) { raw, source ->
        if (active) {
            telemetry.describe(raw, source)
            cooling = telemetry.latest.cooling
            coolingSource = source; coolingUpdatedAt = System.currentTimeMillis()
            changed()
        }
    }
    private val poll = object : Runnable {
        override fun run() {
            if (!active) return
            status = readStatus()
            statusUpdatedAt = System.currentTimeMillis()
            headroom = if (Build.VERSION.SDK_INT >= 30 && manager != null) HeadroomCache.read(manager) else null
            headroomUpdatedAt = if (headroom != null) HeadroomCache.updatedAt else 0L
            reader.poll()
            changed(); main.postDelayed(this, 10000)
        }
    }
    fun start() {
        if (active) return
        active = true
        cooling = emptyList(); coolingUpdatedAt = 0L; coolingSource = "확인 중"
        val token = ++generation
        if (Build.VERSION.SDK_INT >= 29 && manager != null) {
            watcher = try { StatusWatcher(manager, main) { value -> if (active && token == generation) {
                status = value.takeIf { it in 0..6 } ?: -1; statusUpdatedAt = System.currentTimeMillis(); changed()
            } } }
                catch (_: RuntimeException) { null }
        }
        poll.run()
        reader.start()
    }
    fun stop() {
        active = false; generation++; main.removeCallbacks(poll); watcher?.stop(); watcher = null
        reader.stop()
    }
    fun close() { stop(); reader.close() }
    private fun readStatus(): Int = if (Build.VERSION.SDK_INT >= 29) try {
        manager?.currentThermalStatus?.takeIf { it in 0..6 } ?: -1
    } catch (_: RuntimeException) { -1 } else -1
    private interface Watcher { fun stop() }
    @android.annotation.TargetApi(29)
    private class StatusWatcher(private val manager: PowerManager, main: Handler, changed: (Int) -> Unit) : Watcher {
        private val listener = PowerManager.OnThermalStatusChangedListener { changed(it) }
        init { manager.addThermalStatusListener(java.util.concurrent.Executor { main.post(it) }, listener) }
        override fun stop() { try { manager.removeThermalStatusListener(listener) } catch (_: RuntimeException) {} }
    }
    /** Retain the call timestamp across Activity recreation to respect older firmware limits. */
    private object HeadroomCache {
        private var time = -10000L
        private var value: Float? = null
        var updatedAt = 0L; private set
        @android.annotation.TargetApi(30)
        @Synchronized fun read(manager: PowerManager): Float? {
            val now = SystemClock.elapsedRealtime()
            if (now - time >= 10000) {
                time = now
                value = try { ThermalStatus.validHeadroom(manager.getThermalHeadroom(0)) } catch (_: RuntimeException) { null }
                updatedAt = System.currentTimeMillis()
            }
            return value
        }
    }
}
