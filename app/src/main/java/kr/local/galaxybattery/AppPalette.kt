package kr.local.galaxybattery

import android.graphics.Color

/** Material 3-inspired surface/container roles with a restrained green primary. */
data class AppPalette(val background: Int, val card: Int, val foreground: Int, val muted: Int,
                      val accent: Int, val grid: Int, val negative: Int, val navigation: Int, val selection: Int,
                      val onAccent: Int) {
    companion object {
        fun forDark(dark: Boolean): AppPalette = if (dark) AppPalette(
            Color.parseColor("#101512"), Color.parseColor("#1C211E"), Color.parseColor("#E0E4DE"), Color.parseColor("#BFC9C0"),
            Color.parseColor("#8ED5B2"), Color.parseColor("#404943"), Color.parseColor("#AAC7FF"), Color.parseColor("#29302B"), Color.parseColor("#294B3B"), Color.parseColor("#003824"))
        else AppPalette(Color.parseColor("#F6FBF5"), Color.parseColor("#ECF2EC"), Color.parseColor("#181D19"), Color.parseColor("#414942"),
            Color.parseColor("#246B4D"), Color.parseColor("#C1C9C1"), Color.parseColor("#365FAD"), Color.parseColor("#E6EEE7"), Color.parseColor("#B6EED0"), Color.WHITE)
    }
}
