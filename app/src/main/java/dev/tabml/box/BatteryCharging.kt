package dev.tabml.box

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build

/** True when the device is charging or already full on power (USB / AC). */
fun Context.isBatteryChargingOrFull(): Boolean {
    val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && bm.isCharging) return true
    val batteryStatus = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    return status == BatteryManager.BATTERY_STATUS_CHARGING ||
        status == BatteryManager.BATTERY_STATUS_FULL
}
