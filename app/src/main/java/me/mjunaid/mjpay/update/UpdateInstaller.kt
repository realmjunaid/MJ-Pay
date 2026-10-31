package me.mjunaid.mjpay.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File

object UpdateInstaller {

    private const val MAX_APK_BYTES = 20 * 1024 * 1024

    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val intent = Intent(
            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun download(context: Context, apkUrl: String): File {
        if (!UpdateChecker.isAllowedDownloadUrl(apkUrl)) {
            throw IllegalArgumentException("download host not allowed")
        }
        val dir = File(context.cacheDir, "updates")
        dir.mkdirs()
        val file = File(dir, "mjpay-update.apk")
        val connection = UpdateChecker.open(apkUrl)
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("download failed")
            }
            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var total = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_APK_BYTES) throw IllegalStateException("apk too large")
                        output.write(buffer, 0, read)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        return file
    }

    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
