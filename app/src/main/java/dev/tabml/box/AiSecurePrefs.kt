package dev.tabml.box

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted prefs at rest.
 *
 * **API key & Cali PIN** use credential-encrypted storage (available after the user unlocks the
 * device the first time after boot).
 */
object AiSecurePrefs {

    private const val PREFS_CREDENTIAL = "tabml_ai_secure"
    private const val KEY_BEARER = "assistant_https_bearer"
    private const val KEY_CALI_PIN_HASH = "cali_pin_sha256"

    fun apiKey(context: Context): String = try {
        credentialPrefs(context).getString(KEY_BEARER, "") ?: ""
    } catch (_: Exception) {
        ""
    }

    fun setApiKey(context: Context, value: String) {
        try {
            credentialPrefs(context).edit().putString(KEY_BEARER, value).apply()
        } catch (_: Exception) {
        }
    }

    fun caliPinHash(context: Context): String = try {
        credentialPrefs(context).getString(KEY_CALI_PIN_HASH, "") ?: ""
    } catch (_: Exception) {
        ""
    }

    fun setCaliPinHash(context: Context, sha256Hex: String) {
        try {
            credentialPrefs(context).edit().putString(KEY_CALI_PIN_HASH, sha256Hex.trim()).apply()
        } catch (_: Exception) {
        }
    }

    fun clearCaliPin(context: Context) {
        try {
            credentialPrefs(context).edit().remove(KEY_CALI_PIN_HASH).apply()
        } catch (_: Exception) {
        }
    }

    private fun credentialPrefs(context: Context): SharedPreferences {
        val app = context.applicationContext
        val masterKey = MasterKey.Builder(app)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            app,
            PREFS_CREDENTIAL,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
}
