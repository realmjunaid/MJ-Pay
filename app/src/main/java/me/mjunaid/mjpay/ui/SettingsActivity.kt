package me.mjunaid.mjpay.ui

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import me.mjunaid.mjpay.BuildConfig
import me.mjunaid.mjpay.R
import me.mjunaid.mjpay.data.Prefs
import me.mjunaid.mjpay.net.UploadResult
import me.mjunaid.mjpay.net.UploadRules
import me.mjunaid.mjpay.sms.SmsFilter

class SettingsActivity : BaseActivity() {

    private lateinit var prefs: Prefs
    private lateinit var urlInput: EditText
    private lateinit var secretInput: EditText
    private lateinit var secretStatus: TextView
    private lateinit var replaceSecretButton: Button
    private lateinit var deviceIdInput: EditText
    private lateinit var allowlistInput: EditText
    private lateinit var retentionInput: EditText
    private lateinit var lockSwitch: Switch
    private lateinit var testResult: TextView
    private lateinit var sendTestButton: Button

    private var replaceSecretMode = false
    private var pendingSave = false
    private var testInFlight = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        applyEdgeToEdge(findViewById(R.id.root))

        prefs = Prefs(this)
        urlInput = findViewById(R.id.urlInput)
        secretInput = findViewById(R.id.secretInput)
        secretStatus = findViewById(R.id.secretStatus)
        replaceSecretButton = findViewById(R.id.btnReplaceSecret)
        deviceIdInput = findViewById(R.id.deviceIdInput)
        allowlistInput = findViewById(R.id.allowlistInput)
        retentionInput = findViewById(R.id.retentionInput)
        lockSwitch = findViewById(R.id.switchLock)
        testResult = findViewById(R.id.testResult)
        sendTestButton = findViewById(R.id.btnSendTest)

        urlInput.setText(prefs.webhookUrl)
        deviceIdInput.setText(prefs.deviceId)
        allowlistInput.setText(prefs.senderAllowlist)
        retentionInput.setText(prefs.retentionDays.toString())
        lockSwitch.isChecked = prefs.lockEnabled
        refreshSecretUi()

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
        replaceSecretButton.setOnClickListener {
            replaceSecretMode = true
            secretInput.setText("")
            refreshSecretUi()
            secretInput.requestFocus()
            Toast.makeText(this, R.string.secret_replaced, Toast.LENGTH_SHORT).show()
        }
        lockSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked && !hasDeviceCredential()) {
                lockSwitch.isChecked = false
                Toast.makeText(this, R.string.lock_unavailable, Toast.LENGTH_LONG).show()
            }
        }
        findViewById<Button>(R.id.btnSave).setOnClickListener { save() }
        sendTestButton.setOnClickListener { sendTest() }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_SAVE) {
            val shouldSave = pendingSave
            pendingSave = false
            if (resultCode == RESULT_OK && shouldSave) performSave()
        }
    }

    private fun save() {
        if (prefs.lockEnabled && !LockManager.isSessionUnlocked(this)) {
            pendingSave = true
            LockManager.authenticate(this, REQ_SAVE)
            return
        }
        performSave()
    }

    private fun performSave() {
        val url = urlInput.text.toString().trim()
        if (!UploadRules.isAllowedUrl(url, allowDebugCleartext = BuildConfig.DEBUG)) {
            Toast.makeText(this, R.string.invalid_url, Toast.LENGTH_LONG).show()
            return
        }

        val secret = secretInput.text.toString()
        val needsSecret = replaceSecretMode || !prefs.secretStore.hasSecret()
        if (needsSecret && secret.isBlank()) {
            Toast.makeText(this, R.string.invalid_secret, Toast.LENGTH_LONG).show()
            return
        }

        val allowlist = SmsFilter.parseAllowlist(allowlistInput.text.toString())
        if (allowlist.isEmpty()) {
            Toast.makeText(this, R.string.invalid_allowlist, Toast.LENGTH_LONG).show()
            return
        }

        val retention = retentionInput.text.toString().toIntOrNull()
        if (retention == null || retention !in 1..365) {
            Toast.makeText(this, R.string.invalid_retention, Toast.LENGTH_LONG).show()
            return
        }

        if (lockSwitch.isChecked && !hasDeviceCredential()) {
            Toast.makeText(this, R.string.lock_unavailable, Toast.LENGTH_LONG).show()
            lockSwitch.isChecked = false
            return
        }

        prefs.webhookUrl = url
        prefs.deviceId = deviceIdInput.text.toString()
        prefs.senderAllowlist = allowlist.joinToString(",")
        prefs.retentionDays = retention
        if (needsSecret) {
            prefs.secretStore.save(secret)
        }
        LockManager.setEnabled(this, lockSwitch.isChecked)

        replaceSecretMode = false
        secretInput.setText("")
        refreshSecretUi()
        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
    }

    private fun sendTest() {
        if (testInFlight) return
        testInFlight = true
        sendTestButton.isEnabled = false
        testResult.visibility = View.VISIBLE
        testResult.text = getString(R.string.sending)

        TestSender.send(this) { result ->
            if (isFinishing || isDestroyed) return@send
            testInFlight = false
            sendTestButton.isEnabled = true
            testResult.text = when (result) {
                is UploadResult.Success -> getString(R.string.test_success)
                is UploadResult.Retry -> getString(R.string.test_failed, ErrorText.of(this, result.error))
                is UploadResult.Permanent -> getString(R.string.test_failed, ErrorText.of(this, result.error))
            }
        }
    }

    private fun refreshSecretUi() {
        val hasSecret = prefs.secretStore.hasSecret()
        when {
            hasSecret && !replaceSecretMode -> {
                secretStatus.text = getString(R.string.secret_is_set)
                secretInput.visibility = View.GONE
                replaceSecretButton.visibility = View.VISIBLE
            }
            hasSecret -> {
                secretStatus.text = getString(R.string.secret_is_set)
                secretInput.visibility = View.VISIBLE
                replaceSecretButton.visibility = View.GONE
            }
            else -> {
                secretStatus.text = getString(R.string.secret_not_set)
                secretInput.visibility = View.VISIBLE
                replaceSecretButton.visibility = View.GONE
            }
        }
    }

    private fun hasDeviceCredential(): Boolean {
        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return keyguard?.isDeviceSecure == true
    }

    private companion object {
        const val REQ_SAVE = 3001
    }
}
