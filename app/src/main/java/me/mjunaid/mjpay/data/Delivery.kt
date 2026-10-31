package me.mjunaid.mjpay.data

/** One delivery row. [body] is only populated by [Db.getById]. */
data class Delivery(
    val id: Long,
    val deliveryId: String,
    val receivedAt: Long,
    val sender: String,
    val method: String,
    val trxId: String?,
    val amountPaisa: Long?,
    val status: String,
    val attempts: Int,
    val lastError: String?,
    val lastAttemptAt: Long?,
    val sim: Int?,
    val body: String? = null
)
