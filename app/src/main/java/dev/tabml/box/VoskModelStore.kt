package dev.tabml.box

import android.content.Context
import java.io.File

/**
 * On-disk layout for the official small English Vosk model
 * ([vosk-model-small-en-us-0.15](https://alphacephei.com/vosk/models)).
 */
object VoskModelStore {
    private const val MODEL_DIR_NAME = "vosk-model-small-en-us-0.15"

    fun modelDir(context: Context): File = File(context.filesDir, MODEL_DIR_NAME)

    /** Absolute path string passed to [org.vosk.Model]. */
    fun modelPath(context: Context): String = modelDir(context).absolutePath

    fun isInstalled(context: Context): Boolean {
        val d = modelDir(context)
        return File(d, "am").exists() && File(d, "conf").exists()
    }
}
