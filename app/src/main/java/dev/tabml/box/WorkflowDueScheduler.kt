package dev.tabml.box

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/** Schedules one-shot WorkManager jobs when open tasks have a due time. */
object WorkflowDueScheduler {

    private const val TAG = "workflow-due-task"
    private val maxDelayMs = TimeUnit.DAYS.toMillis(364)

    /** 15-minute heads-up before [dueMs]. */
    internal const val EARLY_LEAD_MS = 15 * 60_000L

    internal fun dueWorkName(taskId: String) = "due-$taskId"

    internal fun dueEarlyWorkName(taskId: String) = "due-early-$taskId"

    /** Null if the early reminder is already in the past or at due. */
    internal fun earlyReminderDelayMsOrNull(nowMs: Long, dueMs: Long): Long? {
        val fireAt = dueMs - EARLY_LEAD_MS
        if (fireAt <= nowMs) return null
        return (fireAt - nowMs).coerceIn(1000L, maxDelayMs)
    }

    fun sync(context: Context) {
        val app = context.applicationContext
        DueTaskWorker.ensureChannel(app)
        val wm = WorkManager.getInstance(app)
        wm.cancelAllWorkByTag(TAG)
        val now = System.currentTimeMillis()
        val snap = WorkflowHubStore.load(app)
        for (task in snap.tasks) {
            if (task.status != HubTaskStatus.OPEN) continue
            val due = task.dueMs ?: continue
            val raw = due - now
            val delay = raw.coerceIn(1000L, maxDelayMs)
            val req = OneTimeWorkRequestBuilder<DueTaskWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(
                    workDataOf(
                        DueTaskWorker.KEY_TASK_ID to task.id,
                        DueTaskWorker.KEY_EARLY_REMINDER to false,
                    ),
                )
                .addTag(TAG)
                .build()
            wm.enqueueUniqueWork(dueWorkName(task.id), ExistingWorkPolicy.REPLACE, req)

            val earlyDelay = earlyReminderDelayMsOrNull(now, due) ?: continue
            val reqEarly = OneTimeWorkRequestBuilder<DueTaskWorker>()
                .setInitialDelay(earlyDelay, TimeUnit.MILLISECONDS)
                .setInputData(
                    workDataOf(
                        DueTaskWorker.KEY_TASK_ID to task.id,
                        DueTaskWorker.KEY_EARLY_REMINDER to true,
                    ),
                )
                .addTag(TAG)
                .build()
            wm.enqueueUniqueWork(dueEarlyWorkName(task.id), ExistingWorkPolicy.REPLACE, reqEarly)
        }
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelAllWorkByTag(TAG)
    }

    /** Re-schedule on-time notification only; drops early reminder and clears in-shade notifications for this task. */
    fun scheduleSnooze(context: Context, taskId: String, delayMs: Long) {
        val app = context.applicationContext
        DueTaskWorker.ensureChannel(app)
        DueTaskWorker.cancelNotificationsForTask(app, taskId)
        val wm = WorkManager.getInstance(app)
        wm.cancelUniqueWork(dueEarlyWorkName(taskId))
        val delay = delayMs.coerceIn(1000L, maxDelayMs)
        val req = OneTimeWorkRequestBuilder<DueTaskWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    DueTaskWorker.KEY_TASK_ID to taskId,
                    DueTaskWorker.KEY_EARLY_REMINDER to false,
                ),
            )
            .addTag(TAG)
            .build()
        wm.enqueueUniqueWork(dueWorkName(taskId), ExistingWorkPolicy.REPLACE, req)
    }
}
