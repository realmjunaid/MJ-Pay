package me.mjunaid.mjpay.net

import org.json.JSONObject

/**
 * Builds the exact UTF-8 JSON strings that are signed and sent.
 * Callers must sign the returned string unchanged.
 */
object Payloads {

    fun sms(
        deliveryId: String,
        from: String,
        body: String,
        receivedAt: Long,
        sim: Int?,
        appVersion: String
    ): String = JSONObject().apply {
        put("type", "sms")
        put("delivery_id", deliveryId)
        put("from", from)
        put("body", body)
        put("received_at", receivedAt)
        if (sim == null) put("sim", JSONObject.NULL) else put("sim", sim)
        put("app_version", appVersion)
    }.toString()

    fun heartbeat(
        ts: Long,
        battery: Int,
        charging: Boolean,
        pending: Int,
        lastSuccessAt: Long,
        batteryOptIgnored: Boolean,
        appVersion: String
    ): String = JSONObject().apply {
        put("type", "heartbeat")
        put("ts", ts)
        put("battery", battery)
        put("charging", charging)
        put("pending", pending)
        put("last_success_at", lastSuccessAt)
        put("battery_opt_ignored", batteryOptIgnored)
        put("app_version", appVersion)
    }.toString()

    fun test(ts: Long, appVersion: String): String = JSONObject().apply {
        put("type", "test")
        put("ts", ts)
        put("app_version", appVersion)
    }.toString()
}
