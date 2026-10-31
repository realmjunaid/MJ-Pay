package me.mjunaid.mjpay.debug

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import me.mjunaid.mjpay.R
import me.mjunaid.mjpay.net.UploadResult
import me.mjunaid.mjpay.sms.ImmediateDelivery
import me.mjunaid.mjpay.sms.SmsIntake
import me.mjunaid.mjpay.ui.BaseActivity
import me.mjunaid.mjpay.work.HeartbeatWorker

/** Debug-only screen: runs typed SMS text through the real receiver pipeline. */
class SimulateSmsActivity : BaseActivity() {

    private lateinit var senderInput: EditText
    private lateinit var bodyInput: EditText
    private lateinit var result: TextView
    private lateinit var runButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_simulate_sms)
        applyEdgeToEdge(findViewById(R.id.root))

        senderInput = findViewById(R.id.senderInput)
        bodyInput = findViewById(R.id.bodyInput)
        result = findViewById(R.id.result)
        runButton = findViewById(R.id.btnRun)

        senderInput.setText("bKash")

        findViewById<Button>(R.id.btnFillBkash).setOnClickListener {
            senderInput.setText("bKash")
            bodyInput.setText(
                "You have received Tk 1,250.00 from 01700000000. " +
                    "TrxID ABC123XYZ. Your new balance is Tk 500.00."
            )
        }
        findViewById<Button>(R.id.btnFillNagad).setOnClickListener {
            senderInput.setText("Nagad")
            bodyInput.setText(
                "Amount: Tk 100 received from 01800000000. TxnID: 1234567890."
            )
        }
        findViewById<Button>(R.id.btnFillOtp).setOnClickListener {
            senderInput.setText("bKash")
            bodyInput.setText("Your bKash OTP is 123456. Do not share it with anyone.")
        }
        findViewById<Button>(R.id.btnHeartbeat).setOnClickListener {
            WorkManager.getInstance(this)
                .enqueue(OneTimeWorkRequest.Builder(HeartbeatWorker::class.java).build())
            result.text = "Heartbeat enqueued."
        }
        runButton.setOnClickListener { runPipeline() }
    }

    private fun runPipeline() {
        runButton.isEnabled = false
        result.text = "Running\u2026"
        val sender = senderInput.text.toString()
        val body = bodyInput.text.toString()

        Thread {
            val outcome = try {
                when (val stored = SmsIntake.accept(this, sender, body, System.currentTimeMillis(), null)) {
                    is SmsIntake.Result.Dropped -> "Dropped (${stored.reason}) \u2014 nothing stored."
                    SmsIntake.Result.Duplicate -> "Duplicate \u2014 already stored, nothing sent."
                    is SmsIntake.Result.Stored -> {
                        val upload = ImmediateDelivery.attempt(stored, this)
                        val label = when (upload) {
                            is UploadResult.Success -> "SENT"
                            is UploadResult.Retry -> "PENDING (${upload.error}), worker queued"
                            is UploadResult.Permanent -> "FAILED (${upload.error}), worker queued"
                        }
                        "Stored row #${stored.rowId} (${stored.deliveryId}), upload: $label"
                    }
                }
            } catch (e: Exception) {
                "Error: ${e.javaClass.simpleName}"
            }

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.text = outcome
                runButton.isEnabled = true
            }
        }.start()
    }
}
