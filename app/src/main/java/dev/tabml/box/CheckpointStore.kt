package dev.tabml.box

import android.content.Context
import java.io.File

object CheckpointStore {
    private const val FILE_NAME = "xor_checkpoint.json"

    fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun exists(context: Context): Boolean = file(context).isFile

    fun save(context: Context, checkpoint: XorCheckpoint) {
        file(context).writeText(checkpoint.toJsonString())
    }

    fun load(context: Context): XorCheckpoint {
        val text = file(context).readText()
        return XorCheckpoint.parseJson(text)
    }

    fun delete(context: Context): Boolean {
        val f = file(context)
        return !f.exists() || f.delete()
    }
}
