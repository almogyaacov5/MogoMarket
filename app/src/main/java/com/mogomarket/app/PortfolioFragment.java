package com.mogomarket.app;

import androidx.navigation.Navigation;
import android.content.Intent;
import android.os.Bundle;
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
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class PortfolioFragment extends Fragment {

    private RecyclerView recyclerView;
    private MaterialButton btnRefreshPortfolio;
    private MaterialButton btnAddStockToPortfolio;
    private MaterialButton btnPortfolioChart;
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

    private final OkHttpClient httpClient = new OkHttpClient();
    private static final String FINNHUB_KEY = "d918pn9r01qr1uqui560d918pn9r01qr1uqui56g";

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

        recyclerView           = v.findViewById(R.id.tradesRecyclerView);
        btnAddStockToPortfolio = v.findViewById(R.id.btnAddStockToPortfolio);
        btnRefreshPortfolio    = v.findViewById(R.id.btnRefreshPortfolio);
        tvTotalPnl             = v.findViewById(R.id.tvTotalPnl);
        tvTotalPct             = v.findViewById(R.id.tvTotalPct);
        tvOpenCount            = v.findViewById(R.id.tvOpenCount);
        tvDailyPnl             = v.findViewById(R.id.tvDailyPnl);
        tvDailyPct             = v.findViewById(R.id.tvDailyPct);
        tvTotalInvested        = v.findViewById(R.id.tvTotalInvested);

        stocksList = new ArrayList<>();

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            startActivity(new Intent(requireContext(), AuthLogin.class));
            requireActivity().finish();
            return v;
        }

        String uid = user.getUid();

        portfolioRef = FirebaseDatabase.getInstance()
                .getReference("users").child(uid).child("portfolio-stocks");

        closedTradesRef = FirebaseDatabase.getInstance()
                .getReference("users").child(uid).child("closed-trades");

        adapter = new StocksAdapter(stocksList, new StocksAdapter.OnStockClickListener() {
            @Override
            public void onStockClick(String symbol) { }

            @Override
            public void onStockDelete(String symbol, double sellPrice) {
                if (isGuest()) {
                    Toast.makeText(getContext(),
                            "כניסה כאורח — לא ניתן לבצע שינויים. התחבר עם חשבון.",
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
                });
            }

            @Override
            public void onStockEdit(StockData updatedStock, String oldSymbol) {
                if (isGuest()) {
                    Toast.makeText(getContext(),
                            "כניסה כאורח — לא ניתן לערוך עסקאות. התחבר עם חשבון.",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                String oldKey = oldSymbol.replace(":", "_");
                String newKey = updatedStock.symbol.replace(":", "_");
                if (!newKey.equals(oldKey)) {
                    portfolioRef.child(oldKey).removeValue();
                }
                portfolioRef.child(newKey).setValue(updatedStock);
                updateTotalInvested();
            }
        });

        // מאזין לשינויים בסה"כ הושקע
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
                stocksList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    StockData data = ds.getValue(StockData.class);
                    if (data != null) stocksList.add(data);
                }
                adapter.notifyDataSetChanged();
                tvOpenCount.setText(String.valueOf(stocksList.size()));
                updateTotalInvested();
                refreshPortfolioPnl();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(getContext(), "שגיאה בטעינת נתונים", Toast.LENGTH_SHORT).show();
            }
        });

        btnRefreshPortfolio.setOnClickListener(view -> {
            adapter.refreshPrices();
            refreshPortfolioPnl();
        });

        btnAddStockToPortfolio.setOnClickListener(view -> {
            if (isGuest()) {
                Toast.makeText(getContext(),
                        "כניסה כאורח — לא ניתן להוסיף עסקאות. התחבר עם חשבון.",
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
            if (tvTotalPnl != null) tvTotalPnl.setText("$0.00");
            if (tvTotalPct != null) tvTotalPct.setText("+0.00%");
            if (tvDailyPnl != null) tvDailyPnl.setText("$0.00");
            if (tvDailyPct != null) tvDailyPct.setText("+0.00%");
            return;
        }

        AtomicInteger pendingCount = new AtomicInteger(stocksList.size());
        double[] totalPnlSum = {0};
        double[] totalInvestedSum = {0};
        double[] dailyPnlSum = {0};
        double[] dailyValueSum = {0};

        for (StockData stock : stocksList) {
            if (stock == null || stock.tradeAmount <= 0 || stock.buyPrice <= 0) {
                if (pendingCount.decrementAndGet() == 0) {
                    updatePnlUI(
                            totalPnlSum[0],
                            totalInvestedSum[0],
                            dailyPnlSum[0],
                            dailyValueSum[0]
                    );
                }
                continue;
            }

            String symbol = stock.symbol != null ? stock.symbol.trim() : "";
            if (symbol.isEmpty()) {
                if (pendingCount.decrementAndGet() == 0) {
                    updatePnlUI(
                            totalPnlSum[0],
                            totalInvestedSum[0],
                            dailyPnlSum[0],
                            dailyValueSum[0]
                    );
                }
                continue;
            }

            boolean isCrypto = CryptoHelper.isCryptoSymbol(symbol);
            String requestUrl = isCrypto
                    ? "https://api.binance.com/api/v3/ticker/24hr?symbol=" + CryptoHelper.getPair(symbol)
                    : "https://finnhub.io/api/v1/quote?symbol=" + symbol + "&token=" + FINNHUB_KEY;

            Request request = new Request.Builder()
                    .url(requestUrl)
                    .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    if (pendingCount.decrementAndGet() == 0) {
                        updatePnlUI(
                                totalPnlSum[0],
                                totalInvestedSum[0],
                                dailyPnlSum[0],
                                dailyValueSum[0]
                        );
                    }
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    try {
                        String responseBody = response.body() != null ? response.body().string() : "";
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

                        if (liveCurrentPrice > 0) {
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
                        }

                    } catch (Exception ignored) {
                    }

                    if (pendingCount.decrementAndGet() == 0) {
                        updatePnlUI(
                                totalPnlSum[0],
                                totalInvestedSum[0],
                                dailyPnlSum[0],
                                dailyValueSum[0]
                        );
                    }
                }
            });
        }
    }

    private void updatePnlUI(double totalPnl, double totalInv, double dailyPnl, double dailyInv) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            if (tvTotalPnl == null) return;
            String pnlSign = totalPnl >= 0 ? "+" : "";
            tvTotalPnl.setText(String.format(Locale.US, "%s$%.2f", pnlSign, totalPnl));
            tvTotalPnl.setTextColor(totalPnl >= 0 ? 0xFF00E676 : 0xFFFF5252);

            double totalPct = (totalInv > 0) ? (totalPnl / totalInv * 100) : 0;
            String pctSign = totalPct >= 0 ? "+" : "";
            tvTotalPct.setText(String.format(Locale.US, "%s%.2f%%", pctSign, totalPct));
            tvTotalPct.setTextColor(totalPct >= 0 ? 0xFF00E676 : 0xFFFF5252);

            String dSign = dailyPnl >= 0 ? "+" : "";
            tvDailyPnl.setText(String.format(Locale.US, "%s$%.2f", dSign, dailyPnl));
            tvDailyPnl.setTextColor(dailyPnl >= 0 ? 0xFF00E676 : 0xFFFF5252);

            double dailyPct = (dailyInv > 0) ? (dailyPnl / dailyInv * 100) : 0;
            String dPctSign = dailyPct >= 0 ? "+" : "";
            tvDailyPct.setText(String.format(Locale.US, "%s%.2f%%", dPctSign, dailyPct));
            tvDailyPct.setTextColor(dailyPct >= 0 ? 0xFF00E676 : 0xFFFF5252);
        });
    }
}
