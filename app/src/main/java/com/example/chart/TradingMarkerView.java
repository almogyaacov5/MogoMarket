package com.example.chart;

import android.content.Context;
import android.widget.TextView;

import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.utils.MPPointF;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TradingMarkerView extends MarkerView {

    private final TextView priceText;
    private final TextView dateText;

    public TradingMarkerView(Context context) {
        super(context, R.layout.view_chart_marker);
        priceText = findViewById(R.id.markerPrice);
        dateText  = findViewById(R.id.markerDate);
    }

    @Override
    public void refreshContent(Entry e, Highlight highlight) {
        if (e != null) {
            priceText.setText("$" + String.format(Locale.US, "%.2f", e.getY()));

            Object data = e.getData();
            if (data instanceof Long) {
                long timestamp = (Long) data;
                String formatted = new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
                        .format(new Date(timestamp));
                dateText.setText(formatted);
            } else {
                dateText.setText("Index: " + (int) e.getX());
            }
        }
        super.refreshContent(e, highlight);
    }

    @Override
    public MPPointF getOffset() {
        return new MPPointF(-(getWidth() / 2f), -getHeight() - 20f);
    }
}
