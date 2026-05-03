package dev.tabml.box

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder

/** Opens HTTPS-only intents for search and navigation from Cali ###ACTION blocks. */
object CaliIntentActions {

    fun openWebSearch(context: Context, query: String) {
        val enc = URLEncoder.encode(query.trim(), "UTF-8")
        val uri = Uri.parse("https://duckduckgo.com/?q=$enc")
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openNavigation(context: Context, query: String) {
        val enc = URLEncoder.encode(query.trim(), "UTF-8")
        val uri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$enc")
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Ordered stops (place names or addresses). Opens HTTPS Google Maps directions. */
    fun openMapsWaypoints(context: Context, stops: List<String>) {
        val trimmed = stops.map { it.trim() }.filter { it.isNotEmpty() }
        if (trimmed.isEmpty()) return
        if (trimmed.size == 1) {
            openNavigation(context, trimmed[0])
            return
        }
        val enc: (String) -> String = { URLEncoder.encode(it, "UTF-8") }
        val dest = enc(trimmed.last())
        val wp = trimmed.dropLast(1).joinToString("|") { enc(it) }
        val uri = Uri.parse("https://www.google.com/maps/dir/?api=1&waypoints=$wp&destination=$dest")
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openHttps(context: Context, url: String) {
        val u = url.trim()
        if (!u.startsWith("https://")) return
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
