package dev.tabml.box

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Restarts the wake listener when boot completes. [LOCKED_BOOT_COMPLETED] may run before
 * credential-encrypted storage is unlocked; wake preferences live in device-protected
 * [Prefs] / [VoskModelStore] paths so listening can start when the user enabled it and runtime
 * checks pass. [BOOT_COMPLETED] runs after unlock and catches devices / flows where direct boot was skipped.
 */
class CaliWakeBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                CaliWakeAutoStart.tryStartIfUserEnabled(context)
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                CaliWakeAutoStart.tryStartIfUserEnabled(context)
                WorkflowDueScheduler.sync(context.applicationContext)
            }
            else -> Unit
        }
    }
}
