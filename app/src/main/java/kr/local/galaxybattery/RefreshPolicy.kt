package kr.local.galaxybattery

object RefreshPolicy {
    @JvmField val intervals = intArrayOf(1, 2, 5, 10, 30, 60)
    @JvmStatic fun seconds(value: Int, fallback: Int): Int = if (value in intervals) value else fallback
    @JvmStatic fun dark(mode: String, systemDark: Boolean): Boolean = when (mode) {
        "light" -> false
        "dark" -> true
        else -> systemDark
    }
    /** Estimate the normal spacing so a 30/60 second setting still draws connected lines. */
    @JvmStatic fun graphGapMs(times: List<Long>): Long {
        val gaps = times.zipWithNext().map { (a, b) -> b - a }.filter { it in 1L..90000L }.sorted()
        return maxOf(3000L, (gaps.getOrNull(gaps.size / 2) ?: 5000L) * 3)
    }
}
