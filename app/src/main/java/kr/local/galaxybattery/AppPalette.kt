package kr.local.galaxybattery

import android.graphics.Color

data class AppPalette(val background: Int, val card: Int, val foreground: Int, val muted: Int,
                      val accent: Int, val grid: Int, val negative: Int, val navigation: Int, val selection: Int) {
    companion object {
        fun forDark(dark: Boolean): AppPalette = if (dark) AppPalette(
            Color.rgb(12,19,28), Color.rgb(22,33,45), Color.rgb(238,245,250), Color.rgb(153,172,189),
            Color.rgb(115,235,195), Color.rgb(44,58,69), Color.rgb(115,178,246), Color.rgb(31,42,55), Color.rgb(62,78,94))
        else AppPalette(Color.rgb(243,245,248), Color.WHITE, Color.rgb(25,36,45), Color.rgb(82,101,116),
            Color.rgb(0,116,88), Color.rgb(217,225,232), Color.rgb(42,99,183), Color.rgb(251,252,254), Color.rgb(222,229,234))
    }
}
