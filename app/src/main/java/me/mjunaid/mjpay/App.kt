package me.mjunaid.mjpay

import android.app.Application
import me.mjunaid.mjpay.ui.LockManager

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        LockManager.install(this)
    }
}
