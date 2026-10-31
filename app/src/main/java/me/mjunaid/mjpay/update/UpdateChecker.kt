package me.mjunaid.mjpay.update

import java.net.HttpURLConnection
import java.net.URL

/**
 * Looks at the latest GitHub release of this app. Only an .apk asset from that
 * release is accepted. Nothing else is downloaded.
 */
object UpdateChecker {

    const val LATEST_URL = "https://api.github.com/repos/realmjunaid/MJ-Pay/releases/latest"
    private const val MAX_JSON = 256 * 1024

    data class Release(val version: String, val apkUrl: String)

    fun findNewer(currentVersion: String): Release? {
        val release = fetchLatest() ?: return null
        return if (VersionCompare.isNewer(release.version, currentVersion)) release else null
    }

    fun fetchLatest(): Release? {
        val connection = open(LATEST_URL)
        return try {
            val code = connection.responseCode
            if (code !in 200..299) return null
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            if (text.length > MAX_JSON) return null
            parse(text)
        } finally {
            connection.disconnect()
        }
    }

    fun parse(json: String): Release? {
        val version = Regex(""""tag_name"\s*:\s*"([^"]+)"""")
            .find(json)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        if (version.isEmpty()) return null
        val apkUrl = Regex(""""browser_download_url"\s*:\s*"(https://[^"]+\.apk)"""")
            .find(json)?.groupValues?.getOrNull(1)
            ?: return null
        if (!isAllowedDownloadUrl(apkUrl)) return null
        return Release(version.removePrefix("v").removePrefix("V"), apkUrl)
    }

    fun isAllowedDownloadUrl(url: String): Boolean {
        if (!url.startsWith("https://")) return false
        val host = try {
            URL(url).host.lowercase()
        } catch (e: Exception) {
            return false
        }
        return host == "github.com" ||
            host == "objects.githubusercontent.com" ||
            host == "release-assets.githubusercontent.com" ||
            host.endsWith(".githubusercontent.com")
    }

    fun open(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 15_000
        connection.readTimeout = 60_000
        connection.setRequestProperty("User-Agent", "MJPay")
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        return connection
    }
}
