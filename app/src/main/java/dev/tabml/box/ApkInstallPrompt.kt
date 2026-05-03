package dev.tabml.box

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * Opens a downloaded APK with the system installer (package installer).
 * May send the user to "install unknown apps" settings first on Android 8+.
 */
object ApkInstallPrompt {

    fun open(activity: Activity, apk: File) {
        val uri = FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.fileprovider",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val pm = activity.packageManager
            if (!pm.canRequestPackageInstalls()) {
                android.widget.Toast.makeText(
                    activity,
                    activity.getString(R.string.install_unknown_sources_hint),
                    android.widget.Toast.LENGTH_LONG,
                ).show()
                val settings = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    android.net.Uri.parse("package:${activity.packageName}"),
                )
                activity.startActivity(settings)
                return
            }
        }
        activity.startActivity(Intent.createChooser(intent, null))
    }
}
