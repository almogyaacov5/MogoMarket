package com.mogomarket.app;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;

public class PortfolioFragment extends Fragment {

    private RecyclerView recyclerView;
    private ClosedTradesAdapter closedTradesAdapter;

    private TextView tvTotalPnl, tvTotalPct, tvDailyPnl, tvDailyPct;
    private TextView tvOpenCount, tvCash, tvPortfolioValue;

    private List<StockData> stocksList;

    // ⭐ שווי תיק כולל
    private double totalPortfolioValue = 0.0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_portfolio, container, false);

        tvTotalPnl        = v.findViewById(R.id.tvTotalPnl);
        tvTotalPct        = v.findViewById(R.id.tvTotalPct);
        tvDailyPnl        = v.findViewById(R.id.tvDailyPnl);
        tvDailyPct        = v.findViewById(R.id.tvDailyPct);
        tvOpenCount       = v.findViewById(R.id.tvOpenCount);
        tvCash            = v.findViewById(R.id.tvCash);
        tvPortfolioValue  = v.findViewById(R.id.tvPortfolioValue);

        recyclerView = v.findViewById(R.id.recyclerClosedTrades);

        return v;
    }

    public void setClosedTrades(List<StockData> trades) {
        this.stocksList = trades;

        closedTradesAdapter = new ClosedTradesAdapter(trades, trade -> {
            // עריכת טרייד
        });

        recyclerView.setAdapter(closedTradesAdapter);

        updateSummaryFromLoadedData();
    }

    private void updateSummaryFromLoadedData() {
        if (getActivity() == null) return;

        if (stocksList == null || stocksList.isEmpty()) {
            getActivity().runOnUiThread(() -> {
                tvTotalPnl.setText("$0.00");
                tvTotalPct.setText("+0.00%");
                tvDailyPnl.setText("$0.00");
                tvDailyPct.setText("+0.00%");
                tvOpenCount.setText("0");

                tvCash.setText("$0.00");
                tvPortfolioValue.setText("$0.00");
            });
            return;
        }

        double totalInvested = 0.0;
        double totalPnl = 0.0;
        double dailyPnl = 0.0;
        double totalCurrentValue = 0.0;

        for (StockData stock : stocksList) {
            if (stock == null) continue;
            if (stock.buyPrice <= 0 || stock.tradeAmount <= 0) continue;

            double investedAmount = stock.tradeAmount;
            totalInvested += investedAmount;

            double currentPrice = stock.currentPrice > 0 ? stock.currentPrice : stock.buyPrice;
            double quantity = investedAmount / stock.buyPrice;
            double currentValue = quantity * currentPrice;
            double pnl = currentValue - investedAmount;

            totalCurrentValue += currentValue;
            totalPnl += pnl;

            if (stock.dailyProfitLoss != 0) {
                dailyPnl += stock.dailyProfitLoss;
            } else if (stock.dailyProfitLossPercent != 0) {
                dailyPnl += currentValue * (stock.dailyProfitLossPercent / 100.0);
            }
        }

        double totalPct = totalInvested > 0 ? (totalPnl / totalInvested) * 100.0 : 0.0;
        double dailyPct = totalCurrentValue > 0 ? (dailyPnl / totalCurrentValue) * 100.0 : 0.0;

        // ⭐ חישוב מזומן בתיק
        double cash = totalCurrentValue - totalInvested;

        // ⭐ שמירת שווי תיק כולל
        this.totalPortfolioValue = totalCurrentValue;

        // ⭐ שליחת שווי תיק ל־Adapter
        if (closedTradesAdapter != null) {
            closedTradesAdapter.setPortfolioValue(totalPortfolioValue);
        }

        final double finalTotalPnl = totalPnl;
        final double finalTotalPct = totalPct;
        final double finalDailyPnl = dailyPnl;
        final double finalDailyPct = dailyPct;
        final double finalCash = cash;
        final double finalPortfolioValue = totalCurrentValue;

        getActivity().runOnUiThread(() -> {

            String pnlSign = finalTotalPnl >= 0 ? "+" : "";
            tvTotalPnl.setText(String.format(Locale.US, "%s$%.2f", pnlSign, finalTotalPnl));
            tvTotalPnl.setTextColor(finalTotalPnl >= 0
                    ? Color.parseColor("#00E676")
                    : Color.parseColor("#FF5252"));

            String pctSign = finalTotalPct >= 0 ? "+" : "";
            tvTotalPct.setText(String.format(Locale.US, "%s%.2f%%", pctSign, finalTotalPct));
            tvTotalPct.setTextColor(finalTotalPct >= 0
                    ? Color.parseColor("#00E676")
                    : Color.parseColor("#FF5252"));

            String dailySign = finalDailyPnl >= 0 ? "+" : "";
            tvDailyPnl.setText(String.format(Locale.US, "%s$%.2f", dailySign, finalDailyPnl));
            tvDailyPnl.setTextColor(finalDailyPnl >= 0
                    ? Color.parseColor("#00E676")
                    : Color.parseColor("#FF5252"));

            String dailyPctSign = finalDailyPct >= 0 ? "+" : "";
            tvDailyPct.setText(String.format(Locale.US, "%s%.2f%%", dailyPctSign, finalDailyPct));
            tvDailyPct.setTextColor(finalDailyPct >= 0
                    ? Color.parseColor("#00E676")
                    : Color.parseColor("#FF5252"));

            // ⭐ הצגת מזומן ושווי תיק
            tvCash.setText(String.format(Locale.US, "$%.2f", finalCash));
            tvPortfolioValue.setText(String.format(Locale.US, "$%.2f", finalPortfolioValue));
        });
    }
}
