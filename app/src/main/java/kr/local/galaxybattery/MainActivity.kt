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
    private lateinit var zeroView: TextView
    private val previewZero = ZeroPowerTracker()
    private var historyCategory = 0
    private var recordsGeneration = 0

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
    private lateinit var progress: BatteryLevelView
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
        if (::thermalView.isInitialized) thermalView.setStatus(thermalMonitor.status, thermalMonitor.headroom, hardwareTelemetry.latest.cooling)
    }

    override fun onCreate(state: Bundle?) {
        val style = if (settings.isDark(this)) "AppTheme" else "AppTheme.Light"
        setTheme(resources.getIdentifier(style, "style", packageName))
        super.onCreate(state)
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
                    .setNegativeButton("닫기", null).show()
            }
            override fun result(fields: String) {
                try { setDetailed(DumpParser.parse(fields.byteInputStream()), "직접 조회") }
                catch (_: Exception) { advancedStatus.text = "정보를 해석하지 못했어요. 로그 파일로 다시 확인해 주세요." }
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
        text(advanced, "ASOC·BSOH와 사이클을 읽으려면 Shizuku를 연결해 주세요. 저장한 SysDump 로그도 불러올 수 있어요.", 13, MUTED)
        advancedStatus = text(advanced, "연결 상태를 확인하고 있어요…", 13, MUTED)
        button(advanced, "배터리 상태 확인") { if (!importing) shizuku?.query() }
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        advanced.addView(actions, actionRowParams())
        smallButton(actions, "연결 설정") { showShizukuHelp() }
        smallButton(actions, "로그 불러오기") { pickDump() }
        advancedDetails = text(advanced, "조회 결과는 배터리 진단과 사이클에 표시돼요.", 12, MUTED)
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
            text(it, "화면을 보는 동안 ${settings.batterySeconds}초마다 업데이트돼요.", 12, ACCENT)
            details = text(it, "", 15, MUTED).apply { setLineSpacing(dp(7).toFloat(), 1f) }
            text(it, "‘정상’은 배터리의 상태 코드예요. 용량 유지율은 별도로 확인해 주세요.", 12, MUTED)
        }
        updated = text(outer, "", 12, MUTED).apply { setPadding(0, dp(18), 0, dp(6)) }
        outer = dashboard.pages[1]
        val power = card(outer)
        text(power, "충전·방전 전력", 14, MUTED, true)
        powerView = text(power, "— W", 48, ACCENT, true)
        powerSubtitle = text(power, "배터리로 들어오는 순전력을 확인해요.", 13, MUTED)
        text(power, "배터리 전압 × 순전류로 계산해요. 충전기 출력과는 차이가 있어요.", 12, MUTED)
        powerStats = text(power, "최고 — W     최저 — W", 16, FG, true).apply { setPadding(0, dp(14), 0, dp(8)) }
        thermalView = ThermalStatusView(this, palette)
        power.addView(thermalView, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8); bottomMargin = dp(12) })
        zeroView = text(power, "0W 이후 회복 0회", 14, FG)
        text(power, "전력 변화", 14, FG, true)
        powerGraph = PowerGraphView(this, palette)
        power.addView(powerGraph, LinearLayout.LayoutParams(-1, dp(200)).apply { topMargin = dp(8); bottomMargin = dp(12) })
        graphSelection = text(power, "그래프를 터치하면 그 시점의 값을 볼 수 있어요.", 12, MUTED)
        powerGraph.onSelection = { graphSelection.text = sampleCaption(it) }
        monitorStatus = text(power, "측정을 시작하면 화면을 꺼도 ${settings.powerSeconds}초 간격으로 충전·방전을 기록해요.", 13, MUTED)
        monitorButton = button(power, "측정 시작") { togglePowerRecording() }
        val powerActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        power.addView(powerActions, actionRowParams())
        smallButton(powerActions, "충전·방전 기록") { showPowerHistory() }
        smallButton(powerActions, "상단바 설정") { showPowerSettings() }

        hardwareMonitor = HardwareMonitorView(this, palette, settings) { shizuku?.query() }
        outer.addView(hardwareMonitor, LinearLayout.LayoutParams(-1, -2))

        outer = dashboard.pages[2]
        val history = card(outer)
        text(history, "배터리 기록", 18, FG, true)
        historyStatus = text(history, "조회 결과와 충전 측정을 날짜별로 확인하세요.", 13, MUTED)
        val categories = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        history.addView(categories, actionRowParams())
        chargingCategory = smallButton(categories, "충전·방전 기록") { historyCategory = 0; refreshRecords() }
        diagnosisCategory = smallButton(categories, "진단 기록") { historyCategory = 1; refreshRecords() }
        recordsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        outer.addView(recordsContainer)
        val evidence = card(outer)
        text(evidence, "측정 근거 · 결과 공유", 18, FG, true)
        text(evidence, "값의 출처와 해석을 확인하고 현재 결과를 공유하세요.", 13, MUTED)
        button(evidence, "측정 근거 보기 · 공유") { showReport() }
        text(outer, "기록은 이 기기에만 저장돼요. 원본 덤프는 보관하지 않아요.\nv0.5.2", 12, MUTED)
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
        dashboard.refreshBackdrop()

        report = buildString {
            append("Galaxy Battery v0.5.2 / Kotlin\n조회 시간: $time\n")
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
        val temperature = sample?.temperature?.takeIf { it in -400..1500 }
        hardwareMonitor.setBatteryTemperature(temperature?.let { it / 10.0 })
        powerView.text = ChargePower.text(watts)
        powerSubtitle.text = when {
            sample == null -> "첫 측정 값을 기다리고 있어요."
            watts == null -> "기기에서 전류 또는 전압 값을 제공하지 않아요."
            watts < 0 -> "배터리 사용 중 · 음수는 배터리에서 나가는 전력이에요."
            sample.plugged == 0 -> "충전기 미연결 · 방전 전력도 함께 기록해요."
            else -> "${BatteryValues.charging(sample.status)} · 배터리 기준 순전력"
        }
        if (state.id == null && sample != null && sample.time - (previewSamples.lastOrNull()?.time ?: 0L) >= settings.powerSeconds * 1000L) {
            previewSamples.addLast(sample); if (previewSamples.size > 600) previewSamples.removeFirst()
            previewStats.add(sample); previewZero.add(sample)
        }
        val points = if (state.id != null) state.samples else previewSamples.toList()
        powerGraph.setSamples(points)
        val minimum = if (state.id != null) state.minimum else previewStats.minimum
        val maximum = if (state.id != null) state.maximum else previewStats.maximum
        val dischargeCount = if (state.id != null) state.dischargeCount else previewStats.dischargeCount
        val dischargePeak = if (state.id != null) state.dischargeMaximum else previewStats.dischargeMaximum
        powerStats.text = "충전 최고 ${ChargePower.text(maximum)} · 최저 ${ChargePower.text(minimum)}\n방전 ${dischargeCount}개 · 최대 ${ChargePower.text(dischargePeak)}"
        if (state.active) { recordingWasActive = true; powerStarting = false }
        if (state.error != null) powerStarting = false
        monitorButton.text = if (state.active) "측정 종료 · 기록 저장" else if (powerStarting) "측정 시작 중…" else "측정 시작"
        monitorButton.isEnabled = !powerStarting
        // A recording sample may be up to 60 seconds old. The visible thermal
        // status follows OS events instead of being overwritten by that sample.
        updateThermal()
        val zero = if (state.id != null) state.zeroState else previewZero.state()
        zeroView.text = "0W 이후 회복 ${zero.count}회" + (zero.events.lastOrNull()?.let {
            "\n최근 ${clock(it.started)} → ${clock(it.recovered)}"
        } ?: "")
        dashboard.refreshBackdrop()
        monitorStatus.text = when {
            state.error != null -> state.error
            state.active -> "● 기록 중 · ${settings.powerSeconds}초 간격 · ${state.count}개 측정\n화면을 꺼도 기록해요. 충전·방전 모두 ‘측정 종료’까지 기록해요."
            recordingWasActive -> "측정을 종료했어요. ‘충전·방전 기록’에서 그래프와 최고·최저를 다시 볼 수 있어요."
            else -> "측정을 시작하면 화면을 꺼도 ${settings.powerSeconds}초 간격으로 기록해요."
        }
    }

    private fun sampleCaption(sample: ChargePower.Sample): String {
        val time = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date(sample.time))
        val level = sample.level.takeIf { it in 0..100 }?.let { "$it%" } ?: "잔량 미지원"
        val temperature = sample.temperature.takeIf { it in -400..1500 }?.let { String.format(Locale.US, "%.1f°C", it / 10.0) } ?: "온도 미지원"
        return "$time · ${if ((sample.watts() ?: 0.0) < 0) "방전" else "유입"} ${ChargePower.text(sample.watts())} · $level\n배터리 $temperature · ${ThermalStatus.label(sample.thermalStatus)}"
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
                    toast("측정을 시작하지 못했어요. 앱 알림과 백그라운드 실행 설정을 확인해 주세요.")
                }
                if (!isDestroyed) renderPower()
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
        catch (_: RuntimeException) { toast("휴대폰 설정에서 ‘배터리 사이클 체크’를 찾아 주세요.") }
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
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(12)) }
        val discharge = OneUiToggle(this, palette, "방전 중에도 상단바에 전력 표시", "충전과 방전을 함께 확인", settings.showDischarge) { settings.showDischarge = it }
        content.addView(discharge)
        text(content, "방전은 −4.2W처럼 음수로 표시해요. 최고·최저는 상단바에 표시하지 않아요.", 12, MUTED)
        text(content, "갤럭시: 개발자 설정 → 추가 설정 → ‘모든 앱의 실시간 정보 보기’를 켜 주세요. One UI 버전에 따라 메뉴 위치와 이름이 다를 수 있어요.", 13, FG)
        AlertDialog.Builder(this).setTitle("상단바 · 실시간 전력")
            .setView(content)
            .setMessage("$state\n\n충전 중 ‘12.3W’처럼 현재 전력만 표시하도록 요청해요. 이 기능은 Android 16 이상에서 지원하며, Live Update와 연결된 알림도 함께 존재합니다.\n\n화면을 꺼도 측정하려면 측정 시작을 누르세요. 기록이 자주 중단되면 앱 정보 → 배터리에서 제한 없음으로 설정하고, 삼성 절전 앱 목록에서도 제외해 주세요. 측정 중에는 CPU가 깨어 있어 배터리 사용량이 늘 수 있어요.")
            .setPositiveButton("실시간 업데이트 설정") { _, _ ->
                val intent = Intent("android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS").putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                if (Build.VERSION.SDK_INT >= 36 && intent.resolveActivity(packageManager) != null) {
                    try { startActivity(intent) } catch (_: RuntimeException) { openAppSettings() }
                } else openAppSettings()
            }.setNeutralButton("앱 실행 설정") { _, _ -> openAppSettings() }
            .setNegativeButton("닫기", null).show()
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
                    text(content, "${session.count}개 측정 · $end", 13, MUTED)
                    text(content, "최고 ${ChargePower.text(session.maximum)}\n최저 ${ChargePower.text(session.minimum)}", 21, FG, true)
                    text(content, "방전 ${session.dischargeCount}개 측정 · 최대 ${ChargePower.text(session.dischargeMaximum)}\n최소 ${ChargePower.text(session.dischargeMinimum)} · 전력 크기 기준", 16, palette.negative, true)
                    text(content, "0W 이후 회복 ${session.zeroState.count}회", 18, FG, true)
                    text(content, "최고 열 제한 단계 · ${ThermalStatus.label(session.peakThermal)}", 13, MUTED)
                    session.zeroState.events.forEach { event ->
                        text(content, "${date(event.started)} ${clock(event.started)}\n→ ${date(event.recovered)} ${clock(event.recovered)} · 0W ${event.samples}개 측정", 13, FG)
                    }
                    text(content, "0W 구간의 시작·회복은 측정 시각 기준이에요. 연결 해제·미지원·음수 값은 제외해요. 구간은 최근 200건을 표시하고 횟수는 전체 기록 기준이에요.", 12, MUTED)
                    val graph = PowerGraphView(this, palette).apply { setSamples(session.samples) }
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
        AlertDialog.Builder(this).setTitle("충전·방전 기록 ${ids.size}개를 삭제할까요?")
            .setMessage("그래프와 최고·최저 기록이 함께 삭제되며 복구할 수 없어요.")
            .setNegativeButton("취소") { _, _ -> showPowerHistory() }
            .setPositiveButton("삭제") { _, _ -> historyWorker.execute {
                try {
                    val active = ChargeMonitorService.snapshot.takeIf { it.active }?.id
                    powerStore.delete(ids, active)
                    ChargeMonitorService.forgetDeleted(ids)
                    runOnUiThread { if (!isDestroyed && !isFinishing) { toast("충전·방전 기록을 삭제했어요."); showPowerHistory() } }
                } catch (_: Exception) { runOnUiThread { if (!isDestroyed) toast("기록을 삭제하지 못했어요. 진행 중인 측정을 확인해 주세요.") } }
            } }.show()
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
                        text(it, if (category == 0) "충전 모니터링에서 ‘측정 시작’을 누르면 그래프와 전력·열 제한 단계가 저장돼요."
                            else "배터리 정보를 조회하거나 로그를 불러오면 결과가 자동으로 저장돼요.", 13, MUTED)
                    }
                    sessions.forEach { session -> card(recordsContainer).also {
                        text(it, date(session.started), 17, FG, true)
                        val end = if (session.ended > 0) clock(session.ended) else if (ChargeMonitorService.snapshot.active && ChargeMonitorService.snapshot.id == session.id) "측정 중" else "중단된 측정"
                        text(it, "${clock(session.started)} → $end · ${session.count}개 측정", 12, MUTED)
                        text(it, "최고 ${ChargePower.text(session.maximum)}    최저 ${ChargePower.text(session.minimum)}", 16, ACCENT, true)
                        text(it, "방전 ${session.dischargeCount}개 · 최대 ${ChargePower.text(session.dischargeMaximum)}", 15, palette.negative, true)
                        text(it, "0W 이후 회복 ${session.zeroState.count}회 · 최고 열 제한 ${session.peakThermal.takeIf { v -> v >= 0 }?.let { v -> "${v}단계" } ?: "미기록"}", 13, FG)
                        button(it, "그래프 · 구간 보기") { loadPowerSession(session.id) }
                    } }
                    entries.forEach { entry -> card(recordsContainer).also {
                        text(it, date(entry.time), 17, FG, true)
                        text(it, "${clock(entry.time)} · ${entry.source}", 12, MUTED)
                        text(it, entry.summary, 16, FG, true)
                        button(it, "진단 결과 보기") { showHistoryEntry(entry) }
                    } }
                    if (size > 0) smallButton(recordsContainer, "${if (category == 0) "충전·방전" else "진단"} 기록 전체 삭제") {
                        if (category == 0) confirmPowerDelete(sessions.map { it.id }) else confirmHistoryDelete(entries.map { it.id })
                    }
                    dashboard.refreshBackdrop()
                }
            } catch (_: Exception) { runOnUiThread {
                if (!isDestroyed && generation == recordsGeneration) historyStatus.text = "기록을 읽지 못했어요. 목록을 다시 눌러 주세요."
            } }
        }
    }

    private fun showHistoryEntry(entry: HistoryStore.Entry) {
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(12)) }
        text(content, "${entry.source} · ${clock(entry.time)}", 13, MUTED)
        text(content, entry.summary, 22, FG, true)
        text(content, "로그 분석 기록은 파일을 읽은 시각이에요. 당시의 결과를 보관하며 현재 상태와 다를 수 있어요.", 12, MUTED)
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
        section("기기 정보", "${Build.MODEL}\nAndroid ${Build.VERSION.RELEASE} · SDK ${Build.VERSION.SDK_INT}\n빌드: ${Build.DISPLAY}\n보안 패치: ${Build.VERSION.SECURITY_PATCH}")
        section("원본 진단 필드 · 계산 근거", detailed?.evidence() ?: "상세 조회 후 원본 필드가 표시돼요.", true)
        section("수치 해석", "ASOC와 BSOH는 서로 다른 지표예요. ASOC를 정확한 용량 유지율로 해석하지 마세요.\n추정 사이클은 누적 사용량 ÷ 100이며 비공식 환산이에요.\n잔여 전하량은 완충 용량이 아니며, ‘정상’은 성능 100%를 뜻하지 않아요.")
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
                try { startActivity(Intent.createChooser(send, "결과 공유")) } catch (_: RuntimeException) { toast("공유할 앱을 찾지 못했어요. 복사를 이용해 주세요.") }
            }.setNeutralButton("복사") { _, _ ->
                (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("배터리 사이클 체크", snapshot))
                toast("결과를 복사했어요.")
            }.setNegativeButton("닫기", null).show()
    }

    private fun clock(time: Long) = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date(time))
    private fun date(time: Long) = SimpleDateFormat("yyyy년 M월 d일", Locale.KOREA).format(Date(time))
    private fun roundedButton(color: Int): RippleDrawable {
        val shape = GradientDrawable().apply { setColor(color); cornerRadius = dp(24).toFloat() }
        return RippleDrawable(ColorStateList.valueOf((FG and 0x00ffffff) or (35 shl 24)), shape, null)
    }

    private fun showSettings() {
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(12)) }
        text(content, "테마", 16, FG, true)
        val themes = arrayOf("기기 설정 따르기", "라이트", "다크")
        val themeValues = arrayOf("system", "light", "dark")
        val theme = OptionPicker(this, palette, themes.toList(), themeValues.indexOf(settings.theme).coerceAtLeast(0))
        content.addView(theme, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(16) })
        fun interval(label: String, initial: Int): OptionPicker {
            text(content, label, 16, FG, true)
            return OptionPicker(this, palette, RefreshPolicy.intervals.map { "${it}초" }, RefreshPolicy.intervals.indexOf(initial)).apply {
                content.addView(this, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(16) })
            }
        }
        val power = interval("충전 속도 갱신 · 기록 간격", settings.powerSeconds)
        val battery = interval("기본 배터리 상태 갱신 간격", settings.batterySeconds)
        val hardware = interval("CPU · GPU 사용률 갱신 간격", settings.hardwareSeconds)
        text(content, "상세 ASOC·BSOH 조회는 ‘배터리 상태 확인’ 버튼으로 실행해요. 짧은 기록 간격은 배터리 사용량과 저장 공간을 늘릴 수 있어요.", 12, MUTED)
        val blur = OneUiToggle(this, palette, "배경 블러", "하단 메뉴 · 설정 버튼", settings.blur)
        content.addView(blur)
        text(content, "Android 12 이상에서 적용되며, 지원되지 않으면 단색 배경을 사용해요.", 12, MUTED)
        button(content, "상단바 실시간 전력 설정") { showPowerSettings() }
        AlertDialog.Builder(this).setTitle("설정").setView(ScrollView(this).apply { addView(content) })
            .setPositiveButton("적용") { _, _ ->
                settings.theme = themeValues[theme.selectedItemPosition]; settings.blur = blur.isChecked
                settings.powerSeconds = RefreshPolicy.intervals[power.selectedItemPosition]
                settings.batterySeconds = RefreshPolicy.intervals[battery.selectedItemPosition]
                settings.hardwareSeconds = RefreshPolicy.intervals[hardware.selectedItemPosition]
                // Recreate preserves the selected page and detailed report via saved instance state.
                recreate()
            }.setNegativeButton("취소", null).show()
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
        background = GradientDrawable().apply {
            setColor(CARD); cornerRadius = dp(24).toFloat()
            setStroke(dp(1), (MUTED and 0x00ffffff) or (28 shl 24))
        }
        elevation = 0f
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20) })
    }

    private fun button(parent: LinearLayout, label: String, action: () -> Unit): Button {
        val button = Button(this).apply {
            text = label; isAllCaps = false; textSize = 14f; setTextColor(palette.onAccent)
            background = roundedButton(ACCENT)
            gravity = Gravity.CENTER
            minHeight = 0; minimumHeight = 0; setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener { action() }
        }
        parent.addView(button, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(12); bottomMargin = dp(4) })
        return button
    }

    private fun actionRowParams() = LinearLayout.LayoutParams(-1, -2).apply {
        topMargin = dp(12); bottomMargin = dp(12)
    }

    private fun smallButton(parent: LinearLayout, label: String, action: () -> Unit): Button {
        val button = Button(this, null, android.R.attr.borderlessButtonStyle).apply {
            text = label; isAllCaps = false; textSize = 13f; setTextColor(ACCENT)
            background = roundedButton(palette.actionSurface)
            gravity = Gravity.CENTER
            minHeight = 0; minimumHeight = 0; setPadding(dp(8), 0, dp(8), 0)
            setOnClickListener { action() }
        }
        parent.addView(button, if (parent.orientation == LinearLayout.HORIZONTAL) LinearLayout.LayoutParams(0, dp(48), 1f).apply { if (parent.childCount > 0) marginStart = dp(12) }
            else LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(12); bottomMargin = dp(4) })
        return button
    }

    companion object {
        private const val NOTIFICATION_PERMISSION = 301
        private const val CYCLE = "android.os.extra.CYCLE_COUNT"
        private const val PICK_DUMP = 201
    }
}
