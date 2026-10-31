package me.mjunaid.mjpay.sms

import java.security.MessageDigest

/** Stable dedupe key for a broadcast: sha256(sender|body|smsTimestampMillis). */
object DedupeKey {

    fun of(sender: String, body: String, timestampMillis: Long): String =
        sha256Hex("$sender|$body|$timestampMillis")

    fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
        val digits = "0123456789abcdef"
        val out = StringBuilder(digest.size * 2)
        for (byte in digest) {
            val value = byte.toInt() and 0xff
            out.append(digits[value ushr 4])
            out.append(digits[value and 0x0f])
        }
        return out.toString()
    }
}
