package com.example.mogomarket

import android.content.Context
import android.widget.TextView
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TradingMarkerView(
    context: Context
) : MarkerView(context, R.layout.view_chart_marker) {

    private val priceText: TextView = findViewById(R.id.markerPrice)
    private val dateText: TextView = findViewById(R.id.markerDate)

    override fun refreshContent(
        e: Entry?,
        highlight: Highlight?
    ) {
        if (e != null) {
            priceText.text = "$" + String.format("%.2f", e.y)

            // אם יש Timestamp מחובר ל-Entry דרך getData()
            val timestamp = e.data
            if (timestamp != null && timestamp is Long) {
                dateText.text = SimpleDateFormat(
                    "dd MMM yyyy HH:mm",
                    Locale.getDefault()
                ).format(Date(timestamp))
            } else {
                dateText.text = "Index: ${e.x.toInt()}"
            }
        }
        super.refreshContent(e, highlight)
    }

    override fun getOffset(): MPPointF {
        return MPPointF(
            -(width / 2).toFloat(),
            -height.toFloat() - 20f
        )
    }
}