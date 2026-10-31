package me.mjunaid.mjpay.security

import org.junit.Assert.assertEquals
import org.junit.Test

class SignerTest {

    private fun hex(string: String): ByteArray =
        string.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun bytes(count: Int, value: Int): ByteArray = ByteArray(count) { value.toByte() }

    @Test
    fun rfc4231TestVector1() {
        assertEquals(
            "b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7",
            Signer.hmacSha256Hex(bytes(20, 0x0b), "Hi There".toByteArray())
        )
    }

    @Test
    fun rfc4231TestVector2() {
        assertEquals(
            "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843",
            Signer.hmacSha256Hex("Jefe", "what do ya want for nothing?")
        )
    }

    @Test
    fun rfc4231TestVector3() {
        assertEquals(
            "773ea91e36800e46854db8ebd09181a72959098b3ef8c122d9635514ced565fe",
            Signer.hmacSha256Hex(bytes(20, 0xaa), bytes(50, 0xdd))
        )
    }

    @Test
    fun rfc4231TestVector4() {
        assertEquals(
            "82558a389a443c0ea4cc819899f2083a85f0faa3e578f8077a2e3ff46729665b",
            Signer.hmacSha256Hex(hex("0102030405060708090a0b0c0d0e0f10111213141516171819"), bytes(50, 0xcd))
        )
    }

    @Test
    fun rfc4231TestVector6LargeKey() {
        assertEquals(
            "60e431591ee0b67f0d8a26aacbf5b77f8e0bc6213728c5140546040f0ee37f54",
            Signer.hmacSha256Hex(
                bytes(131, 0xaa),
                "Test Using Larger Than Block-Size Key - Hash Key First".toByteArray()
            )
        )
    }

    @Test
    fun signatureUsesTimestampDotBodyFormat() {
        val secret = "top-secret"
        val timestamp = "1760000000"
        val body = """{"type":"test","ts":1760000000000,"app_version":"1.0.0"}"""

        assertEquals(
            Signer.hmacSha256Hex(secret, "$timestamp.$body"),
            Signer.signature(secret, timestamp, body)
        )
        assertEquals(64, Signer.signature(secret, timestamp, body).length)
    }

    @Test
    fun hexIsLowercase() {
        val hex = Signer.hmacSha256Hex("k", "m")
        assertEquals(hex.lowercase(), hex)
    }
}
