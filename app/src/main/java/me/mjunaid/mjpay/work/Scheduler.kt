package me.mjunaid.mjpay.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object Scheduler {

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
}
