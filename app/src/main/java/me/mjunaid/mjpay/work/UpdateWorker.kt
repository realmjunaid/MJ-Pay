package me.mjunaid.mjpay.work

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import me.mjunaid.mjpay.BuildConfig
import me.mjunaid.mjpay.update.UpdateChecker
import me.mjunaid.mjpay.update.UpdateNotifier

/** Checks GitHub for a newer release and notifies once. Does not download. */
class UpdateWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        return try {
            val newer = UpdateChecker.findNewer(BuildConfig.VERSION_NAME)
            if (newer != null) {
                UpdateNotifier.rememberAvailable(applicationContext, newer.version)
                UpdateNotifier.notifyOnce(applicationContext, newer.version)
            }
            Result.success()
        } catch (e: Exception) {
            // A failed check waits for the next 12-hour run. Retrying would wake the phone again.
            Result.success()
        }
    }
}
