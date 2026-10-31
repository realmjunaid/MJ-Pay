package me.mjunaid.mjpay.net

import android.content.Context
import me.mjunaid.mjpay.BuildConfig
import me.mjunaid.mjpay.data.Prefs
import me.mjunaid.mjpay.security.Signer
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import javax.net.ssl.SSLException

/**
 * HTTPS POST with the server contract headers. Nothing is logged: not the URL,
 * headers, body or secret.
 */
class Uploader(context: Context) {

    private val appContext = context.applicationContext

    fun post(rawJson: String, timeoutMillis: Int = DEFAULT_TIMEOUT_MS): UploadResult {
        val prefs = Prefs(appContext)
        val urlString = prefs.webhookUrl.trim()
        if (!UploadRules.isAllowedUrl(urlString, allowDebugCleartext = BuildConfig.DEBUG)) {
            return UploadResult.Retry("config")
        }
        val secret = prefs.secretStore.load() ?: return UploadResult.Retry("config")

        val timestamp = (System.currentTimeMillis() / 1000L).toString()
        val signature = Signer.signature(secret, timestamp, rawJson)
        val bodyBytes = rawJson.toByteArray(Charsets.UTF_8)

        var connection: HttpURLConnection? = null
        return try {
            connection = URL(urlString).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = timeoutMillis
            connection.readTimeout = timeoutMillis
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("X-Timestamp", timestamp)
            connection.setRequestProperty("X-Signature", signature)
            connection.setRequestProperty("X-Device-Id", prefs.deviceId)
            connection.setFixedLengthStreamingMode(bodyBytes.size)

            connection.outputStream.use { it.write(bodyBytes) }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            readBounded(stream)
            UploadRules.classify(code)
        } catch (e: SocketTimeoutException) {
            UploadResult.Retry("timeout")
        } catch (e: SSLException) {
            UploadResult.Retry("tls")
        } catch (e: IOException) {
            UploadResult.Retry("network")
        } catch (e: Exception) {
            UploadResult.Retry("io")
        } finally {
            connection?.disconnect()
        }
    }

    /** Reads and discards at most 1 KB so the socket can be reused/closed promptly. */
    private fun readBounded(stream: InputStream?) {
        if (stream == null) return
        stream.use {
            val buffer = ByteArray(256)
            var total = 0
            while (total < 1024) {
                val read = it.read(buffer, 0, minOf(buffer.size, 1024 - total))
                if (read <= 0) break
                total += read
            }
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 10_000
    }
}
