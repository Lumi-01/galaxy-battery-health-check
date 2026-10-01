package kr.local.galaxybattery

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/** Shizuku process: one fixed read-only command, no caller-provided shell input. */
class RemoteBatteryService : IRemoteBattery.Stub() {
    override fun readBattery(): String {
        var process: Process? = null
        val reader = Executors.newSingleThreadExecutor()
        return try {
            val running = ProcessBuilder("/system/bin/dumpsys", "-t", "12", "battery")
                .redirectErrorStream(true).start()
            process = running
            val output = reader.submit<String> {
                running.inputStream.use { input ->
                    val bytes = ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        if (bytes.size() + n > 256 * 1024) throw IOException("battery output exceeded limit")
                        bytes.write(buffer, 0, n)
                    }
                    bytes.toString("UTF-8")
                }
            }
            val text = output.get(15, TimeUnit.SECONDS)
            when {
                !running.waitFor(2, TimeUnit.SECONDS) -> "ERROR: timeout"
                running.exitValue() != 0 -> "ERROR: exit ${running.exitValue()}"
                text.contains("Permission Denial") || text.contains("Permission denied") -> "ERROR: permission denied"
                else -> DumpParser.parse(text.byteInputStream()).safeFields().ifEmpty { "NO_FIELDS" }
            }
        } catch (e: Exception) {
            "ERROR: ${e.javaClass.simpleName}"
        } finally {
            process?.destroyForcibly()
            reader.shutdownNow()
        }
    }

    override fun readHardware(): String = HardwareProbe.readSnapshot()

    override fun destroy() { exitProcess(0) }
}
