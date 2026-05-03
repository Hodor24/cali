package dev.tabml.box

import android.content.Context
import org.tensorflow.lite.support.common.FileUtil
import java.io.File
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

object ModelLoader {

    private const val ASSET = "models/xor_mlp.tflite"

    fun downloadedFile(context: Context): File =
        File(context.filesDir, "models/downloaded.tflite")

    fun updatesApkFile(context: Context): File =
        File(context.filesDir, "updates/downloaded.apk")

    /**
     * Prefer a user-downloaded flatbuffer if enabled and present; otherwise bundled asset.
     */
    fun loadMappedForInference(context: Context): MappedByteBuffer {
        val f = downloadedFile(context)
        if (Prefs.preferDownloadedTflite(context) && f.isFile && f.length() >= 256) {
            return mapFile(f)
        }
        return FileUtil.loadMappedFile(context, ASSET)
    }

    private fun mapFile(file: File): MappedByteBuffer {
        FileInputStream(file).use { fis ->
            val channel = fis.channel
            return channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
        }
    }
}
