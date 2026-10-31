package me.mjunaid.mjpay.sms

/**
 * Pure Kotlin SMS filter. Every keyword and pattern of the app lives here so rules
 * are easy to adjust. Dropped messages are never stored, logged or forwarded.
 */
object SmsFilter {

    const val DEFAULT_ALLOWLIST = "bKash,NAGAD"

    sealed class Result {
        object Accept : Result()
        data class Drop(val reason: String) : Result()
    }

    /** Short words need word boundaries so "shopping" is not treated as "pin". */
    private val SHORT_BLOCKED = listOf("otp", "pin")
    private val PHRASE_BLOCKED = listOf(
        "verification code",
        "password",
        "one time",
        "one-time"
    )
    private val SHORT_BLOCKED_PATTERNS = SHORT_BLOCKED.map {
        Regex("(?i)\\b" + Regex.escape(it) + "\\b")
    }

    /**
     * Accepts Tk 1,250.00 / Tk1250 / Amount: Tk 100.
     * Optional space after the currency token, optional colon, thousands commas,
     * optional one or two decimal places.
     */
    val AMOUNT_REGEX = Regex(
        """(?i)\b(?:tk|bdt|taka)\.?\s*:?\s*[0-9][0-9,]*(?:\.[0-9]{1,2})?"""
    )

    fun evaluate(sender: String, body: String, allowlist: List<String> = parseAllowlist(DEFAULT_ALLOWLIST)): Result {
        val trimmedSender = sender.trim()
        if (allowlist.none { it.trim().equals(trimmedSender, ignoreCase = true) }) {
            return Result.Drop("sender")
        }
        if (body.isBlank()) return Result.Drop("empty")

        val lower = body.lowercase()
        if (SHORT_BLOCKED_PATTERNS.any { it.containsMatchIn(body) }) {
            return Result.Drop("blocked_word")
        }
        if (PHRASE_BLOCKED.any { lower.contains(it) }) {
            return Result.Drop("blocked_word")
        }

        if (!lower.contains("received")) return Result.Drop("no_received_keyword")
        if (!AMOUNT_REGEX.containsMatchIn(body)) return Result.Drop("no_amount")

        return Result.Accept
    }

    fun parseAllowlist(raw: String): List<String> =
        raw.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
}
