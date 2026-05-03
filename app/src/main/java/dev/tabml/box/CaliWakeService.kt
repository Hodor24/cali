package dev.tabml.box

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Foreground service: listens for “Cali” (and close variants) using **Vosk** on-device ASR with a
 * tight grammar. Requires the same small English model as Cali offline speech (download on this
 * screen or under Cali → Learning options).
 */
class CaliWakeService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val loadThread = Thread({ loadAndRun() }, "cali-wake-vosk")
    private val running = AtomicBoolean(false)

    @Volatile
    private var destroyed = false

    @Volatile
    private var speechService: SpeechService? = null
    private var voskRecognizer: Recognizer? = null
    private var voskModel: Model? = null

    @Volatile
    private var lastWakeElapsedMs: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!running.compareAndSet(false, true)) return START_STICKY

        if (!VoskModelStore.isInstalled(applicationContext)) {
            startForegroundTyped(
                buildNotification(
                    getString(R.string.cali_wake_notif_title),
                    getString(R.string.cali_wake_missing_config),
                ),
            )
            stopSelfInternal()
            return START_NOT_STICKY
        }

        if (Prefs.caliWakeChargeOnly(this) && !isBatteryChargingOrFull()) {
            startForegroundTyped(
                buildNotification(
                    getString(R.string.cali_wake_notif_title),
                    getString(R.string.cali_wake_need_charging),
                ),
            )
            stopSelfInternal()
            return START_NOT_STICKY
        }

        startForegroundTyped(
            buildNotification(
                getString(R.string.cali_wake_notif_title),
                getString(R.string.cali_wake_notif_listening),
            ),
        )
        loadThread.start()
        return START_STICKY
    }

    private fun loadAndRun() {
        try {
            if (destroyed) return
            LibVosk.setLogLevel(LogLevel.INFO)
            val path = VoskModelStore.modelPath(applicationContext)
            val model = Model(path)
            if (destroyed) {
                model.close()
                return
            }
            voskModel = model
            val rec = try {
                Recognizer(model, 16000f, WAKE_GRAMMAR)
            } catch (_: Throwable) {
                Recognizer(model, 16000f)
            }
            if (destroyed) {
                rec.close()
                model.close()
                voskModel = null
                return
            }
            voskRecognizer = rec
            val svc = SpeechService(rec, 16000f)
            speechService = svc
            if (destroyed) {
                svc.stop()
                svc.shutdown()
                rec.close()
                model.close()
                speechService = null
                voskRecognizer = null
                voskModel = null
                return
            }
            svc.startListening(
                object : RecognitionListener {
                    override fun onPartialResult(hypothesis: String?) {
                        maybeFireWake(hypothesis)
                    }

                    override fun onResult(hypothesis: String?) {
                        maybeFireWake(hypothesis)
                    }

                    override fun onFinalResult(hypothesis: String?) {
                        maybeFireWake(hypothesis)
                    }

                    override fun onError(e: Exception?) {
                        mainHandler.post {
                            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                            nm.notify(
                                NOTIF_ID + 2,
                                buildNotification(
                                    getString(R.string.cali_wake_error_title),
                                    e?.message ?: e.toString(),
                                ),
                            )
                        }
                    }

                    override fun onTimeout() {}
                },
            )
        } catch (e: Throwable) {
            releaseVoskResources()
            mainHandler.post {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(
                    NOTIF_ID + 2,
                    buildNotification(
                        getString(R.string.cali_wake_error_title),
                        e.message ?: e.toString(),
                    ),
                )
                stopSelfInternal()
            }
        }
    }

    private fun releaseVoskResources() {
        runCatching { speechService?.stop() }
        runCatching { speechService?.shutdown() }
        speechService = null
        runCatching { voskRecognizer?.close() }
        voskRecognizer = null
        runCatching { voskModel?.close() }
        voskModel = null
    }

    private fun maybeFireWake(hypothesis: String?) {
        val text = extractVoskText(hypothesis) ?: return
        if (!containsWakeKeyword(text)) return
        val now = SystemClock.elapsedRealtime()
        synchronized(this) {
            if (now - lastWakeElapsedMs < WAKE_DEBOUNCE_MS) return
            lastWakeElapsedMs = now
        }
        mainHandler.post { onWakeDetected() }
    }

    private fun extractVoskText(jsonOrPlain: String?): String? {
        if (jsonOrPlain.isNullOrBlank()) return null
        val raw = jsonOrPlain.trim()
        return try {
            val o = JSONObject(raw)
            o.optString("text").ifBlank { o.optString("partial") }.trim().ifBlank { null }
        } catch (_: Exception) {
            raw.trim().ifBlank { null }
        }
    }

    private fun containsWakeKeyword(spoken: String): Boolean {
        if (HEY_CALI.containsMatchIn(spoken)) return true
        return CALI_AS_WORD.containsMatchIn(spoken)
    }

    private fun onWakeDetected() {
        if (Prefs.caliWakeChargeOnly(this) && !isBatteryChargingOrFull()) return
        val i = Intent(this, AiChatActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(AiChatActivity.EXTRA_WAKE_OPEN_MIC, true)
        }
        startActivity(i)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.cali_wake_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = getString(R.string.cali_wake_channel_desc) },
        )
    }

    private fun buildNotification(title: String, text: String): Notification {
        val open = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            this,
            0,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mic_24)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun startForegroundTyped(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun stopSelfInternal() {
        running.set(false)
        stopSelf()
    }

    override fun onDestroy() {
        destroyed = true
        releaseVoskResources()
        running.set(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_DETACH)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(false)
        }
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "cali_wake"
        private const val NOTIF_ID = 58201
        private const val WAKE_DEBOUNCE_MS = 2200L

        /**
         * Constrained phrases for the small US English Vosk model (with `[unk]` filler).
         * If this fails at runtime, we fall back to an unconstrained recognizer and phrase checks below.
         */
        private const val WAKE_GRAMMAR = """["cali", "hey cali", "[unk]"]"""

        /** “Hey Cali” / “hey, Cali” before a non-letter or end. */
        private val HEY_CALI = Regex("hey\\s*,?\\s*cali(?!\\p{L})", RegexOption.IGNORE_CASE)

        /** “Cali” as its own token (avoids “California”, etc.). */
        private val CALI_AS_WORD = Regex("(?<!\\p{L})cali(?!\\p{L})", RegexOption.IGNORE_CASE)

        fun start(context: Context) {
            val i = Intent(context, CaliWakeService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(context, i)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CaliWakeService::class.java))
        }
    }
}
