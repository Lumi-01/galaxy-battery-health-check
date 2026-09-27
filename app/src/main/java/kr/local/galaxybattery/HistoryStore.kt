package kr.local.galaxybattery

import java.io.*
import java.util.UUID

/** One immutable report per file. Temporary writes never appear in the history list. */
class HistoryStore(private val directory: File) {
    data class Entry(val id: String, val time: Long, val source: String, val summary: String, val report: String)

    @Throws(IOException::class)
    @Synchronized fun save(time: Long, source: String, summary: String, report: String): Entry {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create history directory")
        val entry = Entry(UUID.randomUUID().toString(), time, source, summary, report)
        val target = file(entry.id)
        val temporary = File(directory, entry.id + ".tmp")
        try {
            FileOutputStream(temporary).use { stream ->
                val output = DataOutputStream(stream)
                output.writeInt(1)
                output.writeLong(time)
                output.writeUTF(source)
                output.writeUTF(summary)
                output.writeUTF(report)
                output.flush()
                stream.fd.sync()
            }
            if (!temporary.renameTo(target)) throw IOException("Cannot commit history")
        } finally { temporary.delete() }
        return entry
    }

    @Throws(IOException::class)
    @Synchronized fun list(): List<Entry> {
        if (!directory.exists()) return emptyList()
        val files = directory.listFiles() ?: throw IOException("Cannot read history")
        return files.filter { it.name.endsWith(".record") }.map { record ->
            val id = record.name.removeSuffix(".record")
            file(id) // Validate persisted identifiers before using them for deletion.
            if (record.length() > 200000) throw IOException("Invalid history record")
            DataInputStream(FileInputStream(record)).use { input ->
                if (input.readInt() != 1) throw IOException("Unsupported history version")
                Entry(id, input.readLong(), input.readUTF(), input.readUTF(), input.readUTF())
            }
        }.sortedWith(compareByDescending<Entry> { it.time }.thenBy { it.id })
    }

    @Synchronized fun delete(ids: List<String>) {
        val targets = ids.map { file(it) }
        targets.forEach { if (it.exists() && !it.delete()) throw IOException("Cannot delete history") }
    }

    private fun file(id: String): File {
        require(id.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
        return File(directory, "$id.record")
    }
}
