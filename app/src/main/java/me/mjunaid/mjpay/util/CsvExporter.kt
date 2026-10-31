package me.mjunaid.mjpay.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import me.mjunaid.mjpay.R
import me.mjunaid.mjpay.data.Delivery
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Writes the export to the cache directory and returns a FileProvider URI.
 * SMS bodies are never exported.
 */
object CsvExporter {

    fun write(context: Context, rows: List<Delivery>): Uri {
        val dir = File(context.cacheDir, "exports")
        if (dir.exists()) {
            dir.listFiles()?.forEach { it.delete() }
        } else {
            dir.mkdirs()
        }

        val file = File(dir, "mjpay-${Dates.fileStamp(System.currentTimeMillis())}.csv")
        file.bufferedWriter(StandardCharsets.UTF_8).use { writer ->
            writer.write("\uFEFF")
            writer.write(Csv.row("time", "method", "amount", "trx_id", "status", "attempts"))
            writer.write("\r\n")
            for (row in rows) {
                writer.write(
                    Csv.row(
                        Dates.dateTime(row.receivedAt),
                        row.method,
                        Money.plain(row.amountPaisa),
                        row.trxId.orEmpty(),
                        row.status,
                        row.attempts.toString()
                    )
                )
                writer.write("\r\n")
            }
        }

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun share(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.export_share_title)))
    }
}
