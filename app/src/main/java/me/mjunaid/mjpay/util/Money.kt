package me.mjunaid.mjpay.util

import java.util.Locale

object Money {

    /** 125000 paisa -> "Tk 1,250.00". */
    fun format(paisa: Long?): String {
        if (paisa == null) return "\u2014"
        val whole = paisa / 100
        val fraction = (paisa % 100).toInt()
        return String.format(Locale.US, "Tk %,d.%02d", whole, fraction)
    }

    /** 125000 paisa -> "1250.00", used by CSV export. */
    fun plain(paisa: Long?): String {
        if (paisa == null) return ""
        val whole = paisa / 100
        val fraction = (paisa % 100).toInt()
        return String.format(Locale.US, "%d.%02d", whole, fraction)
    }
}
