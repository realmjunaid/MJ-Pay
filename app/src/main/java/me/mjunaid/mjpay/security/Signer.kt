package me.mjunaid.mjpay.security

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Pure Kotlin HMAC-SHA256 helper. No Android dependencies, unit tested.
 */
object Signer {

    fun hmacSha256Hex(secret: String, message: String): String =
        hmacSha256Hex(secret.toByteArray(Charsets.UTF_8), message.toByteArray(Charsets.UTF_8))

    fun hmacSha256Hex(secret: ByteArray, message: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        return mac.doFinal(message).toLowerHex()
    }

    /** Signature format required by the server contract. */
    fun signature(secret: String, timestamp: String, rawBody: String): String =
        hmacSha256Hex(secret, "$timestamp.$rawBody")

    private fun ByteArray.toLowerHex(): String {
        val digits = "0123456789abcdef"
        val out = StringBuilder(size * 2)
        for (byte in this) {
            val value = byte.toInt() and 0xff
            out.append(digits[value ushr 4])
            out.append(digits[value and 0x0f])
        }
        return out.toString()
    }
}
