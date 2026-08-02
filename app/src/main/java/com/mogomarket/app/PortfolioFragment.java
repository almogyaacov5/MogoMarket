package com.mogomarket.app;

import androidx.navigation.Navigation;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class PortfolioFragment extends Fragment {

    private static final String TAG = "PortfolioFragment";
    private long lastRefreshTs = 0L;
    private RecyclerView recyclerView;
    private MaterialButton btnRefreshPortfolio;
    private MaterialButton btnAddStockToPortfolio;
    private TextView tvTotalPnl;
    private TextView tvTotalPct;
    private TextView tvOpenCount;
    private TextView tvDailyPnl;
    private TextView tvDailyPct;
    private TextView tvTotalInvested;

    private List<StockData> stocksList;
    private StocksAdapter adapter;
    private DatabaseReference portfolioRef;
    private DatabaseReference closedTradesRef;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build();

    private static final String FINNHUB_KEY = "d9ni1qpr01qjcq2r6po0d9ni1qpr01qjcq2r6pog";

    private boolean isGuest() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user != null && user.isAnonymous();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_portfolio, container, false);

        recyclerView = v.findViewById(R.id.tradesRecyclerView);
        btnAddStockToPortfolio = v.findViewById(R.id.btnAddStockToPortfolio);
        btnRefreshPortfolio = v.findViewById(R.id.btnRefreshPortfolio);
        tvTotalPnl = v.findViewById(R.id.tvTotalPnl);
        tvTotalPct = v.findViewById(R.id.tvTotalPct);
        tvOpenCount = v.findViewById(R.id.tvOpenCount);
        tvDailyPnl = v.findViewById(R.id.tvDailyPnl);
        tvDailyPct = v.findViewById(R.id.tvDailyPct);
        tvTotalInvested = v.findViewById(R.id.tvTotalInvested);

        stocksList = new ArrayList<>();

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.e(TAG, "Current user is null, redirecting to AuthLogin");
            startActivity(new Intent(requireContext(), AuthLogin.class));
            requireActivity().finish();
            return v;
        }

        String uid = user.getUid();
        Log.d(TAG, "Portfolio opened for uid=" + uid + ", anonymous=" + user.isAnonymous());

        portfolioRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(uid)
                .child("portfolio-stocks");

        closedTradesRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(uid)
                .child("closed-trades");

        adapter = new StocksAdapter(stocksList, new StocksAdapter.OnStockClickListener() {
            @Override
            public void onStockClick(String symbol) { }

            @Override
            public void onStockDelete(String symbol, double sellPrice) {
                if (isGuest()) {
                    Toast.makeText(getContext(),
                            "Guest mode - changes are disabled. Please sign in with an account.",
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                String firebaseKey = symbol.replace(":", "_");
                portfolioRef.child(firebaseKey).get().addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        StockData data = snapshot.getValue(StockData.class);
                        if (data != null) {
                            data.sellPrice = sellPrice;
                            closedTradesRef.child(firebaseKey).setValue(data);
                            portfolioRef.child(firebaseKey).removeValue();
                        }
                    }
                }).addOnFailureListener(e ->
                        Log.e(TAG, "Failed to move stock to closed-trades: " + symbol, e));
            }

            @Override
            public void onStockEdit(StockData updatedStock, String oldSymbol) {
                if (isGuest()) {
                    Toast.makeText(getContext(),
                            "Guest mode - editing is disabled. Please sign in with an account.",
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                String oldKey = oldSymbol.replace(":", "_");
                String newKey = updatedStock.symbol.replace(":", "_");

                if (!newKey.equals(oldKey)) {
                    portfolioRef.child(oldKey).removeValue();
                }

                portfolioRef.child(newKey).setValue(updatedStock)
                        .addOnSuccessListener(unused -> {
                            Log.d(TAG, "Stock updated successfully: " + updatedStock.symbol);
                            updateTotalInvested();
                            refreshPortfolioPnl();
                        })
                        .addOnFailureListener(e -> Log.e(TAG, "Failed to update stock", e));
            }
        });

        adapter.setTotalInvestedListener(totalInvested -> {
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if (tvTotalInvested != null) {
                    tvTotalInvested.setText(String.format(Locale.US, "$%,.2f", totalInvested));
                }
            });
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        portfolioRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Log.d(TAG, "portfolioRef onDataChange, exists=" + snapshot.exists()
                        + ", children=" + snapshot.getChildrenCount());

                stocksList.clear();

                for (DataSnapshot ds : snapshot.getChildren()) {
                    StockData data = ds.getValue(StockData.class);
                    if (data != null) {
                        stocksList.add(data);
                        Log.d(TAG, "Loaded stock from Firebase: symbol=" + data.symbol
                                + ", buyPrice=" + data.buyPrice
                                + ", tradeAmount=" + data.tradeAmount
                                + ", currentPrice=" + data.currentPrice);
                    } else {
                        Log.e(TAG, "Failed to parse StockData for key=" + ds.getKey());
                    }
                }

                adapter.notifyDataSetChanged();
                tvOpenCount.setText(String.valueOf(stocksList.size()));
                updateTotalInvested();

                // חשוב: לא לקרוא כאן refreshPortfolioPnl();
                // המחירים החיים יטענו רק בלחיצה על כפתור הריענון.


                adapter.notifyDataSetChanged();
                tvOpenCount.setText(String.valueOf(stocksList.size()));
                updateTotalInvested();
                updateSummaryFromLoadedData();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "portfolioRef cancelled: " + error.getMessage(), error.toException());
                Toast.makeText(getContext(), "Failed to load portfolio: " + error.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        });

        btnRefreshPortfolio.setOnClickListener(view -> {
            long now = System.currentTimeMillis();
            if (now - lastRefreshTs < 30_000) { // פחות מ-30 שניות מהרענון הקודם
                Toast.makeText(getContext(),
                        "Please wait a few seconds before refreshing again",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            lastRefreshTs = now;

            Log.d(TAG, "Manual portfolio refresh clicked");
            adapter.refreshPrices();   // אם יש לוגיקה פנימית באדפטר
            refreshPortfolioPnl();     // טוען מחירים חיים ומעדכן P&L
        });

        btnAddStockToPortfolio.setOnClickListener(view -> {
            if (isGuest()) {
                Toast.makeText(getContext(),
                        "Guest mode - cannot add trades. Please sign in with an account.",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            Navigation.findNavController(view).navigate(R.id.portfolioAddStockFragment);
        });

        return v;
    }

    private void updateTotalInvested() {
        if (stocksList == null || tvTotalInvested == null) return;

        double total = 0;
        for (StockData s : stocksList) {
            total += s.tradeAmount;
        }

        double finalTotal = total;
        if (getActivity() != null) {
            getActivity().runOnUiThread(() ->
                    tvTotalInvested.setText(String.format(Locale.US, "$%,.2f", finalTotal)));
        }
    }

    private void refreshPortfolioPnl() {
        if (stocksList == null || stocksList.isEmpty()) {
            Log.d(TAG, "refreshPortfolioPnl: stocksList is empty");
            if (tvTotalPnl != null) tvTotalPnl.setText("$0.00");
            if (tvTotalPct != null) tvTotalPct.setText("+0.00%");
            if (tvDailyPnl != null) tvDailyPnl.setText("$0.00");
            if (tvDailyPct != null) tvDailyPct.setText("+0.00%");
            return;
        }

        Log.d(TAG, "refreshPortfolioPnl started for " + stocksList.size() + " stocks");

        AtomicInteger pendingCount = new AtomicInteger(stocksList.size());
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        double[] totalPnlSum = {0};
        double[] totalInvestedSum = {0};
        double[] dailyPnlSum = {0};
        double[] dailyValueSum = {0};

        for (StockData stock : stocksList) {
            if (stock == null || stock.tradeAmount <= 0 || stock.buyPrice <= 0) {
                Log.e(TAG, "Skipping invalid stock: stock null or invalid values");
                finishOneRequest(pendingCount, successCount, failCount,
                        totalPnlSum[0], totalInvestedSum[0], dailyPnlSum[0], dailyValueSum[0]);
                continue;
            }

            String symbol = stock.symbol != null ? stock.symbol.trim() : "";
            if (symbol.isEmpty()) {
                Log.e(TAG, "Skipping stock with empty symbol");
                finishOneRequest(pendingCount, successCount, failCount,
                        totalPnlSum[0], totalInvestedSum[0], dailyPnlSum[0], dailyValueSum[0]);
                continue;
            }

            boolean isCrypto = CryptoHelper.isCryptoSymbol(symbol);
            String requestUrl = isCrypto
                    ? "https://api.binance.com/api/v3/ticker/24hr?symbol=" + CryptoHelper.getPair(symbol)
                    : "https://finnhub.io/api/v1/quote?symbol=" + symbol + "&token=" + FINNHUB_KEY;

            Log.d(TAG, "Requesting live price for " + symbol + " | url=" + requestUrl);

            Request request = new Request.Builder()
                    .url(requestUrl)
                    .get()
                    .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.e(TAG, "Price request failed for " + symbol, e);
                    failCount.incrementAndGet();
                    finishOneRequest(pendingCount, successCount, failCount,
                            totalPnlSum[0], totalInvestedSum[0], dailyPnlSum[0], dailyValueSum[0]);
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    try {
                        if (!response.isSuccessful()) {
                            Log.e(TAG, "HTTP error for " + symbol + ": code=" + response.code());
                            failCount.incrementAndGet();
                            return;
                        }

                        String responseBody = response.body() != null ? response.body().string() : "";
                        Log.d(TAG, "Response for " + symbol + ": " + responseBody);

                        JSONObject json = new JSONObject(responseBody);

                        float liveCurrentPrice;
                        float liveDailyChangePercent;

                        if (isCrypto) {
                            liveCurrentPrice = (float) json.optDouble("lastPrice", 0.0);
                            liveDailyChangePercent = (float) json.optDouble("priceChangePercent", 0.0);
                        } else {
                            liveCurrentPrice = (float) json.optDouble("c", 0.0);
                            liveDailyChangePercent = (float) json.optDouble("dp", 0.0);
                        }

                        if (liveCurrentPrice <= 0) {
                            Log.e(TAG, "Invalid liveCurrentPrice for " + symbol + ": " + liveCurrentPrice);
                            failCount.incrementAndGet();
                            return;
                        }

                        double investedAmount = stock.tradeAmount;
                        double quantity = investedAmount / stock.buyPrice;
                        double currentValue = quantity * liveCurrentPrice;

                        double totalProfitLoss = currentValue - investedAmount;
                        double totalProfitLossPercent =
                                investedAmount > 0 ? (totalProfitLoss / investedAmount) * 100.0 : 0.0;

                        double dailyProfitLoss = currentValue * (liveDailyChangePercent / 100.0);
                        double dailyProfitLossPercent = liveDailyChangePercent;

                        synchronized (totalPnlSum) {
                            totalPnlSum[0] += totalProfitLoss;
                            totalInvestedSum[0] += investedAmount;
                            dailyPnlSum[0] += dailyProfitLoss;
                            dailyValueSum[0] += currentValue;
                        }

                        stock.currentPrice = liveCurrentPrice;
                        stock.changePercent = (float) totalProfitLossPercent;
                        stock.currentValue = currentValue;
                        stock.profitLoss = totalProfitLoss;
                        stock.profitLossPercent = totalProfitLossPercent;
                        stock.dailyProfitLoss = dailyProfitLoss;
                        stock.dailyProfitLossPercent = dailyProfitLossPercent;

                        String firebaseKey = symbol.replace(":", "_");
                        portfolioRef.child(firebaseKey).child("currentPrice").setValue(stock.currentPrice);
                        portfolioRef.child(firebaseKey).child("changePercent").setValue(stock.changePercent);
                        portfolioRef.child(firebaseKey).child("currentValue").setValue(stock.currentValue);
                        portfolioRef.child(firebaseKey).child("profitLoss").setValue(stock.profitLoss);
                        portfolioRef.child(firebaseKey).child("profitLossPercent").setValue(stock.profitLossPercent);
                        portfolioRef.child(firebaseKey).child("dailyProfitLoss").setValue(stock.dailyProfitLoss);
                        portfolioRef.child(firebaseKey).child("dailyProfitLossPercent").setValue(stock.dailyProfitLossPercent);

                        successCount.incrementAndGet();
                        Log.d(TAG, "Updated live data for " + symbol + " | price=" + liveCurrentPrice);

                    } catch (Exception e) {
                        Log.e(TAG, "Failed parsing/updating live price for " + symbol, e);
                        failCount.incrementAndGet();
                    } finally {
                        finishOneRequest(pendingCount, successCount, failCount,
                                totalPnlSum[0], totalInvestedSum[0], dailyPnlSum[0], dailyValueSum[0]);
                    }
                }
            });
        }
    }

    private void finishOneRequest(AtomicInteger pendingCount,
                                  AtomicInteger successCount,
                                  AtomicInteger failCount,
                                  double totalPnl,
                                  double totalInv,
                                  double dailyPnl,
                                  double dailyInv) {

        if (pendingCount.decrementAndGet() == 0) {
            Log.d(TAG, "All price requests finished | success=" + successCount.get()
                    + " | failed=" + failCount.get());

            updatePnlUI(totalPnl, totalInv, dailyPnl, dailyInv);

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    adapter.notifyDataSetChanged();

                    if (successCount.get() == 0 && !stocksList.isEmpty()) {
                        Toast.makeText(getContext(),
                                "Failed to load live prices. Check symbols or network/API response.",
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        }
    }

    private void updatePnlUI(double totalPnl, double totalInv, double dailyPnl, double dailyInv) {
        if (getActivity() == null) return;

        getActivity().runOnUiThread(() -> {
            if (tvTotalPnl == null) return;

            String pnlSign = totalPnl >= 0 ? "+" : "";
            tvTotalPnl.setText(String.format(Locale.US, "%s$%.2f", pnlSign, totalPnl));
            tvTotalPnl.setTextColor(totalPnl >= 0 ? Color.parseColor("#00E676") : Color.parseColor("#FF5252"));

            double totalPct = (totalInv > 0) ? (totalPnl / totalInv * 100) : 0;
            String pctSign = totalPct >= 0 ? "+" : "";
            tvTotalPct.setText(String.format(Locale.US, "%s%.2f%%", pctSign, totalPct));
            tvTotalPct.setTextColor(totalPct >= 0 ? Color.parseColor("#00E676") : Color.parseColor("#FF5252"));

            String dSign = dailyPnl >= 0 ? "+" : "";
            tvDailyPnl.setText(String.format(Locale.US, "%s$%.2f", dSign, dailyPnl));
            tvDailyPnl.setTextColor(dailyPnl >= 0 ? Color.parseColor("#00E676") : Color.parseColor("#FF5252"));

            double dailyPct = (dailyInv > 0) ? (dailyPnl / dailyInv * 100) : 0;
            String dPctSign = dailyPct >= 0 ? "+" : "";
            tvDailyPct.setText(String.format(Locale.US, "%s%.2f%%", dPctSign, dailyPct));
            tvDailyPct.setTextColor(dailyPct >= 0 ? Color.parseColor("#00E676") : Color.parseColor("#FF5252"));
        });
    }

    private void updateSummaryFromLoadedData() {
        if (getActivity() == null) return;

        if (stocksList == null || stocksList.isEmpty()) {
            getActivity().runOnUiThread(() -> {
                if (tvTotalPnl != null) tvTotalPnl.setText("$0.00");
                if (tvTotalPct != null) tvTotalPct.setText("+0.00%");
                if (tvDailyPnl != null) tvDailyPnl.setText("$0.00");
                if (tvDailyPct != null) tvDailyPct.setText("+0.00%");
                if (tvOpenCount != null) tvOpenCount.setText("0");
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

        final double finalTotalPnl = totalPnl;
        final double finalTotalPct = totalPct;
        final double finalDailyPnl = dailyPnl;
        final double finalDailyPct = dailyPct;

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
        });
    }
}