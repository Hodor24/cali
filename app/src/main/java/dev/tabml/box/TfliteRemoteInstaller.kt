package dev.tabml.box

import android.content.Context

/** Saves a remote .tflite into private storage and switches inference to use it. */
object TfliteRemoteInstaller {

    fun installBlocking(context: Context, url: String): Long {
        require(NetworkGuard.isAllowed(context)) { "network disabled in app settings" }
        val dest = ModelLoader.downloadedFile(context)
        val n = HttpsDownload.streamToFile(context, url, dest)
        require(n >= 256) { "downloaded file too small to be a useful .tflite" }
        Prefs.setPreferDownloadedTflite(context, true)
        return n
    }
}
