package me.mjunaid.mjpay.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import androidx.core.content.ContextCompat
import me.mjunaid.mjpay.R
import me.mjunaid.mjpay.data.DbLogic
import me.mjunaid.mjpay.data.Delivery
import me.mjunaid.mjpay.util.Dates
import me.mjunaid.mjpay.util.Money

class HistoryAdapter(
    private val context: Context,
    private val onClick: (Delivery) -> Unit
) : BaseAdapter() {

    private val items = ArrayList<Delivery>()

    fun submit(rows: List<Delivery>) {
        items.clear()
        items.addAll(rows)
        notifyDataSetChanged()
    }

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): Delivery = items[position]

    override fun getItemId(position: Int): Long = items[position].id

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView
            ?: LayoutInflater.from(context).inflate(R.layout.item_delivery, parent, false)
        val delivery = items[position]

        view.findViewById<TextView>(R.id.rowMethod).text = delivery.method
        val status = view.findViewById<TextView>(R.id.rowStatus)
        status.text = delivery.status
        status.setTextColor(ContextCompat.getColor(context, statusColor(delivery.status)))

        view.findViewById<TextView>(R.id.rowAmount).text =
            if (delivery.amountPaisa == null) context.getString(R.string.amount_prefix)
            else Money.format(delivery.amountPaisa)
        view.findViewById<TextView>(R.id.rowTime).text = Dates.short(delivery.receivedAt)

        val trx = view.findViewById<TextView>(R.id.rowTrx)
        if (delivery.trxId.isNullOrBlank()) {
            trx.visibility = View.GONE
        } else {
            trx.visibility = View.VISIBLE
            trx.text = delivery.trxId
        }

        view.setOnClickListener { onClick(delivery) }
        return view
    }

    private fun statusColor(status: String): Int = when (status) {
        DbLogic.STATUS_SENT -> R.color.status_sent
        DbLogic.STATUS_FAILED -> R.color.status_failed
        else -> R.color.status_pending
    }
}
