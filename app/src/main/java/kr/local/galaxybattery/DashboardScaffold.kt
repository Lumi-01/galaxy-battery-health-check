package kr.local.galaxybattery

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.os.Build
import android.widget.*

/** Owns page navigation and insets; pages stay mounted to preserve scroll and query state. */
class DashboardScaffold(private val activity: Activity, palette: AppPalette, blur: Boolean,
                        openSettings: () -> Unit, private val onPage: (Int) -> Unit) {
    val root = FrameLayout(activity).apply { setBackgroundColor(palette.background) }
    private val host = FrameLayout(activity)
    private val scrolls = mutableListOf<ScrollView>()
    val pages = mutableListOf<LinearLayout>()
    val navigation: FrostedNavigation
    private val settingsButton: FrostedSettingsButton
    var selected = 0; private set
    init {
        root.addView(host, FrameLayout.LayoutParams(-1, -1))
        navigation = FrostedNavigation(activity, host, palette, blur) { select(it) }
        settingsButton = FrostedSettingsButton(activity, host, palette, blur, openSettings)
        val titles = arrayOf("배터리 상태", "충전 모니터링", "기록·공유")
        titles.forEach { title ->
            val content = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(22), dp(20), dp(100))
            }
            val scroll = ScrollView(activity).apply {
                isFillViewport = true; clipToPadding = false
                addView(content)
                setOnScrollChangeListener { _, _, _, _, _ -> refreshBackdrop() }
            }
            host.addView(scroll, FrameLayout.LayoutParams(-1, -1))
            val heading = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
            val headingText = TextView(activity).apply {
                text = title; textSize = 27f; setTextColor(palette.foreground)
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            }
            heading.addView(headingText, LinearLayout.LayoutParams(0, -2, 1f))
            heading.addView(View(activity), LinearLayout.LayoutParams(dp(60), dp(48)))
            content.addView(heading, LinearLayout.LayoutParams(-1, dp(48)))
            scrolls.add(scroll); pages.add(content)
        }
        root.addView(navigation, FrameLayout.LayoutParams(-1, dp(64), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            leftMargin = dp(20); rightMargin = dp(20); bottomMargin = dp(12)
        })
        root.addView(settingsButton, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP or Gravity.END).apply {
            rightMargin = dp(20); topMargin = dp(22)
        })
        root.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            @Suppress("DEPRECATION")
            insets.consumeSystemWindowInsets()
        }
        select(0)
    }
    fun select(index: Int) {
        selected = index.coerceIn(0, 2)
        scrolls.forEachIndexed { i, scroll -> scroll.visibility = if (i == selected) View.VISIBLE else View.GONE }
        navigation.select(selected); settingsButton.refreshBackdrop(); onPage(selected)
    }
    fun refreshBackdrop() { navigation.refreshBackdrop(); settingsButton.refreshBackdrop() }
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density + .5f).toInt()
}
