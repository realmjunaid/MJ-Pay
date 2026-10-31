package me.mjunaid.mjpay.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class DbLogicTest {

    @Test
    fun emptyFilterHasNoClause() {
        val (where, args) = DbLogic.buildFilter(null, null, null)
        assertNull(where)
        assertNull(args)
    }

    @Test
    fun buildsStatusAndMethodFilter() {
        val (where, args) = DbLogic.buildFilter("PENDING", "bKash", null)
        assertEquals("status = ? AND method = ?", where)
        assertArrayEquals(arrayOf("PENDING", "bKash"), args)
    }

    @Test
    fun trxQueryUsesLikeWithEscaping() {
        val (where, args) = DbLogic.buildFilter(null, null, "50%_x")
        assertEquals("trx_id LIKE ? ESCAPE '\\'", where)
        assertArrayEquals(arrayOf("%50\\%\\_x%"), args)
    }

    @Test
    fun trxQueryIsTrimmed() {
        val (_, args) = DbLogic.buildFilter(null, null, "  ABC  ")
        assertArrayEquals(arrayOf("%ABC%"), args)
    }

    @Test
    fun escapedLikeRoundTrip() {
        assertEquals("a\\\\b\\%c\\_d", DbLogic.escapeLike("a\\b%c_d"))
    }

    @Test
    fun startOfDayUsesLocalTimezone() {
        val dhaka = TimeZone.getTimeZone("Asia/Dhaka")
        val calendar = Calendar.getInstance(dhaka).apply {
            set(2026, Calendar.NOVEMBER, 1, 10, 30, 45)
            set(Calendar.MILLISECOND, 500)
        }
        val start = DbLogic.startOfLocalDay(calendar.timeInMillis, dhaka)

        val expected = Calendar.getInstance(dhaka).apply {
            timeInMillis = calendar.timeInMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        assertEquals(expected, start)
        assertTrue(start <= calendar.timeInMillis)
        assertTrue(calendar.timeInMillis - start < 86_400_000L)
    }

    @Test
    fun statusValidation() {
        assertTrue(DbLogic.isValidStatus("PENDING"))
        assertTrue(DbLogic.isValidStatus("SENT"))
        assertTrue(DbLogic.isValidStatus("FAILED"))
        assertTrue(!DbLogic.isValidStatus("SENT "))
        assertTrue(!DbLogic.isValidStatus(null))
    }
}
