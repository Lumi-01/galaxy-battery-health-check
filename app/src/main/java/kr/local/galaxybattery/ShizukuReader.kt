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

/** Owns permission, timeout and cleanup for one foreground battery query. */
class ShizukuReader(activity: Activity, private val callback: Callback) {
    interface Callback {
        fun status(message: String)
        fun result(fields: String)
        fun failure(message: String)
    }
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val args = Shizuku.UserServiceArgs(ComponentName(activity, RemoteBatteryService::class.java))
        .daemon(false).processNameSuffix("battery_reader").tag("battery_read_only").version(3)
    private var closed = false
    var busy = false
        private set
    private var bound = false
    private var waitingPermission = false
    private var generation = 0
    private var timeout: Runnable? = null
    var diagnostic = "not queried"
        private set

    private val received = Shizuku.OnBinderReceivedListener { main.post { connectionStatus() } }
    private val died = Shizuku.OnBinderDeadListener { main.post {
        if (!closed) {
            val attempted = busy || waitingPermission
            finish("연결이 끊겼어요. Shizuku를 시작한 뒤 다시 확인해 주세요.")
            if (attempted) callback.failure("조회 중 Shizuku 연결이 끊겼어요. Shizuku를 다시 시작해 주세요.")
        }
    } }
    private val permission = Shizuku.OnRequestPermissionResultListener { code, result -> main.post {
        if (!closed && code == REQUEST && waitingPermission) {
            waitingPermission = false
            if (result == PackageManager.PERMISSION_GRANTED) query()
            else failed("권한이 필요해요. Shizuku의 ‘승인된 앱’에서 배터리 상태를 허용해 주세요.")
        }
    } }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) { main.post {
            if (closed || !busy) return@post
            val current = generation
            val remote = IRemoteBattery.Stub.asInterface(binder)
            callback.status("배터리 기록을 확인하고 있어요…")
            worker.execute {
                val value = try { remote.readBattery() ?: "ERROR: empty response" }
                    catch (e: Exception) { "ERROR: ${e.javaClass.simpleName}" }
                main.post {
                    if (closed || !busy || generation != current) return@post
                    diagnostic = value.take(4096)
                    finish("배터리 기록을 확인했어요.")
                    when {
                        value.startsWith("ERROR:") -> failed("연결했지만 정보를 읽지 못했어요. Shizuku를 다시 시작하거나 배터리 로그를 불러와 주세요.")
                        value.startsWith("NO_FIELDS") -> callback.status("Shizuku 연결은 확인됐지만 기기에서 진단 값을 제공하지 않았어요. ‘로그 불러오기’로 SysDump 파일을 선택해 주세요.")
                        else -> callback.result(value)
                    }
                }
            }
        } }
        override fun onServiceDisconnected(name: ComponentName) { main.post {
            if (!closed && busy) failed("조회 중 연결이 끊겼어요. 다시 확인해 주세요.")
        } }
    }

    init {
        Shizuku.addBinderReceivedListenerSticky(received)
        Shizuku.addBinderDeadListener(died)
        Shizuku.addRequestPermissionResultListener(permission)
        connectionStatus()
    }

    private fun connectionStatus() {
        if (closed || busy || waitingPermission) return
        callback.status(if (Shizuku.pingBinder()) "연결 준비 완료 · 배터리 상태를 확인해 보세요."
            else "처음 한 번, Shizuku를 연결해 주세요. 다음부터는 버튼 하나로 확인할 수 있어요.")
    }

    fun query() {
        if (closed || busy || waitingPermission) return
        try {
            if (!Shizuku.pingBinder()) { failed("Shizuku가 실행 중이지 않아요. Shizuku에서 페어링 후 ‘시작’을 누르고 다시 확인해 주세요."); return }
            if (Shizuku.isPreV11() || Shizuku.getVersion() < 12) {
                failed("Shizuku를 최신 버전으로 업데이트해 주세요."); return
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                if (Shizuku.shouldShowRequestPermissionRationale()) {
                    failed("Shizuku의 ‘승인된 앱’에서 배터리 상태를 허용해 주세요.")
                } else {
                    waitingPermission = true
                    callback.status("권한 안내가 열리면 ‘허용’을 선택해 주세요.")
                    Shizuku.requestPermission(REQUEST)
                }
                return
            }
            busy = true
            val current = ++generation
            callback.status("배터리 정보에 연결하고 있어요…")
            timeout = Runnable {
                if (!closed && busy && generation == current) failed("Shizuku 연결 검증 시간이 초과됐어요. 실행 상태와 앱 권한을 확인한 뒤 다시 시도해 주세요.")
            }.also { main.postDelayed(it, 25000) }
            bound = true
            Shizuku.bindUserService(args, connection)
        } catch (e: RuntimeException) {
            waitingPermission = false
            diagnostic = "ERROR: ${e.javaClass.simpleName}"
            failed("Shizuku에 연결하지 못했어요. 실행 상태와 앱 권한을 확인해 주세요.")
        }
    }

    private fun failed(message: String) {
        finish(message)
        if (!closed) callback.failure(message)
    }

    private fun finish(message: String) {
        busy = false
        waitingPermission = false
        generation++
        timeout?.let { main.removeCallbacks(it) }
        if (bound) {
            bound = false
            try { Shizuku.unbindUserService(args, connection, true) } catch (_: RuntimeException) {}
        }
        if (!closed) callback.status(message)
    }

    fun close() {
        closed = true
        finish("")
        Shizuku.removeBinderReceivedListener(received)
        Shizuku.removeBinderDeadListener(died)
        Shizuku.removeRequestPermissionResultListener(permission)
        worker.shutdownNow()
    }

    companion object { private const val REQUEST = 102 }
}
