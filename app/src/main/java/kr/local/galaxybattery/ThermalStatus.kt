package kr.local.galaxybattery

/** Android system thermal severity, not a Samsung charging-controller limit. */
object ThermalStatus {
    @JvmStatic fun label(status: Int): String = when (status) {
        0 -> "열 제한 없음 · 0단계"
        1 -> "가벼운 열 제한 · 1단계"
        2 -> "보통 열 제한 · 2단계"
        3 -> "심한 열 제한 · 3단계"
        4 -> "위험 · 4단계"
        5 -> "긴급 · 5단계"
        6 -> "기기 종료 필요 · 6단계"
        else -> "열 제한 상태 확인 불가"
    }
}
