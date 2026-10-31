package me.mjunaid.mjpay.work

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import me.mjunaid.mjpay.BuildConfig
import me.mjunaid.mjpay.R
import me.mjunaid.mjpay.data.Db
import me.mjunaid.mjpay.data.Prefs
import me.mjunaid.mjpay.net.Payloads
import me.mjunaid.mjpay.net.UploadResult
import me.mjunaid.mjpay.net.Uploader
import me.mjunaid.mjpay.ui.MainActivity

/**
 * Single periodic worker: heartbeat, retention purge, retry re-enqueue and one
 * low-importance alert when deliveries stay pending for too long.
 */
class HeartbeatWorker(
    appContext: Context,
    params: WorkerParameters
) : Worker(appContext, params) {

    override fun doWork(): Result {
        val context = applicationContext
        val prefs = Prefs(context)
        val db = Db.get(context)
        val now = System.currentTimeMillis()

        // (b) Retention purge.
        db.purgeOlderThan(prefs.retentionDays, now)

        // (c) Re-enqueue PENDING rows older than 5 minutes.
        for (id in db.listPendingIds(now - RETRY_AFTER_MS)) {
            db.getById(id)?.let { Scheduler.enqueueDelivery(context, it.deliveryId) }
        }

        // (a) Heartbeat.
        val battery = batteryState(context)
        val payload = Payloads.heartbeat(
            ts = now,
            battery = battery.level,
            charging = battery.charging,
            pending = db.pendingCount(),
            lastSuccessAt = prefs.lastSuccessAt,
            batteryOptIgnored = isIgnoringBatteryOptimizations(context),
            appVersion = BuildConfig.VERSION_NAME
        )

        when (val result = Uploader(context).post(payload)) {
            is UploadResult.Success -> {
                prefs.lastHeartbeatAt = now
                prefs.lastHeartbeatOk = true
                prefs.lastHeartbeatError = ""
                prefs.heartbeatFailSince = 0L
            }
            is UploadResult.Retry -> recordFailure(prefs, now, result.error)
            is UploadResult.Permanent -> recordFailure(prefs, now, result.error)
        }

        // (d) One notification, no SMS content.
        updatePendingNotification(context, db.pendingInfo(now))

        return Result.success()
    }

    private fun recordFailure(prefs: Prefs, now: Long, error: String) {
        prefs.lastHeartbeatAt = now
        prefs.lastHeartbeatOk = false
        prefs.lastHeartbeatError = error
        if (prefs.heartbeatFailSince == 0L) prefs.heartbeatFailSince = now
    }

    private fun updatePendingNotification(context: Context, info: Db.PendingInfo) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val failingTooLong = info.oldestAgeMillis != null && info.oldestAgeMillis > ALERT_AFTER_MS
        if (!failingTooLong || info.count == 0) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(context.getString(R.string.notif_title, info.count))
            .setContentText(context.getString(R.string.notif_text))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    private data class Battery(val level: Int, val charging: Boolean)

    private fun batteryState(context: Context): Battery {
        val intent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ) ?: return Battery(-1, false)
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        return Battery(percent, charging)
    }

    private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    private companion object {
        const val CHANNEL_ID = "mjpay_alert"
        const val NOTIFICATION_ID = 1
        const val RETRY_AFTER_MS = 5 * 60 * 1000L
        const val ALERT_AFTER_MS = 30 * 60 * 1000L
    }
}
