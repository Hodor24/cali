package dev.tabml.box

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object OpsNotifPermission {

    /**
     * After a denied POST_NOTIFICATIONS request: if the system will not show the sheet again,
     * offer to open app notification settings.
     */
    fun showBlockedFallbackIfNeeded(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        activity.window.decorView.post {
            if (ContextCompat.checkSelfPermission(
                    activity,
                    android.Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                return@post
            }
            if (activity.shouldShowRequestPermissionRationale(
                    android.Manifest.permission.POST_NOTIFICATIONS,
                )
            ) {
                return@post
            }
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.ops_notif_blocked_title)
                .setMessage(R.string.ops_notif_blocked_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.ops_notif_open_settings) { _, _ ->
                    activity.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                        },
                    )
                }
                .show()
        }
    }
}
