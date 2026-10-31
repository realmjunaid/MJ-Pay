package me.mjunaid.mjpay.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionCompareTest {

    @Test
    fun newerVersionIsDetected() {
        assertTrue(VersionCompare.isNewer("1.0.1", "1.0.0"))
        assertTrue(VersionCompare.isNewer("v1.2.0", "1.1.9"))
        assertFalse(VersionCompare.isNewer("1.0.0", "1.0.0"))
        assertFalse(VersionCompare.isNewer("1.0.0", "1.0.1"))
    }

    @Test
    fun parsesGithubReleaseAndRejectsOtherHosts() {
        val json = """
            {"tag_name":"v1.0.2","assets":[
              {"browser_download_url":"https://github.com/realmjunaid/MJ-Pay/releases/download/v1.0.2/MJ-Pay.apk"}
            ]}
        """.trimIndent()
        val release = UpdateChecker.parse(json)
        assertEquals("1.0.2", release?.version)
        assertTrue(release?.apkUrl?.endsWith(".apk") == true)
        assertNull(UpdateChecker.parse("""{"tag_name":"v9","assets":[{"browser_download_url":"https://evil.example/app.apk"}]}"""))
        assertFalse(UpdateChecker.isAllowedDownloadUrl("http://github.com/a.apk"))
    }
}
