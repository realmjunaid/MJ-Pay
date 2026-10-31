package me.mjunaid.mjpay.util

/**
 * CSV escaping plus formula-injection neutralization. Pure Kotlin, unit tested.
 */
object Csv {

    private val DANGEROUS_PREFIXES = charArrayOf('=', '+', '-', '@', '\t', '\r')

    fun escapeCell(value: String): String {
        val neutralized = if (value.isNotEmpty() && value[0] in DANGEROUS_PREFIXES) {
            "'$value"
        } else {
            value
        }
        val mustQuote = neutralized.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (mustQuote) {
            "\"" + neutralized.replace("\"", "\"\"") + "\""
        } else {
            neutralized
        }
    }

    fun row(cells: List<String>): String = cells.joinToString(",") { escapeCell(it) }

    fun row(vararg cells: String): String = row(cells.toList())
}
