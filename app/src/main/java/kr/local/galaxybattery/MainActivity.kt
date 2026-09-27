package kr.local.galaxybattery

import android.app.Activity
import android.app.AlertDialog
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
        text(outer, "GALAXY BATTERY", 12, GREEN, true)
        text(outer, "배터리 상태", 30, FG, true).setPadding(0, dp(10), 0, dp(6))
        text(outer, "${Build.MODEL}  ·  Android ${Build.VERSION.RELEASE}", 13, MUTED)

        val advanced = card(outer)
        text(advanced, "배터리 정보 불러오기", 18, FG, true)
        text(advanced, "배터리 수명과 사이클을 불러오려면 Shizuku 연결이 필요해요. 아래 ‘연결 설정’에서 시작해 주세요.", 13, MUTED)
        advancedStatus = text(advanced, "연결 상태를 확인하고 있어요…", 13, MUTED)
        button(advanced, "배터리 상태 확인") { if (!importing) shizuku?.query() }
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        advanced.addView(actions)
        smallButton(actions, "연결 설정") { showShizukuHelp() }
        smallButton(actions, "로그 불러오기") { pickDump() }
        advancedDetails = text(advanced, "조회 결과는 아래에서 확인할 수 있어요.", 12, MUTED)

        val hero = card(outer)
        text(hero, "현재 잔량", 14, MUTED)
        levelView = text(hero, "—", 58, GREEN, true)
        statusView = text(hero, "확인 중", 15, FG)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = ColorStateList.valueOf(GREEN)
        }
        hero.addView(progress, LinearLayout.LayoutParams(-1, dp(8)).apply { topMargin = dp(18) })

        card(outer).also {
            text(it, "충전 사이클", 14, MUTED)
            cycleView = text(it, "—", 30, FG, true)
            cycleNote = text(it, "", 13, MUTED)
        }
        card(outer).also {
            text(it, "배터리 성능", 14, MUTED)
            sohView = text(it, "—", 30, FG, true)
            sohNote = text(it, "", 13, MUTED)
        }
        card(outer).also {
            text(it, "배터리 상태", 16, FG, true)
            text(it, "화면을 보는 동안 2초마다 업데이트돼요.", 12, GREEN)
            details = text(it, "", 15, MUTED).apply { setLineSpacing(dp(7).toFloat(), 1f) }
            text(it, "상태가 ‘정상’이어도 배터리는 사용하면서 노화돼요. 성능 수치와 함께 확인해 주세요.", 12, MUTED)
        }
        updated = text(outer, "", 12, MUTED).apply { setPadding(0, dp(18), 0, dp(6)) }
        button(outer, "측정 근거 · 결과 공유") { showReport() }
        text(outer, "배터리 기록은 이 기기에서만 처리해요. 공유할 때도 배터리 결과만 전달됩니다.\nv0.3.2", 12, MUTED)
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
                "휴대폰에 저장된 성능 추정치예요. 로그로 확인한 값은 로그 생성 당시의 상태입니다."
                else "이 기록에 유효한 성능 값이 없어요. 새 로그로 다시 확인해 주세요."
        }
        val tempText = temperature?.let { BatteryValues.decimal(it / 10.0, "°C") } ?: "정보 없음"
        val voltText = voltage?.takeIf { it > 0 }?.let { String.format(Locale.KOREA, "%.3f V", it / 1000.0) } ?: "정보 없음"
        val currentText = current.value?.let { BatteryValues.decimal(it / 1000.0, "mA") } ?: "정보 없음"
        val chargeText = charge.value?.takeIf { it > 0 }?.let { BatteryValues.decimal(it / 1000.0, "mAh") } ?: "정보 없음"
        details.text = "온도     $tempText\n전압     $voltText\n순간 전류     $currentText\n남은 전하량     $chargeText\n상태     ${BatteryValues.condition(extra(battery, BatteryManager.EXTRA_HEALTH))}"
        updated.text = "기본 정보 업데이트  ${SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date())}"

        report = buildString {
            append("Galaxy Battery v0.3.2 / Kotlin\n조회 시간: $time\n")
            append("Model: ${Build.MODEL}\nAndroid: ${Build.VERSION.RELEASE}\nSDK: ${Build.VERSION.SDK_INT}\n")
            append("Build: ${Build.DISPLAY}\nSecurity patch: ${Build.VERSION.SECURITY_PATCH}\n")
            append("\n공식 사이클 원본: ${rawCycle ?: "미제공"}\n플랫폼 SOH 속성 10: ${healthProperty.raw}\n")
            append("Current (uA): ${current.raw}\nCharge counter (uAh): ${charge.raw}\n")
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
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
    }

    private fun button(parent: LinearLayout, label: String, action: () -> Unit) {
        val button = Button(this).apply {
            text = label; isAllCaps = false; setTextColor(BG)
            backgroundTintList = ColorStateList.valueOf(GREEN)
            minHeight = dp(52); setOnClickListener { action() }
        }
        parent.addView(button, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6); bottomMargin = dp(6) })
    }

    private fun smallButton(parent: LinearLayout, label: String, action: () -> Unit) {
        parent.addView(Button(this, null, android.R.attr.borderlessButtonStyle).apply {
            text = label; isAllCaps = false; textSize = 13f; setTextColor(GREEN)
            minHeight = dp(48); setOnClickListener { action() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
    }

    companion object {
        private val BG = Color.rgb(16, 21, 19)
        private val CARD = Color.rgb(28, 36, 32)
        private val FG = Color.rgb(239, 245, 241)
        private val MUTED = Color.rgb(170, 187, 177)
        private val GREEN = Color.rgb(153, 227, 186)
        private const val CYCLE = "android.os.extra.CYCLE_COUNT"
        private const val PICK_DUMP = 201
    }
}
