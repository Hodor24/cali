package dev.tabml.box

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Stores the AI API key encrypted at rest (AES). */
object AiSecurePrefs {

    private const val PREFS_NAME = "tabml_ai_secure"
    private const val KEY_API_KEY = "openai_compatible_api_key"

    fun apiKey(context: Context): String = try {
        prefs(context).getString(KEY_API_KEY, "") ?: ""
    } catch (_: Exception) {
        ""
    }

    fun setApiKey(context: Context, value: String) {
        try {
            prefs(context).edit().putString(KEY_API_KEY, value).apply()
        } catch (_: Exception) {
        }
    }

    private fun prefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
}
