package me.mjunaid.mjpay.net

import java.net.URI

/**
 * Pure request rules so they can be unit tested without Android.
 */
object UploadRules {

    /** Response classification from the server contract (Section 4). */
    fun classify(statusCode: Int): UploadResult = when {
        statusCode in 200..299 -> UploadResult.Success
        statusCode == 429 || statusCode in 500..599 -> UploadResult.Retry("http-$statusCode")
        statusCode == 401 || statusCode == 403 -> UploadResult.Permanent("auth")
        statusCode in 300..399 -> UploadResult.Permanent("http-$statusCode")
        statusCode in 400..499 -> UploadResult.Permanent("http-$statusCode")
        else -> UploadResult.Retry("http-$statusCode")
    }

    /**
     * Release allows https only. Debug additionally allows http for the two local
     * mock-server hosts, nothing else.
     */
    fun isAllowedUrl(url: String, allowDebugCleartext: Boolean): Boolean {
        val uri = try {
            URI(url.trim())
        } catch (e: Exception) {
            return false
        }
        val host = uri.host ?: return false
        if (host.isBlank()) return false

        return when (uri.scheme?.lowercase()) {
            "https" -> true
            "http" -> allowDebugCleartext && (
                host.equals("10.0.2.2", ignoreCase = true) ||
                    host.equals("localhost", ignoreCase = true) ||
                    host.equals("127.0.0.1", ignoreCase = true)
                )
            else -> false
        }
    }
}
