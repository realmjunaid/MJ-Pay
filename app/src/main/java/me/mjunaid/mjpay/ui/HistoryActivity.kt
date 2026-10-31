package me.mjunaid.mjpay.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import me.mjunaid.mjpay.R
import me.mjunaid.mjpay.data.Db
import me.mjunaid.mjpay.data.DbLogic
import me.mjunaid.mjpay.data.Delivery
import me.mjunaid.mjpay.util.CsvExporter
import me.mjunaid.mjpay.util.Money
import me.mjunaid.mjpay.work.Scheduler

class HistoryActivity : BaseActivity() {

    private lateinit var db: Db
    private lateinit var adapter: HistoryAdapter
    private lateinit var list: ListView
    private lateinit var loadMoreButton: Button
    private lateinit var limitNote: TextView
    private lateinit var searchInput: EditText

    private val items = ArrayList<Delivery>()
    private var statusFilter: String? = null
    private var methodFilter: String? = null
    private var trxQuery: String = ""

    private val searchHandler = Handler(Looper.getMainLooper())
    private val searchRunnable = Runnable {
        trxQuery = searchInput.text.toString().trim()
        load(reset = true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)
        applyEdgeToEdge(findViewById(R.id.root))

        db = Db.get(this)
        list = findViewById(R.id.list)
        loadMoreButton = findViewById(R.id.btnLoadMore)
        limitNote = findViewById(R.id.limitNote)
        searchInput = findViewById(R.id.searchInput)

        adapter = HistoryAdapter(this) { delivery -> openDetail(delivery) }
        list.adapter = adapter
        list.emptyView = findViewById(R.id.emptyView)

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnResendAll).setOnClickListener { resendAllFailed() }
        findViewById<Button>(R.id.btnExport).setOnClickListener { confirmExport() }
        loadMoreButton.setOnClickListener { load(reset = false) }

        searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                searchHandler.removeCallbacks(searchRunnable)
                searchHandler.postDelayed(searchRunnable, SEARCH_DEBOUNCE_MS)
            }
        })

        setupSpinner(
            R.id.statusSpinner,
            R.array.status_filter,
            arrayOf(null, DbLogic.STATUS_PENDING, DbLogic.STATUS_SENT, DbLogic.STATUS_FAILED)
        ) { statusFilter = it; load(reset = true) }

        setupSpinner(
            R.id.methodSpinner,
            R.array.method_filter,
            arrayOf(null, "bKash", "Nagad")
        ) { methodFilter = it; load(reset = true) }
    }

    override fun onResume() {
        super.onResume()
        refreshTodayHeader()
        load(reset = true)
    }

    override fun onDestroy() {
        searchHandler.removeCallbacks(searchRunnable)
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_EXPORT) {
            if (resultCode == RESULT_OK) {
                exportCsv()
            } else {
                Toast.makeText(this, R.string.unlock_cancelled, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupSpinner(
        spinnerId: Int,
        labels: Int,
        values: Array<String?>,
        onSelected: (String?) -> Unit
    ) {
        val spinner = findViewById<Spinner>(spinnerId)
        val adapter = ArrayAdapter.createFromResource(
            this, labels, android.R.layout.simple_spinner_item
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                onSelected(values.getOrNull(position))
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun refreshTodayHeader() {
        val stats = db.todayStats()
        findViewById<TextView>(R.id.todayHeader).text = getString(
            R.string.history_today,
            stats.count,
            Money.format(stats.totalPaisa)
        )
    }

    private fun load(reset: Boolean) {
        val offset = if (reset) 0 else items.size
        val rows = db.list(statusFilter, methodFilter, trxQuery, PAGE_SIZE, offset)

        if (reset) items.clear()
        items.addAll(rows)
        adapter.submit(items)

        loadMoreButton.visibility =
            if (rows.size == PAGE_SIZE && items.size < Db.MAX_PAGE) View.VISIBLE else View.GONE
        limitNote.visibility = if (items.size >= Db.MAX_PAGE) View.VISIBLE else View.GONE
    }

    private fun openDetail(delivery: Delivery) {
        startActivity(
            Intent(this, DetailActivity::class.java)
                .putExtra(DetailActivity.EXTRA_ID, delivery.id)
        )
    }

    private fun resendAllFailed() {
        val ids = db.listFailedIds()
        if (ids.isEmpty()) {
            Toast.makeText(this, R.string.resend_none, Toast.LENGTH_SHORT).show()
            return
        }
        var count = 0
        for (id in ids) {
            val row = db.getById(id) ?: continue
            db.resetToPending(row.id)
            Scheduler.enqueueDelivery(this, row.deliveryId)
            count++
        }
        Toast.makeText(this, getString(R.string.resend_queued, count), Toast.LENGTH_SHORT).show()
        load(reset = true)
    }

    private fun confirmExport() {
        AlertDialog.Builder(this)
            .setTitle(R.string.export_title)
            .setMessage(R.string.export_message)
            .setPositiveButton(R.string.export_continue) { _, _ ->
                // Devices without a secure screen lock cannot show the credential prompt;
                // the warning above is the only gate there.
                if (LockManager.hasDeviceCredential(this)) {
                    LockManager.authenticate(this, REQ_EXPORT)
                } else {
                    exportCsv()
                }
            }
            .setNegativeButton(R.string.export_cancel, null)
            .show()
    }

    private fun exportCsv() {
        val context = this
        Thread {
            try {
                val rows = db.listForExport(statusFilter, methodFilter, trxQuery)
                val uri = CsvExporter.write(context, rows)
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    CsvExporter.share(context, uri)
                    Toast.makeText(context, R.string.export_ready, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (!isFinishing) {
                        Toast.makeText(context, R.string.export_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }.start()
    }

    private companion object {
        const val PAGE_SIZE = 50
        const val SEARCH_DEBOUNCE_MS = 250L
        const val REQ_EXPORT = 2001
    }
}
