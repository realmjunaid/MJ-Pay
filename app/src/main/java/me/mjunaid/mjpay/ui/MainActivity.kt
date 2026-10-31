package me.mjunaid.mjpay.ui

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import me.mjunaid.mjpay.R

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(
            TextView(this).apply {
                text = getString(R.string.app_name)
                gravity = Gravity.CENTER
            }
        )
    }
}
