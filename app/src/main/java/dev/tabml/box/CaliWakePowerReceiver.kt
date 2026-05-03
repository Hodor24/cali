package dev.tabml.box

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Listens for plug / unplug so "wake only while charging" can stop without disabling the user's
 * wake toggle, and start again when power returns. Registered only from [TabMlApplication]
 * (implicit power broadcasts are unreliable from the manifest on O+).
 */
object CaliWakePowerReceiver {

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            val app = context.applicationContext
            when (intent?.action) {
                Intent.ACTION_POWER_CONNECTED -> onPowerConnected(app)
                Intent.ACTION_POWER_DISCONNECTED -> onPowerDisconnected(app)
            }
        }
    }

    fun register(application: Application) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            application.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            application.registerReceiver(receiver, filter)
        }
        CaliWakeAutoStart.tryStartIfUserEnabled(application)
    }

    private fun onPowerConnected(context: Context) {
        if (!Prefs.caliWakeChargeOnly(context)) return
        CaliWakeAutoStart.tryStartIfUserEnabled(context)
    }

    private fun onPowerDisconnected(context: Context) {
        if (!Prefs.caliWakeChargeOnly(context)) return
        if (!Prefs.caliWakeEnabled(context)) return
        CaliWakeService.stop(context)
    }
}
