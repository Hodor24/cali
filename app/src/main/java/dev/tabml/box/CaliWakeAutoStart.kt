package dev.tabml.box

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Shared checks to start [CaliWakeService] from boot, power connect, or app process start. */
object CaliWakeAutoStart {

    /** Starts the wake service when the user left "Listen for wake word" on and runtime checks pass. */
    fun tryStartIfUserEnabled(context: Context) {
        val app = context.applicationContext
        if (!Prefs.caliWakeEnabled(app)) return
        if (!canStartCaliWakeEngine(app)) return
        CaliWakeService.start(app)
    }

    fun canStartCaliWakeEngine(context: Context): Boolean {
        if (!VoskModelStore.isInstalled(context)) return false
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        if (Prefs.caliWakeChargeOnly(context) && !context.isBatteryChargingOrFull()) return false
        return true
    }
}
