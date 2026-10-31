package me.mjunaid.mjpay.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import me.mjunaid.mjpay.R

object Ui {

    fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    fun addBanner(container: LinearLayout, text: String) {
        val context = container.context
        val view = TextView(context).apply {
            setText(text)
            setTextColor(ContextCompat.getColor(context, R.color.error))
            setBackgroundColor(ContextCompat.getColor(context, R.color.error_bg))
            textSize = 14f
            setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(context, 8) }
        }
        container.addView(view)
    }

    fun clear(container: LinearLayout) {
        container.removeAllViews()
        container.visibility = View.GONE
    }

    fun showBanners(container: LinearLayout, messages: List<String>) {
        clear(container)
        messages.forEach { addBanner(container, it) }
        container.visibility = if (messages.isEmpty()) View.GONE else View.VISIBLE
    }
}
