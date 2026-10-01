package kr.local.galaxybattery

import android.graphics.Color

/** Neutral One UI surfaces, mint actions, and a separate gray navigation selection. */
data class AppPalette(val background: Int, val card: Int, val foreground: Int, val muted: Int,
                      val accent: Int, val grid: Int, val negative: Int, val navigation: Int, val selection: Int,
                      val onAccent: Int, val actionSurface: Int, val batteryStart: Int, val batteryEnd: Int) {
    companion object {
        fun forDark(dark: Boolean): AppPalette = if (dark) AppPalette(
            Color.parseColor("#111317"), Color.parseColor("#1F2226"), Color.parseColor("#F1F3F7"), Color.parseColor("#B7BDC6"),
            Color.parseColor("#8BDEC9"), Color.parseColor("#3B4149"), Color.parseColor("#C7ADF4"), Color.parseColor("#25282D"), Color.parseColor("#3C414A"), Color.parseColor("#083D34"),
            Color.parseColor("#223F39"), Color.parseColor("#64C6AC"), Color.parseColor("#94E4CE"))
        else AppPalette(Color.parseColor("#F4F5F8"), Color.WHITE, Color.parseColor("#151619"), Color.parseColor("#66707B"),
            Color.parseColor("#147D70"), Color.parseColor("#E1E5EB"), Color.parseColor("#8C70C0"), Color.WHITE, Color.parseColor("#E2E4E8"), Color.WHITE,
            Color.parseColor("#E3F4EF"), Color.parseColor("#92E1C7"), Color.parseColor("#28B6A1"))
    }
}
