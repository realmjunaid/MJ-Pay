package me.mjunaid.mjpay.sms

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Best-effort parse for the UI only. The server does the authoritative parsing.
 * Never throws: a parse failure returns nulls and forwarding continues.
 */
object SmsParserLite {

    const val METHOD_BKASH = "bKash"
    const val METHOD_NAGAD = "Nagad"
    const val METHOD_OTHER = "Other"

    data class Parsed(
        val amountPaisa: Long?,
        val trxId: String?,
        val method: String
    )

    private val AMOUNT_WITH_GROUP = Regex(
        """(?i)\b(?:tk|bdt|taka)\.?\s*:?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)"""
    )

    private val TRX_ID = Regex(
        """(?i)\b(?:trx\s*id|trxid|txn\s*id|txnid)\s*:?\s*([A-Za-z0-9]{4,})"""
    )

    fun parse(sender: String, body: String): Parsed = Parsed(
        amountPaisa = amountPaisa(body),
        trxId = trxId(body),
        method = method(sender)
    )

    fun amountPaisa(body: String): Long? = try {
        val raw = AMOUNT_WITH_GROUP.find(body)?.groupValues?.getOrNull(1) ?: return null
        val number = BigDecimal(raw.replace(",", ""))
        number.multiply(BigDecimal(100))
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()
    } catch (e: Exception) {
        null
    }

    fun trxId(body: String): String? = try {
        TRX_ID.find(body)?.groupValues?.getOrNull(1)
    } catch (e: Exception) {
        null
    }

    fun method(sender: String): String {
        val s = sender.lowercase()
        return when {
            s.contains("bkash") -> METHOD_BKASH
            s.contains("nagad") -> METHOD_NAGAD
            else -> METHOD_OTHER
        }
    }
}
