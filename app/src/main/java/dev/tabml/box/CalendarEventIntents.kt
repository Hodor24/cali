package dev.tabml.box

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract

/** Opens the platform calendar insert flow (user confirms / edits in their calendar app). */
object CalendarEventIntents {

    fun insertTaskEvent(context: Context, title: String, description: String, startMs: Long, endMs: Long) {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.Events.DESCRIPTION, description)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMs)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMs.coerceAtLeast(startMs + 60_000L))
        }
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.ops_calendar_chooser)),
        )
    }
}
