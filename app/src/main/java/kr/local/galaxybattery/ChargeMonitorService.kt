package kr.local.galaxybattery

import android.app.*
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.*
import java.io.File
import java.util.ArrayDeque
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.ScheduledFuture

/** User-started foreground measurement. The screen remains off while the CPU samples. */
class ChargeMonitorService : Service() {
    private val worker = Executors.newSingleThreadScheduledExecutor()
    private val store by lazy { PowerLogStore(File(noBackupFilesDir, "power-history")) }
    private val preferences by lazy { getSharedPreferences("power-monitor", MODE_PRIVATE) }
    private val settings by lazy { AppSettings(this) }
    @Volatile private var nextSample: ScheduledFuture<*>? = null
    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (initialized && !stopping && !worker.isShutdown) worker.execute {
            if (stopping) return@execute
            if (key == AppSettings.POWER_INTERVAL) {
                nextSample?.cancel(false)
                sampleAndSchedule()
            } else if (key == AppSettings.SHOW_DISCHARGE) {
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(snapshot.samples.lastOrNull()))
            }
        }
    }
    private var wakeLock: PowerManager.WakeLock? = null
    private var wakeRenewed = 0L
    @Volatile private var stopping = false
    private var initialized = false
    private var sessionId: String? = null
    private var started = 0L
    private var count = 0L
    private var minimum: Double? = null
    private var maximum: Double? = null
    private val points = ArrayDeque<ChargePower.Sample>()
    private val zero = ZeroPowerTracker()

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(CHANNEL, "충전 전력 실시간 업데이트", NotificationManager.IMPORTANCE_LOW).apply {
            description = "진행 중인 측정의 현재 전력만 표시합니다."
            setSound(null, null); enableVibration(false); setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:power-monitor").apply { setReferenceCounted(false) }
        settings.preferences.registerOnSharedPreferenceChangeListener(settingsListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP || intent?.action == DISMISSED) {
            stopping = true
            worker.execute {
                sessionId?.let { try { store.finish(it, System.currentTimeMillis()) } catch (_: Exception) {} }
                preferences.edit().remove("session").commit()
                snapshot = snapshot.copy(active = false, error = null)
                Handler(Looper.getMainLooper()).post { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
            }
            return START_NOT_STICKY
        }
        if (initialized) return START_STICKY
        initialized = true
        val notification = notification(null)
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(NOTIFICATION_ID, notification)
        snapshot = Snapshot(active = true)
        worker.execute {
            try {
                val savedId = preferences.getString("session", null)
                val recovered = if (intent == null && savedId != null) try { store.read(savedId) } catch (_: Exception) { null } else null
                if (recovered != null && recovered.ended == 0L) {
                    sessionId = recovered.id; started = recovered.started; count = recovered.count
                    minimum = recovered.minimum; maximum = recovered.maximum; points.addAll(recovered.samples)
                    zero.restore(recovered.zeroState)
                } else {
                    if (savedId != null) try { store.finish(savedId, System.currentTimeMillis()) } catch (_: Exception) {}
                    started = System.currentTimeMillis(); sessionId = store.create(started)
                }
                preferences.edit().putString("session", sessionId).commit()
            } catch (_: Exception) { fail("기록을 시작하지 못했어요. 저장 공간을 확인해 주세요.") }
        }
        worker.execute { sampleAndSchedule() }
        return START_STICKY
    }

    /** Read the saved interval each cycle; setting changes reschedule without a new session. */
    private fun sampleAndSchedule() {
        sample()
        if (!stopping) nextSample = worker.schedule({ sampleAndSchedule() }, settings.powerSeconds.toLong(), TimeUnit.SECONDS)
    }

    private fun sample() {
        if (stopping) return
        try {
            // A timeout limits wake retention even if unexpected work stalls the service.
            retainCpu()
            if (stopping) return
            val value = PowerSampler.read(this)
            store.append(sessionId ?: return, value)
            if (stopping) return
            count++
            zero.add(value)
            points.addLast(value); if (points.size > 600) points.removeFirst()
            value.chargingWatts()?.let { watts ->
                minimum = minimum?.let { minOf(it, watts) } ?: watts
                maximum = maximum?.let { maxOf(it, watts) } ?: watts
            }
            snapshot = Snapshot(true, sessionId, started, count, minimum, maximum, points.toList(), zeroState = zero.state())
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(value))
        } catch (_: Exception) { fail("측정이 중단됐어요. 저장 공간과 앱 실행 설정을 확인해 주세요.") }
    }

    private fun fail(message: String) {
        stopping = true
        sessionId?.let { try { store.finish(it, System.currentTimeMillis()) } catch (_: Exception) {} }
        preferences.edit().remove("session").commit()
        snapshot = snapshot.copy(active = false, error = message)
        Handler(Looper.getMainLooper()).post { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
    }

    @Synchronized private fun retainCpu() {
        if (stopping) return
        if (SystemClock.elapsedRealtime() - wakeRenewed > 300000L || wakeLock?.isHeld != true) {
            wakeLock?.acquire(600000L); wakeRenewed = SystemClock.elapsedRealtime()
        }
    }

    private fun notification(value: ChargePower.Sample?): Notification {
        val watts = ChargePower.liveWatts(value, settings.showDischarge)
        val title = when {
            value == null -> "충전 전력 측정 중"
            watts != null && watts < 0 -> "방전 ${ChargePower.text(watts)}"
            value.plugged == 0 -> "충전기 연결 대기"
            watts == null -> "충전 전력 확인 중"
            else -> ChargePower.text(watts)
        }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, ChargeMonitorService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val dismissed = PendingIntent.getService(this, 2, Intent(this, ChargeMonitorService::class.java).setAction(DISMISSED), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(resources.getIdentifier("ic_power_notification", "drawable", packageName))
            .setContentTitle(title).setContentText("배터리 기준 전력 · 측정 중")
            .setContentIntent(open).setDeleteIntent(dismissed)
            .setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false)
            .setCategory(Notification.CATEGORY_STATUS).setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(Notification.Action.Builder(null, "측정 종료", stop).build())
        if (Build.VERSION.SDK_INT >= 36) {
            // Same extras key as NotificationCompat.setRequestPromotedOngoing; the
            // framework setter is a 36.1 API, while the chip text API is available in 36.
            builder.addExtras(Bundle().apply { putBoolean("android.requestPromotedOngoing", watts != null) })
            builder.setShortCriticalText(ChargePower.chip(watts))
        }
        return builder.build()
    }

    override fun onDestroy() {
        synchronized(this) {
            stopping = true
            wakeLock?.let { if (it.isHeld) it.release() }
        }
        worker.shutdown()
        nextSample?.cancel(false)
        settings.preferences.unregisterOnSharedPreferenceChangeListener(settingsListener)
        snapshot = snapshot.copy(active = false)
        super.onDestroy()
    }

    data class Snapshot(val active: Boolean = false, val id: String? = null, val started: Long = 0L,
                        val count: Long = 0L, val minimum: Double? = null, val maximum: Double? = null,
                        val samples: List<ChargePower.Sample> = emptyList(), val error: String? = null,
                        val zeroState: ZeroPowerTracker.State = ZeroPowerTracker().state())
    companion object {
        @Volatile var snapshot = Snapshot(); private set
        fun forgetDeleted(ids: List<String>) {
            val state = snapshot
            if (!state.active && ids.contains(state.id)) snapshot = Snapshot()
        }
        const val STOP = "kr.local.galaxybattery.STOP_POWER"
        const val DISMISSED = "kr.local.galaxybattery.DISMISS_POWER"
        const val CHANNEL = "charging-live-update"
        private const val NOTIFICATION_ID = 40
    }
}
