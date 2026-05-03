package dev.tabml.box

import android.content.Context
import androidx.core.content.edit

object Prefs {
    private const val NAME = "tab_ml_prefs"
    private const val KEY_ALLOW_NETWORK = "allow_network"

    fun allowNetwork(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ALLOW_NETWORK, false)

    fun setAllowNetwork(context: Context, value: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_ALLOW_NETWORK, value)
        }
    }
}
