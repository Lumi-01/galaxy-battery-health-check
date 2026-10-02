package kr.local.galaxybattery

/** Android system thermal severity, not a Samsung charging-controller limit. */
object ThermalStatus {
    @JvmStatic fun label(status: Int): String = if (status in 0..6) "쓰로틀링 ${status}단계" else "쓰로틀링 확인 불가"
    @JvmStatic fun validHeadroom(value: Float): Float? = value.takeIf { it.isFinite() && it >= 0f }
    @JvmStatic fun headroomWarning(value: Float?): String = when {
        value == null -> "발열 부하 확인 불가"
        value >= 1f -> "발열 부하 높음 · 심한 쓰로틀링 기준 이상"
        value >= .85f -> "발열 부하 상승 · 쓰로틀링 가능성"
        else -> "발열 부하 낮음"
    }
}
