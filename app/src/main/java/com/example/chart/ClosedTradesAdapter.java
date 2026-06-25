package com.example.chart;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;

public class ClosedTradesAdapter extends RecyclerView.Adapter<ClosedTradesAdapter.ViewHolder> {

    // ── Trading Dark Theme colors ──────────────────────────
    private static final int BG_CARD       = 0xFF151C2E;
    private static final int TEXT_PRIMARY  = 0xFFE6EDF3;
    private static final int TEXT_SECONDARY= 0xFF8B98A5;
    private static final int COLOR_GAIN    = 0xFF00C896;
    private static final int COLOR_LOSS    = 0xFFFF4D4D;
    private static final int COLOR_NEUTRAL = 0xFF8B98A5;
    // ──────────────────────────────────────────────────────

    private static final int[] CIRCLE_COLORS = {
        0xFF1565C0, 0xFF6A1B9A, 0xFF00695C,
        0xFFE65100, 0xFF4A148C, 0xFF1B5E20
    };

    public interface OnTradeEditListener   { void onEditTrade(StockData trade); }
    public interface OnSummaryUpdateListener { void onSummaryUpdated(double totalPnl, int wins, int total); }

    private final List<StockData> closedTrades;
    private final OnTradeEditListener editListener;
    private OnSummaryUpdateListener summaryListener;

    public ClosedTradesAdapter(List<StockData> closedTrades, OnTradeEditListener listener) {
        this.closedTrades = closedTrades;
        this.editListener = listener;
    }

    public void setSummaryListener(OnSummaryUpdateListener listener) {
        this.summaryListener = listener;
        notifySummary();
    }

    public void triggerSummaryUpdate() { notifySummary(); }

    private void notifySummary() {
        if (summaryListener == null || closedTrades == null) return;
        double total = 0; int wins = 0;
        for (StockData t : closedTrades) {
            double pnl = t.sellPrice - t.buyPrice;
            total += pnl;
            if (pnl > 0) wins++;
        }
        summaryListener.onSummaryUpdated(total, wins, closedTrades.size());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_closed_trade, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        StockData trade = closedTrades.get(position);

        // ── עיצוב כרטיס ───────────────────────────────────
        holder.itemView.setBackgroundColor(BG_CARD);

        String symbol = trade.symbol != null ? trade.symbol.toUpperCase(Locale.US) : "?";
        holder.symbolText.setText(symbol);
        holder.symbolText.setTextColor(TEXT_PRIMARY);

        // עיגול ראשית מניה
        if (holder.symbolInitial != null) {
            String initial = symbol.length() > 0 ? String.valueOf(symbol.charAt(0)) : "?";
            holder.symbolInitial.setText(initial);
            int colorIndex = Math.abs((symbol.charAt(0) - 'A') % CIRCLE_COLORS.length);
            holder.symbolInitial.getBackground().setTint(CIRCLE_COLORS[colorIndex]);
        }

        double buyPrice  = trade.buyPrice;
        double sellPrice = trade.sellPrice;
        double currPrice = (trade.currentPrice > 0) ? trade.currentPrice : sellPrice;

        holder.buyPriceView.setText(String.format(Locale.US, "$%.2f", buyPrice));
        holder.buyPriceView.setTextColor(TEXT_SECONDARY);
        holder.sellPriceView.setText(String.format(Locale.US, "$%.2f", sellPrice));
        holder.sellPriceView.setTextColor(TEXT_SECONDARY);
        holder.currPriceView.setText(String.format(Locale.US, "$%.2f", currPrice));
        holder.currPriceView.setTextColor(TEXT_PRIMARY);

        double pnlAbs     = sellPrice - buyPrice;
        double pnlPercent = (buyPrice != 0) ? ((sellPrice - buyPrice) / buyPrice) * 100 : 0;

        int    pnlColor;
        String pnlSign;
        if (pnlAbs > 0)      { pnlColor = COLOR_GAIN;    pnlSign = "+"; }
        else if (pnlAbs < 0) { pnlColor = COLOR_LOSS;    pnlSign = "";  }
        else                 { pnlColor = COLOR_NEUTRAL;  pnlSign = "";  }

        holder.pnlView.setText(String.format(Locale.US, "%s$%.2f", pnlSign, pnlAbs));
        holder.pnlView.setTextColor(pnlColor);
        holder.percentText.setText(String.format(Locale.US, "%s%.2f%%", pnlSign, pnlPercent));
        holder.percentText.setTextColor(pnlColor);

        holder.editButton.setOnClickListener(v -> { if (editListener != null) editListener.onEditTrade(trade); });
    }

    @Override
    public int getItemCount() { return closedTrades != null ? closedTrades.size() : 0; }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView   symbolInitial, symbolText, tradeDateText;
        TextView   pnlView, percentText;
        TextView   buyPriceView, sellPriceView, currPriceView;
        ImageButton editButton;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            symbolInitial = itemView.findViewById(R.id.symbolInitial);
            symbolText    = itemView.findViewById(R.id.tradeSymbolText);
            tradeDateText = itemView.findViewById(R.id.tradeDateText);
            pnlView       = itemView.findViewById(R.id.pnlView);
            percentText   = itemView.findViewById(R.id.tradeChangePercent);
            buyPriceView  = itemView.findViewById(R.id.tradeBuyPrice);
            sellPriceView = itemView.findViewById(R.id.tradeSellingPrice);
            currPriceView = itemView.findViewById(R.id.tradeCurrentPrice);
            editButton    = itemView.findViewById(R.id.btnEditTrade);
        }
    }
}
