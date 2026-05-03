package dev.tabml.box

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener as VoskRecognitionListener
import org.vosk.android.SpeechService
import java.util.Locale

/**
 * Text-to-speech and push-to-talk speech recognition for Cali.
 * Offline STT uses Vosk when a model is installed and the user enables it in Cali options.
 */
class CaliVoice(
    private val activity: AppCompatActivity,
    private val onListenResult: (String) -> Unit,
    private val onListenError: () -> Unit,
    private val onSpeakDone: () -> Unit,
    /** Fires when TTS starts or finishes (including error / [stopSpeaking]). */
    var onSpeakingChanged: ((speaking: Boolean) -> Unit)? = null,
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    private var ttsReady = false

    private var voskModel: Model? = null
    private var voskRecognizer: Recognizer? = null
    private var voskSpeechService: SpeechService? = null

    private val utteranceId = "cali_utt"

    /** True after first successful [Model] load for the current process. */
    fun isVoskModelLoaded(): Boolean = voskModel != null

    fun init() {
        tts = TextToSpeech(activity, this)
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val engine = tts ?: return
        applyPreferredTtsVoice(engine)
        engine.setSpeechRate(Prefs.caliTtsRate(activity))
        engine.setPitch(1.0f)
        engine.setOnUtteranceProgressListener(
            @Suppress("DEPRECATION")
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    activity.runOnUiThread { onSpeakingChanged?.invoke(true) }
                }

                override fun onDone(utteranceId: String?) {
                    activity.runOnUiThread {
                        onSpeakingChanged?.invoke(false)
                        onSpeakDone()
                    }
                }

                override fun onError(utteranceId: String?) {
                    activity.runOnUiThread {
                        onSpeakingChanged?.invoke(false)
                        onSpeakDone()
                    }
                }
            },
        )
        ttsReady = true
    }

    fun applySpeechRateFromPrefs() {
        tts?.setSpeechRate(Prefs.caliTtsRate(activity))
    }

    private fun applyPreferredTtsVoice(engine: TextToSpeech) {
        engine.language = Locale.UK
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        val voices = engine.voices ?: return
        fun score(v: Voice): Int {
            var s = 0
            if (!v.isNetworkConnectionRequired) s += 100
            if (v.locale == Locale.UK) s += 40
            else if (v.locale.language == "en") s += 30
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                s += v.quality.coerceAtMost(200)
            }
            return s
        }
        val best = voices.filter { v ->
            !v.isNetworkConnectionRequired &&
                (v.locale == Locale.UK || v.locale.language == "en")
        }.maxByOrNull(::score)
        if (best != null) {
            engine.voice = best
            engine.language = best.locale
        }
    }

    fun isTtsReady(): Boolean = ttsReady

    fun speak(text: String) {
        val t = tts ?: return
        if (!ttsReady || text.isBlank()) {
            onSpeakingChanged?.invoke(false)
            onSpeakDone()
            return
        }
        val params = Bundle()
        t.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    /** True while the engine is playing queued speech (after onStart, until idle). */
    fun isSpeaking(): Boolean = tts?.isSpeaking == true

    fun stopSpeaking() {
        tts?.stop()
        activity.runOnUiThread { onSpeakingChanged?.invoke(false) }
    }

    /**
     * Loads the Vosk model from [modelPath] (expensive). Call from a worker thread.
     * Safe to call multiple times; loads at most once per process until [shutdown].
     */
    fun ensureVoskModelLoaded(modelPath: String): Boolean {
        synchronized(this) {
            if (voskModel != null) return true
            return try {
                LibVosk.setLogLevel(LogLevel.INFO)
                voskModel = Model(modelPath)
                true
            } catch (_: Throwable) {
                voskModel = null
                false
            }
        }
    }

    /**
     * Offline recognition using Vosk. Requires [ensureVoskModelLoaded] to have succeeded.
     * Stops after the first finalized utterance (Vosk endpointing) or after [maxListenMs] of silence cap.
     */
    fun startVoskListening(maxListenMs: Int = 25_000) {
        destroyRecognizer()
        destroyVoskSession()
        val model = voskModel
        if (model == null) {
            onListenError()
            return
        }
        try {
            val rec = Recognizer(model, 16000.0f)
            voskRecognizer = rec
            val service = SpeechService(rec, 16000.0f)
            voskSpeechService = service

            val listener = object : VoskRecognitionListener {
                private var lastText = ""

                override fun onPartialResult(hypothesis: String?) {}

                override fun onResult(hypothesis: String?) {
                    val t = parseVoskText(hypothesis.orEmpty())
                    if (t.isNotEmpty()) {
                        lastText = t
                        voskSpeechService?.stop()
                    }
                }

                override fun onFinalResult(hypothesis: String?) {
                    val t = parseVoskText(hypothesis.orEmpty()).ifBlank { preTrimHypothesis(lastText) }
                    deliverListenResult(t)
                }

                override fun onError(exception: Exception?) {
                    deliverListenError()
                }

                override fun onTimeout() {
                    deliverListenResult(preTrimHypothesis(lastText))
                }

                private fun preTrimHypothesis(s: String): String = s.trim()
            }

            if (maxListenMs > 0) {
                service.startListening(listener, maxListenMs)
            } else {
                service.startListening(listener)
            }
        } catch (_: Throwable) {
            destroyVoskSession()
            onListenError()
        }
    }

    private fun parseVoskText(jsonOrPlain: String): String {
        val raw = jsonOrPlain.trim()
        if (raw.isEmpty()) return ""
        return try {
            JSONObject(raw).optString("text").trim()
        } catch (_: Exception) {
            raw
        }
    }

    private fun deliverListenResult(text: String) {
        activity.runOnUiThread {
            destroyVoskSession()
            if (text.isNotEmpty()) onListenResult(text) else onListenError()
        }
    }

    private fun deliverListenError() {
        activity.runOnUiThread {
            destroyVoskSession()
            onListenError()
        }
    }

    fun destroyVoskSession() {
        try {
            voskSpeechService?.stop()
            voskSpeechService?.shutdown()
        } catch (_: Exception) {
        }
        voskSpeechService = null
        try {
            voskRecognizer?.close()
        } catch (_: Exception) {
        }
        voskRecognizer = null
    }

    fun startListening() {
        destroyRecognizer()
        destroyVoskSession()
        if (!SpeechRecognizer.isRecognitionAvailable(activity)) {
            onListenError()
            return
        }
        val sr = SpeechRecognizer.createSpeechRecognizer(activity)
        recognizer = sr
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.UK)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        sr.setRecognitionListener(
            object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    activity.runOnUiThread {
                        destroyRecognizer()
                        onListenError()
                    }
                }

                override fun onResults(results: Bundle?) {
                    activity.runOnUiThread {
                        destroyRecognizer()
                        val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val said = list?.firstOrNull()?.trim().orEmpty()
                        if (said.isNotEmpty()) onListenResult(said) else onListenError()
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}

                override fun onEvent(eventType: Int, params: Bundle?) {}
            },
        )
        sr.startListening(intent)
    }

    fun destroyRecognizer() {
        recognizer?.destroy()
        recognizer = null
    }

    fun shutdown() {
        stopSpeaking()
        destroyRecognizer()
        destroyVoskSession()
        try {
            voskModel?.close()
        } catch (_: Exception) {
        }
        voskModel = null
        tts?.shutdown()
        tts = null
        ttsReady = false
    }
}
