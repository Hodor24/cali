package dev.tabml.box

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

object Prefs {
    private const val NAME = "tab_ml_prefs"
    /** Device-protected prefs: readable during direct boot (before CE unlock). */
    private const val WAKE_PREFS_NAME = "tab_ml_wake"
    private const val KEY_WAKE_DE_MIGRATED = "_wake_de_migrated"
    private val wakeMigrateLock = Any()
    private const val KEY_ALLOW_NETWORK = "allow_network"
    private const val KEY_PREFER_DOWNLOADED_TFLITE = "prefer_downloaded_tflite"
    private const val KEY_LAST_HTTPS_URL = "last_https_url"
    private const val KEY_AI_BASE_URL = "ai_base_url"
    private const val KEY_AI_MODEL = "ai_model"
    private const val KEY_NVQ_ASSESSOR_MODE = "nvq_assessor_mode"
    private const val KEY_OBSERVATION_LEARNING = "observation_learning"
    private const val KEY_NVQ_WATCH_APP_OPT_IN = "nvq_watch_app_opt_in"
    private const val KEY_NVQ_WATCH_LAST_PROMPT_MS = "nvq_watch_last_prompt_ms"
    private const val KEY_NVQ_WATCH_PENDING_PROMPT = "nvq_watch_pending_prompt"
    private const val KEY_NVQ_WATCH_PENDING_REASON = "nvq_watch_pending_reason"
    private const val KEY_NVQ_WATCH_NEVER_SUGGEST = "nvq_watch_never_suggest"
    private const val KEY_CALI_SPEAK_REPLIES = "cali_speak_replies"
    private const val KEY_CALI_LISTEN_AFTER = "cali_listen_after"
    private const val KEY_CALI_WAKE_ENABLED = "cali_wake_enabled"
    private const val KEY_CALI_OFFLINE_STT = "cali_offline_stt"
    private const val KEY_ASSISTANT_ON_DEVICE = "assistant_on_device_llm"
    private const val KEY_ON_DEVICE_PROFILE = "on_device_model_profile"
    private const val KEY_CALI_TTS_RATE = "cali_tts_rate"
    private const val KEY_CALI_LARGE_CHAT = "cali_large_chat_text"
    private const val KEY_CALI_HIGH_CONTRAST_CHAT = "cali_high_contrast_chat"
    private const val KEY_WAKE_CLEAR_SUBJECT = "cali_wake_clear_subject"
    private const val KEY_WAKE_CHARGE_ONLY = "cali_wake_charge_only"
    private const val KEY_CALI_PIN_ENABLED = "cali_pin_enabled"
    private const val KEY_OPS_POST_NOTIF_PROMPT_SHOWN = "ops_post_notif_prompt_shown"

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

    /**
     * When true, the assistant system message includes the bundled UK construction NVQ assessor brief.
     * Default true so assessor workflows are one toggle away from off.
     */
    fun nvqAssessorMode(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_NVQ_ASSESSOR_MODE, true)

