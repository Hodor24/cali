package dev.tabml.box

import android.content.Context
import java.text.DateFormat
import java.util.Date

object OpsDateTime {
    fun formatDateTime(context: Context, ms: Long): String {
        val loc = context.resources.configuration.locales[0]
        val fmt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, loc)
        return fmt.format(Date(ms))
    }
}
