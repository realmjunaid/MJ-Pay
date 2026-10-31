package me.mjunaid.mjpay.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DedupeKeyTest {

    @Test
    fun stableForSameInputs() {
        assertEquals(
            DedupeKey.of("bKash", "Tk 10 received", 1760000000000L),
            DedupeKey.of("bKash", "Tk 10 received", 1760000000000L)
        )
    }

    @Test
    fun changesWhenBodyChanges() {
        assertNotEquals(
            DedupeKey.of("bKash", "Tk 10 received", 1760000000000L),
            DedupeKey.of("bKash", "Tk 11 received", 1760000000000L)
        )
    }

    @Test
    fun changesWhenTimestampChanges() {
        assertNotEquals(
            DedupeKey.of("bKash", "Tk 10 received", 1760000000000L),
            DedupeKey.of("bKash", "Tk 10 received", 1760000000001L)
        )
    }

    @Test
    fun isLowercaseSha256Hex() {
        val key = DedupeKey.of("NAGAD", "Amount: Tk 100 received", 1L)
        assertEquals(64, key.length)
        assertEquals(key.lowercase(), key)
    }
}
