package kr.local.galaxybattery

import java.io.*
import java.util.ArrayDeque
import java.util.UUID

/** Append-only sessions; fixed-size samples survive an interrupted final write. */
class PowerLogStore(private val directory: File) {
    data class Session(val id: String, val started: Long, val ended: Long, val count: Long,
                       val minimum: Double?, val maximum: Double?, val samples: List<ChargePower.Sample>,
                       val zeroState: ZeroPowerTracker.State, val peakThermal: Int,
                       val dischargeCount: Long = 0, val dischargeMinimum: Double? = null, val dischargeMaximum: Double? = null)

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
        val zero = ZeroPowerTracker()
        var peakThermal = -1
        RandomAccessFile(file(id), "r").use { input ->
            val record = checkHeader(input)
            val started = input.readLong(); val ended = input.readLong()
            val count = (input.length() - HEADER) / record
            repeatLong(count) {
                val sample = ChargePower.Sample(input.readLong(), input.readInt(), input.readInt(), input.readInt(),
                    input.readInt(), input.readInt(), input.readInt(), if (record == RECORD) input.readInt() else -1)
                stats.add(sample)
                zero.add(sample)
                if (sample.thermalStatus in 0..6) peakThermal = maxOf(peakThermal, sample.thermalStatus)
                if (limit > 0) { points.addLast(sample); if (points.size > limit) points.removeFirst() }
            }
            Session(id, started, ended, count, stats.minimum, stats.maximum, points.toList(), zero.state(), peakThermal, stats.dischargeCount, stats.dischargeMinimum, stats.dischargeMaximum)
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
    companion object { private val lock = Any(); private const val MAGIC = 0x47504232; private const val HEADER = 20L; private const val RECORD = 36L }
}
