package dev.tabml.box

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Optional HTTPS bearer token for your assistant server, encrypted at rest. */
object AiSecurePrefs {

    private const val PREFS_NAME = "tabml_ai_secure"
    private const val KEY_BEARER = "assistant_https_bearer"

    fun apiKey(context: Context): String = try {
        prefs(context).getString(KEY_BEARER, "") ?: ""
    } catch (_: Exception) {
        ""
    }

    fun setApiKey(context: Context, value: String) {
        try {
            prefs(context).edit().putString(KEY_BEARER, value).apply()
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
