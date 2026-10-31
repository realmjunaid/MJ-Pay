package me.mjunaid.mjpay.ui

import android.content.Context
import me.mjunaid.mjpay.R

/** Short, content-free labels for stored error codes. Never shows SMS text. */
object ErrorText {

    fun of(context: Context, code: String?): String {
        if (code.isNullOrBlank()) return "\u2014"
        return when {
            code == "config" -> context.getString(R.string.error_config)
            code == "network" -> context.getString(R.string.error_network)
            code == "timeout" -> context.getString(R.string.error_timeout)
            code == "tls" -> context.getString(R.string.error_tls)
            code == "auth" -> context.getString(R.string.error_auth)
            code == "io" -> context.getString(R.string.error_io)
            code == "body" -> context.getString(R.string.error_body)
            code == "expired" -> context.getString(R.string.error_expired)
            code.startsWith("http-") ->
                context.getString(R.string.error_http, code.removePrefix("http-"))
            else -> code
        }
    }
}
