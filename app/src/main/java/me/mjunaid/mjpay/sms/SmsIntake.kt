package me.mjunaid.mjpay.sms

import android.content.Context
import me.mjunaid.mjpay.data.Db
import me.mjunaid.mjpay.data.Prefs
import java.util.UUID

/**
 * Shared SMS pipeline used by the receiver and the debug simulation screen.
 * A message is stored before any network call; dropped messages touch nothing.
 */
object SmsIntake {

    sealed class Result {
        /** Never stored, never logged, never sent. */
        data class Dropped(val reason: String) : Result()

        /** Same sender + body + SMS timestamp was already stored. */
        object Duplicate : Result()

        data class Stored(
            val rowId: Long,
            val deliveryId: String,
            val sender: String,
            val body: String,
            val receivedAt: Long,
            val sim: Int?
        ) : Result()
    }

    fun accept(
        context: Context,
        sender: String,
        body: String,
        receivedAt: Long,
        sim: Int?
    ): Result {
        val prefs = Prefs(context)
        when (SmsFilter.evaluate(sender, body, prefs.allowlist())) {
            is SmsFilter.Result.Drop -> return Result.Dropped(
                (SmsFilter.evaluate(sender, body, prefs.allowlist()) as SmsFilter.Result.Drop).reason
            )
            SmsFilter.Result.Accept -> Unit
        }

        val parsed = SmsParserLite.parse(sender, body)
        val deliveryId = UUID.randomUUID().toString()
        val rowId = Db.get(context).insertPending(
            deliveryId = deliveryId,
            receivedAt = receivedAt,
            sender = sender,
            method = parsed.method,
            body = body,
            trxId = parsed.trxId,
            amountPaisa = parsed.amountPaisa,
            sim = sim
        ) ?: return Result.Duplicate

        return Result.Stored(rowId, deliveryId, sender, body, receivedAt, sim)
    }

    fun joinMultipart(parts: List<String>): String = parts.joinToString("")
}
