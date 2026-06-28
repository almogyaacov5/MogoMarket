package com.example.chart;

import android.content.Context;
import android.widget.TextView;

import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.utils.MPPointF;

import java.util.List;
import java.util.Locale;

public class TradingMarkerView extends MarkerView {

    private final TextView priceText;
    private final TextView dateText;
    private List<String> dateLabels;

    public TradingMarkerView(Context context) {
        super(context, R.layout.view_chart_marker);
        priceText = findViewById(R.id.markerPrice);
        dateText  = findViewById(R.id.markerDate);
    }

    public void setDateLabels(List<String> labels) {
        this.dateLabels = labels;
    }

    @Override
    public void refreshContent(Entry e, Highlight highlight) {
        if (e != null) {
            priceText.setText("$" + String.format(Locale.US, "%.2f", e.getY()));

            int index = (int) e.getX();
            if (dateLabels != null && index >= 0 && index < dateLabels.size()) {
                dateText.setText(dateLabels.get(index));
            } else {
                dateText.setText("Index: " + index);
            }
        }
        super.refreshContent(e, highlight);
    }

    @Override
    public MPPointF getOffset() {
        return new MPPointF(-(getWidth() / 2f), -getHeight() - 20f);
    }
}
