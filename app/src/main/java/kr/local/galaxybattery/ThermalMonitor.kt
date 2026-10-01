package kr.local.galaxybattery

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock

/** OS events are independent of saved power samples; headroom is polled conservatively. */
class ThermalMonitor(context: Context, private val changed: () -> Unit) {
    private val manager = context.getSystemService(PowerManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var active = false
    private var generation = 0
    private var watcher: Watcher? = null
    var status = -1; private set
    var headroom: Float? = null; private set
    private val poll = object : Runnable {
        override fun run() {
            if (!active) return
            status = readStatus()
            headroom = if (Build.VERSION.SDK_INT >= 30 && manager != null) HeadroomCache.read(manager) else null
            changed(); main.postDelayed(this, 10000)
        }
    }
    fun start() {
        if (active) return
        active = true
        val token = ++generation
        if (Build.VERSION.SDK_INT >= 29 && manager != null) {
            watcher = try { StatusWatcher(manager) { value -> if (active && token == generation) { status = value.takeIf { it in 0..6 } ?: -1; changed() } } }
                catch (_: RuntimeException) { null }
        }
        poll.run()
    }
    fun stop() {
        active = false; generation++; main.removeCallbacks(poll); watcher?.stop(); watcher = null
    }
    private fun readStatus(): Int = if (Build.VERSION.SDK_INT >= 29) try {
        manager?.currentThermalStatus?.takeIf { it in 0..6 } ?: -1
    } catch (_: RuntimeException) { -1 } else -1
    private interface Watcher { fun stop() }
    @android.annotation.TargetApi(29)
    private class StatusWatcher(private val manager: PowerManager, changed: (Int) -> Unit) : Watcher {
        private val listener = PowerManager.OnThermalStatusChangedListener { changed(it) }
        init { manager.addThermalStatusListener(listener) }
        override fun stop() { try { manager.removeThermalStatusListener(listener) } catch (_: RuntimeException) {} }
    }
    /** Retain the call timestamp across Activity recreation to respect older firmware limits. */
    private object HeadroomCache {
        private var time = -10000L
        private var value: Float? = null
        @android.annotation.TargetApi(30)
        @Synchronized fun read(manager: PowerManager): Float? {
            val now = SystemClock.elapsedRealtime()
            if (now - time >= 10000) {
                time = now
                value = try { ThermalStatus.validHeadroom(manager.getThermalHeadroom(0)) } catch (_: RuntimeException) { null }
            }
            return value
        }
    }
}
