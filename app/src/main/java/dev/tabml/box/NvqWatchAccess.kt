package dev.tabml.box

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager

object NvqWatchAccess {

    fun isServiceEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false
        if (!am.isEnabled) return false
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) {
            val raw = splitter.next() ?: continue
            val parsed = ComponentName.unflattenFromString(raw) ?: continue
            if (parsed.packageName == context.packageName &&
                parsed.className == NvqWatchAccessibilityService::class.java.name
            ) {
                return true
            }
        }
        return false
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = android.content.Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
