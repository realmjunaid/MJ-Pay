package me.mjunaid.mjpay

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import me.mjunaid.mjpay.ui.LockManager

/**
 * WorkManager starts on demand, not on every process start. An ordinary SMS must
 * not initialize the scheduler or check GitHub. Scheduled heartbeat and delivery
 * jobs still run: the system starts WorkManager when those jobs are due.
 */
class App : Application(), Configuration.Provider {
    override fun onCreate() {
        super.onCreate()
        LockManager.install(this)
    }

    override val workManagerConfiguration: Configuration =
        Configuration.Builder()
            .setMinimumLoggingLevel(Log.ERROR)
            .build()
}
