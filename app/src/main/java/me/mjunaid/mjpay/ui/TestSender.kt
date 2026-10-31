package me.mjunaid.mjpay.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import me.mjunaid.mjpay.BuildConfig
import me.mjunaid.mjpay.net.Payloads
import me.mjunaid.mjpay.net.UploadResult
import me.mjunaid.mjpay.net.Uploader

/** Sends the contract's `test` payload off the main thread. */
object TestSender {

    fun send(context: Context, onResult: (UploadResult) -> Unit) {
        val appContext = context.applicationContext
        Thread {
            val result = Uploader(appContext).post(
                Payloads.test(System.currentTimeMillis(), BuildConfig.VERSION_NAME)
            )
            Handler(Looper.getMainLooper()).post { onResult(result) }
        }.start()
    }
}
