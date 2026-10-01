package kr.local.galaxybattery

import android.app.Activity
import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import java.util.concurrent.Executors
import rikka.shizuku.Shizuku

/** Foreground-only telemetry; never requests permission automatically. */
class HardwareReader(activity: Activity, private val callback: (String, String) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val args = Shizuku.UserServiceArgs(ComponentName(activity, RemoteBatteryService::class.java))
        .daemon(false).processNameSuffix("hardware_reader").tag("hardware_read_only").version(4)
    private var bound = false
    private var remote: IRemoteBattery? = null
    private var active = false
    private var busy = false
    private var generation = 0
    private var bindingStarted = 0L
    private var closed = false
    private var timeout: Runnable? = null
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            if (!active || !bound) return
            remote = IRemoteBattery.Stub.asInterface(binder)
            poll()
        }
        override fun onServiceDisconnected(name: ComponentName) { remote = null }
    }
    fun start() { if (closed) return; if (!active) { active = true; generation++ }; poll() }
    fun poll() {
        if (closed || !active || busy) return
        if (bound && remote == null && android.os.SystemClock.elapsedRealtime() - bindingStarted > 5000) {
            try { Shizuku.unbindUserService(args, connection, true) } catch (_: RuntimeException) {}
            bound = false
        }
        val authorized = try { Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED } catch (_: RuntimeException) { false }
        if (authorized && !bound) try {
            bound = true
            bindingStarted = android.os.SystemClock.elapsedRealtime()
            Shizuku.bindUserService(args, connection)
        } catch (_: RuntimeException) { bound = false }
        val service = if (authorized) remote else null
        busy = true
        val current = generation
        var timedOut = false
        timeout = Runnable {
            if (!closed && active && busy && current == generation) {
                timedOut = true
                // Destroying a stalled Shizuku process releases its Binder call.
                // Keep busy until that worker returns so no unbounded queue is built.
                if (bound) try { Shizuku.unbindUserService(args, connection, true) } catch (_: RuntimeException) {}
                bound = false; remote = null
                callback("", "하드웨어 조회 시간 초과 · 다시 확인 중")
            }
        }.also { main.postDelayed(it, 5000) }
        worker.execute {
            var source = "앱 직접 읽기"
            var failedRemote = false
            val raw = try {
                if (service != null) { source = "Shizuku"; service.readHardware() ?: "" }
                else HardwareProbe.readSnapshot()
            } catch (_: Exception) {
                failedRemote = service != null
                source = "앱 직접 읽기 · Shizuku 접근 실패"
                HardwareProbe.readSnapshot()
            }
            main.post {
                timeout?.let { main.removeCallbacks(it) }; timeout = null
                busy = false
                if (!closed && failedRemote && current == generation) {
                    if (bound) try { Shizuku.unbindUserService(args, connection, true) } catch (_: RuntimeException) {}
                    bound = false; remote = null
                }
                if (!closed && !timedOut && active && current == generation) callback(raw, source)
            }
        }
    }
    fun stop() {
        active = false; generation++
        timeout?.let { main.removeCallbacks(it) }; timeout = null
        if (bound) try { Shizuku.unbindUserService(args, connection, true) } catch (_: RuntimeException) {}
        bound = false; remote = null
    }
    fun close() { closed = true; stop(); worker.shutdownNow(); main.removeCallbacksAndMessages(null) }
}
