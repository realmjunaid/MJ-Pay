package me.mjunaid.mjpay.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadRulesTest {

    @Test
    fun twoHundredsAreSuccess() {
        assertEquals(UploadResult.Success, UploadRules.classify(200))
        assertEquals(UploadResult.Success, UploadRules.classify(204))
        assertEquals(UploadResult.Success, UploadRules.classify(299))
    }

    @Test
    fun retryableStatuses() {
        assertEquals(UploadResult.Retry("http-429"), UploadRules.classify(429))
        assertEquals(UploadResult.Retry("http-500"), UploadRules.classify(500))
        assertEquals(UploadResult.Retry("http-503"), UploadRules.classify(503))
    }

    @Test
    fun authStatusesArePermanent() {
        assertEquals(UploadResult.Permanent("auth"), UploadRules.classify(401))
        assertEquals(UploadResult.Permanent("auth"), UploadRules.classify(403))
    }

    @Test
    fun otherClientErrorsArePermanent() {
        assertEquals(UploadResult.Permanent("http-400"), UploadRules.classify(400))
        assertEquals(UploadResult.Permanent("http-404"), UploadRules.classify(404))
        assertEquals(UploadResult.Permanent("http-422"), UploadRules.classify(422))
    }

    @Test
    fun redirectsArePermanent() {
        assertEquals(UploadResult.Permanent("http-301"), UploadRules.classify(301))
        assertEquals(UploadResult.Permanent("http-302"), UploadRules.classify(302))
    }

    @Test
    fun httpsIsAlwaysAllowed() {
        assertTrue(UploadRules.isAllowedUrl("https://example.com/hook", allowDebugCleartext = false))
        assertTrue(UploadRules.isAllowedUrl("https://example.com:8443/hook?x=1", allowDebugCleartext = true))
    }

    @Test
    fun releaseRejectsHttp() {
        assertFalse(UploadRules.isAllowedUrl("http://example.com/hook", allowDebugCleartext = false))
    }

    @Test
    fun debugAllowsOnlyLocalCleartextHosts() {
        assertTrue(UploadRules.isAllowedUrl("http://10.0.2.2:8080/hook", allowDebugCleartext = true))
        assertTrue(UploadRules.isAllowedUrl("http://localhost:8080/hook", allowDebugCleartext = true))
        assertTrue(UploadRules.isAllowedUrl("http://127.0.0.1:8080/hook", allowDebugCleartext = true))
        assertFalse(UploadRules.isAllowedUrl("http://example.com/hook", allowDebugCleartext = true))
        assertFalse(UploadRules.isAllowedUrl("http://192.168.0.5:8080/hook", allowDebugCleartext = true))
    }

    @Test
    fun rejectsInvalidOrUnsupportedUrls() {
        assertFalse(UploadRules.isAllowedUrl("", allowDebugCleartext = true))
        assertFalse(UploadRules.isAllowedUrl("ftp://example.com", allowDebugCleartext = true))
        assertFalse(UploadRules.isAllowedUrl("not a url", allowDebugCleartext = true))
        assertFalse(UploadRules.isAllowedUrl("https://", allowDebugCleartext = true))
    }
}
