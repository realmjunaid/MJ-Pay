package me.mjunaid.mjpay.ui

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import me.mjunaid.mjpay.R
import me.mjunaid.mjpay.data.Db
import me.mjunaid.mjpay.data.DbLogic
import me.mjunaid.mjpay.data.Delivery
import me.mjunaid.mjpay.util.Dates
import me.mjunaid.mjpay.util.Money
import me.mjunaid.mjpay.work.Scheduler

class DetailActivity : BaseActivity() {

    private lateinit var db: Db
    private var rowId: Long = -1L
    private var delivery: Delivery? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)
        applyEdgeToEdge(findViewById(R.id.root))

        db = Db.get(this)
        rowId = intent.getLongExtra(EXTRA_ID, -1L)
        if (rowId <= 0L) {
            finish()
            return
        }

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnResend).setOnClickListener { resend() }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val row = db.getById(rowId)
        if (row == null) {
            finish()
            return
        }
        delivery = row

        findViewById<TextView>(R.id.detailAmount).text = Money.format(row.amountPaisa)
        findViewById<TextView>(R.id.detailMethod).text = row.method

        val status = findViewById<TextView>(R.id.detailStatus)
        status.text = row.status
        status.setTextColor(
            ContextCompat.getColor(
                this,
                when (row.status) {
                    DbLogic.STATUS_SENT -> R.color.status_sent
                    DbLogic.STATUS_FAILED -> R.color.status_failed
                    else -> R.color.status_pending
                }
            )
        )

        findViewById<TextView>(R.id.detailSender).text = row.sender
        findViewById<TextView>(R.id.detailReceived).text = Dates.dateTime(row.receivedAt)
        findViewById<TextView>(R.id.detailTrx).text = row.trxId ?: "\u2014"
        findViewById<TextView>(R.id.detailSim).text = row.sim?.toString() ?: "\u2014"
        findViewById<TextView>(R.id.detailAttempts).text = row.attempts.toString()
        findViewById<TextView>(R.id.detailLastAttempt).text =
            row.lastAttemptAt?.let { Dates.dateTime(it) } ?: getString(R.string.never)
        findViewById<TextView>(R.id.detailLastError).text =
            ErrorText.of(this, row.lastError)
        findViewById<TextView>(R.id.detailBody).text =
            row.body ?: getString(R.string.body_unavailable)
    }

    private fun resend() {
        val row = delivery ?: return
        db.resetToPending(row.id)
        Scheduler.enqueueDelivery(this, row.deliveryId)
        Toast.makeText(this, R.string.resend_done, Toast.LENGTH_SHORT).show()
        render()
    }

    companion object {
        const val EXTRA_ID = "delivery_row_id"
    }
}
