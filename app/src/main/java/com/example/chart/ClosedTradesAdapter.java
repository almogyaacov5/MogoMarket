package com.mogomarket.app;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
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
        Context ctx = holder.itemView.getContext();

        // צבעים דינמיים מה-Theme
        int bgCard        = ctx.getColor(R.color.bg_card);
        int textPrimary   = ctx.getColor(R.color.text_primary);
        int textSecondary = ctx.getColor(R.color.text_secondary);
        int colorGain     = ctx.getColor(R.color.gain);
        int colorLoss     = ctx.getColor(R.color.loss);
        int colorNeutral  = textSecondary;

        holder.itemView.setBackgroundColor(bgCard);

        String symbol = trade.symbol != null ? trade.symbol.toUpperCase(Locale.US) : "?";
        holder.symbolText.setText(symbol);
        holder.symbolText.setTextColor(textPrimary);

        if (holder.symbolInitial != null) {
            String initial = symbol.length() > 0 ? String.valueOf(symbol.charAt(0)) : "?";
            holder.symbolInitial.setText(initial);
            int colorIndex = Math.abs((symbol.charAt(0) - 'A') % CIRCLE_COLORS.length);
            int circleColor = CIRCLE_COLORS[colorIndex];

            Drawable bg = holder.symbolInitial.getBackground();
            if (bg != null) {
                bg = bg.mutate();
                bg.setTint(circleColor);
                holder.symbolInitial.setBackground(bg);
            } else {
                GradientDrawable circle = new GradientDrawable();
                circle.setShape(GradientDrawable.OVAL);
                circle.setColor(circleColor);
                holder.symbolInitial.setBackground(circle);
            }
        }

        double buyPrice  = trade.buyPrice;
        double sellPrice = trade.sellPrice;
        double currPrice = (trade.currentPrice > 0) ? trade.currentPrice : sellPrice;

        holder.buyPriceView.setText(String.format(Locale.US, "$%.2f", buyPrice));
        holder.buyPriceView.setTextColor(textSecondary);
        holder.sellPriceView.setText(String.format(Locale.US, "$%.2f", sellPrice));
        holder.sellPriceView.setTextColor(textSecondary);
        holder.currPriceView.setText(String.format(Locale.US, "$%.2f", currPrice));
        holder.currPriceView.setTextColor(textPrimary);

        double pnlAbs     = sellPrice - buyPrice;
        double pnlPercent = (buyPrice != 0) ? ((sellPrice - buyPrice) / buyPrice) * 100 : 0;

        int    pnlColor;
        String pnlSign;
        if (pnlAbs > 0)      { pnlColor = colorGain;    pnlSign = "+"; }
        else if (pnlAbs < 0) { pnlColor = colorLoss;    pnlSign = "";  }
        else                 { pnlColor = colorNeutral;  pnlSign = "";  }

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
