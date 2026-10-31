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
}
