package me.mjunaid.mjpay.ui

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager

/**
 * Base for every screen: blocks screenshots/recents snapshots and applies
 * edge-to-edge window insets for the latest target SDK.
 */
open class BaseActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        super.onCreate(savedInstanceState)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        LockManager.onActivityResult(this, requestCode, resultCode)
    }

    protected fun applyEdgeToEdge(root: View) {
        val baseLeft = root.paddingLeft
        val baseTop = root.paddingTop
        val baseRight = root.paddingRight
        val baseBottom = root.paddingBottom

        root.setOnApplyWindowInsetsListener { view, insets ->
            val left: Int
            val top: Int
            val right: Int
            val bottom: Int
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(
                    WindowInsets.Type.systemBars() or
                        WindowInsets.Type.ime() or
                        WindowInsets.Type.displayCutout()
                )
                left = bars.left
                top = bars.top
                right = bars.right
                bottom = bars.bottom
            } else {
                @Suppress("DEPRECATION")
                left = insets.systemWindowInsetLeft
                @Suppress("DEPRECATION")
                top = insets.systemWindowInsetTop
                @Suppress("DEPRECATION")
                right = insets.systemWindowInsetRight
                @Suppress("DEPRECATION")
                bottom = insets.systemWindowInsetBottom
            }
            view.setPadding(
                baseLeft + left,
                baseTop + top,
                baseRight + right,
                baseBottom + bottom
            )
            insets
        }
        root.requestApplyInsets()
    }
}