    fun setNvqAssessorMode(context: Context, value: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_NVQ_ASSESSOR_MODE, value)
        }
    }

    /**
     * Log assistant session metadata locally and include a short summary in the system prompt.
     */
    fun observationLearning(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_OBSERVATION_LEARNING, true)

    fun setObservationLearning(context: Context, value: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_OBSERVATION_LEARNING, value)
        }
    }

    /** User enabled “watch for NVQ-related screen text” in app (still needs Android Accessibility). */
    fun nvqWatchAppOptIn(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_NVQ_WATCH_APP_OPT_IN, false)

    fun setNvqWatchAppOptIn(context: Context, value: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_NVQ_WATCH_APP_OPT_IN, value)
        }
    }

    fun nvqWatchLastPromptMs(context: Context): Long =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getLong(KEY_NVQ_WATCH_LAST_PROMPT_MS, 0L)

    fun setNvqWatchLastPromptMs(context: Context, value: Long) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit {
            putLong(KEY_NVQ_WATCH_LAST_PROMPT_MS, value)
        }
    }

    fun nvqWatchPendingPrompt(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_NVQ_WATCH_PENDING_PROMPT, false)

    fun setNvqWatchPendingPrompt(context: Context, value: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_NVQ_WATCH_PENDING_PROMPT, value)
        }
    }

    fun nvqWatchPendingReason(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString(KEY_NVQ_WATCH_PENDING_REASON, "") ?: ""

    fun setNvqWatchPendingReason(context: Context, value: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_NVQ_WATCH_PENDING_REASON, value)
        }
    }

    fun nvqWatchNeverSuggest(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_NVQ_WATCH_NEVER_SUGGEST, false)

    fun setNvqWatchNeverSuggest(context: Context, value: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_NVQ_WATCH_NEVER_SUGGEST, value)
        }
    }

    fun clearNvqWatchPending(context: Context) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_NVQ_WATCH_PENDING_PROMPT, false)
            putString(KEY_NVQ_WATCH_PENDING_REASON, "")
        }
    }

    fun caliSpeakReplies(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_CALI_SPEAK_REPLIES, true)

    fun setCaliSpeakReplies(context: Context, value: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_CALI_SPEAK_REPLIES, value)
        }
    }

    fun caliListenAfter(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_CALI_LISTEN_AFTER, false)

    fun setCaliListenAfter(context: Context, value: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_CALI_LISTEN_AFTER, value)
        }
    }

    /**
     * Copies wake flags from credential [NAME] into device-protected storage when CE is available.
     * Safe to call from [TabMlApplication]; no-op if CE is locked or already migrated.
     */
    fun syncWakeFlagsToDeviceProtectedStorage(context: Context) {
        try {
            migrateWakeFlagsFromCredentialIfNeeded(context.applicationContext)
        } catch (_: Exception) {
        }
    }

    /** Cali wake-word foreground service (Vosk; uses offline English model when installed). */
    fun caliWakeEnabled(context: Context): Boolean {
        val app = context.applicationContext
        migrateWakeFlagsFromCredentialIfNeeded(app)
        return wakeDevicePrefs(app).getBoolean(KEY_CALI_WAKE_ENABLED, false)
    }

    fun setCaliWakeEnabled(context: Context, value: Boolean) {
        val app = context.applicationContext
        wakeDevicePrefs(app).edit {
            putBoolean(KEY_CALI_WAKE_ENABLED, value)
            putBoolean(KEY_WAKE_DE_MIGRATED, true)
        }
        try {
            app.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit {
                putBoolean(KEY_CALI_WAKE_ENABLED, value)
            }
        } catch (_: Exception) {
        }
    }

    /** Use on-device Vosk STT when a model is installed (see Cali options). */
    fun caliOfflineStt(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_CALI_OFFLINE_STT, false)

    fun setCaliOfflineStt(context: Context, value: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_CALI_OFFLINE_STT, value)
        }
    }

    /**
     * When true, Cali runs a GGUF model on this device (Llama Bro). No HTTPS assistant connection is used for chat.
     */
    fun assistantOnDeviceLlm(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ASSISTANT_ON_DEVICE, false)

    fun setAssistantOnDeviceLlm(context: Context, value: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_ASSISTANT_ON_DEVICE, value)
        }
    }

    /** One of: LLAMA_3_2, GEMMA, QWEN_2_5, SMOLLM2 — must match the GGUF chat template. */
    fun onDeviceModelProfileKey(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString(KEY_ON_DEVICE_PROFILE, "LLAMA_3_2") ?: "LLAMA_3_2"

    fun setOnDeviceModelProfileKey(context: Context, value: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_ON_DEVICE_PROFILE, value)
        }
    }

    /** TextToSpeech rate (roughly 0.8–1.2). */
    fun caliTtsRate(context: Context): Float =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_CALI_TTS_RATE, 0.95f)
            .coerceIn(0.8f, 1.2f)

    fun setCaliTtsRate(context: Context, value: Float) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit {
            putFloat(KEY_CALI_TTS_RATE, value.coerceIn(0.8f, 1.2f))
        }
    }

    fun caliLargeChatText(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_CALI_LARGE_CHAT, false)

    fun setCaliLargeChatText(context: Context, value: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_CALI_LARGE_CHAT, value)
        }
    }

    fun caliHighContrastChat(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_CALI_HIGH_CONTRAST_CHAT, false)

    fun setCaliHighContrastChat(context: Context, value: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_CALI_HIGH_CONTRAST_CHAT, value)
        }
    }

    /** When true, opening Cali from wake word clears the subject field. */
    fun caliWakeClearSubject(context: Context): Boolean {
        val app = context.applicationContext
        migrateWakeFlagsFromCredentialIfNeeded(app)
        return wakeDevicePrefs(app).getBoolean(KEY_WAKE_CLEAR_SUBJECT, false)
    }

    fun setCaliWakeClearSubject(context: Context, value: Boolean) {
        val app = context.applicationContext
        wakeDevicePrefs(app).edit {
            putBoolean(KEY_WAKE_CLEAR_SUBJECT, value)
            putBoolean(KEY_WAKE_DE_MIGRATED, true)
        }
        try {
            app.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit {
                putBoolean(KEY_WAKE_CLEAR_SUBJECT, value)
            }
        } catch (_: Exception) {
        }
    }

    /** Wake service only starts while the device is charging when this is on. */
    fun caliWakeChargeOnly(context: Context): Boolean {
        val app = context.applicationContext
        migrateWakeFlagsFromCredentialIfNeeded(app)
        return wakeDevicePrefs(app).getBoolean(KEY_WAKE_CHARGE_ONLY, false)
    }

    fun setCaliWakeChargeOnly(context: Context, value: Boolean) {
        val app = context.applicationContext
        wakeDevicePrefs(app).edit {
            putBoolean(KEY_WAKE_CHARGE_ONLY, value)
            putBoolean(KEY_WAKE_DE_MIGRATED, true)
        }
        try {
            app.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit {
                putBoolean(KEY_WAKE_CHARGE_ONLY, value)
            }
        } catch (_: Exception) {
        }
    }

    fun caliPinEnabled(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_CALI_PIN_ENABLED, false)

    fun setCaliPinEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_CALI_PIN_ENABLED, value)
        }
    }

    /**
     * True after we have launched the system POST_NOTIFICATIONS prompt from Operations (banner or task save).
     * Used to hide the in-app "Allow" button when the system will not show the sheet again.
     */
    fun opsPostNotifPromptShown(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_OPS_POST_NOTIF_PROMPT_SHOWN, false)

    fun setOpsPostNotifPromptShown(context: Context, value: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_OPS_POST_NOTIF_PROMPT_SHOWN, value)
        }
    }

    private fun wakeDevicePrefs(app: Context): SharedPreferences =
        app.createDeviceProtectedStorageContext().getSharedPreferences(WAKE_PREFS_NAME, Context.MODE_PRIVATE)

    private fun migrateWakeFlagsFromCredentialIfNeeded(app: Context) {
        synchronized(wakeMigrateLock) {
            val de = wakeDevicePrefs(app)
            if (de.getBoolean(KEY_WAKE_DE_MIGRATED, false)) return
            val ce = try {
                app.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            } catch (_: Exception) {
                return
            }
            de.edit {
                putBoolean(KEY_CALI_WAKE_ENABLED, ce.getBoolean(KEY_CALI_WAKE_ENABLED, false))
                putBoolean(KEY_WAKE_CHARGE_ONLY, ce.getBoolean(KEY_WAKE_CHARGE_ONLY, false))
                putBoolean(KEY_WAKE_CLEAR_SUBJECT, ce.getBoolean(KEY_WAKE_CLEAR_SUBJECT, false))
                putBoolean(KEY_WAKE_DE_MIGRATED, true)
            }
        }
    }
}
