package kr.local.galaxybattery

import java.io.*
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

/** Keeps only numeric battery fields; no complete log lines or archive extraction. */
object DumpParser {
    private val fieldPattern = Regex("""\b(mSavedBatteryAsoc|mSavedBatteryBsoh|mSavedBatteryUsage)\s*[:=]\s*(\[[^\]\r\n]{0,100}\]|[^\s,;}]{1,100})""")
    const val MAX_BYTES = 512L * 1024 * 1024
    private const val MAX_LINE = 65536

    class Field(val name: String) {
        private val samples = linkedSetOf<String>()
        private var first: Long? = null
        private var ambiguous = false
        var count: Int = 0
            private set

        internal fun accept(raw: String) {
            val clean = raw.trim().removeSurrounding("[", "]").trim()
            val value = if (clean.matches(Regex("[+-]?\\d{1,19}"))) clean.toLongOrNull() else null
            val safe = if (clean.matches(Regex("[+\\-0-9, \\t]{1,100}"))) clean else "invalid / unsupported"
            if (samples.size < 8) samples.add(safe)
            if (count++ == 0) first = value else if (first != value) ambiguous = true
            if (value == null) ambiguous = true
        }

        fun single(): Long? = if (count > 0 && !ambiguous) first else null
        fun health(): Int? = single()?.takeIf { it in 1L..100L }?.toInt()
        fun raw(): String = if (count == 0) "없음" else samples.joinToString(" / ")
        fun conflicted(): Boolean = ambiguous
    }

    class Result {
        @JvmField val asoc = Field("mSavedBatteryAsoc")
        @JvmField val bsoh = Field("mSavedBatteryBsoh")
        @JvmField val usage = Field("mSavedBatteryUsage")
        @JvmField var skippedLongLines = false
        @JvmField var files = 0
        fun hasFields(): Boolean = asoc.count + bsoh.count + usage.count > 0
        fun cycleText(): String = usage.single()?.takeIf { it > 0 }?.let {
            String.format(Locale.KOREA, "약 %.2f 회", it / 100.0)
        } ?: "확인 불가"

        fun summary(): String = "ASOC  ${asoc.health()?.let { "$it%" } ?: "확인 불가"}" +
            "\n추정 사이클  ${cycleText()}" +
            "\nBSOH (별도 지표)  ${bsoh.health()?.let { "$it%" } ?: "확인 불가"}"

        fun evidence(): String = buildString {
            listOf(asoc, usage, bsoh).forEach { field ->
                append("${field.name} = ${field.raw()}")
                if (field.conflicted()) append(" (복수 값 또는 해석 불가: 단일 값 선택 안 함)")
                append('\n')
            }
            append("사이클 환산: mSavedBatteryUsage / 100 (비공식 추정)\n")
            append("ASOC/BSOH는 삼성 내부 보고값이며 서로 다른 지표입니다.\n")
            if (skippedLongLines) append("64 KiB가 넘는 줄은 제외했습니다.\n")
        }

        /** A minimal, parseable snapshot. Unresolved values stay unresolved. */
        fun safeFields(): String = buildString {
            listOf(asoc, usage, bsoh).filter { it.count > 0 }.forEach {
                append("${it.name}: ${it.single() ?: "unsupported"}\n")
            }
        }
    }

    @JvmStatic @Throws(IOException::class)
    fun parse(input: InputStream): Result {
        val result = Result()
        val budget = Budget()
        BufferedInputStream(input).use { stream ->
            stream.mark(4)
            val a = stream.read()
            val b = stream.read()
            stream.reset()
            when {
                a == 'P'.code && b == 'K'.code -> {
                    ZipInputStream(stream).use { zip ->
                    var count = 0
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        if (++count > 2048) throw IOException("ZIP에 파일이 너무 많습니다.")
                        val name = entry.name.lowercase(Locale.ROOT)
                        val limited = LimitedInput(zip, budget)
                        if (!entry.isDirectory && (name.endsWith(".txt") || name.endsWith(".log") || name.contains("dumpstate"))
                            && !name.endsWith(".zip") && !name.endsWith(".gz")) {
                            scan(limited, result)
                            result.files++
                        } else {
                            val buffer = ByteArray(8192)
                            while (limited.read(buffer) != -1) { /* bounded discard */ }
                        }
                        zip.closeEntry()
                    }
                    }
                }
                a == 0x1f && b == 0x8b -> {
                    GZIPInputStream(stream).use { scan(LimitedInput(it, budget), result) }
                    result.files = 1
                }
                else -> {
                    scan(LimitedInput(stream, budget), result)
                    result.files = 1
                }
            }
        }
        return result
    }

    private fun scan(input: InputStream, result: Result) {
        val bytes = PushbackInputStream(input, 3)
        val bom = ByteArray(3)
        var size = 0
        while (size < 3) { val value = bytes.read(); if (value < 0) break; bom[size++] = value.toByte() }
        var skip = 0
        val charset = when {
            size >= 2 && bom[0] == 0xff.toByte() && bom[1] == 0xfe.toByte() -> { skip = 2; Charsets.UTF_16LE }
            size >= 2 && bom[0] == 0xfe.toByte() && bom[1] == 0xff.toByte() -> { skip = 2; Charsets.UTF_16BE }
            else -> {
                if (size == 3 && bom[0] == 0xef.toByte() && bom[1] == 0xbb.toByte() && bom[2] == 0xbf.toByte()) skip = 3
                Charsets.UTF_8
            }
        }
        if (size > skip) bytes.unread(bom, skip, size - skip)
        val reader = InputStreamReader(bytes, charset)
        val buffer = CharArray(8192)
        val line = StringBuilder()
        var tooLong = false
        while (true) {
            val n = reader.read(buffer)
            if (n < 0) break
            for (i in 0 until n) {
                val c = buffer[i]
                if (c == '\n' || c == '\r') {
                    if (!tooLong) acceptLine(line.toString(), result)
                    line.setLength(0)
                    tooLong = false
                } else if (!tooLong) {
                    if (line.length < MAX_LINE) line.append(c)
                    else { tooLong = true; result.skippedLongLines = true; line.setLength(0) }
                }
            }
        }
        if (!tooLong) acceptLine(line.toString(), result)
    }

    private fun acceptLine(line: String, result: Result) {
        if (!line.contains("mSavedBattery")) return
        fieldPattern.findAll(line).forEach { match ->
            val field = when (match.groupValues[1]) {
                "mSavedBatteryAsoc" -> result.asoc
                "mSavedBatteryBsoh" -> result.bsoh
                else -> result.usage
            }
            field.accept(match.groupValues[2])
        }
    }

    private class Budget {
        var bytes = 0L
        val start = System.nanoTime()
        fun add(count: Int) {
            if (Thread.currentThread().isInterrupted) throw InterruptedIOException("분석 취소")
            if (System.nanoTime() - start > 90_000_000_000L) throw IOException("분석 시간 초과")
            if (count > 0) bytes += count
            if (bytes > MAX_BYTES) throw IOException("압축 해제 후 512 MiB 초과")
        }
    }

    private class LimitedInput(input: InputStream, val budget: Budget) : FilterInputStream(input) {
        override fun read(): Int {
            budget.add(0)
            return `in`.read().also { budget.add(if (it < 0) 0 else 1) }
        }
        override fun read(b: ByteArray, offset: Int, length: Int): Int {
            budget.add(0)
            return `in`.read(b, offset, length).also { budget.add(it) }
        }
        override fun close() { /* enclosing archive owns this stream */ }
    }
}
