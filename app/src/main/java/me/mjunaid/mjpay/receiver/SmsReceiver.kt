package me.mjunaid.mjpay.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import me.mjunaid.mjpay.sms.ImmediateDelivery
import me.mjunaid.mjpay.sms.SmsIntake
import me.mjunaid.mjpay.work.Scheduler
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Event-driven SMS intake. Only payment-received messages pass the filter; everything
 * else returns immediately without storage, logging or network use.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        val sender = messages.firstOrNull { !it.originatingAddress.isNullOrBlank() }
            ?.originatingAddress
            ?: ""
        val body = SmsIntake.joinMultipart(messages.map { it.messageBody ?: "" })
        val receivedAt = messages.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()
        val sim = intent.getIntExtra(EXTRA_SUBSCRIPTION, -1).takeIf { it >= 0 }

        val appContext = context.applicationContext
        val stored = when (val result = SmsIntake.accept(appContext, sender, body, receivedAt, sim)) {
            is SmsIntake.Result.Dropped -> return
            SmsIntake.Result.Duplicate -> return
            is SmsIntake.Result.Stored -> result
        }

        val pendingResult = goAsync()
        val finished = AtomicBoolean(false)

        // Immediate attempt on a short-lived thread, hard-stopped after ~8 seconds.
        Thread {
            try {
                ImmediateDelivery.attempt(stored, appContext)
            } catch (e: Exception) {
                Scheduler.enqueueDelivery(appContext, stored.deliveryId)
            } finally {
                if (finished.compareAndSet(false, true)) pendingResult.finish()
            }
        }.start()

        Handler(Looper.getMainLooper()).postDelayed({
            if (finished.compareAndSet(false, true)) {
                Scheduler.enqueueDelivery(appContext, stored.deliveryId)
                pendingResult.finish()
            }
        }, HARD_STOP_MS)
    }

    private companion object {
        const val EXTRA_SUBSCRIPTION = "subscription"
        const val HARD_STOP_MS = 8_000L
    }
}
