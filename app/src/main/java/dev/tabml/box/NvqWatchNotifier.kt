package dev.tabml.box

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Rate-limited notification + in-app pending flag when NVQ-like screen text is seen.
 */
object NvqWatchNotifier {

    const val EXTRA_SHOW_NVQ_PROMPT = "extra_show_nvq_watch_prompt"

    private const val CHANNEL_ID = "nvq_watch"
    private const val NOTIF_ID = 71001
    private const val MIN_INTERVAL_MS = 15 * 60 * 1000L

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        val ch = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.nvq_watch_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.nvq_watch_channel_desc) }
        mgr.createNotificationChannel(ch)
    }

    fun maybePromptUser(context: Context, syntheticReason: String) {
        val now = System.currentTimeMillis()
        if (now - Prefs.nvqWatchLastPromptMs(context) < MIN_INTERVAL_MS) return
        if (Prefs.nvqWatchNeverSuggest(context)) return
        Prefs.setNvqWatchLastPromptMs(context, now)
        Prefs.setNvqWatchPendingPrompt(context, true)
        Prefs.setNvqWatchPendingReason(context, syntheticReason.take(200))

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_SHOW_NVQ_PROMPT, true)
        }
        val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        val pending = PendingIntent.getActivity(context, 0, intent, piFlags)

        ensureChannel(context)
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_chat_24)
            .setContentTitle(context.getString(R.string.nvq_watch_notif_title))
            .setContentText(context.getString(R.string.nvq_watch_notif_text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID, notif)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS denied — pending flag still set for next app open
        }
    }
}
