package me.mjunaid.mjpay.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object Scheduler {

    const val HEARTBEAT_WORK_NAME = "heartbeat"

    fun enqueueDelivery(context: Context, deliveryId: String) {
        val request = OneTimeWorkRequest.Builder(DeliveryWorker::class.java)
            .setInputData(
                Data.Builder()
                    .putString(DeliveryWorker.KEY_DELIVERY_ID, deliveryId)
                    .build()
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(workName(deliveryId), ExistingWorkPolicy.KEEP, request)
    }

    fun workName(deliveryId: String): String = "deliver-$deliveryId"

    /**
     * One periodic heartbeat every 15 minutes (5 minute flex), restored by WorkManager
     * after reboot. KEEP means an already scheduled heartbeat is left untouched.
     */
    fun scheduleHeartbeat(context: Context) {
        val request = PeriodicWorkRequest.Builder(
            HeartbeatWorker::class.java,
            15,
            TimeUnit.MINUTES,
            5,
            TimeUnit.MINUTES
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(HEARTBEAT_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    const val UPDATE_WORK_NAME = "app-update-check"
    private const val UPDATE_NOW_WORK_NAME = "app-update-now"
    private const val UPDATE_PREFS = "mjpay_updates"
    private const val KEY_SCHEDULE_REV = "schedule_rev"
    private const val SCHEDULE_REV = 2

    /**
     * One update check per day, and only after the user has opened the app.
     * Do not call this from [android.app.Application.onCreate]: an SMS wake must
     * not touch WorkManager or GitHub. Settings still checks when the user asks.
     */
    fun ensureUpdateCheck(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_SCHEDULE_REV, 0) >= SCHEDULE_REV) return

        val request = PeriodicWorkRequest.Builder(
            UpdateWorker::class.java,
            24,
            TimeUnit.HOURS
        )
            .setInitialDelay(24, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()

        val workManager = WorkManager.getInstance(appContext)
        workManager.enqueueUniquePeriodicWork(
            UPDATE_WORK_NAME,
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            request
        )
        workManager.cancelUniqueWork(UPDATE_NOW_WORK_NAME)
        prefs.edit().putInt(KEY_SCHEDULE_REV, SCHEDULE_REV).apply()
    }
}
