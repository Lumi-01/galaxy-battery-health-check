package kr.local.galaxybattery

/** Android system thermal severity, not a Samsung charging-controller limit. */
object ThermalStatus {
    @JvmStatic fun label(status: Int): String = if (status in 0..6) "쓰로틀링 ${status}단계" else "쓰로틀링 확인 불가"
    @JvmStatic fun validHeadroom(value: Float): Float? = value.takeIf { it.isFinite() && it >= 0f }
    @JvmStatic fun headroomWarning(value: Float?): String = when {
        value == null -> "발열 부하 정보를 읽을 수 없어요"
        value >= 1f -> "심한 성능 제한 기준 이상이에요"
        value >= .85f -> "성능 제한 가능성이 있어요"
        else -> "심한 성능 제한 기준보다 낮아요"
    }
}
