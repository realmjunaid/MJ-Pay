package me.mjunaid.mjpay.data

import java.util.Calendar
import java.util.TimeZone

/**
 * Pure helpers behind [Db] so query construction and day boundaries can be unit tested
 * on the JVM without an emulator.
 */
object DbLogic {

    const val STATUS_PENDING = "PENDING"
    const val STATUS_SENT = "SENT"
    const val STATUS_FAILED = "FAILED"

    fun buildFilter(
        status: String?,
        method: String?,
        trxQuery: String?
    ): Pair<String?, Array<String>?> {
        val clauses = ArrayList<String>(3)
        val args = ArrayList<String>(3)

        if (!status.isNullOrBlank()) {
            clauses.add("status = ?")
            args.add(status)
        }
        if (!method.isNullOrBlank()) {
            clauses.add("method = ?")
            args.add(method)
        }
        if (!trxQuery.isNullOrBlank()) {
            clauses.add("trx_id LIKE ? ESCAPE '\\'")
            args.add("%" + escapeLike(trxQuery.trim()) + "%")
        }

        return if (clauses.isEmpty()) {
            null to null
        } else {
            clauses.joinToString(" AND ") to args.toTypedArray()
        }
    }

    fun escapeLike(value: String): String = buildString(value.length) {
        for (char in value) {
            when (char) {
                '\\', '%', '_' -> {
                    append('\\')
                    append(char)
                }
                else -> append(char)
            }
        }
    }

    fun startOfLocalDay(nowMillis: Long, timeZone: TimeZone = TimeZone.getDefault()): Long {
        val calendar = Calendar.getInstance(timeZone)
        calendar.timeInMillis = nowMillis
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    fun isValidStatus(status: String?): Boolean =
        status == STATUS_PENDING || status == STATUS_SENT || status == STATUS_FAILED
}
