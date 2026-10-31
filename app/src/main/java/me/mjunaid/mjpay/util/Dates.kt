package me.mjunaid.mjpay.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Dates {

    fun dateTime(millis: Long): String =
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(millis))

    fun short(millis: Long): String =
        SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(millis))

    fun fileStamp(millis: Long): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(millis))

    fun duration(ms: Long): String {
        val minutes = ms / 60_000L
        return when {
            minutes < 1 -> "less than a minute"
            minutes < 60 -> "$minutes min"
            minutes < 60 * 24 -> "${minutes / 60} h ${minutes % 60} min"
            else -> "${minutes / (60 * 24)} d"
        }
    }
}
