package me.mjunaid.mjpay.work

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import me.mjunaid.mjpay.BuildConfig
import me.mjunaid.mjpay.data.Db
import me.mjunaid.mjpay.data.DbLogic
import me.mjunaid.mjpay.data.Prefs
import me.mjunaid.mjpay.net.Payloads
import me.mjunaid.mjpay.net.UploadResult
import me.mjunaid.mjpay.net.Uploader

/**
 * Uploads one stored delivery. Runs only while a network is available; WorkManager
 * applies exponential backoff (30 s base) on retryable results.
 */
class DeliveryWorker(
    appContext: Context,
    params: WorkerParameters
) : Worker(appContext, params) {

    override fun doWork(): Result {
        val deliveryId = inputData.getString(KEY_DELIVERY_ID) ?: return Result.success()
        val db = Db.get(applicationContext)
        val row = db.getByDeliveryId(deliveryId) ?: return Result.success()

        if (row.status == DbLogic.STATUS_SENT) return Result.success()
        if (row.status != DbLogic.STATUS_PENDING) return Result.success()

        val now = System.currentTimeMillis()
        if (now - row.receivedAt > MAX_AGE_MS) {
            db.markFailed(row.id, "expired", now)
            return Result.success()
        }

        val body = row.body
        if (body == null) {
            db.markFailed(row.id, "body", now)
            return Result.success()
        }

        val payload = Payloads.sms(
            deliveryId = row.deliveryId,
            from = row.sender,
            body = body,
            receivedAt = row.receivedAt,
            sim = row.sim,
            appVersion = BuildConfig.VERSION_NAME
        )

        return when (val result = Uploader(applicationContext).post(payload)) {
            is UploadResult.Success -> {
                val at = System.currentTimeMillis()
                db.markSent(row.id, at)
                Prefs(applicationContext).lastSuccessAt = at
                Result.success()
            }
            is UploadResult.Retry -> {
                db.incrementAttempt(row.id, result.error, System.currentTimeMillis())
                Result.retry()
            }
            is UploadResult.Permanent -> {
                val at = System.currentTimeMillis()
                db.incrementAttempt(row.id, result.error, at)
                db.markFailed(row.id, result.error, at)
                Result.success()
            }
        }
    }

    companion object {
        const val KEY_DELIVERY_ID = "delivery_id"
        private const val MAX_AGE_MS = 24 * 60 * 60 * 1000L
    }
}
