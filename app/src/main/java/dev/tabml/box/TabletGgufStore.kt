package dev.tabml.box

import android.content.Context
import java.io.File

object TabletGgufStore {

    /** Llama 3.2 1B Instruct Q4_K_M — matches [ModelProfiles.LLAMA_3_2] chat template in Llama Bro. */
    const val DEFAULT_DOWNLOAD_URL =
        "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf"

    fun modelFile(context: Context): File =
        File(File(context.filesDir, "on-device-llm"), "model.gguf")

    fun isModelPresent(context: Context): Boolean {
        val f = modelFile(context)
        return f.isFile && f.length() > 1_000_000L
    }

    fun deleteModelFile(context: Context): Boolean {
        val f = modelFile(context)
        return !f.exists() || f.delete()
    }
}
