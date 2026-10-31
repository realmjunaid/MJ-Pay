package me.mjunaid.mjpay.data

import android.content.Context
import android.content.SharedPreferences
import me.mjunaid.mjpay.security.SecretStore
import me.mjunaid.mjpay.sms.SmsFilter

/**
 * Non-secret settings. The webhook secret lives in [SecretStore] (encrypted).
 */
class Prefs(context: Context) {

    private val appContext = context.applicationContext
    private val sp: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val secretStore: SecretStore get() = SecretStore(appContext)

    var webhookUrl: String
        get() = sp.getString(KEY_WEBHOOK_URL, "").orEmpty()
        set(value) = sp.edit().putString(KEY_WEBHOOK_URL, value.trim()).apply()

    var deviceId: String
        get() = sp.getString(KEY_DEVICE_ID, DEFAULT_DEVICE_ID).orEmpty().ifBlank { DEFAULT_DEVICE_ID }
        set(value) = sp.edit().putString(KEY_DEVICE_ID, value.trim().ifBlank { DEFAULT_DEVICE_ID }).apply()

    var senderAllowlist: String
        get() = sp.getString(KEY_ALLOWLIST, SmsFilter.DEFAULT_ALLOWLIST).orEmpty()
        set(value) = sp.edit().putString(KEY_ALLOWLIST, value.trim()).apply()

    var retentionDays: Int
        get() = sp.getInt(KEY_RETENTION_DAYS, DEFAULT_RETENTION_DAYS)
        set(value) = sp.edit().putInt(KEY_RETENTION_DAYS, value).apply()

    var lockEnabled: Boolean
        get() = sp.getBoolean(KEY_LOCK_ENABLED, false)
        set(value) = sp.edit().putBoolean(KEY_LOCK_ENABLED, value).apply()

    var lastSuccessAt: Long
        get() = sp.getLong(KEY_LAST_SUCCESS_AT, 0L)
        set(value) = sp.edit().putLong(KEY_LAST_SUCCESS_AT, value).apply()

    var lastHeartbeatAt: Long
        get() = sp.getLong(KEY_LAST_HEARTBEAT_AT, 0L)
        set(value) = sp.edit().putLong(KEY_LAST_HEARTBEAT_AT, value).apply()

    /** null = heartbeat has never run, true = last run OK, false = last run failed. */
    var lastHeartbeatOk: Boolean?
        get() = when (sp.getInt(KEY_LAST_HEARTBEAT_OK, -1)) {
            1 -> true
            0 -> false
            else -> null
        }
        set(value) = sp.edit().putInt(KEY_LAST_HEARTBEAT_OK, when (value) {
            true -> 1
            false -> 0
            null -> -1
        }).apply()

    var lastHeartbeatError: String
        get() = sp.getString(KEY_LAST_HEARTBEAT_ERROR, "").orEmpty()
        set(value) = sp.edit().putString(KEY_LAST_HEARTBEAT_ERROR, value ?: "").apply()

    /** Epoch millis when the current run of heartbeat failures started, or 0. */
    var heartbeatFailSince: Long
        get() = sp.getLong(KEY_HEARTBEAT_FAIL_SINCE, 0L)
        set(value) = sp.edit().putLong(KEY_HEARTBEAT_FAIL_SINCE, value).apply()

    fun allowlist(): List<String> = SmsFilter.parseAllowlist(senderAllowlist)

    fun isConfigured(): Boolean = webhookUrl.isNotBlank() && secretStore.hasSecret()

    companion object {
        const val DEFAULT_DEVICE_ID = "phone-1"
        const val DEFAULT_RETENTION_DAYS = 30

        private const val PREFS_NAME = "mjpay_settings"
        private const val KEY_WEBHOOK_URL = "webhook_url"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_ALLOWLIST = "sender_allowlist"
        private const val KEY_RETENTION_DAYS = "retention_days"
        private const val KEY_LOCK_ENABLED = "lock_enabled"
        private const val KEY_LAST_SUCCESS_AT = "last_success_at"
        private const val KEY_LAST_HEARTBEAT_AT = "last_heartbeat_at"
        private const val KEY_LAST_HEARTBEAT_OK = "last_heartbeat_ok"
        private const val KEY_LAST_HEARTBEAT_ERROR = "last_heartbeat_error"
        private const val KEY_HEARTBEAT_FAIL_SINCE = "heartbeat_fail_since"
    }
}
