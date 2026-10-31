package me.mjunaid.mjpay.net

sealed class UploadResult {
    /** 2xx: mark SENT. */
    object Success : UploadResult()

    /** 429, 5xx, timeout, no network, TLS/IO error: keep PENDING and retry. */
    data class Retry(val error: String) : UploadResult()

    /** 401/403 (auth) or other 4xx/3xx: mark FAILED, manual resend only. */
    data class Permanent(val error: String) : UploadResult()
}
