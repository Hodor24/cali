package dev.tabml.box

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection

object HttpsDownload {

    class HttpError(val code: Int) : Exception("HTTP $code")

    /**
     * Streams [httpsUrl] to [destination] (parent dirs created). Only https://.
     * @return bytes written
     */
    fun streamToFile(context: Context, httpsUrl: String, destination: File): Long {
        require(NetworkGuard.isAllowed(context)) { "network disabled in app settings" }
        val trimmed = httpsUrl.trim()
        require(trimmed.startsWith("https://")) { "only https:// URLs are allowed" }
        val conn = URL(trimmed).openConnection() as HttpsURLConnection
        conn.connectTimeout = 30_000
        conn.readTimeout = 300_000
        conn.instanceFollowRedirects = true
        conn.connect()
        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            throw HttpError(code)
        }
        destination.parentFile?.mkdirs()
        var total = 0L
        FileOutputStream(destination).use { out ->
            conn.inputStream.use { input ->
                val buf = ByteArray(8192)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                    total += n
                }
            }
        }
        conn.disconnect()
        return total
    }
}
