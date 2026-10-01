package kr.local.galaxybattery

import java.util.Locale
import kotlin.math.roundToInt

/** Missing and unsupported values must never look like a healthy battery. */
object BatteryValues {
    @JvmStatic fun cycle(raw: Int?): Int? = raw?.takeIf { it > 0 }
    @JvmStatic fun stateOfHealth(raw: Int?): Int? = raw?.takeIf { it in 1..100 }
    @JvmStatic fun percent(level: Int?, scale: Int?): Int? =
        if (level == null || scale == null || scale <= 0 || level !in 0..scale) null
        else (level * 100.0 / scale).roundToInt()

    @JvmStatic fun charging(status: Int?): String = when (status) {
        2 -> "충전 중"
        3 -> "배터리 사용 중"
        4 -> "충전 안 함"
        5 -> "충전 완료"
        else -> "확인 불가"
    }

    @JvmStatic fun condition(health: Int?): String = when (health) {
        2 -> "정상"
        3 -> "과열"
        4 -> "배터리 이상"
        5 -> "과전압"
        6 -> "알 수 없는 오류"
        7 -> "저온"
        else -> "확인 불가"
    }

    @JvmStatic fun decimal(value: Double, unit: String): String =
        String.format(Locale.KOREA, "%.1f %s", value, unit)
}
