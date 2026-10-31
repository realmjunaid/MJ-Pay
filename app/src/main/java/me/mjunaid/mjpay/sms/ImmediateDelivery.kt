package me.mjunaid.mjpay.sms

import android.content.Context
import me.mjunaid.mjpay.BuildConfig
import me.mjunaid.mjpay.data.Db
import me.mjunaid.mjpay.data.DbLogic
import me.mjunaid.mjpay.data.Prefs
import me.mjunaid.mjpay.net.Payloads
import me.mjunaid.mjpay.net.UploadResult
import me.mjunaid.mjpay.net.Uploader
import me.mjunaid.mjpay.work.Scheduler

/**
 * One short-lived attempt to deliver right after the SMS arrives. Any non-success
 * result falls back to WorkManager, which retries with backoff.
 */
object ImmediateDelivery {

    fun attempt(stored: SmsIntake.Result.Stored, context: Context, timeoutMillis: Int = 3_000): UploadResult {
        val appContext = context.applicationContext
        val db = Db.get(appContext)
        val row = db.getById(stored.rowId) ?: return UploadResult.Retry("missing")
        if (row.status != DbLogic.STATUS_PENDING) return UploadResult.Success

        val payload = Payloads.sms(
            deliveryId = row.deliveryId,
            from = row.sender,
            body = stored.body,
            receivedAt = row.receivedAt,
            sim = row.sim,
            appVersion = BuildConfig.VERSION_NAME
        )

        val result = Uploader(appContext).post(payload, timeoutMillis)
        if (result is UploadResult.Success) {
            val at = System.currentTimeMillis()
            db.markSent(row.id, at)
            Prefs(appContext).lastSuccessAt = at
        } else {
            Scheduler.enqueueDelivery(appContext, row.deliveryId)
        }
        return result
    }
}
