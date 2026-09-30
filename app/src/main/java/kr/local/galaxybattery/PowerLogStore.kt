package kr.local.galaxybattery

import java.io.*
import java.util.ArrayDeque
import java.util.UUID

/** Append-only sessions; fixed-size samples survive an interrupted final write. */
class PowerLogStore(private val directory: File) {
    data class Session(val id: String, val started: Long, val ended: Long, val count: Long,
                       val minimum: Double?, val maximum: Double?, val samples: List<ChargePower.Sample>)

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
            checkHeader(out)
            out.seek(12)
            if (out.readLong() != 0L) throw IOException("Session already ended")
            val safeLength = HEADER + ((out.length() - HEADER) / RECORD) * RECORD
            out.setLength(safeLength); out.seek(safeLength)
            out.writeLong(sample.time); out.writeInt(sample.currentUa); out.writeInt(sample.voltageMv)
            out.writeInt(sample.level); out.writeInt(sample.temperature); out.writeInt(sample.status); out.writeInt(sample.plugged)
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
        RandomAccessFile(file(id), "r").use { input ->
            checkHeader(input)
            val started = input.readLong(); val ended = input.readLong()
            val count = (input.length() - HEADER) / RECORD
            repeatLong(count) {
                val sample = ChargePower.Sample(input.readLong(), input.readInt(), input.readInt(), input.readInt(),
                    input.readInt(), input.readInt(), input.readInt())
                stats.add(sample)
                if (limit > 0) { points.addLast(sample); if (points.size > limit) points.removeFirst() }
            }
            Session(id, started, ended, count, stats.minimum, stats.maximum, points.toList())
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
    private fun checkHeader(input: RandomAccessFile) {
        if (input.length() < HEADER || input.readInt() != MAGIC) throw IOException("Invalid power session")
    }
    private inline fun repeatLong(count: Long, action: () -> Unit) { var n = 0L; while (n++ < count) action() }
    companion object { private val lock = Any(); private const val MAGIC = 0x47504231; private const val HEADER = 20L; private const val RECORD = 32L }
}
