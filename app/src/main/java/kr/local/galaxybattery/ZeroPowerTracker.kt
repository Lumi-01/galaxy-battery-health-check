package kr.local.galaxybattery

/** A dip is counted only after positive charging power returns on the same connection. */
class ZeroPowerTracker {
    data class Event(val started: Long, val recovered: Long, val samples: Long)
    data class State(val count: Long, val events: List<Event>, val previousTime: Long,
                     val plugged: Int, val hadPositive: Boolean, val zeroStarted: Long, val zeroSamples: Long)
    private var count = 0L
    private val events = java.util.ArrayDeque<Event>()
    private var previousTime = 0L
    private var plugged = 0
    private var hadPositive = false
    private var zeroStarted = 0L
    private var zeroSamples = 0L
    fun restore(state: State) {
        count = state.count; events.clear(); events.addAll(state.events)
        previousTime = state.previousTime; plugged = state.plugged
        hadPositive = state.hadPositive; zeroStarted = state.zeroStarted; zeroSamples = state.zeroSamples
    }
    fun state() = State(count, events.toList(), previousTime, plugged, hadPositive, zeroStarted, zeroSamples)
    fun add(sample: ChargePower.Sample) {
        // Some devices report NOT_CHARGING during a temporary charging pause.
        // Keep its genuine zero sample, while still rejecting discharge and unplugging.
        val watts = sample.watts()?.takeIf {
            sample.plugged > 0 && it >= 0 && (sample.status == 2 || sample.status == 5 || (sample.status == 4 && it == 0.0))
        }
        if (sample.time <= previousTime || sample.time - previousTime > 180000 || sample.plugged != plugged || watts == null) {
            hadPositive = false; zeroStarted = 0L; zeroSamples = 0L
        }
        previousTime = sample.time; plugged = sample.plugged
        if (watts == null) return
        if (watts > 0) {
            if (zeroSamples > 0) {
                count++; events.addLast(Event(zeroStarted, sample.time, zeroSamples))
                if (events.size > 200) events.removeFirst()
            }
            hadPositive = true; zeroStarted = 0L; zeroSamples = 0L
        } else if (hadPositive) {
            if (zeroSamples == 0L) zeroStarted = sample.time
            zeroSamples++
        }
    }
}
