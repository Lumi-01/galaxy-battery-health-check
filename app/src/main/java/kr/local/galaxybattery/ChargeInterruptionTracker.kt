package kr.local.galaxybattery

/** Counts a connected charging -> discharging -> charging transition, once on recovery.
 * Zero alone is not a discharge. Invalid samples, a source change, or a sampling outage
 * break continuity, so disconnecting the charger cannot count as a charging interruption.
 */
class ChargeInterruptionTracker {
    data class Event(val started: Long, val recovered: Long, val lowestWatts: Double)
    data class State(val count: Long, val events: List<Event>, val previousTime: Long,
                     val plugged: Int, val hadCharging: Boolean, val dischargeStarted: Long?, val lowestWatts: Double?)
    private var count = 0L
    private val events = java.util.ArrayDeque<Event>()
    private var previousTime = 0L
    private var plugged = 0
    private var hadCharging = false
    private var dischargeStarted: Long? = null
    private var lowestWatts: Double? = null

    fun restore(state: State) {
        count = state.count; events.clear(); events.addAll(state.events)
        previousTime = state.previousTime; plugged = state.plugged
        hadCharging = state.hadCharging; dischargeStarted = state.dischargeStarted; lowestWatts = state.lowestWatts
    }
    fun state() = State(count, events.toList(), previousTime, plugged, hadCharging, dischargeStarted, lowestWatts)
    private fun resetTransition() { hadCharging = false; dischargeStarted = null; lowestWatts = null }
    fun add(sample: ChargePower.Sample) {
        val watts = sample.watts()?.takeIf { sample.plugged > 0 && sample.status in 2..5 }
        if (sample.time <= previousTime || sample.time - previousTime > 180000 || sample.plugged != plugged || watts == null) resetTransition()
        previousTime = sample.time; plugged = sample.plugged
        if (watts == null) return
        when {
            watts > 0 && sample.chargingWatts() != null -> {
                dischargeStarted?.let { start ->
                    count++; events.addLast(Event(start, sample.time, lowestWatts!!))
                    if (events.size > 200) events.removeFirst()
                }
                hadCharging = true; dischargeStarted = null; lowestWatts = null
            }
            watts < 0 && hadCharging -> {
                if (dischargeStarted == null) dischargeStarted = sample.time
                lowestWatts = minOf(lowestWatts ?: watts, watts)
            }
            watts > 0 -> resetTransition() // Positive power without a charging state is not proof of recovery.
            // A valid zero sample can bridge the transition but never starts an event.
        }
    }
}
