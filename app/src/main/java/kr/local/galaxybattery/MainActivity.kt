package kr.local.galaxybattery

import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.content.*
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.WindowInsets
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var levelView: TextView
    private lateinit var statusView: TextView
    private lateinit var cycleView: TextView
    private lateinit var cycleNote: TextView
    private lateinit var sohView: TextView
    private lateinit var sohNote: TextView
    private lateinit var details: TextView
    private lateinit var updated: TextView
    private lateinit var advancedStatus: TextView
    private lateinit var advancedDetails: TextView
    private lateinit var progress: ProgressBar
    private var shizuku: ShizukuReader? = null
    private val fileWorker = Executors.newSingleThreadExecutor()
    private val historyWorker = Executors.newSingleThreadExecutor()
    private val historyStore by lazy { HistoryStore(java.io.File(noBackupFilesDir, "battery-history")) }
    private lateinit var historyStatus: TextView
    private lateinit var powerView: TextView
    private lateinit var powerSubtitle: TextView
    private lateinit var powerStats: TextView
    private lateinit var monitorStatus: TextView
    private lateinit var monitorButton: Button
    private lateinit var powerGraph: PowerGraphView
    private lateinit var graphSelection: TextView
    private val powerStore by lazy { PowerLogStore(java.io.File(noBackupFilesDir, "power-history")) }
    private val previewSamples = java.util.ArrayDeque<ChargePower.Sample>()
    private val previewStats = ChargePower.Stats()
    private var powerStarting = false
    private var recordingWasActive = false
    private var importing = false
    private var registered = false
    private var lastBattery: Intent? = null
    private var detailed: DumpParser.Result? = null
    private var detailedSource = ""
    private var detailedTime = ""
    private var report = "아직 조회한 정보가 없습니다."
    private val refreshHandler = Handler(Looper.getMainLooper())
    private val liveRefresh = object : Runnable {
        override fun run() {
            if (!registered || isFinishing || isDestroyed) return
            render(registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))
            refreshHandler.postDelayed(this, 2000)
        }
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = render(intent)
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        state?.getString("fields")?.let {
            detailed = DumpParser.parse(it.byteInputStream())
            detailedSource = state.getString("source", "이전 조회")
            detailedTime = state.getString("readTime", "")
        }
        buildUi()
        shizuku = ShizukuReader(this, object : ShizukuReader.Callback {
            override fun status(message: String) { advancedStatus.text = message }
            override fun result(fields: String) {
                try { setDetailed(DumpParser.parse(fields.byteInputStream()), "직접 조회") }
                catch (_: Exception) { advancedStatus.text = "정보를 해석하지 못했어요. 로그 파일로 다시 확인해 주세요." }
            }
        })
        updateDetailCaption()
    }

    override fun onSaveInstanceState(state: Bundle) {
        detailed?.let {
            state.putString("fields", it.safeFields())
            state.putString("source", detailedSource)
            state.putString("readTime", detailedTime)
        }
        super.onSaveInstanceState(state)
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val sticky = if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            else registerReceiver(receiver, filter)
        registered = true
        render(sticky)
        refreshHandler.removeCallbacks(liveRefresh)
        refreshHandler.postDelayed(liveRefresh, 2000)
    }

    override fun onStop() {
        refreshHandler.removeCallbacks(liveRefresh)
        if (registered) { unregisterReceiver(receiver); registered = false }
        super.onStop()
    }

    override fun onDestroy() {
        refreshHandler.removeCallbacks(liveRefresh)
        shizuku?.close()
        fileWorker.shutdownNow()
        historyWorker.shutdown()
        super.onDestroy()
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(BG) }
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(26), dp(22), dp(24))
        }
        scroll.addView(outer)
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            @Suppress("DEPRECATION")
            insets.consumeSystemWindowInsets()
        }
        text(outer, "GALAXY  /  BATTERY", 11, GREEN, true)
        text(outer, "배터리 상태", 30, FG, true).setPadding(0, dp(10), 0, dp(6))
        text(outer, "${Build.MODEL}  ·  Android ${Build.VERSION.RELEASE}", 13, MUTED)

        val hero = card(outer)
        text(hero, "현재 잔량", 13, MUTED)
        levelView = text(hero, "—", 44, FG, true)
        statusView = text(hero, "확인 중", 15, FG)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = ColorStateList.valueOf(GREEN)
        }
        hero.addView(progress, LinearLayout.LayoutParams(-1, dp(8)).apply { topMargin = dp(18) })

        val power = card(outer)
        text(power, "충전 전력", 14, MUTED, true)
        powerView = text(power, "— W", 48, GREEN, true)
        powerSubtitle = text(power, "배터리로 들어오는 순전력을 확인해요.", 13, MUTED)
        text(power, "배터리 전압 × 순전류로 계산해요. 충전기 출력과는 차이가 있어요.", 12, MUTED)
        powerStats = text(power, "최고 — W     최저 — W", 16, FG, true).apply { setPadding(0, dp(14), 0, dp(8)) }
        text(power, "전력 변화", 14, FG, true)
        powerGraph = PowerGraphView(this)
        power.addView(powerGraph, LinearLayout.LayoutParams(-1, dp(200)))
        graphSelection = text(power, "그래프를 터치하면 그 시점의 값을 볼 수 있어요.", 12, MUTED)
        powerGraph.onSelection = { graphSelection.text = sampleCaption(it) }
        monitorStatus = text(power, "측정을 시작하면 화면을 꺼도 5초 간격으로 기록해요.", 13, MUTED)
        monitorButton = button(power, "측정 시작") { togglePowerRecording() }
        val powerActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        power.addView(powerActions)
        smallButton(powerActions, "충전 기록") { showPowerHistory() }
        smallButton(powerActions, "상단바 설정") { showPowerSettings() }

        card(outer).also {
            text(it, "충전 사이클", 14, MUTED)
            cycleView = text(it, "—", 30, FG, true)
            cycleNote = text(it, "", 13, MUTED)
        }
        card(outer).also {
            text(it, "배터리 진단", 14, MUTED)
            sohView = text(it, "—", 30, FG, true)
            sohNote = text(it, "", 13, MUTED)
        }
        card(outer).also {
            text(it, "배터리 상태", 16, FG, true)
            text(it, "화면을 보는 동안 2초마다 업데이트돼요.", 12, GREEN)
            details = text(it, "", 15, MUTED).apply { setLineSpacing(dp(7).toFloat(), 1f) }
            text(it, "‘정상’은 배터리의 상태 코드예요. 용량 유지율은 별도로 확인해 주세요.", 12, MUTED)
        }
        val advanced = card(outer)
        text(advanced, "배터리 정보 불러오기", 18, FG, true)
        text(advanced, "ASOC·BSOH와 사이클을 읽으려면 Shizuku를 연결해 주세요. 저장한 SysDump 로그도 불러올 수 있어요.", 13, MUTED)
        advancedStatus = text(advanced, "연결 상태를 확인하고 있어요…", 13, MUTED)
        button(advanced, "배터리 상태 확인") { if (!importing) shizuku?.query() }
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        advanced.addView(actions)
        smallButton(actions, "연결 설정") { showShizukuHelp() }
        smallButton(actions, "로그 불러오기") { pickDump() }
        advancedDetails = text(advanced, "조회 결과는 배터리 진단과 사이클에 표시돼요.", 12, MUTED)
        updated = text(outer, "", 12, MUTED).apply { setPadding(0, dp(18), 0, dp(6)) }
        button(outer, "측정 근거 · 결과 공유") { showReport() }
        card(outer).also {
            text(it, "배터리 기록", 18, FG, true)
            historyStatus = text(it, "상세 조회와 로그 분석 결과를 자동으로 저장해요. 이전 기록에서 변화와 측정 근거를 확인할 수 있어요.", 13, MUTED)
            button(it, "이전 기록 보기") { showHistory() }
            text(it, "원본 덤프는 보관하지 않아요. 저장한 기록은 개별 또는 전체 삭제할 수 있어요.", 12, MUTED)
        }
        text(outer, "배터리 기록은 이 기기에서만 처리해요. 공유할 때도 배터리 결과만 전달됩니다.\nv0.4.0", 12, MUTED)
        setContentView(scroll)
    }

    @Suppress("DEPRECATION")
    private fun extra(battery: Intent?, key: String): Int? = battery?.extras?.get(key) as? Int

    private data class Property(val value: Int?, val raw: String)
    private fun property(manager: BatteryManager?, id: Int): Property {
        if (manager == null) return Property(null, "service unavailable")
        return try {
            val value = manager.getIntProperty(id)
            if (value == Int.MIN_VALUE) Property(null, "unsupported") else Property(value, value.toString())
        } catch (_: SecurityException) { Property(null, "permission denied") }
        catch (e: RuntimeException) { Property(null, e.javaClass.simpleName) }
    }

    private fun render(incoming: Intent?) {
        if (incoming != null) lastBattery = incoming
        val battery = lastBattery
        val manager = getSystemService(BATTERY_SERVICE) as? BatteryManager
        val level = BatteryValues.percent(extra(battery, BatteryManager.EXTRA_LEVEL), extra(battery, BatteryManager.EXTRA_SCALE))
        val rawCycle = extra(battery, CYCLE)
        val cycle = BatteryValues.cycle(rawCycle)
        // AOSP property 10: experimental, firmware-dependent and permission-checked.
        val healthProperty = if (Build.VERSION.SDK_INT >= 34) property(manager, 10) else Property(null, "OS unsupported")
        val soh = BatteryValues.stateOfHealth(healthProperty.value)
        val current = property(manager, BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val charge = property(manager, BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val temperature = extra(battery, BatteryManager.EXTRA_TEMPERATURE)
        val voltage = extra(battery, BatteryManager.EXTRA_VOLTAGE)
        val time = timestamp()

        levelView.text = level?.let { "$it%" } ?: "—"
        statusView.text = BatteryValues.charging(extra(battery, BatteryManager.EXTRA_STATUS))
        progress.progress = level ?: 0
        cycleView.text = cycle?.let { "$it 회" } ?: "조회 필요"
        cycleNote.text = if (cycle != null) "휴대폰이 기록한 누적 사이클이에요. 충전기를 꽂은 횟수와는 달라요."
            else "‘배터리 상태 확인’을 눌러 상세 기록을 읽어 주세요."
        sohView.text = soh?.let { "$it%" } ?: "조회 필요"
        sohNote.text = if (soh != null) "정격 용량 대비 현재 완충 용량을 휴대폰이 추정한 값이에요."
            else "연결을 설정하면 휴대폰에 저장된 배터리 성능을 조회할 수 있어요."
        detailed?.let { value ->
            cycleView.text = value.cycleText().replace("확인 불가", "정보 없음")
            cycleNote.text = if (value.usage.single()?.let { it > 0 } == true)
                "누적 사용량을 완전 충전 기준으로 환산한 추정치예요.\n$detailedSource · $detailedTime"
                else "이 기록에서는 사이클을 확정할 수 없어요. ‘측정 근거’에서 이유를 확인해 주세요."
            sohView.text = value.asoc.health()?.let { "$it%" } ?: "정보 없음"
            sohNote.text = if (value.asoc.health() != null)
                "ASOC · 삼성 내부 참고 값이에요. 건강 상태 지표 BSOH는 ‘측정 근거’에서 확인할 수 있어요."
                else "이 기록에 유효한 성능 값이 없어요. 새 로그로 다시 확인해 주세요."
        }
        val tempText = temperature?.let { BatteryValues.decimal(it / 10.0, "°C") } ?: "정보 없음"
        val voltText = voltage?.takeIf { it > 0 }?.let { String.format(Locale.KOREA, "%.3f V", it / 1000.0) } ?: "정보 없음"
        val currentText = current.value?.let { BatteryValues.decimal(it / 1000.0, "mA") } ?: "정보 없음"
        val chargeText = charge.value?.takeIf { it > 0 }?.let { BatteryValues.decimal(it / 1000.0, "mAh") } ?: "정보 없음"
        details.text = "온도     $tempText\n전압     $voltText\n순간 전류     $currentText\n남은 전하량     $chargeText\n상태     ${BatteryValues.condition(extra(battery, BatteryManager.EXTRA_HEALTH))}"
        updated.text = "기본 정보 업데이트  ${SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date())}"
        renderPower()

        report = buildString {
            append("Galaxy Battery v0.4.0 / Kotlin\n조회 시간: $time\n")
            append("Model: ${Build.MODEL}\nAndroid: ${Build.VERSION.RELEASE}\nSDK: ${Build.VERSION.SDK_INT}\n")
            append("Build: ${Build.DISPLAY}\nSecurity patch: ${Build.VERSION.SECURITY_PATCH}\n")
            append("\n공식 사이클 원본: ${rawCycle ?: "미제공"}\n플랫폼 SOH 속성 10: ${healthProperty.raw}\n")
            append("Current (uA): ${current.raw}\nCharge counter (uAh): ${charge.raw}\n")
            append("현재 잔량: ${level?.let { "$it%" } ?: "정보 없음"}\n충전 상태: ${statusView.text}\n")
            append("${details.text}\n")
            append("잔여 전하량은 완충 용량이 아닙니다. 상태 ‘정상’은 성능 100%를 뜻하지 않습니다.\n")
            append("사이클 0은 실제 0회/미지원 값을 구분할 수 없어 확정하지 않습니다.\n")
            detailed?.let {
                append("\n상세 조회: $detailedSource\n읽은 시간: $detailedTime (로그 생성 시각이 아님)\n")
                append(it.summary()).append("\n\n").append(it.evidence())
                append("ASOC는 독립 실측값이 아니며 펌웨어/배터리 교체 이력에 따라 해석이 달라질 수 있습니다.\n")
            }
        }
    }

    private fun setDetailed(result: DumpParser.Result, source: String) {
        detailed = result
        detailedSource = source
        detailedTime = timestamp()
        advancedStatus.text = if (result.hasFields()) "배터리 기록을 확인했어요."
            else "배터리 항목을 찾지 못했어요. 새로 생성한 dumpstate 로그를 선택해 주세요."
        updateDetailCaption()
        render(lastBattery)
        if (result.hasFields()) {
            val savedTime = System.currentTimeMillis()
            val savedReport = report
            val summary = result.summary()
            historyWorker.execute {
                try {
                    historyStore.save(savedTime, source, summary, savedReport)
                    runOnUiThread { if (!isDestroyed) historyStatus.text = "최근 조회 결과를 저장했어요. ‘이전 기록 보기’에서 확인해 주세요." }
                } catch (_: Exception) {
                    runOnUiThread { if (!isDestroyed) historyStatus.text = "결과는 조회했지만 기록을 저장하지 못했어요. 기기의 저장 공간을 확인해 주세요." }
                }
            }
        }
    }

    private fun renderPower() {
        val state = ChargeMonitorService.snapshot
        val sample = if (state.active) state.samples.lastOrNull() else PowerSampler.read(this)
        val watts = sample?.watts()
        powerView.text = ChargePower.text(watts)
        powerSubtitle.text = when {
            sample == null -> "첫 측정 값을 기다리고 있어요."
            watts == null -> "기기에서 전류 또는 전압 값을 제공하지 않아요."
            watts < 0 -> "배터리 사용 중 · 음수는 배터리에서 나가는 전력이에요."
            sample.plugged == 0 -> "충전기를 연결하면 유입 전력을 확인할 수 있어요."
            else -> "${BatteryValues.charging(sample.status)} · 배터리 기준 순전력"
        }
        if (state.id == null && sample != null && sample.time - (previewSamples.lastOrNull()?.time ?: 0L) >= 5000) {
            previewSamples.addLast(sample); if (previewSamples.size > 600) previewSamples.removeFirst()
            previewStats.add(sample)
        }
        val points = if (state.id != null) state.samples else previewSamples.toList()
        powerGraph.setSamples(points)
        val minimum = if (state.id != null) state.minimum else previewStats.minimum
        val maximum = if (state.id != null) state.maximum else previewStats.maximum
        powerStats.text = "최고 ${ChargePower.text(maximum)}     최저 ${ChargePower.text(minimum)}"
        if (state.active) { recordingWasActive = true; powerStarting = false }
        if (state.error != null) powerStarting = false
        monitorButton.text = if (state.active) "측정 종료 · 기록 저장" else if (powerStarting) "측정 시작 중…" else "측정 시작"
        monitorButton.isEnabled = !powerStarting
        monitorStatus.text = when {
            state.error != null -> state.error
            state.active -> "● 기록 중 · 5초 간격 · ${state.count}개 측정\n화면을 꺼도 기록해요. 충전기의 연결을 해제해도 ‘측정 종료’까지 계속됩니다."
            recordingWasActive -> "측정을 종료했어요. ‘충전 기록’에서 그래프와 최고·최저를 다시 볼 수 있어요."
            else -> "측정을 시작하면 화면을 꺼도 5초 간격으로 기록해요."
        }
    }

    private fun sampleCaption(sample: ChargePower.Sample): String {
        val time = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date(sample.time))
        val level = sample.level.takeIf { it in 0..100 }?.let { "$it%" } ?: "잔량 미지원"
        return "$time · ${ChargePower.text(sample.watts())} · $level"
    }

    private fun togglePowerRecording() {
        if (ChargeMonitorService.snapshot.active) {
            startService(Intent(this, ChargeMonitorService::class.java).setAction(ChargeMonitorService.STOP))
            return
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), NOTIFICATION_PERMISSION)
            return
        }
        startPowerRecording()
    }

    private fun startPowerRecording() {
        try {
            powerStarting = true; monitorButton.isEnabled = false; monitorButton.text = "측정 시작 중…"
            startForegroundService(Intent(this, ChargeMonitorService::class.java))
            refreshHandler.postDelayed({
                if (!isDestroyed && !ChargeMonitorService.snapshot.active) {
                    powerStarting = false; renderPower()
                    toast("측정을 시작하지 못했어요. 앱 알림과 백그라운드 실행 설정을 확인해 주세요.")
                }
            }, 5000)
        } catch (_: RuntimeException) {
            powerStarting = false; renderPower()
            toast("측정을 시작하지 못했어요. 앱을 다시 열고 시도해 주세요.")
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATION_PERMISSION) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startPowerRecording()
            else AlertDialog.Builder(this).setTitle("알림 표시를 허용해 주세요")
                .setMessage("Live Update 상단바 표시와 화면 꺼짐 측정을 시작하려면 앱 알림을 허용해 주세요.")
                .setPositiveButton("설정 열기") { _, _ -> openAppSettings() }.setNegativeButton("닫기", null).show()
        }
    }

    private fun openAppSettings() {
        try { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
        catch (_: RuntimeException) { toast("휴대폰 설정에서 ‘배터리 상태’를 찾아 주세요.") }
    }

    private fun showPowerSettings() {
        val manager = getSystemService(NotificationManager::class.java)
        val enabled = manager.areNotificationsEnabled()
        val promoted = if (Build.VERSION.SDK_INT >= 36) manager.canPostPromotedNotifications() else false
        val actual = if (Build.VERSION.SDK_INT >= 36) manager.activeNotifications.any {
            it.notification.flags and android.app.Notification.FLAG_PROMOTED_ONGOING != 0
        } else false
        val state = when {
            Build.VERSION.SDK_INT < 36 -> "이 Android 버전은 Live Update 상단바 칩을 지원하지 않아요."
            !enabled -> "앱 알림이 꺼져 있어요. 설정에서 허용해 주세요."
            !promoted -> "앱의 실시간 업데이트가 허용되지 않았어요. 설정에서 확인해 주세요."
            actual -> "현재 측정이 Live Update로 표시되고 있어요."
            else -> "실시간 업데이트를 요청할 수 있어요. 실제 칩 표시는 One UI가 결정합니다."
        }
        AlertDialog.Builder(this).setTitle("상단바 · 현재 충전 전력")
            .setMessage("$state\n\n충전 중 ‘12.3W’처럼 현재 전력만 표시하도록 요청해요. 이 기능은 Android 16 이상에서 지원하며, Live Update와 연결된 알림도 함께 존재합니다.\n\n화면을 꺼도 측정하려면 측정 시작을 누르세요. 기록이 자주 중단되면 앱 정보 → 배터리에서 제한 없음으로 설정하고, 삼성 절전 앱 목록에서도 제외해 주세요. 측정 중에는 CPU가 깨어 있어 배터리 사용량이 늘 수 있어요.")
            .setPositiveButton("실시간 업데이트 설정") { _, _ ->
                val intent = Intent("android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS").putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                if (Build.VERSION.SDK_INT >= 36 && intent.resolveActivity(packageManager) != null) {
                    try { startActivity(intent) } catch (_: RuntimeException) { openAppSettings() }
                } else openAppSettings()
            }.setNeutralButton("앱 실행 설정") { _, _ -> openAppSettings() }
            .setNegativeButton("닫기", null).show()
    }

    private fun showPowerHistory() {
        historyWorker.execute {
            try {
                val sessions = powerStore.list()
                runOnUiThread {
                    if (isDestroyed || isFinishing) return@runOnUiThread
                    val dialog = AlertDialog.Builder(this).setTitle("충전 기록 · ${sessions.size}개").setNegativeButton("닫기", null)
                    if (sessions.isEmpty()) dialog.setMessage("측정 시작을 누르면 전력 변화가 저장돼요. 화면을 꺼도 기록되며, 최고·최저 값도 함께 보관해요.")
                    else {
                        dialog.setItems(sessions.map {
                            "${SimpleDateFormat("MM/dd HH:mm", Locale.KOREA).format(Date(it.started))} · ${it.count}개 측정\n최고 ${ChargePower.text(it.maximum)} / 최저 ${ChargePower.text(it.minimum)}"
                        }.toTypedArray()) { _, index -> loadPowerSession(sessions[index].id) }
                        dialog.setNeutralButton("전체 삭제") { _, _ -> confirmPowerDelete(sessions.map { it.id }) }
                    }
                    dialog.show()
                }
            } catch (_: Exception) { runOnUiThread { if (!isDestroyed) toast("충전 기록을 읽지 못했어요. 다시 시도해 주세요.") } }
        }
    }

    private fun loadPowerSession(id: String) {
        historyWorker.execute {
            try {
                val session = powerStore.read(id)
                runOnUiThread {
                    if (isDestroyed || isFinishing) return@runOnUiThread
                    val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(12)); setBackgroundColor(CARD) }
                    val end = if (session.ended > 0) SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date(session.ended))
                        else if (ChargeMonitorService.snapshot.active && ChargeMonitorService.snapshot.id == id) "측정 중" else "종료 시각 미기록 · 중단된 측정"
                    text(content, "${session.count}개 측정 · $end", 13, MUTED)
                    text(content, "최고 ${ChargePower.text(session.maximum)}\n최저 ${ChargePower.text(session.minimum)}", 21, FG, true)
                    val graph = PowerGraphView(this).apply { setSamples(session.samples) }
                    content.addView(graph, LinearLayout.LayoutParams(-1, dp(220)))
                    val selection = text(content, "최근 최대 600개를 표시해요. 터치해서 해당 시점의 값을 확인하세요.", 12, MUTED)
                    graph.onSelection = { selection.text = sampleCaption(it) }
                    text(content, "최고·최저는 전체 기록 중 충전 중인 유효한 값으로 계산해요. 실제 0W도 최저 값에 포함됩니다. 음수는 방전 전력이에요.", 12, MUTED)
                    AlertDialog.Builder(this).setTitle(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA).format(Date(session.started)))
                        .setView(ScrollView(this).apply { addView(content) })
                        .setPositiveButton("목록으로") { _, _ -> showPowerHistory() }
                        .setNeutralButton("삭제") { _, _ -> confirmPowerDelete(listOf(id)) }
                        .setNegativeButton("닫기", null).show()
                }
            } catch (_: Exception) { runOnUiThread { if (!isDestroyed) toast("이 기록을 읽지 못했어요.") } }
        }
    }

    private fun confirmPowerDelete(ids: List<String>) {
        if (ChargeMonitorService.snapshot.active && ids.contains(ChargeMonitorService.snapshot.id)) {
            toast("진행 중인 측정을 종료한 뒤 삭제해 주세요."); return
        }
        AlertDialog.Builder(this).setTitle("충전 기록 ${ids.size}개를 삭제할까요?")
            .setMessage("그래프와 최고·최저 기록이 함께 삭제되며 복구할 수 없어요.")
            .setNegativeButton("취소") { _, _ -> showPowerHistory() }
            .setPositiveButton("삭제") { _, _ -> historyWorker.execute {
                try {
                    val active = ChargeMonitorService.snapshot.takeIf { it.active }?.id
                    powerStore.delete(ids, active)
                    ChargeMonitorService.forgetDeleted(ids)
                    runOnUiThread { if (!isDestroyed && !isFinishing) { toast("충전 기록을 삭제했어요."); showPowerHistory() } }
                } catch (_: Exception) { runOnUiThread { if (!isDestroyed) toast("기록을 삭제하지 못했어요. 진행 중인 측정을 확인해 주세요.") } }
            } }.show()
    }

    private fun showHistory() {
        historyWorker.execute {
            try {
                val entries = historyStore.list()
                runOnUiThread {
                    if (isDestroyed || isFinishing) return@runOnUiThread
                    val dialog = AlertDialog.Builder(this).setTitle("배터리 기록 · ${entries.size}개")
                        .setNegativeButton("닫기", null)
                    if (entries.isEmpty()) {
                        dialog.setMessage("아직 저장된 기록이 없어요. 상세 정보를 조회하거나 배터리 로그를 불러오면 자동으로 저장돼요.")
                    } else {
                        dialog.setItems(entries.map {
                            "${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA).format(Date(it.time))} · ${it.source}\n${it.summary.replace('\n', ' ')}"
                        }.toTypedArray()) { _, index -> showHistoryEntry(entries[index]) }
                        dialog.setNeutralButton("전체 삭제") { _, _ -> confirmHistoryDelete(entries.map { it.id }) }
                    }
                    dialog.show()
                }
            } catch (_: Exception) {
                runOnUiThread { if (!isDestroyed) toast("저장된 기록을 읽지 못했어요. 잠시 후 다시 시도해 주세요.") }
            }
        }
    }

    private fun showHistoryEntry(entry: HistoryStore.Entry) {
        val content = TextView(this).apply {
            text = "저장된 조회 결과 · ${entry.source}\n로그 분석 기록의 시각은 파일을 읽은 시각이에요.\n\n${entry.report}"
            textSize = 13f
            setTextIsSelectable(true)
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        AlertDialog.Builder(this)
            .setTitle(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA).format(Date(entry.time)))
            .setView(ScrollView(this).apply { addView(content) })
            .setPositiveButton("목록으로") { _, _ -> showHistory() }
            .setNeutralButton("삭제") { _, _ -> confirmHistoryDelete(listOf(entry.id)) }
            .setNegativeButton("닫기", null).show()
    }

    private fun confirmHistoryDelete(ids: List<String>) {
        AlertDialog.Builder(this).setTitle("기록 ${ids.size}개를 삭제할까요?")
            .setMessage("삭제한 기록은 복구할 수 없어요. 직접 선택했던 원본 로그 파일은 삭제하지 않아요.")
            .setNegativeButton("취소") { _, _ -> showHistory() }
            .setPositiveButton("삭제") { _, _ ->
                historyWorker.execute {
                    try {
                        historyStore.delete(ids)
                        runOnUiThread {
                            if (!isDestroyed && !isFinishing) {
                                historyStatus.text = "선택한 기록을 삭제했어요. 다음 조회 결과는 다시 자동 저장돼요."
                                showHistory()
                            }
                        }
                    } catch (_: Exception) {
                        runOnUiThread { if (!isDestroyed) toast("일부 기록을 삭제하지 못했어요. 목록을 다시 확인해 주세요.") }
                    }
                }
            }.show()
    }

    private fun updateDetailCaption() {
        detailed?.let {
            advancedDetails.text = "$detailedSource · $detailedTime\n" +
                if (detailedSource == "로그 분석") "파일에 남아 있는 기록이에요. 최신 상태를 보려면 새 로그를 불러와 주세요."
                else "다시 확인할 때까지 이 결과를 표시해요."
        }
    }

    private fun showShizukuHelp() {
        AlertDialog.Builder(this).setTitle("처음 한 번 연결해 주세요")
            .setMessage("1. Shizuku를 설치하고 열어 주세요.\n2. 휴대폰 개발자 옵션에서 ‘무선 디버깅’을 켜세요.\n3. Shizuku 안내에 따라 페어링하고 시작하세요.\n4. 이 앱의 ‘배터리 상태 확인’을 누르고 권한을 허용하세요.\n\n이후에는 버튼 하나로 확인할 수 있어요. 재부팅하면 Shizuku를 다시 시작해야 할 수 있습니다.\n\n기기에서 정보를 공개하지 않으면 로그 파일로 확인할 수 있어요.")
            .setPositiveButton("Shizuku 열기") { _, _ ->
                val launch = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                    ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))
                try { startActivity(launch) }
                catch (_: RuntimeException) { toast("브라우저에서 shizuku.rikka.app을 열어 주세요.") }
            }.setNegativeButton("닫기", null).show()
    }

    private fun pickDump() {
        if (importing || shizuku?.busy == true) { toast("진행 중인 조회가 끝나면 선택해 주세요."); return }
        AlertDialog.Builder(this).setTitle("배터리 로그 불러오기")
            .setMessage("1. 삼성 전화에서 *#9900#을 입력하세요.\n2. Run dumpstate/logcat을 실행하세요.\n3. 완료되면 Copy to sdcard를 누르세요.\n4. 내부 저장소의 log 폴더에서 새 dumpstate 파일을 선택하세요.\n\n메뉴와 저장 위치는 기기마다 다를 수 있어요. 파일이 보이지 않으면 ‘내 파일’에서 Download 폴더로 복사해 주세요.\n\nTXT·LOG·ZIP·GZ 지원 · 배터리 항목만 추출합니다.")
            .setPositiveButton("파일 선택") { _, _ ->
                try {
                    startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*")
                        .addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), PICK_DUMP)
                } catch (_: RuntimeException) { advancedStatus.text = "파일 선택기를 열지 못했어요. 잠시 후 다시 시도해 주세요." }
            }.setNegativeButton("취소", null).show()
    }

    @Deprecated("Framework activity result API, used without AndroidX")
    override fun onActivityResult(request: Int, result: Int, data: Intent?) {
        super.onActivityResult(request, result, data)
        if (request != PICK_DUMP || result != RESULT_OK) return
        val uri = data?.data ?: return
        importing = true
        advancedStatus.text = "로그에서 배터리 기록을 찾고 있어요…"
        fileWorker.execute {
            try {
                val parsed = contentResolver.openInputStream(uri)?.use { DumpParser.parse(it) }
                    ?: throw java.io.IOException("Cannot open file")
                runOnUiThread {
                    importing = false
                    if (!isDestroyed) setDetailed(parsed, "로그 분석")
                }
            } catch (_: Exception) {
                runOnUiThread {
                    importing = false
                    if (!isDestroyed) advancedStatus.text = "로그를 읽지 못했어요. 압축을 풀어 dumpstate TXT·LOG 파일을 선택해 주세요. 이전 결과는 그대로 유지돼요."
                }
            }
        }
    }

    private fun showReport() {
        val snapshot = report + "\nShizuku 진단: ${shizuku?.diagnostic ?: "미연결"}\n"
        val content = TextView(this).apply {
            text = snapshot; textSize = 12f; typeface = Typeface.MONOSPACE
            setTextIsSelectable(true); setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        AlertDialog.Builder(this).setTitle("측정 근거")
            .setView(ScrollView(this).apply { addView(content) })
            .setPositiveButton("공유") { _, _ ->
                val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, "배터리 상태 확인 결과").putExtra(Intent.EXTRA_TEXT, snapshot)
                try { startActivity(Intent.createChooser(send, "결과 공유")) } catch (_: RuntimeException) { toast("공유할 앱을 찾지 못했어요. 복사를 이용해 주세요.") }
            }.setNeutralButton("복사") { _, _ ->
                (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("배터리 상태", snapshot))
                toast("결과를 복사했어요.")
            }.setNegativeButton("닫기", null).show()
    }

    private fun timestamp() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA).format(Date())
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun text(parent: LinearLayout, value: String, size: Int, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value; textSize = size.toFloat(); setTextColor(color)
            if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(0, dp(3), 0, dp(3))
            parent.addView(this, LinearLayout.LayoutParams(-1, -2))
        }

    private fun card(parent: LinearLayout): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(18), dp(20), dp(18))
        background = GradientDrawable().apply { setColor(CARD); cornerRadius = dp(24).toFloat() }
        elevation = dp(2).toFloat()
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
    }

    private fun button(parent: LinearLayout, label: String, action: () -> Unit): Button {
        val button = Button(this).apply {
            text = label; isAllCaps = false; setTextColor(BG)
            backgroundTintList = ColorStateList.valueOf(GREEN)
            minHeight = dp(52); setOnClickListener { action() }
        }
        parent.addView(button, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6); bottomMargin = dp(6) })
        return button
    }

    private fun smallButton(parent: LinearLayout, label: String, action: () -> Unit) {
        parent.addView(Button(this, null, android.R.attr.borderlessButtonStyle).apply {
            text = label; isAllCaps = false; textSize = 13f; setTextColor(GREEN)
            minHeight = dp(48); setOnClickListener { action() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
    }

    companion object {
        private val BG = Color.rgb(12, 19, 28)
        private val CARD = Color.rgb(22, 33, 45)
        private val FG = Color.rgb(238, 245, 250)
        private val MUTED = Color.rgb(153, 172, 189)
        private val GREEN = Color.rgb(115, 235, 195)
        private const val NOTIFICATION_PERMISSION = 301
        private const val CYCLE = "android.os.extra.CYCLE_COUNT"
        private const val PICK_DUMP = 201
    }
}
