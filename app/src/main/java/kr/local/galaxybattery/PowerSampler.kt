package kr.local.galaxybattery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

object PowerSampler {
    fun read(context: Context): ChargePower.Sample {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val current = try { manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: ChargePower.MISSING }
            catch (_: RuntimeException) { ChargePower.MISSING }
        return ChargePower.Sample(System.currentTimeMillis(), current,
            battery?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, ChargePower.MISSING) ?: ChargePower.MISSING,
            BatteryValues.percent(battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1)) ?: -1,
            battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, ChargePower.MISSING) ?: ChargePower.MISSING,
            battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1,
            battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0)
    }
}
