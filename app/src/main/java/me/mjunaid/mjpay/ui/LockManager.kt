package me.mjunaid.mjpay.ui

import android.app.Activity
import android.app.Application
import android.app.KeyguardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import me.mjunaid.mjpay.R
import me.mjunaid.mjpay.data.Prefs
import java.lang.ref.WeakReference

/**
 * Device-credential app lock. Prompts on cold start and again after the app has been
 * in the background for more than 60 seconds. SMS intake and delivery never use this.
 */
object LockManager {

    const val REQUEST_UNLOCK = 9001
    private const val GRACE_MS = 60_000L

    private var sessionUnlocked = false
    private var promptInFlight = false
    private var foregroundCount = 0
    private var backgroundedAt = 0L
    private var suppressedActivity: WeakReference<Activity>? = null

    fun install(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                onResumed(activity)
            }

            override fun onActivityStopped(activity: Activity) {
                foregroundCount = (foregroundCount - 1).coerceAtLeast(0)
                if (foregroundCount == 0) {
                    backgroundedAt = SystemClock.elapsedRealtime()
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) {
                foregroundCount++
            }

            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    fun isEnabled(context: Context): Boolean = Prefs(context).lockEnabled

    fun isSessionUnlocked(context: Context): Boolean =
        !isEnabled(context) || sessionUnlocked

    /** Call when the lock switch is turned on or off in settings. */
    fun setEnabled(context: Context, enabled: Boolean) {
        Prefs(context).lockEnabled = enabled
        if (enabled) {
            sessionUnlocked = true
            backgroundedAt = SystemClock.elapsedRealtime()
        } else {
            sessionUnlocked = false
        }
    }

    /** Used before export and before saving settings while the lock is on. */
    fun authenticate(activity: Activity, requestCode: Int) {
        if (promptInFlight) return
        promptInFlight = true
        launchCredentialPrompt(activity, requestCode)
    }

    /**
     * Must be called from [BaseActivity.onActivityResult].
     * Returns true when this was the app-lock request.
     */
    fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int): Boolean {
        if (requestCode == REQUEST_UNLOCK) {
            promptInFlight = false
            if (resultCode == Activity.RESULT_OK) {
                sessionUnlocked = true
                backgroundedAt = SystemClock.elapsedRealtime()
            } else {
                sessionUnlocked = false
                suppressedActivity = WeakReference(activity)
                activity.finish()
            }
            return true
        }
        if (promptInFlight) promptInFlight = false
        return false
    }

    private fun onResumed(activity: Activity) {
        if (!isEnabled(activity)) return
        if (suppressedActivity?.get() === activity) return
        if (promptInFlight) return

        val elapsed = SystemClock.elapsedRealtime() - backgroundedAt
        val withinGrace = sessionUnlocked && backgroundedAt > 0 && elapsed <= GRACE_MS
        if (withinGrace) return

        promptInFlight = true
        Handler(Looper.getMainLooper()).post {
            if (!isEnabled(activity) || activity.isFinishing) {
                promptInFlight = false
                return@post
            }
            launchCredentialPrompt(activity, REQUEST_UNLOCK)
        }
    }

    private fun launchCredentialPrompt(activity: Activity, requestCode: Int) {
        val keyguard = activity.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val intent = keyguard?.createConfirmDeviceCredentialIntent(
            activity.getString(R.string.unlock_title),
            null
        )
        if (intent == null) {
            // No secure screen lock: never lock the user out of the app.
            promptInFlight = false
            Prefs(activity).lockEnabled = false
            Toast.makeText(activity, R.string.lock_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        try {
            activity.startActivityForResult(intent, requestCode)
        } catch (e: Exception) {
            promptInFlight = false
            Prefs(activity).lockEnabled = false
            Toast.makeText(activity, R.string.lock_unavailable, Toast.LENGTH_LONG).show()
        }
    }
}
