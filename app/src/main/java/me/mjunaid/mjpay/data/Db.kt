package me.mjunaid.mjpay.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import me.mjunaid.mjpay.data.DbLogic.STATUS_FAILED
import me.mjunaid.mjpay.data.DbLogic.STATUS_PENDING
import me.mjunaid.mjpay.data.DbLogic.STATUS_SENT
import me.mjunaid.mjpay.security.KeyStoreCrypto
import me.mjunaid.mjpay.sms.DedupeKey

/**
 * Single SQLite database for deliveries. Bodies are AES-GCM encrypted before insert
 * and only decrypted for the detail screen and for building an upload payload.
 */
class Db private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                delivery_id TEXT NOT NULL UNIQUE,
                dedupe_key TEXT NOT NULL UNIQUE,
                received_at INTEGER NOT NULL,
                sender TEXT NOT NULL,
                method TEXT NOT NULL,
                body_enc TEXT NOT NULL,
                trx_id TEXT,
                amount_paisa INTEGER,
                status TEXT NOT NULL,
                attempts INTEGER NOT NULL DEFAULT 0,
                last_error TEXT,
                last_attempt_at INTEGER,
                sim INTEGER
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_deliveries_status ON $TABLE(status)")
        db.execSQL("CREATE INDEX idx_deliveries_received_at ON $TABLE(received_at)")
        db.execSQL("CREATE INDEX idx_deliveries_trx_id ON $TABLE(trx_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Version 1 is the first release; no migrations exist yet.
    }

    data class Stats(val count: Int, val totalPaisa: Long)
    data class PendingInfo(val count: Int, val oldestAgeMillis: Long?)

    /**
     * Saves an accepted SMS as PENDING before any network call.
     * Returns the new row id, or null when the dedupe key already exists.
     */
    fun insertPending(
        deliveryId: String,
        receivedAt: Long,
        sender: String,
        method: String,
        body: String,
        trxId: String?,
        amountPaisa: Long?,
        sim: Int?
    ): Long? {
        val values = ContentValues().apply {
            put(COL_DELIVERY_ID, deliveryId)
            put(COL_DEDUPE_KEY, DedupeKey.of(sender, body, receivedAt))
            put(COL_RECEIVED_AT, receivedAt)
            put(COL_SENDER, sender)
            put(COL_METHOD, method)
            put(COL_BODY_ENC, KeyStoreCrypto.encrypt(body))
            put(COL_TRX_ID, trxId)
            if (amountPaisa == null) putNull(COL_AMOUNT_PAISA) else put(COL_AMOUNT_PAISA, amountPaisa)
            put(COL_STATUS, STATUS_PENDING)
            put(COL_ATTEMPTS, 0)
            if (sim == null) putNull(COL_SIM) else put(COL_SIM, sim)
        }
        val id = writableDatabase.insertWithOnConflict(
            TABLE, null, values, SQLiteDatabase.CONFLICT_IGNORE
        )
        return if (id == -1L) null else id
    }

    fun getById(id: Long): Delivery? =
        queryOne("$COL_ID = ?", arrayOf(id.toString()))

    fun getByDeliveryId(deliveryId: String): Delivery? =
        queryOne("$COL_DELIVERY_ID = ?", arrayOf(deliveryId))

    fun markSent(id: Long, at: Long) {
        val values = ContentValues().apply {
            put(COL_STATUS, STATUS_SENT)
            putNull(COL_LAST_ERROR)
            put(COL_LAST_ATTEMPT_AT, at)
        }
        writableDatabase.update(TABLE, values, "$COL_ID = ?", arrayOf(id.toString()))
    }

    fun markFailed(id: Long, error: String, at: Long = System.currentTimeMillis()) {
        val values = ContentValues().apply {
            put(COL_STATUS, STATUS_FAILED)
            put(COL_LAST_ERROR, error)
            put(COL_LAST_ATTEMPT_AT, at)
        }
        writableDatabase.update(TABLE, values, "$COL_ID = ?", arrayOf(id.toString()))
    }

    fun incrementAttempt(id: Long, error: String, at: Long) {
        writableDatabase.execSQL(
            "UPDATE $TABLE SET attempts = attempts + 1, last_error = ?, last_attempt_at = ? WHERE id = ?",
            arrayOf<Any>(error, at, id)
        )
    }

    fun resetToPending(id: Long) {
        val values = ContentValues().apply {
            put(COL_STATUS, STATUS_PENDING)
            putNull(COL_LAST_ERROR)
        }
        writableDatabase.update(TABLE, values, "$COL_ID = ?", arrayOf(id.toString()))
    }

    fun list(
        status: String? = null,
        method: String? = null,
        trxQuery: String? = null,
        limit: Int = 50,
        offset: Int = 0
    ): List<Delivery> {
        val (where, args) = DbLogic.buildFilter(status, method, trxQuery)
        val safeLimit = limit.coerceIn(1, MAX_PAGE)
        val safeOffset = offset.coerceAtLeast(0)
        val rows = ArrayList<Delivery>(safeLimit)
        readableDatabase.query(
            TABLE,
            LIST_COLUMNS,
            where,
            args,
            null,
            null,
            "$COL_RECEIVED_AT DESC, $COL_ID DESC",
            "$safeOffset,$safeLimit"
        ).use { cursor ->
            while (cursor.moveToNext()) rows.add(readRow(cursor))
        }
        return rows
    }

    fun listFailedIds(limit: Int = MAX_PAGE): List<Long> =
        listIds("$COL_STATUS = ?", arrayOf(STATUS_FAILED), limit)

    /** ids of PENDING rows received before [olderThanMillis]. */
    fun listPendingIds(olderThanMillis: Long = Long.MAX_VALUE): List<Long> =
        listIds(
            "$COL_STATUS = ? AND $COL_RECEIVED_AT < ?",
            arrayOf(STATUS_PENDING, olderThanMillis.toString()),
            MAX_PAGE
        )

    fun pendingCount(): Int = scalarLong(
        "SELECT COUNT(*) FROM $TABLE WHERE $COL_STATUS = ?",
        arrayOf(STATUS_PENDING)
    ).toInt()

    fun pendingInfo(now: Long = System.currentTimeMillis()): PendingInfo {
        val count = pendingCount()
        if (count == 0) return PendingInfo(0, null)
        val oldest = scalarLong(
            "SELECT MIN($COL_RECEIVED_AT) FROM $TABLE WHERE $COL_STATUS = ?",
            arrayOf(STATUS_PENDING)
        )
        return PendingInfo(count, (now - oldest).coerceAtLeast(0))
    }

    /** Count and sum of all accepted payments received since local midnight. */
    fun todayStats(now: Long = System.currentTimeMillis()): Stats {
        val start = DbLogic.startOfLocalDay(now)
        val cursor = readableDatabase.rawQuery(
            "SELECT COUNT(*), COALESCE(SUM($COL_AMOUNT_PAISA), 0) FROM $TABLE WHERE $COL_RECEIVED_AT >= ?",
            arrayOf(start.toString())
        )
        cursor.use {
            if (!it.moveToFirst()) return Stats(0, 0)
            return Stats(it.getInt(0), it.getLong(1))
        }
    }

    /** Deletes rows received before the retention cutoff. Returns deleted row count. */
    fun purgeOlderThan(days: Int, now: Long = System.currentTimeMillis()): Int {
        val safeDays = days.coerceAtLeast(1)
        val cutoff = now - safeDays * 86_400_000L
        return writableDatabase.delete(
            TABLE, "$COL_RECEIVED_AT < ?", arrayOf(cutoff.toString())
        )
    }

    fun countAll(): Int =
        scalarLong("SELECT COUNT(*) FROM $TABLE", null).toInt()

    private fun queryOne(where: String, args: Array<String>): Delivery? {
        readableDatabase.query(
            TABLE, ALL_COLUMNS, where, args, null, null, null, "1"
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            val delivery = readRow(cursor)
            val body = try {
                KeyStoreCrypto.decrypt(cursor.getString(cursor.getColumnIndexOrThrow(COL_BODY_ENC)))
            } catch (e: Exception) {
                null
            }
            return delivery.copy(body = body)
        }
    }

    private fun listIds(where: String, args: Array<String>, limit: Int): List<Long> {
        val ids = ArrayList<Long>()
        readableDatabase.query(
            TABLE,
            arrayOf(COL_ID),
            where,
            args,
            null,
            null,
            "$COL_RECEIVED_AT ASC",
            limit.coerceIn(1, MAX_PAGE).toString()
        ).use { cursor ->
            while (cursor.moveToNext()) ids.add(cursor.getLong(0))
        }
        return ids
    }

    private fun scalarLong(sql: String, args: Array<String>?): Long {
        readableDatabase.rawQuery(sql, args).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        }
    }

    private fun readRow(cursor: Cursor): Delivery = Delivery(
        id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
        deliveryId = cursor.getString(cursor.getColumnIndexOrThrow(COL_DELIVERY_ID)),
        receivedAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_RECEIVED_AT)),
        sender = cursor.getString(cursor.getColumnIndexOrThrow(COL_SENDER)),
        method = cursor.getString(cursor.getColumnIndexOrThrow(COL_METHOD)),
        trxId = cursor.getStringOrNull(COL_TRX_ID),
        amountPaisa = cursor.getLongOrNull(COL_AMOUNT_PAISA),
        status = cursor.getString(cursor.getColumnIndexOrThrow(COL_STATUS)),
        attempts = cursor.getInt(cursor.getColumnIndexOrThrow(COL_ATTEMPTS)),
        lastError = cursor.getStringOrNull(COL_LAST_ERROR),
        lastAttemptAt = cursor.getLongOrNull(COL_LAST_ATTEMPT_AT),
        sim = cursor.getIntOrNull(COL_SIM)
    )

    private fun Cursor.getStringOrNull(column: String): String? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getString(index)
    }

    private fun Cursor.getLongOrNull(column: String): Long? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getLong(index)
    }

    private fun Cursor.getIntOrNull(column: String): Int? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getInt(index)
    }

    companion object {
        private const val DB_NAME = "mjpay.db"
        private const val DB_VERSION = 1

        const val TABLE = "deliveries"
        const val MAX_PAGE = 300

        private const val COL_ID = "id"
        private const val COL_DELIVERY_ID = "delivery_id"
        private const val COL_DEDUPE_KEY = "dedupe_key"
        private const val COL_RECEIVED_AT = "received_at"
        private const val COL_SENDER = "sender"
        private const val COL_METHOD = "method"
        private const val COL_BODY_ENC = "body_enc"
        private const val COL_TRX_ID = "trx_id"
        private const val COL_AMOUNT_PAISA = "amount_paisa"
        private const val COL_STATUS = "status"
        private const val COL_ATTEMPTS = "attempts"
        private const val COL_LAST_ERROR = "last_error"
        private const val COL_LAST_ATTEMPT_AT = "last_attempt_at"
        private const val COL_SIM = "sim"

        private val LIST_COLUMNS = arrayOf(
            COL_ID, COL_DELIVERY_ID, COL_RECEIVED_AT, COL_SENDER, COL_METHOD,
            COL_TRX_ID, COL_AMOUNT_PAISA, COL_STATUS, COL_ATTEMPTS, COL_LAST_ERROR,
            COL_LAST_ATTEMPT_AT, COL_SIM
        )
        private val ALL_COLUMNS = LIST_COLUMNS + COL_BODY_ENC

        @Volatile
        private var instance: Db? = null

        fun get(context: Context): Db =
            instance ?: synchronized(this) {
                instance ?: Db(context).also { instance = it }
            }
    }
}
