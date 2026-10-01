package kr.local.galaxybattery

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration

class AppSettings(context: Context) {
    val preferences: SharedPreferences = context.getSharedPreferences("app-settings", Context.MODE_PRIVATE)
    var theme: String
        get() = preferences.getString("theme", "system") ?: "system"
        set(value) { preferences.edit().putString("theme", value).apply() }
    var powerSeconds: Int
        get() = RefreshPolicy.seconds(preferences.getInt(POWER_INTERVAL, 5), 5)
        set(value) { preferences.edit().putInt(POWER_INTERVAL, RefreshPolicy.seconds(value, 5)).apply() }
    var batterySeconds: Int
        get() = RefreshPolicy.seconds(preferences.getInt("battery_seconds", 2), 2)
        set(value) { preferences.edit().putInt("battery_seconds", RefreshPolicy.seconds(value, 2)).apply() }
    var blur: Boolean
        get() = preferences.getBoolean("nav_blur", true)
        set(value) { preferences.edit().putBoolean("nav_blur", value).apply() }
    var showDischarge: Boolean
        get() = preferences.getBoolean(SHOW_DISCHARGE, false)
        set(value) { preferences.edit().putBoolean(SHOW_DISCHARGE, value).apply() }
    fun isDark(context: Context) = RefreshPolicy.dark(theme,
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)
    companion object { const val POWER_INTERVAL = "power_seconds"; const val SHOW_DISCHARGE = "show_discharge" }
}
