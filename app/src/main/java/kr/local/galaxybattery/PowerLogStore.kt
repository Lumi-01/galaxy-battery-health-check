package kr.local.galaxybattery

import java.io.*
import java.util.ArrayDeque
import java.util.UUID

/** Append-only sessions; fixed-size samples survive an interrupted final write. */
class PowerLogStore(private val directory: File) {
    data class Session(val id: String, val started: Long, val ended: Long, val count: Long,
                       val minimum: Double?, val maximum: Double?, val samples: List<ChargePower.Sample>,
                       val interruptionState: ChargeInterruptionTracker.State, val peakThermal: Int,
                       val dischargeCount: Long = 0, val dischargeMinimum: Double? = null, val dischargeMaximum: Double? = null,
                       val chargingCount: Long = 0, val chargingAverage: Double? = null, val dischargeAverage: Double? = null,
                       val screenEvents: List<ScreenTimeline.Event> = emptyList(), val lastSampleTime: Long = 0L)

    @Throws(IOException::class)
    fun create(time: Long): String = synchronized(lock) {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create power directory")
        val id = UUID.randomUUID().toString()
        RandomAccessFile(file(id), "rw").use { out ->
            out.writeInt(MAGIC); out.writeLong(time); out.writeLong(0L); out.fd.sync()
        }
        id
    }

    @Throws(IOException::class)
    fun append(id: String, sample: ChargePower.Sample) = synchronized(lock) {
        val target = file(id)
        if (!target.isFile) throw IOException("Power session missing")
        RandomAccessFile(target, "rw").use { out ->
            val record = checkHeader(out)
            out.seek(12)
            if (out.readLong() != 0L) throw IOException("Session already ended")
            val safeLength = HEADER + ((out.length() - HEADER) / record) * record
            out.setLength(safeLength); out.seek(safeLength)
            out.writeLong(sample.time); out.writeInt(sample.currentUa); out.writeInt(sample.voltageMv)
            out.writeInt(sample.level); out.writeInt(sample.temperature); out.writeInt(sample.status); out.writeInt(sample.plugged)
            if (record == RECORD) out.writeInt(sample.thermalStatus)
            out.fd.sync()
        }
    }

    @Throws(IOException::class)
    fun finish(id: String, time: Long) = synchronized(lock) {
        val target = file(id)
        if (!target.isFile) throw IOException("Power session missing")
        RandomAccessFile(target, "rw").use { out -> checkHeader(out); out.seek(12); out.writeLong(time); out.fd.sync() }
    }

    @Throws(IOException::class)
    fun read(id: String, limit: Int = 600): Session = synchronized(lock) {
        require(limit in 0..3600)
        val points = ArrayDeque<ChargePower.Sample>()
        val stats = ChargePower.Stats()
        val interruptions = ChargeInterruptionTracker()
        var peakThermal = -1
        var lastSampleTime = 0L
        RandomAccessFile(file(id), "r").use { input ->
            val record = checkHeader(input)
            val started = input.readLong(); val ended = input.readLong()
            val count = (input.length() - HEADER) / record
            repeatLong(count) {
                val sample = ChargePower.Sample(input.readLong(), input.readInt(), input.readInt(), input.readInt(),
                    input.readInt(), input.readInt(), input.readInt(), if (record == RECORD) input.readInt() else -1)
                stats.add(sample)
                lastSampleTime = sample.time
                interruptions.add(sample)
                if (sample.thermalStatus in 0..6) peakThermal = maxOf(peakThermal, sample.thermalStatus)
                if (limit > 0) { points.addLast(sample); if (points.size > limit) points.removeFirst() }
            }
            Session(id, started, ended, count, stats.minimum, stats.maximum, points.toList(), interruptions.state(), peakThermal,
                stats.dischargeCount, stats.dischargeMinimum, stats.dischargeMaximum, stats.chargingCount,
                stats.chargingAverage, stats.dischargeAverage, readScreenEvents(id), lastSampleTime)
        }
    }

    @Throws(IOException::class)
    fun list(): List<Session> = synchronized(lock) {
        if (!directory.exists()) return emptyList()
        val files = directory.listFiles() ?: throw IOException("Cannot list power sessions")
        files.filter { it.name.endsWith(".power") }.map { read(it.name.removeSuffix(".power"), 0) }.sortedByDescending { it.started }
    }

    @Throws(IOException::class)
    fun delete(ids: List<String>, activeId: String?) = synchronized(lock) {
        if (ids.contains(activeId)) throw IOException("Stop recording before deleting this session")
        val targets = ids.map { file(it) }
        targets.forEach { if (it.exists() && !it.delete()) throw IOException("Cannot delete power session") }
        ids.map { screenFile(it) }.forEach { if (it.exists() && !it.delete()) throw IOException("Cannot delete screen events") }
    }

    /** A sidecar keeps all earlier .power record formats unchanged. Partial final events are recoverable. */
    @Throws(IOException::class)
    fun appendScreenEvent(id: String, event: ScreenTimeline.Event) = synchronized(lock) {
        require(event.time >= 0 && event.state in ScreenTimeline.UNKNOWN..ScreenTimeline.ON)
        RandomAccessFile(file(id), "r").use { power ->
            checkHeader(power); power.seek(12)
            if (power.readLong() != 0L) throw IOException("Session already ended")
        }
        RandomAccessFile(screenFile(id), "rw").use { out ->
            if (out.length() == 0L) out.writeInt(SCREEN_MAGIC)
            else { out.seek(0); if (out.length() < 4 || out.readInt() != SCREEN_MAGIC) throw IOException("Invalid screen events") }
            val safeLength = 4 + ((out.length() - 4) / 12) * 12
            out.setLength(safeLength); out.seek(safeLength)
            out.writeLong(event.time); out.writeInt(event.state); out.fd.sync()
        }
    }
    private fun screenFile(id: String): File { file(id); return File(directory, "$id.screen") }
    private fun readScreenEvents(id: String): List<ScreenTimeline.Event> {
        val target = screenFile(id)
        if (!target.exists()) return emptyList()
        // Damage to the optional timeline must never make existing power samples unreadable.
        return try {
            RandomAccessFile(target, "r").use { input ->
                if (input.length() < 4 || input.readInt() != SCREEN_MAGIC) return emptyList()
                val events = mutableListOf<ScreenTimeline.Event>()
                repeatLong((input.length() - 4) / 12) {
                    val time = input.readLong(); val state = input.readInt()
                    if (time >= 0 && state in ScreenTimeline.UNKNOWN..ScreenTimeline.ON) events.add(ScreenTimeline.Event(time, state))
                }
                events
            }
        } catch (_: IOException) { emptyList() }
    }

    private fun file(id: String): File {
        require(id.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
        return File(directory, "$id.power")
    }
    private fun checkHeader(input: RandomAccessFile): Long {
        if (input.length() < HEADER) throw IOException("Invalid power session")
        return when (input.readInt()) {
            MAGIC -> RECORD
            0x47504231 -> 32L // v0.4 logs contain no thermal field; keep reading and appending safely.
            else -> throw IOException("Invalid power session")
        }
    }
    private inline fun repeatLong(count: Long, action: () -> Unit) { var n = 0L; while (n++ < count) action() }
    companion object { private val lock = Any(); private const val MAGIC = 0x47504232; private const val HEADER = 20L; private const val RECORD = 36L; private const val SCREEN_MAGIC = 0x47505331 }
}
