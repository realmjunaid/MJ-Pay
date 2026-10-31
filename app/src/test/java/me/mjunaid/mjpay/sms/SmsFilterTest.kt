package me.mjunaid.mjpay.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsFilterTest {

    private val bKashBody =
        "You have received Tk 1,250.00 from 01700000000. TrxID ABC123XYZ. Your new balance is Tk 500.00."
    private val nagadBody =
        "Amount: Tk 100 received from 01800000000. TxnID: 1234567890. Balance Tk 400.00."

    private fun accepted(sender: String, body: String, allowlist: List<String> = SmsFilter.parseAllowlist(SmsFilter.DEFAULT_ALLOWLIST)) =
        SmsFilter.evaluate(sender, body, allowlist)

    @Test
    fun acceptsFakeBkashReceivedSms() {
        assertEquals(SmsFilter.Result.Accept, accepted("bKash", bKashBody))
    }

    @Test
    fun acceptsFakeNagadReceivedSms() {
        assertEquals(SmsFilter.Result.Accept, accepted("NAGAD", nagadBody))
    }

    @Test
    fun senderMatchIsCaseInsensitive() {
        assertEquals(SmsFilter.Result.Accept, accepted("BKASH", bKashBody))
        assertEquals(SmsFilter.Result.Accept, accepted("Nagad", nagadBody))
    }

    @Test
    fun acceptsAmountWithoutSpace() {
        assertEquals(
            SmsFilter.Result.Accept,
            accepted("bKash", "You have received Tk1250 from 01700000000.")
        )
    }

    @Test
    fun acceptsAmountWithOneDecimal() {
        assertEquals(
            SmsFilter.Result.Accept,
            accepted("bKash", "You have received Tk 99.5 from 01700000000.")
        )
    }

    @Test
    fun dropsOtpSms() {
        val result = accepted("bKash", "Your bKash OTP is 123456. Do not share it with anyone.")
        assertEquals(SmsFilter.Result.Drop("blocked_word"), result)
    }

    @Test
    fun dropsPinSms() {
        val result = accepted("bKash", "Your PIN has been changed. You received no payment.")
        assertEquals(SmsFilter.Result.Drop("blocked_word"), result)
    }

    @Test
    fun dropsVerificationCodeSms() {
        val result = accepted("NAGAD", "Your verification code is 445566. Amount Tk 0 received?")
        assertEquals(SmsFilter.Result.Drop("blocked_word"), result)
    }

    @Test
    fun dropsPasswordAndOneTimePhrases() {
        assertTrue(accepted("bKash", "Do not share your password. Tk 10 received.") is SmsFilter.Result.Drop)
        assertTrue(accepted("bKash", "This is a one time code. Tk 10 received.") is SmsFilter.Result.Drop)
        assertTrue(accepted("bKash", "This is a one-time code. Tk 10 received.") is SmsFilter.Result.Drop)
    }

    @Test
    fun wordBoundaryPreventsFalsePositiveOnPin() {
        assertEquals(
            SmsFilter.Result.Accept,
            accepted("bKash", "You have received Tk 500 for shopping at a store.")
        )
    }

    @Test
    fun dropsNonAllowlistedSender() {
        assertEquals(
            SmsFilter.Result.Drop("sender"),
            accepted("GP", "You have received Tk 100 from 01700000000.")
        )
    }

    @Test
    fun dropsNonAllowlistedSenderEvenWithPaymentText() {
        assertEquals(
            SmsFilter.Result.Drop("sender"),
            accepted("UnknownBank", "Amount: Tk 999 received.")
        )
    }

    @Test
    fun allowlistIsEditable() {
        val allowlist = SmsFilter.parseAllowlist(" bKash , NAGAD , Rocket ")
        assertEquals(listOf("bKash", "NAGAD", "Rocket"), allowlist)
        assertEquals(
            SmsFilter.Result.Accept,
            accepted("Rocket", "You have received Tk 10.", allowlist)
        )
        assertEquals(
            SmsFilter.Result.Drop("sender"),
            accepted("Nagad", nagadBody, SmsFilter.parseAllowlist("bKash"))
        )
    }

    @Test
    fun dropsReceivedWithoutAmount() {
        assertEquals(
            SmsFilter.Result.Drop("no_amount"),
            accepted("bKash", "You have received a message from bKash.")
        )
    }

    @Test
    fun dropsAmountWithoutReceivedKeyword() {
        assertEquals(
            SmsFilter.Result.Drop("no_received_keyword"),
            accepted("bKash", "You sent Tk 500.00 to 01700000000.")
        )
    }

    @Test
    fun dropsEmptyBody() {
        assertEquals(SmsFilter.Result.Drop("empty"), accepted("bKash", "   "))
    }

    @Test
    fun amountPatternAcceptsAllSpecExamples() {
        assertTrue(SmsFilter.AMOUNT_REGEX.containsMatchIn("Tk 1,250.00"))
        assertTrue(SmsFilter.AMOUNT_REGEX.containsMatchIn("Tk1250"))
        assertTrue(SmsFilter.AMOUNT_REGEX.containsMatchIn("Amount: Tk 100"))
        assertTrue(SmsFilter.AMOUNT_REGEX.containsMatchIn("BDT 50.5"))
        assertTrue(SmsFilter.AMOUNT_REGEX.containsMatchIn("taka 10"))
        assertTrue(!SmsFilter.AMOUNT_REGEX.containsMatchIn("Tk .50"))
    }
}
