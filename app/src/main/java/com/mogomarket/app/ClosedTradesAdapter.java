package com.mogomarket.app;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
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

    public interface OnTradeEditListener { void onEditTrade(StockData trade); }
    public interface OnTradeDeleteListener { void onDeleteTrade(StockData trade); }
    public interface OnTradeScreenshotsListener { void onManageScreenshots(StockData trade); }
    public interface OnSummaryUpdateListener {
        void onSummaryUpdated(double totalPnl, int wins, int total, double totalInvested,
                              ClosedTradeAnalytics analytics);
    }

    private final List<StockData> closedTrades;
    private final OnTradeEditListener editListener;
    private OnTradeDeleteListener deleteListener;
    private OnTradeScreenshotsListener screenshotsListener;
    private OnSummaryUpdateListener summaryListener;

    // ⭐ חדש — שווי תיק כולל
    private double totalPortfolioValue = 0.0;

    public ClosedTradesAdapter(List<StockData> closedTrades, OnTradeEditListener listener) {
        this.closedTrades = closedTrades;
        this.editListener = listener;
    }

    // ⭐ חדש — setter לשווי תיק כולל
    public void setPortfolioValue(double value) {
        this.totalPortfolioValue = value;
        notifyDataSetChanged();
    }

    public void setDeleteListener(OnTradeDeleteListener listener) {
        this.deleteListener = listener;
    }

    public void setScreenshotsListener(OnTradeScreenshotsListener listener) {
        this.screenshotsListener = listener;
    }

    public void setSummaryListener(OnSummaryUpdateListener listener) {
        this.summaryListener = listener;
        notifySummary();
    }

    public void triggerSummaryUpdate() { notifySummary(); }

    private void notifySummary() {
        if (summaryListener == null || closedTrades == null) return;
        double totalPnl = 0;
        double totalInvested = 0;
        int wins = 0;
        for (StockData t : closedTrades) {
            double pnl = StockData.calculateProfitLoss(t);
            totalPnl += pnl;
            if (pnl > 0) wins++;
            totalInvested += t.tradeAmount;
        }
        summaryListener.onSummaryUpdated(totalPnl, wins, closedTrades.size(), totalInvested,
                ClosedTradeAnalytics.calculate(closedTrades));
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

        int bgCard        = ctx.getColor(R.color.bg_card);
        int textPrimary   = ctx.getColor(R.color.text_primary);
        int textSecondary = ctx.getColor(R.color.text_secondary);
        int colorGain     = ctx.getColor(R.color.gain);
        int colorLoss     = ctx.getColor(R.color.loss);
        int colorNeutral  = textSecondary;

        if (holder.itemView instanceof com.google.android.material.card.MaterialCardView) {
            ((com.google.android.material.card.MaterialCardView) holder.itemView)
                    .setCardBackgroundColor(bgCard);
        }

        String symbol = trade.symbol != null ? trade.symbol.toUpperCase(Locale.US) : "?";
        String tradeNumber = trade.tradeNumber != null && !trade.tradeNumber.trim().isEmpty()
                ? " " + trade.tradeNumber : "";
        holder.symbolText.setText(symbol + tradeNumber);
        holder.symbolText.setTextColor(textPrimary);
        java.text.DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(ctx);
        StringBuilder tradeDates = new StringBuilder();
        if (trade.openedAt > 0) {
            tradeDates.append("Opened ")
                    .append(dateFormat.format(new java.util.Date(trade.openedAt)));
        }
        if (trade.closedAt > 0) {
            if (tradeDates.length() > 0) tradeDates.append("  ·  ");
            tradeDates.append("Closed ")
                    .append(dateFormat.format(new java.util.Date(trade.closedAt)));
        }
        holder.tradeDateText.setText(tradeDates.toString());
        holder.tradeDateText.setVisibility(tradeDates.length() > 0 ? View.VISIBLE : View.GONE);

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

        String exitReason = formatExitReason(trade.exitReason);
        holder.exitReasonText.setText("Exit: " + exitReason);
        holder.exitReasonText.setTextColor(textSecondary);
        if (trade.strategyTag != null && !trade.strategyTag.trim().isEmpty()) {
            holder.strategyTagText.setText("Setup: " + trade.strategyTag.trim());
            holder.strategyTagText.setVisibility(View.VISIBLE);
        } else {
            holder.strategyTagText.setVisibility(View.GONE);
        }
        holder.sectorText.setText("Sector: "
                + (trade.sector == null || trade.sector.trim().isEmpty()
                ? "Other" : trade.sector.trim()));
        if ("AVAILABLE".equals(trade.missedPnlStatus) && trade.missedProfitLoss != null) {
            holder.missedPnlText.setText(String.format(Locale.US, "Missed P&L: $%.2f",
                    trade.missedProfitLoss));
            holder.missedPnlText.setVisibility(View.VISIBLE);
        } else if ("UNAVAILABLE".equals(trade.missedPnlStatus)) {
            holder.missedPnlText.setText("Missed P&L: unavailable");
            holder.missedPnlText.setVisibility(View.VISIBLE);
        } else if (trade.closedAt > 0) {
            holder.missedPnlText.setText("Missed P&L: calculating");
            holder.missedPnlText.setVisibility(View.VISIBLE);
        } else {
            holder.missedPnlText.setVisibility(View.GONE);
        }
        if (trade.openedAt > 0 && trade.closedAt >= trade.openedAt) {
            long holdingDays = (trade.closedAt - trade.openedAt) / 86_400_000L;
            holder.holdingDurationText.setText("Held: " + holdingDays + " days");
            holder.holdingDurationText.setVisibility(View.VISIBLE);
        } else {
            holder.holdingDurationText.setVisibility(View.GONE);
        }

        double pnlAbs = StockData.calculateProfitLoss(trade);
        double pnlPercent = StockData.calculateProfitLossPercent(trade);

        int    pnlColor;
        String pnlSign;
        if (pnlAbs > 0)      { pnlColor = colorGain;    pnlSign = "+"; }
        else if (pnlAbs < 0) { pnlColor = colorLoss;    pnlSign = "";  }
        else                 { pnlColor = colorNeutral;  pnlSign = "";  }

        holder.pnlView.setText(String.format(Locale.US, "%s$%.2f", pnlSign, pnlAbs));
        holder.pnlView.setTextColor(pnlColor);
        holder.percentText.setText(String.format(Locale.US, "%s%.2f%%", pnlSign, pnlPercent));
        holder.percentText.setTextColor(pnlColor);
        int pillColor = pnlAbs > 0 ? 0x3310B981 : pnlAbs < 0 ? 0x33FB5A75 : 0x332A303A;
        GradientDrawable percentPill = new GradientDrawable();
        percentPill.setShape(GradientDrawable.RECTANGLE);
        percentPill.setCornerRadius(100f);
        percentPill.setColor(pillColor);
        percentPill.setStroke(1, pnlColor);
        holder.percentText.setBackground(percentPill);
        float density = ctx.getResources().getDisplayMetrics().density;
        int horizontalPadding = Math.round(10 * density);
        int verticalPadding = Math.round(4 * density);
        holder.percentText.setPadding(horizontalPadding, verticalPadding,
                horizontalPadding, verticalPadding);

        if (holder.itemView instanceof com.google.android.material.card.MaterialCardView) {
            int strokeColor = pnlAbs > 0 ? 0xFF087F8C : ctx.getColor(R.color.colorBorder);
            ((com.google.android.material.card.MaterialCardView) holder.itemView)
                    .setStrokeColor(strokeColor);
        }

        // הצגת סכום הושקע בטרייד הסגור
        if (trade.tradeAmount > 0) {
            holder.investedAmountText.setText(String.format(Locale.US, "$%,.2f", trade.tradeAmount));
            holder.layoutClosedInvested.setVisibility(View.VISIBLE);
        } else {
            holder.layoutClosedInvested.setVisibility(View.GONE);
        }

        // ⭐ חדש — אחוז מהתיק הכולל
        if (totalPortfolioValue > 0 && trade.tradeAmount > 0) {
            double percentOfPortfolio = (trade.tradeAmount / totalPortfolioValue) * 100.0;
            holder.percentOfPortfolioText.setText(
                    String.format(Locale.US, "%.2f%%", percentOfPortfolio)
            );
            holder.percentOfPortfolioText.setVisibility(View.VISIBLE);
        } else {
            holder.percentOfPortfolioText.setVisibility(View.GONE);
        }

        holder.editButton.setOnClickListener(v -> {
            if (editListener != null) editListener.onEditTrade(trade);
        });

        if (holder.deleteButton != null) {
            holder.deleteButton.setOnClickListener(v -> {
                if (deleteListener != null) deleteListener.onDeleteTrade(trade);
            });
        }
        holder.screenshotsButton.setOnClickListener(v -> {
            if (screenshotsListener != null) screenshotsListener.onManageScreenshots(trade);
        });
    }

    private String formatExitReason(String reason) {
        if ("HIT_TP".equals(reason)) return "Hit TP";
        if ("HIT_SL".equals(reason)) return "Hit SL";
        if ("TRAILING_STOP".equals(reason)) return "Trailing Stop";
        if ("MANUAL_EXIT".equals(reason)) return "Manual Exit";
        return "Not recorded";
    }

    @Override
    public int getItemCount() {
        return closedTrades != null ? closedTrades.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView symbolInitial, symbolText, tradeDateText;
        TextView exitReasonText, holdingDurationText;
        TextView strategyTagText;
        TextView sectorText;
        TextView missedPnlText;
        TextView pnlView, percentText;
        TextView buyPriceView, sellPriceView, currPriceView;
        TextView investedAmountText;
        LinearLayout layoutClosedInvested;
        ImageButton editButton;
        ImageButton deleteButton;
        ImageButton screenshotsButton;

        // ⭐ חדש
        TextView percentOfPortfolioText;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            symbolInitial = itemView.findViewById(R.id.symbolInitial);
            symbolText = itemView.findViewById(R.id.tradeSymbolText);
            tradeDateText = itemView.findViewById(R.id.tradeDateText);
            exitReasonText = itemView.findViewById(R.id.tradeExitReason);
            holdingDurationText = itemView.findViewById(R.id.tradeHoldingDuration);
            strategyTagText = itemView.findViewById(R.id.tradeStrategyTag);
            sectorText = itemView.findViewById(R.id.tradeSector);
            missedPnlText = itemView.findViewById(R.id.tradeMissedPnl);

            pnlView = itemView.findViewById(R.id.pnlView);
            percentText = itemView.findViewById(R.id.tradeChangePercent);

            buyPriceView = itemView.findViewById(R.id.tradeBuyPrice);
            sellPriceView = itemView.findViewById(R.id.tradeSellingPrice);
            currPriceView = itemView.findViewById(R.id.tradeCurrentPrice);

            investedAmountText = itemView.findViewById(R.id.tvClosedInvestedAmount);
            layoutClosedInvested = itemView.findViewById(R.id.layoutClosedInvested);

            editButton = itemView.findViewById(R.id.btnEditTrade);
            deleteButton = itemView.findViewById(R.id.btnDeleteTrade);
            screenshotsButton = itemView.findViewById(R.id.btnTradeScreenshots);

            // ⭐ חדש
            percentOfPortfolioText = itemView.findViewById(R.id.percentOfPortfolioText);
        }
    }
}
