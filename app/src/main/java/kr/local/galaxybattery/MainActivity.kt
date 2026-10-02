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
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowInsets
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val settings by lazy { AppSettings(this) }
    private val palette by lazy { AppPalette.forDark(settings.isDark(this)) }
    private val BG get() = palette.background
    private val CARD get() = palette.card
    private val FG get() = palette.foreground
    private val MUTED get() = palette.muted
    private val ACCENT get() = palette.accent
    private lateinit var dashboard: DashboardScaffold
    private lateinit var recordsContainer: LinearLayout
    private lateinit var chargingCategory: Button
    private lateinit var diagnosisCategory: Button
    private lateinit var thermalView: ThermalStatusView
    private lateinit var hardwareMonitor: HardwareMonitorView
    private val hardwareTelemetry = HardwareTelemetry()
    private val thermalMonitor by lazy { ThermalMonitor(this) { updateThermal() } }
    private var hardwareReader: HardwareReader? = null
    private val hardwareFrames = java.util.ArrayDeque<HardwareTelemetry.Frame>()
    private lateinit var interruptionView: TextView
    private val previewInterruptions = ChargeInterruptionTracker()
    private var historyCategory = 0
    private var recordsGeneration = 0

    private lateinit var levelView: TextView
    private lateinit var statusView: TextView
    private lateinit var cycleView: TextView
    private lateinit var cycleNote: TextView
    private lateinit var sohView: TextView
    private lateinit var sohNote: TextView
    private lateinit var diagnosisLabel: TextView
    private lateinit var details: TextView
    private lateinit var updated: TextView
    private lateinit var advancedStatus: TextView
    private lateinit var advancedDetails: TextView
    private lateinit var progress: BatteryLevelView
    private var shizuku: ShizukuReader? = null
    private val fileWorker = Executors.newSingleThreadExecutor()
    private val historyWorker = Executors.newSingleThreadExecutor()
    private val historyStore by lazy { HistoryStore(java.io.File(noBackupFilesDir, "battery-history")) }
    private lateinit var historyStatus: TextView
    private lateinit var powerView: TextView
    private lateinit var powerSubtitle: TextView
    private lateinit var powerStats: PowerStatisticsView
    private lateinit var monitorStatus: TextView
    private lateinit var monitorButton: Button
    private lateinit var powerGraph: PowerGraphView
    private lateinit var graphSelection: TextView
    private val powerStore by lazy { PowerLogStore(java.io.File(noBackupFilesDir, "power-history")) }
    private val previewSamples = java.util.ArrayDeque<ChargePower.Sample>()
    private val previewStats = ChargePower.Stats()
    private val previewScreenEvents = java.util.ArrayDeque<ScreenTimeline.Event>()
    private var screenObserverRegistered = false
    private val screenObserver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ChargeMonitorService.snapshot.id == null) {
                rememberPreviewScreen(if (intent.action == Intent.ACTION_SCREEN_OFF) ScreenTimeline.OFF else ScreenTimeline.ON)
            }
        }
    }
    private fun rememberPreviewScreen(state: Int) {
        previewScreenEvents.addLast(ScreenTimeline.Event(System.currentTimeMillis(), state))
        previewSamples.peekFirst()?.time?.let { first ->
            while (previewScreenEvents.size > 1 && previewScreenEvents.elementAt(1).time <= first) previewScreenEvents.removeFirst()
        }
        // Screen events update the preview even while its power timer is paused.
        if (::powerGraph.isInitialized) powerGraph.setScreenEvents(previewScreenEvents.toList(), 0L, true)
    }
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
            refreshHandler.postDelayed(this, settings.batterySeconds * 1000L)
        }
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { lastBattery = intent }
    }

    private val powerRefresh = object : Runnable {
        override fun run() {
            if (!registered || isFinishing || isDestroyed) return
            renderPower()
            refreshHandler.postDelayed(this, settings.powerSeconds * 1000L)
        }
    }
    private val hardwareRefresh = object : Runnable {
        override fun run() {
            if (!registered || dashboard.selected != 1 || isFinishing || isDestroyed) return
            hardwareReader?.poll()
            refreshHandler.postDelayed(this, settings.hardwareSeconds * 1000L)
        }
    }

    private fun startHardware() {
        refreshHandler.removeCallbacks(hardwareRefresh)
        hardwareTelemetry.reset()
        hardwareReader?.start()
        thermalMonitor.start()
        refreshHandler.postDelayed(hardwareRefresh, settings.hardwareSeconds * 1000L)
    }
    private fun stopHardware() {
        refreshHandler.removeCallbacks(hardwareRefresh)
        hardwareReader?.stop()
        thermalMonitor.stop()
        hardwareTelemetry.reset()
    }
    private fun updateThermal() {
        if (::thermalView.isInitialized) thermalView.setStatus(thermalMonitor.status, thermalMonitor.headroom,
            thermalMonitor.cooling, thermalMonitor.statusUpdatedAt,
            thermalMonitor.coolingSource, thermalMonitor.coolingUpdatedAt, thermalMonitor.headroomUpdatedAt)
    }

    override fun onCreate(state: Bundle?) {
        val style = if (settings.isDark(this)) "AppTheme" else "AppTheme.Light"
        setTheme(resources.getIdentifier(style, "style", packageName))
        super.onCreate(state)
        rememberPreviewScreen(if (getSystemService(android.os.PowerManager::class.java).isInteractive) ScreenTimeline.ON else ScreenTimeline.OFF)
        val screenFilter = IntentFilter(Intent.ACTION_SCREEN_ON).apply { addAction(Intent.ACTION_SCREEN_OFF) }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenObserver, screenFilter, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(screenObserver, screenFilter)
        screenObserverRegistered = true
        state?.getString("fields")?.let {
            detailed = DumpParser.parse(it.byteInputStream())
            detailedSource = state.getString("source", "이전 조회")
            detailedTime = state.getString("readTime", "")
        }
        buildUi()
        historyCategory = state?.getInt("historyCategory", 0) ?: 0
        dashboard.select(state?.getInt("page", 0) ?: 0)
        shizuku = ShizukuReader(this, object : ShizukuReader.Callback {
            override fun status(message: String) { advancedStatus.text = message }
            override fun failure(message: String) {
                if (isDestroyed || isFinishing) return
                AlertDialog.Builder(this@MainActivity).setTitle("Shizuku 연결을 확인해 주세요")
                    .setMessage(message + "\n\nShizuku가 실행 중인지, 이 앱의 권한을 허용했는지 확인한 뒤 다시 조회하세요.")
                    .setPositiveButton("연결 안내") { _, _ -> showShizukuHelp() }
                    .setNeutralButton("로그 불러오기") { _, _ -> pickDump() }
                    .setNegativeButton("닫기", null).showUniform()
            }
            override fun result(fields: String) {
                try { setDetailed(DumpParser.parse(fields.byteInputStream()), "직접 조회") }
                catch (_: Exception) { advancedStatus.text = "정보를 해석하지 못했어요.\n로그 파일로 다시 확인해 주세요." }
            }
        })
        hardwareReader = HardwareReader(this) { raw, source ->
            if (registered && dashboard.selected == 1) {
                hardwareTelemetry.describe(raw, source)
                val frame = hardwareTelemetry.latest
                hardwareFrames.addLast(frame)
                if (hardwareFrames.size > 120) hardwareFrames.removeFirst()
                hardwareMonitor.setFrames(hardwareFrames.toList(), source)
                updateThermal()
                dashboard.refreshBackdrop()
            }
        }
        updateDetailCaption()
    }

    override fun onSaveInstanceState(state: Bundle) {
        detailed?.let {
            state.putString("fields", it.safeFields())
            state.putString("source", detailedSource)
            state.putString("readTime", detailedTime)
        }
        state.putInt("page", dashboard.selected)
        state.putInt("historyCategory", historyCategory)
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
        refreshHandler.postDelayed(liveRefresh, settings.batterySeconds * 1000L)
        refreshHandler.removeCallbacks(powerRefresh)
        renderPower()
        if (dashboard.selected == 1) startHardware()
        refreshHandler.postDelayed(powerRefresh, settings.powerSeconds * 1000L)
        if (dashboard.selected == 2) refreshRecords()
    }

    override fun onStop() {
        stopHardware()
        refreshHandler.removeCallbacks(liveRefresh)
        refreshHandler.removeCallbacks(powerRefresh)
        if (registered) { unregisterReceiver(receiver); registered = false }
        super.onStop()
    }

    override fun onDestroy() {
        refreshHandler.removeCallbacksAndMessages(null)
        shizuku?.close()
        hardwareReader?.close()
        thermalMonitor.close()
        if (screenObserverRegistered) { unregisterReceiver(screenObserver); screenObserverRegistered = false }
        fileWorker.shutdownNow()
        historyWorker.shutdown()
        super.onDestroy()
    }

    private fun buildUi() {
        dashboard = DashboardScaffold(this, palette, settings.blur, { showSettings() }) {
            if (it == 2 && ::recordsContainer.isInitialized) refreshRecords()
            if (it == 1 && registered) startHardware() else stopHardware()
        }
        var outer = dashboard.pages[0]
        text(outer, "${Build.MODEL}  ·  Android ${Build.VERSION.RELEASE}", 13, MUTED)
        val hero = card(outer)
        text(hero, "현재 잔량", 13, MUTED)
        levelView = text(hero, "—", 44, FG, true)
        statusView = text(hero, "확인 중", 15, FG)
        progress = BatteryLevelView(this, palette)
        hero.addView(progress, LinearLayout.LayoutParams(-1, dp(12)).apply { topMargin = dp(18) })

        val advanced = card(outer)
        text(advanced, "배터리 정보 불러오기", 18, FG, true)
        text(advanced, "Shizuku를 연결해 조회하거나, 삼성 진단 로그(SysDump)를 불러오세요", 13, MUTED)
        advancedStatus = text(advanced, "연결 상태를 확인하고 있어요…", 13, MUTED)
        button(advanced, "배터리 상태 확인") { if (!importing) shizuku?.query() }
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        advanced.addView(actions, actionRowParams())
        smallButton(actions, "연결 설정") { showShizukuHelp() }
        smallButton(actions, "로그 불러오기") { pickDump() }
        advancedDetails = text(advanced, "조회 결과는 자동 저장돼요", 12, MUTED)
        card(outer).also {
            text(it, "배터리 진단", 18, FG, true)
            text(it, "충전 사이클", 13, MUTED)
            cycleView = text(it, "—", 26, FG, true)
            cycleNote = text(it, "", 12, MUTED)
            diagnosisLabel = text(it, "충전량 보정 값 (ASOC)", 13, MUTED).apply { setPadding(0, dp(14), 0, dp(3)) }
            sohView = text(it, "—", 26, FG, true)
            sohNote = text(it, "", 12, MUTED)
        }
        card(outer).also {
            text(it, "배터리 상태", 16, FG, true)
            text(it, "화면을 보는 동안 ${settings.batterySeconds}초마다 업데이트돼요.", 12, ACCENT)
            details = text(it, "", 15, MUTED).apply { setLineSpacing(dp(7).toFloat(), 1f) }
            text(it, "현재 남은 용량은 완충했을 때의 용량과 달라요.\n‘정상’은 배터리 수명 100%를 뜻하지 않아요.", 12, MUTED)
        }
        updated = text(outer, "", 12, MUTED).apply { setPadding(0, dp(18), 0, dp(6)) }
        outer = dashboard.pages[1]
        val power = card(outer)
        text(power, "현재 전력", 13, MUTED)
        powerView = text(power, "— W", 48, ACCENT, true)
        powerSubtitle = text(power, "확인 중", 14, MUTED)
        text(power, "배터리에 들어오거나 빠져나가는 전력이에요.\n충전기 출력과는 달라요.", 12, MUTED)
        monitorButton = button(power, "측정 시작") { togglePowerRecording() }
        monitorStatus = text(power, "측정 중에는 화면이 꺼져도 기록해요", 12, MUTED)
        val powerActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        power.addView(powerActions, actionRowParams())
        smallButton(powerActions, "기록 보기") { showPowerHistory() }
        smallButton(powerActions, "표시 설정") { showSettings() }

        val chart = card(outer)
        text(chart, "전력 변화", 18, FG, true)
        powerGraph = PowerGraphView(this, palette)
        chart.addView(powerGraph, LinearLayout.LayoutParams(-1, dp(200)).apply { topMargin = dp(8); bottomMargin = dp(8) })
        graphSelection = text(chart, "+ 충전 · − 방전 · 터치해서 값 확인", 12, MUTED)
        powerGraph.onSelection = { graphSelection.text = sampleCaption(it) }

        val statistics = card(outer)
        text(statistics, "전력 요약", 18, FG, true)
        powerStats = PowerStatisticsView(this, palette)
        statistics.addView(powerStats, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        interruptionView = text(statistics, "충전 회복 0회", 14, FG, true).apply { setPadding(0, dp(12), 0, dp(3)) }
        text(statistics, "충전기를 연결한 채 방전으로 바뀌었다가 다시 충전된 횟수", 12, MUTED)

        val thermal = card(outer)
        thermalView = ThermalStatusView(this, palette)
        thermal.addView(thermalView, LinearLayout.LayoutParams(-1, -2))
        hardwareMonitor = HardwareMonitorView(this, palette, settings) { shizuku?.query() }
        outer.addView(hardwareMonitor, LinearLayout.LayoutParams(-1, -2))

        outer = dashboard.pages[2]
        val history = card(outer)
        text(history, "배터리 기록", 18, FG, true)
        historyStatus = text(history, "날짜별 조회·측정 기록", 13, MUTED)
        val categories = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        history.addView(categories, actionRowParams())
        chargingCategory = smallButton(categories, "충전·방전 기록") { historyCategory = 0; refreshRecords() }
        diagnosisCategory = smallButton(categories, "진단 기록") { historyCategory = 1; refreshRecords() }
        recordsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        outer.addView(recordsContainer)
        val evidence = card(outer)
        text(evidence, "측정 근거 · 결과 공유", 18, FG, true)
        text(evidence, "원본 값 확인과 결과 공유", 13, MUTED)
        button(evidence, "측정 근거 보기 · 공유") { showReport() }
        text(outer, "기록은 이 기기에만 저장돼요.\n원본 덤프는 보관하지 않아요.\nv0.5.6", 12, MUTED)
        setContentView(dashboard.root)
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
        progress.setLevel(level)
        cycleView.text = cycle?.let { "$it 회" } ?: "조회 필요"
        cycleNote.text = if (cycle != null) "누적 충전·사용 횟수예요. 충전기 연결 횟수와 달라요."
            else "배터리 상태 확인을 눌러 조회하세요"
        sohView.text = soh?.let { "$it%" } ?: "조회 필요"
        diagnosisLabel.text = if (soh != null) "배터리 성능" else "충전량 보정 값 (ASOC)"
        sohNote.text = if (soh != null) "기기가 추정한 완충 용량 비율"
            else "Shizuku 연결 후 조회하세요"
        detailed?.let { value ->
            diagnosisLabel.text = "충전량 보정 값 (ASOC)"
            cycleView.text = value.cycleText().replace("확인 불가", "정보 없음")
            cycleNote.text = if (value.usage.single()?.let { it > 0 } == true)
                "누적 배터리 사용량으로 계산한 대략적인 횟수예요.\n$detailedSource · $detailedTime"
                else "사이클 확인 불가 · 측정 근거 참고"
            sohView.text = value.asoc.health()?.let { "$it%" } ?: "정보 없음"
            sohNote.text = if (value.asoc.health() != null)
                "배터리 충전량을 계산·보정할 때 참고하는 값이에요.\n남은 수명 %는 아니에요. BSOH는 측정 근거에서 확인하세요."
                else "진단 값 없음 · 새 로그로 확인하세요"
        }
        val tempText = temperature?.let { BatteryValues.decimal(it / 10.0, "°C") } ?: "정보 없음"
        val voltText = voltage?.takeIf { it > 0 }?.let { String.format(Locale.KOREA, "%.3f V", it / 1000.0) } ?: "정보 없음"
        val currentText = current.value?.let { BatteryValues.decimal(it / 1000.0, "mA") } ?: "정보 없음"
        val chargeText = charge.value?.takeIf { it > 0 }?.let { BatteryValues.decimal(it / 1000.0, "mAh") } ?: "정보 없음"
        details.text = "온도  $tempText\n전압     $voltText\n현재 전류     $currentText\n현재 남은 용량     $chargeText\n상태     ${BatteryValues.condition(extra(battery, BatteryManager.EXTRA_HEALTH))}"
        updated.text = "기본 정보 업데이트  ${SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date())}"
        dashboard.refreshBackdrop()

        report = buildString {
            append("Galaxy Battery v0.5.6 / Kotlin\n조회 시간: $time\n")
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
            else "배터리 항목을 찾지 못했어요.\n새로 생성한 dumpstate 로그를 선택해 주세요."
        updateDetailCaption()
        render(lastBattery)
        if (result.hasFields()) {
            val savedTime = System.currentTimeMillis()
            val savedReport = report
            val summary = result.summary()
            historyWorker.execute {
                try {
                    historyStore.save(savedTime, source, summary, savedReport)
                    runOnUiThread { if (!isDestroyed) historyStatus.text = "최근 조회 결과를 저장했어요.\n‘이전 기록 보기’에서 확인해 주세요." }
                } catch (_: Exception) {
                    runOnUiThread { if (!isDestroyed) historyStatus.text = "결과는 조회했지만 기록을 저장하지 못했어요.\n기기의 저장 공간을 확인해 주세요." }
                }
            }
        }
    }

    private fun renderPower() {
        val state = ChargeMonitorService.snapshot
        val sample = if (state.active) state.samples.lastOrNull() else PowerSampler.read(this)
        val watts = sample?.watts()
        val temperature = sample?.temperature?.takeIf { it in -400..1500 }
        hardwareMonitor.setBatteryTemperature(temperature?.let { it / 10.0 })
        powerView.text = ChargePower.text(watts)
        powerView.setTextColor(if (watts != null && watts < 0) palette.negative else ACCENT)
        powerSubtitle.text = when {
            sample == null -> "측정 대기"
            watts == null -> "전력 정보 없음"
            watts < 0 -> "방전 중"
            sample.plugged == 0 -> "충전기 미연결"
            else -> BatteryValues.charging(sample.status)
        }
        if (state.id == null && sample != null && sample.time - (previewSamples.lastOrNull()?.time ?: 0L) >= settings.powerSeconds * 1000L) {
            previewSamples.addLast(sample); if (previewSamples.size > 600) previewSamples.removeFirst()
            previewStats.add(sample); previewInterruptions.add(sample)
        }
        val points = if (state.id != null) state.samples else previewSamples.toList()
        powerGraph.setSamples(points)
        val maximum = if (state.id != null) state.maximum else previewStats.maximum
        val dischargePeak = if (state.id != null) state.dischargeMaximum else previewStats.dischargeMaximum
        val chargeAverage = if (state.id != null) state.chargingAverage else previewStats.chargingAverage
        val dischargeAverage = if (state.id != null) state.dischargeAverage else previewStats.dischargeAverage
        powerStats.setValues(chargeAverage, maximum, dischargeAverage, dischargePeak)
        powerGraph.setScreenEvents(if (state.id != null) state.screenEvents else previewScreenEvents.toList(),
            if (state.active) System.currentTimeMillis() else 0L, state.id == null)
        if (state.active) { recordingWasActive = true; powerStarting = false }
        if (state.error != null) powerStarting = false
        monitorButton.text = if (state.active) "측정 종료" else if (powerStarting) "측정 시작 중…" else "측정 시작"
        monitorButton.isEnabled = !powerStarting
        // A recording sample may be up to 60 seconds old. The visible thermal
        // status follows OS events instead of being overwritten by that sample.
        updateThermal()
        val interruptions = if (state.id != null) state.interruptionState else previewInterruptions.state()
        interruptionView.text = "충전 회복 ${interruptions.count}회" + (interruptions.events.lastOrNull()?.let {
            "\n최근 ${clock(it.started)} → ${clock(it.recovered)}"
        } ?: "")
        dashboard.refreshBackdrop()
        monitorStatus.text = when {
            state.error != null -> state.error
            state.active -> "기록 중 · ${settings.powerSeconds}초 간격\n화면이 꺼져도 기록해요"
            recordingWasActive -> "저장 완료 · 기록에서 다시 볼 수 있어요"
            else -> "${settings.powerSeconds}초 간격\n측정을 시작하면 화면이 꺼져도 기록해요"
        }
    }

    private fun sampleCaption(sample: ChargePower.Sample): String {
        val time = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date(sample.time))
        val level = sample.level.takeIf { it in 0..100 }?.let { "$it%" } ?: "잔량 정보 없음"
        val temperature = sample.temperature.takeIf { it in -400..1500 }?.let { String.format(Locale.US, "%.1f°C", it / 10.0) } ?: "온도 정보 없음"
        return "측정 시각 $time\n${if ((sample.watts() ?: 0.0) < 0) "방전" else "유입"} ${ChargePower.text(sample.watts())}\n배터리 잔량 $level\n배터리 온도 $temperature\n${ThermalStatus.label(sample.thermalStatus).replace(" ·", "\n")}"
    }

    private fun togglePowerRecording() {
        if (ChargeMonitorService.snapshot.active) {
            startService(Intent(this, ChargeMonitorService::class.java).setAction(ChargeMonitorService.STOP))
            refreshHandler.postDelayed({ if (!isDestroyed) renderPower() }, 500)
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
            refreshHandler.postDelayed({ if (!isDestroyed) renderPower() }, 500)
            refreshHandler.postDelayed({
                if (!isDestroyed && !ChargeMonitorService.snapshot.active) {
                    powerStarting = false; renderPower()
                    toast("측정을 시작하지 못했어요.\n앱 알림과 백그라운드 실행 설정을 확인해 주세요.")
                }
                if (!isDestroyed) renderPower()
            }, 5000)
        } catch (_: RuntimeException) {
            powerStarting = false; renderPower()
            toast("측정을 시작하지 못했어요.\n앱을 다시 열고 시도해 주세요.")
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATION_PERMISSION) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startPowerRecording()
            else AlertDialog.Builder(this).setTitle("알림 표시를 허용해 주세요")
                .setMessage("화면이 꺼져도 기록하고 상단바에 W를 표시하려면 알림을 허용해 주세요.")
                .setPositiveButton("설정 열기") { _, _ -> openAppSettings() }.setNegativeButton("닫기", null).showUniform()
        }
    }

    private fun openAppSettings() {
        try { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
        catch (_: RuntimeException) { toast("휴대폰 설정에서 ‘배터리 사이클 체크’를 찾아 주세요.") }
    }

    private fun openLiveUpdateSettings() {
        val intent = Intent("android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS").putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        if (Build.VERSION.SDK_INT >= 36 && intent.resolveActivity(packageManager) != null) {
            try { startActivity(intent) } catch (_: RuntimeException) { openAppSettings() }
        } else openAppSettings()
    }

    private fun showPowerHistory() { historyCategory = 0; dashboard.select(2) }

    private fun loadPowerSession(id: String) {
        historyWorker.execute {
            try {
                val session = powerStore.read(id)
                runOnUiThread {
                    if (isDestroyed || isFinishing) return@runOnUiThread
                    val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(12)); setBackgroundColor(CARD) }
                    val end = if (session.ended > 0) SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date(session.ended))
                        else if (ChargeMonitorService.snapshot.active && ChargeMonitorService.snapshot.id == id) "측정 중" else "종료 시각 미기록 · 중단된 측정"
                    text(content, "종료 $end", 13, MUTED)
                    powerStatistics(content, session.chargingAverage, session.maximum, session.dischargeAverage, session.dischargeMaximum)
                    text(content, "충전 회복 ${session.interruptionState.count}회", 18, FG, true)
                    text(content, "최대 ${ThermalStatus.label(session.peakThermal)}", 13, MUTED)
                    session.interruptionState.events.forEach { event ->
                        text(content, "방전 시작 ${date(event.started)} ${clock(event.started)}\n충전 회복 ${date(event.recovered)} ${clock(event.recovered)}\n최저 전력 ${ChargePower.text(event.lowestWatts)}", 13, FG)
                    }
                    text(content, "충전기를 연결한 채 방전됐다가 다시 충전된 경우만 세요.\n시각은 측정한 시간이에요. 최근 200건까지 표시해요.", 12, MUTED)
                    val screenEnd = if (session.ended > 0) session.ended else if (ChargeMonitorService.snapshot.active && ChargeMonitorService.snapshot.id == id) System.currentTimeMillis() else maxOf(session.lastSampleTime, session.screenEvents.maxOfOrNull { it.time } ?: 0L)
                    val graph = PowerGraphView(this, palette).apply { setSamples(session.samples) }
                    graph.setScreenEvents(session.screenEvents, screenEnd)
                    content.addView(graph, LinearLayout.LayoutParams(-1, dp(220)))
                    val selection = text(content, "+ 충전 · − 방전 · 터치해서 값 확인", 12, MUTED)
                    graph.onSelection = { selection.text = sampleCaption(it) }
                    val off = ScreenTimeline.intervals(session.screenEvents, session.started, screenEnd)
                    text(content, if (session.screenEvents.isEmpty()) "이전 기록에는 화면 상태가 저장되지 않았어요."
                        else if (off.isEmpty()) "관측된 화면 꺼짐 구간이 없어요."
                        else "화면 꺼짐 ${off.size}구간\n총 ${off.sumOf { it.end - it.start } / 1000}초\n" + off.takeLast(30).joinToString("\n") { "꺼짐 ${date(it.start)} ${clock(it.start)}\n켜짐 ${date(it.end)} ${clock(it.end)}" }, 12, MUTED)
                    text(content, "회색은 화면이 꺼진 구간이에요.\n평균·최대는 읽을 수 있었던 값으로 계산해요.\n방전 통계는 빠져나간 전력을 양수로 표시해요.", 12, MUTED)
                    AlertDialog.Builder(this).setTitle(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA).format(Date(session.started)))
                        .setView(ScrollView(this).apply { addView(content) })
                        .setPositiveButton("목록으로") { _, _ -> showPowerHistory() }
                        .setNeutralButton("삭제") { _, _ -> confirmPowerDelete(listOf(id)) }
                        .setNegativeButton("닫기", null).showUniform()
                }
            } catch (_: Exception) { runOnUiThread { if (!isDestroyed) toast("이 기록을 읽지 못했어요.") } }
        }
    }

    private fun confirmPowerDelete(ids: List<String>) {
        if (ChargeMonitorService.snapshot.active && ids.contains(ChargeMonitorService.snapshot.id)) {
            toast("진행 중인 측정을 종료한 뒤 삭제해 주세요."); return
        }
        AlertDialog.Builder(this).setTitle("충전·방전 기록 ${ids.size}개를 삭제할까요?")
            .setMessage("삭제한 기록은 복구할 수 없어요.")
            .setNegativeButton("취소") { _, _ -> showPowerHistory() }
            .setPositiveButton("삭제") { _, _ -> historyWorker.execute {
                try {
                    val active = ChargeMonitorService.snapshot.takeIf { it.active }?.id
                    powerStore.delete(ids, active)
                    ChargeMonitorService.forgetDeleted(ids)
                    runOnUiThread { if (!isDestroyed && !isFinishing) { toast("충전·방전 기록을 삭제했어요."); showPowerHistory() } }
                } catch (_: Exception) { runOnUiThread { if (!isDestroyed) toast("기록을 삭제하지 못했어요.\n진행 중인 측정을 확인해 주세요.") } }
            } }.showUniform()
    }

    private fun showHistory() { historyCategory = 1; dashboard.select(2) }

    private fun refreshRecords() {
        listOf(chargingCategory, diagnosisCategory).forEachIndexed { index, button ->
            val selected = index == historyCategory
            button.isSelected = selected
            button.background = roundedButton(if (selected) palette.accent else palette.actionSurface)
            button.setTextColor(if (selected) palette.onAccent else palette.accent)
        }
        val generation = ++recordsGeneration
        val category = historyCategory
        historyStatus.text = if (category == 0) "충전·방전 기록을 불러오는 중…" else "진단 기록을 불러오는 중…"
        historyWorker.execute {
            try {
                val sessions = if (category == 0) powerStore.list() else emptyList()
                val entries = if (category == 1) historyStore.list() else emptyList()
                runOnUiThread {
                    if (isDestroyed || generation != recordsGeneration) return@runOnUiThread
                    recordsContainer.removeAllViews()
                    val size = sessions.size + entries.size
                    historyStatus.text = "${if (category == 0) "충전·방전 기록" else "진단 기록"} · ${size}개"
                    if (size == 0) card(recordsContainer).also {
                        text(it, "아직 기록이 없어요", 17, FG, true)
                        text(it, if (category == 0) "모니터링에서 측정을 시작하세요"
                            else "배터리 조회 결과가 자동 저장돼요", 13, MUTED)
                    }
                    sessions.forEach { session -> card(recordsContainer).also {
                        text(it, date(session.started), 17, FG, true)
                        val end = if (session.ended > 0) clock(session.ended) else if (ChargeMonitorService.snapshot.active && ChargeMonitorService.snapshot.id == session.id) "측정 중" else "중단된 측정"
                        text(it, "${clock(session.started)} → $end", 12, MUTED)
                        powerStatistics(it, session.chargingAverage, session.maximum, session.dischargeAverage, session.dischargeMaximum)
                        text(it, "충전 회복 ${session.interruptionState.count}회", 13, FG)
                        text(it, "최대 ${ThermalStatus.label(session.peakThermal)}", 12, MUTED)
                        button(it, "상세 보기") { loadPowerSession(session.id) }
                    } }
                    entries.forEach { entry -> card(recordsContainer).also {
                        text(it, date(entry.time), 17, FG, true)
                        text(it, "${clock(entry.time)}\n조회 출처 ${entry.source}", 12, MUTED)
                        text(it, entry.summary, 16, FG, true)
                        button(it, "진단 결과 보기") { showHistoryEntry(entry) }
                    } }
                    if (size > 0) smallButton(recordsContainer, "${if (category == 0) "충전·방전" else "진단"} 기록 전체 삭제") {
                        if (category == 0) confirmPowerDelete(sessions.map { it.id }) else confirmHistoryDelete(entries.map { it.id })
                    }
                    dashboard.refreshBackdrop()
                }
            } catch (_: Exception) { runOnUiThread {
                if (!isDestroyed && generation == recordsGeneration) historyStatus.text = "기록을 읽지 못했어요.\n목록을 다시 눌러 주세요."
            } }
        }
    }

    private fun showHistoryEntry(entry: HistoryStore.Entry) {
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(12)) }
        text(content, "조회 출처 ${entry.source}\n조회 시각 ${clock(entry.time)}", 13, MUTED)
        text(content, entry.summary, 22, FG, true)
        text(content, "조회 당시 결과예요. 로그 기록 날짜는 불러온 시각이에요.", 12, MUTED)
        val evidence = text(content, entry.report, 12, MUTED).apply {
            visibility = android.view.View.GONE
            typeface = Typeface.MONOSPACE; setTextIsSelectable(true)
        }
        button(content, "측정 근거 펼치기") {
            evidence.visibility = if (evidence.visibility == android.view.View.GONE) android.view.View.VISIBLE else android.view.View.GONE
        }
        AlertDialog.Builder(this)
            .setTitle(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA).format(Date(entry.time)))
            .setView(ScrollView(this).apply { addView(content) })
            .setPositiveButton("목록으로") { _, _ -> showHistory() }
            .setNeutralButton("삭제") { _, _ -> confirmHistoryDelete(listOf(entry.id)) }
            .setNegativeButton("닫기", null).showUniform()
    }

    private fun confirmHistoryDelete(ids: List<String>) {
        AlertDialog.Builder(this).setTitle("기록 ${ids.size}개를 삭제할까요?")
            .setMessage("복구할 수 없어요. 원본 로그 파일은 유지됩니다.")
            .setNegativeButton("취소") { _, _ -> showHistory() }
            .setPositiveButton("삭제") { _, _ ->
                historyWorker.execute {
                    try {
                        historyStore.delete(ids)
                        runOnUiThread {
                            if (!isDestroyed && !isFinishing) {
                                historyStatus.text = "선택한 기록을 삭제했어요.\n다음 조회 결과는 다시 자동 저장돼요."
                                showHistory()
                            }
                        }
                    } catch (_: Exception) {
                        runOnUiThread { if (!isDestroyed) toast("일부 기록을 삭제하지 못했어요.\n목록을 다시 확인해 주세요.") }
                    }
                }
            }.showUniform()
    }

    private fun updateDetailCaption() {
        detailed?.let {
            advancedDetails.text = "출처 $detailedSource\n조회 시각 $detailedTime\n" +
                if (detailedSource == "로그 분석") "파일의 저장 값 · 최신 로그로 갱신하세요"
                else "재조회하면 갱신돼요"
        }
    }

    private fun showShizukuHelp() {
        AlertDialog.Builder(this).setTitle("처음 한 번 연결해 주세요")
            .setMessage("1. Shizuku를 설치하고 열어 주세요.\n2. 휴대폰 개발자 옵션에서 ‘무선 디버깅’을 켜세요.\n3. Shizuku 안내에 따라 페어링하고 시작하세요.\n4. 이 앱의 ‘배터리 상태 확인’을 누르고 권한을 허용하세요.\n\n이후에는 버튼 하나로 확인할 수 있어요.\n재부팅하면 Shizuku를 다시 시작해야 할 수 있습니다.\n\n기기에서 정보를 공개하지 않으면 로그 파일로 확인할 수 있어요.")
            .setPositiveButton("Shizuku 열기") { _, _ ->
                val launch = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                    ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))
                try { startActivity(launch) }
                catch (_: RuntimeException) { toast("브라우저에서 shizuku.rikka.app을 열어 주세요.") }
            }.setNegativeButton("닫기", null).showUniform()
    }

    private fun pickDump() {
        if (importing || shizuku?.busy == true) { toast("진행 중인 조회가 끝나면 선택해 주세요."); return }
        AlertDialog.Builder(this).setTitle("배터리 로그 불러오기")
            .setMessage("1. 삼성 전화에서 *#9900#을 입력하세요.\n2. Run dumpstate/logcat을 실행하세요.\n3. 완료되면 Copy to sdcard를 누르세요.\n4. 내부 저장소의 log 폴더에서 새 dumpstate 파일을 선택하세요.\n\n메뉴와 저장 위치는 기기마다 다를 수 있어요.\n파일이 보이지 않으면 ‘내 파일’에서 Download 폴더로 복사해 주세요.\n\nTXT·LOG·ZIP·GZ 지원 · 배터리 항목만 추출합니다.")
            .setPositiveButton("파일 선택") { _, _ ->
                try {
                    startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*")
                        .addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), PICK_DUMP)
                } catch (_: RuntimeException) { advancedStatus.text = "파일 선택기를 열지 못했어요.\n잠시 후 다시 시도해 주세요." }
            }.setNegativeButton("취소", null).showUniform()
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
                    if (!isDestroyed) advancedStatus.text = "로그를 읽지 못했어요.\n압축을 풀어 dumpstate TXT·LOG 파일을 선택해 주세요.\n이전 결과는 그대로 유지돼요."
                }
            }
        }
    }

    private fun showReport() {
        val snapshot = report + "\nShizuku 진단: ${shizuku?.diagnostic ?: "미연결"}\n"
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(20)); setBackgroundColor(BG)
        }
        fun section(title: String, body: String, raw: Boolean = false) {
            card(content).also { panel ->
                text(panel, title, 16, FG, true)
                text(panel, body, if (raw) 12 else 14, if (raw) MUTED else FG).apply {
                    setTextIsSelectable(true)
                    setLineSpacing(dp(5).toFloat(), 1f)
                    if (raw) typeface = Typeface.MONOSPACE
                }
            }
        }
        section("배터리 진단 요약", detailed?.summary() ?: "아직 상세 진단을 조회하지 않았어요.")
        section("조회 출처 · 시간", "출처: ${detailedSource.ifEmpty { "기본 상태 조회" }}\n상세 조회: ${detailedTime.ifEmpty { "미조회" }}\n기본 상태: ${updated.text}\n로그를 불러온 시각은 로그 생성 시각과 달라요.")
        section("현재 배터리 상태", "잔량: ${levelView.text}\n상태: ${statusView.text}\n${details.text}")
        section("기기 정보", "${Build.MODEL}\nAndroid ${Build.VERSION.RELEASE}\nSDK ${Build.VERSION.SDK_INT}\n빌드: ${Build.DISPLAY}\n보안 패치: ${Build.VERSION.SECURITY_PATCH}")
        section("원본 진단 필드 · 계산 근거", detailed?.evidence() ?: "상세 조회 후 원본 필드가 표시돼요.", true)
        section("수치 해석", "ASOC는 충전량 계산·보정에 참고하는 값이에요. 정확한 남은 수명 %는 아니에요.\nBSOH는 처음 설계한 용량과 비교한 배터리 건강 상태예요. ASOC와는 따로 보세요.\n추정 사이클은 누적 사용량 ÷ 100으로 계산한 대략적인 횟수예요. 삼성의 공식 환산 규칙은 아니에요.\n현재 남은 용량은 완충 용량과 달라요. ‘정상’은 성능 100%를 뜻하지 않아요.")
        var expanded = false
        val rawPanel = card(content).apply { visibility = android.view.View.GONE }
        text(rawPanel, "전체 원문", 16, FG, true)
        text(rawPanel, snapshot, 12, MUTED).apply { typeface = Typeface.MONOSPACE; setTextIsSelectable(true) }
        smallButton(content, "전체 원문 펼치기 · 접기") {
            expanded = !expanded
            rawPanel.visibility = if (expanded) android.view.View.VISIBLE else android.view.View.GONE
        }
        AlertDialog.Builder(this).setTitle("측정 근거")
            .setView(ScrollView(this).apply { addView(content) })
            .setPositiveButton("공유") { _, _ ->
                val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, "배터리 사이클 체크 결과").putExtra(Intent.EXTRA_TEXT, snapshot)
                try { startActivity(Intent.createChooser(send, "결과 공유")) } catch (_: RuntimeException) { toast("공유할 앱을 찾지 못했어요.\n복사를 이용해 주세요.") }
            }.setNeutralButton("복사") { _, _ ->
                (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("배터리 사이클 체크", snapshot))
                toast("결과를 복사했어요.")
            }.setNegativeButton("닫기", null).showUniform()
    }

    private fun clock(time: Long) = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date(time))
    private fun date(time: Long) = SimpleDateFormat("yyyy년 M월 d일", Locale.KOREA).format(Date(time))
    private fun roundedButton(color: Int): RippleDrawable {
        val shape = GradientDrawable().apply { setColor(color); cornerRadius = dp(24).toFloat() }
        return RippleDrawable(ColorStateList.valueOf((FG and 0x00ffffff) or (35 shl 24)), shape, null)
    }

    private fun showSettings() {
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(12)) }
        fun section(title: String): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(14), dp(14), dp(14))
            background = GradientDrawable().apply { setColor(BG); cornerRadius = dp(18).toFloat() }
            content.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
            text(this, title, 16, FG, true)
        }
        val live = section("실시간 정보 표시")
        val chargingDisplay = OneUiToggle(this, palette, "충전 전력 표시", "상단바에 충전 W 표시", settings.showCharging)
        val dischargeDisplay = OneUiToggle(this, palette, "방전 전력 표시", "상단바에 방전 W 표시", settings.showDischarge)
        live.addView(chargingDisplay); live.addView(dischargeDisplay)
        text(live, "측정 중에만 표시 · Android 16 이상", 12, MUTED)
        text(live, "개발자 설정 → 추가 설정 → ‘모든 앱의 실시간 정보 보기’를 켜야 정상 표시돼요.", 12, MUTED)
        smallButton(live, "시스템 표시 설정") { openLiveUpdateSettings() }

        val appearance = section("화면")
        val themes = arrayOf("기기 설정 따르기", "라이트", "다크")
        val themeValues = arrayOf("system", "light", "dark")
        val theme = OptionPicker(this, palette, themes.toList(), themeValues.indexOf(settings.theme).coerceAtLeast(0))
        appearance.addView(theme, LinearLayout.LayoutParams(-1, dp(48)))
        val blur = OneUiToggle(this, palette, "배경 블러", "하단 메뉴와 설정 버튼", settings.blur)
        appearance.addView(blur)
        text(appearance, "블러는 Android 12 이상에서 지원해요", 12, MUTED)

        val refresh = section("갱신 간격")
        fun interval(label: String, initial: Int): OptionPicker {
            text(refresh, label, 13, MUTED)
            return OptionPicker(this, palette, RefreshPolicy.intervals.map { "${it}초" }, RefreshPolicy.intervals.indexOf(initial)).apply {
                refresh.addView(this, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(8) })
            }
        }
        val power = interval("전력 측정", settings.powerSeconds)
        val battery = interval("배터리 상태", settings.batterySeconds)
        val hardware = interval("CPU·GPU", settings.hardwareSeconds)
        text(refresh, "발열 정보는 10초마다 확인해요.\n시스템이 알려주는 쓰로틀링 단계 변화는 바로 반영해요.", 12, MUTED)
        text(refresh, "짧은 간격은 배터리·저장 공간을 더 사용해요", 12, MUTED)
        AlertDialog.Builder(this).setTitle("설정").setView(ScrollView(this).apply { addView(content) })
            .setPositiveButton("적용") { _, _ ->
                val appearanceChanged = settings.theme != themeValues[theme.selectedItemPosition] || settings.blur != blur.isChecked
                settings.preferences.edit()
                    .putBoolean(AppSettings.SHOW_CHARGING, chargingDisplay.isChecked)
                    .putBoolean(AppSettings.SHOW_DISCHARGE, dischargeDisplay.isChecked).apply()
                settings.theme = themeValues[theme.selectedItemPosition]; settings.blur = blur.isChecked
                settings.powerSeconds = RefreshPolicy.intervals[power.selectedItemPosition]
                settings.batterySeconds = RefreshPolicy.intervals[battery.selectedItemPosition]
                settings.hardwareSeconds = RefreshPolicy.intervals[hardware.selectedItemPosition]
                if (appearanceChanged) recreate() else {
                    // Applying live controls must preserve preview/history and the running session.
                    refreshHandler.removeCallbacks(liveRefresh); refreshHandler.removeCallbacks(powerRefresh); refreshHandler.removeCallbacks(hardwareRefresh)
                    if (registered) {
                        refreshHandler.post(liveRefresh); refreshHandler.post(powerRefresh)
                        if (dashboard.selected == 1) refreshHandler.post(hardwareRefresh)
                    }
                }
            }.setNegativeButton("취소", null).showUniform()
    }

    private fun timestamp() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA).format(Date())
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun text(parent: LinearLayout, value: String, size: Int, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value; setLineSpacing(dp(2).toFloat(), 1f); textSize = size.toFloat(); setTextColor(color)
            if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(0, dp(3), 0, dp(3))
            parent.addView(this, LinearLayout.LayoutParams(-1, -2))
        }

    private fun powerStatistics(parent: LinearLayout, chargeMean: Double?, chargePeak: Double?, dischargeMean: Double?, dischargePeak: Double?) {
        parent.addView(PowerStatisticsView(this, palette).apply { setValues(chargeMean, chargePeak, dischargeMean, dischargePeak) },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10); bottomMargin = dp(10) })
    }

    private fun card(parent: LinearLayout): LinearLayout = AppUi.card(this, palette).apply {
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
    }

    private fun button(parent: LinearLayout, label: String, action: () -> Unit): Button {
        val button = AppUi.action(this, palette, label, primary = true, clicked = action)
        parent.addView(button, LinearLayout.LayoutParams(-1, dp(AppUi.ACTION_HEIGHT)).apply { topMargin = dp(12); bottomMargin = dp(4) })
        return button
    }

    private fun actionRowParams() = LinearLayout.LayoutParams(-1, -2).apply {
        topMargin = dp(12); bottomMargin = dp(12)
    }

    private fun smallButton(parent: LinearLayout, label: String, action: () -> Unit): Button {
        val button = AppUi.action(this, palette, label, clicked = action)
        parent.addView(button, if (parent.orientation == LinearLayout.HORIZONTAL) LinearLayout.LayoutParams(0, dp(AppUi.ACTION_HEIGHT), 1f).apply { if (parent.childCount > 0) marginStart = dp(AppUi.GAP) }
            else LinearLayout.LayoutParams(-1, dp(AppUi.ACTION_HEIGHT)).apply { topMargin = dp(12); bottomMargin = dp(4) })
        return button
    }

    companion object {
        private const val NOTIFICATION_PERMISSION = 301
        private const val CYCLE = "android.os.extra.CYCLE_COUNT"
        private const val PICK_DUMP = 201
    }
}
