package me.mjunaid.mjpay.update

/** Compares dotted version names such as 1.0.1. A leading v is ignored. */
object VersionCompare {

    fun isNewer(latest: String, current: String): Boolean {
        val left = parts(latest)
        val right = parts(current)
        val count = maxOf(left.size, right.size)
        for (index in 0 until count) {
            val diff = left.getOrElse(index) { 0 } - right.getOrElse(index) { 0 }
            if (diff != 0) return diff > 0
        }
        return false
    }

    private fun parts(version: String): List<Int> =
        version.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore("-")
            .split(".")
            .map { it.toIntOrNull() ?: 0 }
}
