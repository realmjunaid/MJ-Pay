package me.mjunaid.mjpay.security

import android.content.Context
import android.content.SharedPreferences

/**
 * Stores the webhook shared secret encrypted with [KeyStoreCrypto].
 * The plaintext is never rendered in the UI after it is saved.
 */
class SecretStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(secret: String) {
        require(secret.isNotEmpty()) { "secret must not be empty" }
        prefs.edit().putString(KEY_SECRET, KeyStoreCrypto.encrypt(secret)).apply()
    }

    fun load(): String? {
        val encoded = prefs.getString(KEY_SECRET, null) ?: return null
        return try {
            KeyStoreCrypto.decrypt(encoded)
        } catch (e: Exception) {
            // Key invalidated or data corrupt: treat as "no usable secret".
            null
        }
    }

    fun hasSecret(): Boolean = !prefs.getString(KEY_SECRET, null).isNullOrEmpty()

    fun clear() {
        prefs.edit().remove(KEY_SECRET).apply()
    }

    private companion object {
        const val PREFS_NAME = "mjpay_secret"
        const val KEY_SECRET = "webhook_secret"
    }
}
