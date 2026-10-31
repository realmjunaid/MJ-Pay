package me.mjunaid.mjpay

import android.app.Application
import me.mjunaid.mjpay.ui.LockManager
import me.mjunaid.mjpay.work.Scheduler

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        LockManager.install(this)
        Scheduler.scheduleUpdateCheck(this)
    }
}
