package me.mjunaid.mjpay.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmsParserLiteTest {

    @Test
    fun parsesAmountWithCommasAndDecimals() {
        assertEquals(125000L, SmsParserLite.amountPaisa("You have received Tk 1,250.00 from someone."))
    }

    @Test
    fun parsesAmountWithoutSpace() {
        assertEquals(125000L, SmsParserLite.amountPaisa("You have received Tk1250 from someone."))
    }

    @Test
    fun parsesAmountAfterColon() {
        assertEquals(10000L, SmsParserLite.amountPaisa("Amount: Tk 100 received."))
    }

    @Test
    fun parsesOneDecimalAmount() {
        assertEquals(9950L, SmsParserLite.amountPaisa("Amount: Tk 99.5 received."))
    }

    @Test
    fun returnsNullAmountOnOddText() {
        assertNull(SmsParserLite.amountPaisa("hello world"))
        assertNull(SmsParserLite.amountPaisa(""))
        assertNull(SmsParserLite.amountPaisa("Tk maybe 100"))
    }

    @Test
    fun parsesTrxIdAfterLabel() {
        assertEquals(
            "ABC123XYZ",
            SmsParserLite.trxId("You have received Tk 10. TrxID ABC123XYZ.")
        )
    }

    @Test
    fun parsesTrxIdWithColonAndDifferentLabel() {
        assertEquals(
            "1234567890",
            SmsParserLite.trxId("Amount: Tk 100 received. TxnID: 1234567890.")
        )
        assertEquals(
            "DEADBEEF01",
            SmsParserLite.trxId("Txn ID DEADBEEF01")
        )
    }

    @Test
    fun trxIdMissingReturnsNull() {
        assertNull(SmsParserLite.trxId("You have received Tk 10. No reference here."))
    }

    @Test
    fun methodComesFromSender() {
        assertEquals(SmsParserLite.METHOD_BKASH, SmsParserLite.method("bKash"))
        assertEquals(SmsParserLite.METHOD_BKASH, SmsParserLite.method("BKASH"))
        assertEquals(SmsParserLite.METHOD_NAGAD, SmsParserLite.method("Nagad"))
        assertEquals(SmsParserLite.METHOD_OTHER, SmsParserLite.method("Rocket"))
        assertEquals(SmsParserLite.METHOD_OTHER, SmsParserLite.method(""))
    }

    @Test
    fun fullParseNeverThrows() {
        val parsed = SmsParserLite.parse("bKash", "Tk 1,250.00 received. TrxID XYZ98765")
        assertEquals(125000L, parsed.amountPaisa)
        assertEquals("XYZ98765", parsed.trxId)
        assertEquals(SmsParserLite.METHOD_BKASH, parsed.method)

        val empty = SmsParserLite.parse("??", "%%%%")
        assertNull(empty.amountPaisa)
        assertNull(empty.trxId)
        assertEquals(SmsParserLite.METHOD_OTHER, empty.method)
    }
}
