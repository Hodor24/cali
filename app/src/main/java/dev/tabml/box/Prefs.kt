package dev.tabml.box

import android.content.Context
import androidx.core.content.edit

object Prefs {
    private const val NAME = "tab_ml_prefs"
    private const val KEY_ALLOW_NETWORK = "allow_network"
    private const val KEY_PREFER_DOWNLOADED_TFLITE = "prefer_downloaded_tflite"
    private const val KEY_LAST_HTTPS_URL = "last_https_url"
    private const val KEY_AI_BASE_URL = "ai_base_url"
    private const val KEY_AI_MODEL = "ai_model"

    fun allowNetwork(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ALLOW_NETWORK, false)

    fun setAllowNetwork(context: Context, value: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_ALLOW_NETWORK, value)
        }
    }

    fun preferDownloadedTflite(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PREFER_DOWNLOADED_TFLITE, false)

    fun setPreferDownloadedTflite(context: Context, value: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_PREFER_DOWNLOADED_TFLITE, value)
        }
    }

    fun lastHttpsUrl(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_HTTPS_URL, "") ?: ""

    fun setLastHttpsUrl(context: Context, value: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_LAST_HTTPS_URL, value)
        }
    }

    /** HTTPS origin you control, e.g. https://assistant.example.com (no trailing path). */
    fun aiBaseUrl(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString(KEY_AI_BASE_URL, "") ?: ""

    fun setAiBaseUrl(context: Context, value: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_AI_BASE_URL, value.trimEnd('/'))
        }
    }

    fun aiModel(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString(KEY_AI_MODEL, "model") ?: "model"

    fun setAiModel(context: Context, value: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_AI_MODEL, value.trim())
        }
    }
}
