package dev.tabml.box

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object TabletGgufDownloader {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun downloadDefaultModel(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val dest = TabletGgufStore.modelFile(context)
            dest.parentFile?.mkdirs()
            val tmp = File(context.cacheDir, "gguf-${System.currentTimeMillis()}.part")

            try {
                val req = Request.Builder()
                    .url(TabletGgufStore.DEFAULT_DOWNLOAD_URL)
                    .header("Accept", "*/*")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) error("HTTP ${resp.code}")
                    val body = resp.body ?: error("empty body")
                    FileOutputStream(tmp).use { out ->
                        body.byteStream().use { input -> input.copyTo(out) }
                    }
                }
                if (!tmp.renameTo(dest)) {
                    tmp.copyTo(dest, overwrite = true)
                    tmp.delete()
                }
            } finally {
                if (tmp.exists()) tmp.delete()
            }
            Unit
        }
    }
}
