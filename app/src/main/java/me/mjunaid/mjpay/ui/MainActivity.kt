package me.mjunaid.mjpay.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import me.mjunaid.mjpay.BuildConfig
import me.mjunaid.mjpay.R
import me.mjunaid.mjpay.data.Db
import me.mjunaid.mjpay.data.Prefs
import me.mjunaid.mjpay.net.Payloads
import me.mjunaid.mjpay.net.UploadResult
import me.mjunaid.mjpay.net.Uploader
import me.mjunaid.mjpay.util.Dates
import me.mjunaid.mjpay.util.Money
import me.mjunaid.mjpay.work.Scheduler

class MainActivity : BaseActivity() {

    private lateinit var prefs: Prefs
    private lateinit var bannerContainer: LinearLayout
    private lateinit var testResult: TextView
    private lateinit var sendTestButton: Button
    private var testInFlight = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        applyEdgeToEdge(findViewById(R.id.root))

        prefs = Prefs(this)
        bannerContainer = findViewById(R.id.bannerContainer)
        testResult = findViewById(R.id.testResult)
        sendTestButton = findViewById(R.id.btnSendTest)

        findViewById<Button>(R.id.rowSmsAction).text = getString(R.string.grant)
        findViewById<Button>(R.id.rowSmsAction).setOnClickListener { requestSmsPermission() }
        findViewById<Button>(R.id.rowBatteryAction).setOnClickListener { openBatterySettings() }
        findViewById<Button>(R.id.rowNotifAction).setOnClickListener { requestNotificationPermission() }
        findViewById<Button>(R.id.btnHistory).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        sendTestButton.setOnClickListener { sendTest() }

        if (BuildConfig.DEBUG) {
            // Debug-only shortcut, removed from release by R8: long-press the title.
            findViewById<TextView>(R.id.title).setOnLongClickListener {
                try {
                    startActivity(
                        Intent(
                            this,
                            Class.forName("me.mjunaid.mjpay.debug.SimulateSmsActivity")
                        )
                    )
                    true
                } catch (e: Exception) {
                    false
                }
            }
        }

        Scheduler.scheduleHeartbeat(this)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }

    private fun refresh() {
        val db = Db.get(this)
        val now = System.currentTimeMillis()

        val smsGranted = isGranted(Manifest.permission.RECEIVE_SMS)
        setRow(
            valueId = R.id.rowSmsValue,
            actionId = R.id.rowSmsAction,
            value = getString(if (smsGranted) R.string.allowed else R.string.not_allowed),
            actionVisible = !smsGranted
        )

        val batteryIgnored = isIgnoringBatteryOptimizations()
        setRow(
            valueId = R.id.rowBatteryValue,
            actionId = R.id.rowBatteryAction,
            value = getString(if (batteryIgnored) R.string.unrestricted else R.string.restricted),
            actionVisible = !batteryIgnored
        )

        val notificationsAllowed = notificationsAllowed()
        setRow(
            valueId = R.id.rowNotifValue,
            actionId = R.id.rowNotifAction,
            value = getString(if (notificationsAllowed) R.string.allowed else R.string.not_allowed),
            actionVisible = !notificationsAllowed
        )

        findViewById<TextView>(R.id.rowLastSuccessValue).text =
            if (prefs.lastSuccessAt == 0L) getString(R.string.never)
            else Dates.short(prefs.lastSuccessAt)

        findViewById<TextView>(R.id.rowHeartbeatValue).text = heartbeatText()

        val pending = db.pendingCount()
        findViewById<TextView>(R.id.rowPendingValue).text = pending.toString()

        val stats = db.todayStats(now)
        findViewById<TextView>(R.id.todayCountValue).text = stats.count.toString()
        findViewById<TextView>(R.id.todayTotalValue).text = Money.format(stats.totalPaisa)

        val banners = ArrayList<String>()
        if (!smsGranted) banners.add(getString(R.string.banner_sms))
        if (!prefs.isConfigured()) banners.add(getString(R.string.banner_config))
        if (prefs.allowlist().isEmpty()) banners.add(getString(R.string.banner_empty_allowlist))
        if (heartbeatFailingTooLong(now)) banners.add(getString(R.string.banner_heartbeat))
        Ui.showBanners(bannerContainer, banners)
    }

    private fun heartbeatText(): String {
        if (prefs.lastHeartbeatAt == 0L) return getString(R.string.not_scheduled)
        val whenText = Dates.short(prefs.lastHeartbeatAt)
        return when (prefs.lastHeartbeatOk) {
            true -> "$whenText \u00b7 ${getString(R.string.ok)}"
            false -> "$whenText \u00b7 ${getString(R.string.failed)} (${
                ErrorText.of(this, prefs.lastHeartbeatError)
            })"
            null -> whenText
        }
    }

    private fun heartbeatFailingTooLong(now: Long): Boolean {
        val since = prefs.heartbeatFailSince
        return prefs.lastHeartbeatOk == false && since > 0 && now - since > 45 * 60 * 1000L
    }

    private fun setRow(valueId: Int, actionId: Int, value: String, actionVisible: Boolean) {
        findViewById<TextView>(valueId).text = value
        findViewById<Button>(actionId).visibility = if (actionVisible) View.VISIBLE else View.GONE
    }

    private fun isGranted(permission: String): Boolean =
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun notificationsAllowed(): Boolean =
        Build.VERSION.SDK_INT < 33 || isGranted(Manifest.permission.POST_NOTIFICATIONS)

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val power = getSystemService(POWER_SERVICE) as? PowerManager ?: return false
        return power.isIgnoringBatteryOptimizations(packageName)
    }

    private fun requestSmsPermission() {
        requestPermissions(arrayOf(Manifest.permission.RECEIVE_SMS), REQ_SMS)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATIONS)
        } else {
            refresh()
        }
    }

    private fun openBatterySettings() {
        try {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (e: Exception) {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (ignored: Exception) {
                Toast.makeText(this, R.string.save_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendTest() {
        if (testInFlight) return
        testInFlight = true
        sendTestButton.isEnabled = false
        testResult.visibility = View.VISIBLE
        testResult.text = getString(R.string.sending)

        val appContext = applicationContext
        Thread {
            val result = Uploader(appContext).post(
                Payloads.test(System.currentTimeMillis(), BuildConfig.VERSION_NAME)
            )
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                testInFlight = false
                sendTestButton.isEnabled = true
                testResult.text = when (result) {
                    is UploadResult.Success -> getString(R.string.test_success)
                    is UploadResult.Retry -> getString(R.string.test_failed, ErrorText.of(this, result.error))
                    is UploadResult.Permanent -> getString(R.string.test_failed, ErrorText.of(this, result.error))
                }
                refresh()
            }
        }.start()
    }

    private companion object {
        const val REQ_SMS = 1001
        const val REQ_NOTIFICATIONS = 1002
    }
}
