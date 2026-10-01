package kr.local.galaxybattery

/** Screen events are separate from power samples so a 60s interval cannot miss a short screen-off period. */
object ScreenTimeline {
    const val UNKNOWN = -1
    const val OFF = 0
    const val ON = 1
    data class Event(val time: Long, val state: Int)
    data class Interval(val start: Long, val end: Long)

    @JvmStatic fun intervals(events: List<Event>, from: Long, to: Long): List<Interval> {
        if (to <= from) return emptyList()
        val result = mutableListOf<Interval>()
        var off: Long? = null
        events.filter { it.time >= 0 && it.state in UNKNOWN..ON }.sortedBy { it.time }.forEach {
            if (it.time > to) return@forEach
            if (it.state == OFF) { if (off == null) off = it.time }
            else {
                off?.let { start ->
                    if (it.time > maxOf(start, from)) result.add(Interval(maxOf(start, from), it.time))
                }
                off = null
            }
        }
        off?.let { if (to > maxOf(it, from)) result.add(Interval(maxOf(it, from), to)) }
        return result
    }
}
