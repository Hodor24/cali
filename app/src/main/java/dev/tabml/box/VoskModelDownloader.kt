package dev.tabml.box

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * Downloads and unpacks the small English Vosk model from Alpha Cephei (one-time, ~40 MB).
 * Requires [Prefs.allowNetwork] to be true in the UI layer before calling.
 */
object VoskModelDownloader {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private const val MODEL_ZIP_URL =
        "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"

    suspend fun downloadAndInstall(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val destDir = VoskModelStore.modelDir(context)
            val tmpZip = File(context.cacheDir, "vosk-small-en-us-0.15-download.zip")
            val unzipRoot = File(context.cacheDir, "vosk-unzip-${System.currentTimeMillis()}")

            try {
                val req = Request.Builder().url(MODEL_ZIP_URL).build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        error("HTTP ${resp.code}")
                    }
                    val body = resp.body ?: error("empty body")
                    FileOutputStream(tmpZip).use { out ->
                        body.byteStream().use { input -> input.copyTo(out) }
                    }
                }

                if (!unzipRoot.mkdirs()) error("mkdir unzip")
                unzipToDirectory(tmpZip, unzipRoot)

                val modelRoot = findVoskModelRoot(unzipRoot)
                    ?: error("model layout not recognized after unzip")

                if (destDir.exists()) {
                    destDir.deleteRecursively()
                }
                if (!destDir.mkdirs()) error("mkdir model dir")
                modelRoot.copyRecursively(destDir, overwrite = true)
            } finally {
                tmpZip.delete()
                unzipRoot.deleteRecursively()
            }
            Unit
        }
    }

    private fun unzipToDirectory(zipFile: File, destDir: File) {
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(destDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { zis.copyTo(it) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun findVoskModelRoot(dir: File): File? {
        if (File(dir, "am").exists() && File(dir, "conf").exists()) {
            return dir
        }
        val children = dir.listFiles() ?: return null
        for (c in children) {
            if (c.isDirectory) {
                val inner = findVoskModelRoot(c)
                if (inner != null) return inner
            }
        }
        return null
    }
}
