package dev.tabml.box

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Snooze action on due-task notification; reschedules [DueTaskWorker] and dismisses the notification. */
class DueTaskSnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val i = intent ?: return
        val taskId = i.getStringExtra(EXTRA_TASK_ID) ?: return
        val delayMs = i.getLongExtra(EXTRA_DELAY_MS, SNOOZE_15_MS)
        WorkflowDueScheduler.scheduleSnooze(context.applicationContext, taskId, delayMs)
    }

    companion object {
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_DELAY_MS = "delay_ms"
        const val SNOOZE_15_MS = 15 * 60_000L
        const val SNOOZE_60_MS = 60 * 60_000L
    }
}
