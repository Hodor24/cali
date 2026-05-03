package dev.tabml.box

import android.content.Context

/** App-level gate: no sockets unless the user enabled network in settings. */
object NetworkGuard {
    fun isAllowed(context: Context): Boolean = Prefs.allowNetwork(context)
}
