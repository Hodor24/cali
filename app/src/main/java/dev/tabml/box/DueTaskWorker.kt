package dev.tabml.box

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.Manifest
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DueTaskWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        val taskId = inputData.getString(KEY_TASK_ID) ?: return@withContext Result.success()
        val earlyReminder = inputData.getBoolean(KEY_EARLY_REMINDER, false)
        val snap = WorkflowHubStore.load(applicationContext)
        val task = snap.tasks.find { it.id == taskId } ?: return@withContext Result.success()
        if (task.status != HubTaskStatus.OPEN) return@withContext Result.success()
        ensureChannel(applicationContext)
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifId = notificationId(taskId, earlyReminder)
        val open = Intent(applicationContext, OperationsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(OperationsActivity.EXTRA_INITIAL_TAB, 0)
            putExtra(OperationsActivity.EXTRA_FOCUS_TASK_ID, taskId)
        }
        val pi = PendingIntent.getActivity(
            applicationContext,
            notifId,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val snooze15 = Intent(applicationContext, DueTaskSnoozeReceiver::class.java).apply {
            putExtra(DueTaskSnoozeReceiver.EXTRA_TASK_ID, taskId)
            putExtra(DueTaskSnoozeReceiver.EXTRA_DELAY_MS, DueTaskSnoozeReceiver.SNOOZE_15_MS)
        }
        val snooze60 = Intent(applicationContext, DueTaskSnoozeReceiver::class.java).apply {
            putExtra(DueTaskSnoozeReceiver.EXTRA_TASK_ID, taskId)
            putExtra(DueTaskSnoozeReceiver.EXTRA_DELAY_MS, DueTaskSnoozeReceiver.SNOOZE_60_MS)
        }
        val pi15 = PendingIntent.getBroadcast(
            applicationContext,
            notifId + 17,
            snooze15,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val pi60 = PendingIntent.getBroadcast(
            applicationContext,
            notifId + 29,
            snooze60,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return@withContext Result.success()
        }
        val titleRes = if (earlyReminder) R.string.due_task_early_notif_title else R.string.due_task_notif_title
        val bigText = when {
            earlyReminder -> applicationContext.getString(
                R.string.due_task_early_notif_big,
                task.title,
                task.details.ifBlank { task.title },
            )
            else -> task.details.ifBlank { task.title }
        }
        val notif = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mic_24)
            .setContentTitle(applicationContext.getString(titleRes))
            .setContentText(task.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setContentIntent(pi)
            .addAction(0, applicationContext.getString(R.string.due_snooze_15), pi15)
            .addAction(0, applicationContext.getString(R.string.due_snooze_60), pi60)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        nm.notify(notifId, notif)
        Result.success()
    }

    companion object {
        const val KEY_TASK_ID = "task_id"
        const val KEY_EARLY_REMINDER = "early_reminder"
        private const val CHANNEL_ID = "workflow_due_tasks"

        /** Distinct IDs for on-time vs 15-minute-early reminders (both may be visible). */
        fun notificationId(taskId: String, earlyReminder: Boolean): Int {
            val h = taskId.hashCode() and 0x7FFF_FFFF
            return if (earlyReminder) (h xor 0x485f) and 0x7FFF_FFFF else h
        }

        fun cancelNotificationsForTask(context: Context, taskId: String) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(notificationId(taskId, false))
            nm.cancel(notificationId(taskId, true))
        }

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.due_task_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = context.getString(R.string.due_task_channel_desc)
                },
            )
        }
    }
}
