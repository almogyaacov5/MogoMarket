package com.mogomarket.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

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
    private TextView tvTotalPnl;
    private TextView tvOpenCount;
    private TextView tvDailyPnl;
    private TextView tvDailyPct;

    private List<StockData> stocksList;
    private StocksAdapter adapter;
    private DatabaseReference portfolioRef;
    private DatabaseReference closedTradesRef;

    private final OkHttpClient httpClient = new OkHttpClient();
    private static final String FINNHUB_KEY = "d918pn9r01qr1uqui560d918pn9r01qr1uqui56g";

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
        tvOpenCount            = v.findViewById(R.id.tvOpenCount);
        tvDailyPnl             = v.findViewById(R.id.tvDailyPnl);
        tvDailyPct             = v.findViewById(R.id.tvDailyPct);

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
                String oldKey = oldSymbol.replace(":", "_");
                String newKey = updatedStock.symbol.replace(":", "_");
                if (!newKey.equals(oldKey)) {
                    portfolioRef.child(oldKey).removeValue();
                }
                portfolioRef.child(newKey).setValue(updatedStock);
            }
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        portfolioRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                stocksList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    StockData data = ds.getValue(StockData.class);
                    if (data == null) continue;
                    if (data.symbol == null || data.symbol.isEmpty()) {
                        String key = ds.getKey();
                        if (key != null) data.symbol = key.replace("_BINANCE", ":BINANCE")
                                .replaceFirst("BINANCE_", "BINANCE:");
                    }
                    stocksList.add(data);
                }
                adapter.notifyDataSetChanged();
                updateSummary();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });

        btnAddStockToPortfolio.setOnClickListener(view -> {
            requireActivity().getSupportFragmentManager().beginTransaction()
                    .setCustomAnimations(R.anim.fade_in_fast, R.anim.fade_out_fast)
                    .replace(R.id.fragment_container, new PortfolioAddStockFragment())
                    .addToBackStack(null)
                    .commit();
        });

        btnRefreshPortfolio.setOnClickListener(view -> {
            adapter.refreshPrices();
            updateSummary();
        });

        return v;
    }

    // ==================== סיכום PnL ====================

    private void updateSummary() {
        if (tvOpenCount != null) {
            tvOpenCount.setText(String.valueOf(stocksList.size()));
        }
        if (stocksList.isEmpty()) {
            if (tvTotalPnl != null) tvTotalPnl.setText("$0.00");
            if (tvDailyPnl != null) tvDailyPnl.setText("$0.00");
            if (tvDailyPct != null) tvDailyPct.setText("+0.00%");
            return;
        }

        List<StockData> withAmount = new ArrayList<>();
        for (StockData s : stocksList) {
            if (s.tradeAmount > 0) withAmount.add(s);
        }
        if (withAmount.isEmpty()) {
            if (tvTotalPnl != null) tvTotalPnl.setText("N/A");
            if (tvDailyPnl != null) tvDailyPnl.setText("N/A");
            if (tvDailyPct != null) tvDailyPct.setText("-");
            return;
        }

        final double[] totalPnl  = {0.0};
        final double[] dailyPnl  = {0.0};
        final double[] totalInvested = {0.0};
        final AtomicInteger remaining = new AtomicInteger(withAmount.size());

        for (StockData stock : withAmount) {
            totalInvested[0] += stock.tradeAmount;

            if (CryptoHelper.isCryptoSymbol(stock.symbol)) {
                // Binance 24hr ticker
                String pair = CryptoHelper.getPair(stock.symbol);
                String url  = "https://api.binance.com/api/v3/ticker/24hr?symbol=" + pair;
                httpClient.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
                    @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        if (remaining.decrementAndGet() == 0)
                            showSummary(totalPnl[0], dailyPnl[0], totalInvested[0]);
                    }
                    @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        try {
                            JSONObject obj = new JSONObject(response.body().string());
                            float currentPrice       = (float) obj.getDouble("lastPrice");
                            float dailyChangePct     = (float) obj.getDouble("priceChangePercent");
                            if (currentPrice > 0 && stock.buyPrice != 0f) {
                                float totalPct = (currentPrice - stock.buyPrice) / stock.buyPrice * 100f;
                                double pnl     = stock.tradeAmount * (totalPct / 100.0);
                                // רווח/הפסד יומי: dailyChangePct הוא % שינוי ב-24ש על המחיר
                                // הסכום שהושקע * dailyChangePct / 100 = רווח יומי על ההשקעה הנוכחית
                                double dPnl    = stock.tradeAmount * (1 + totalPct / 100.0) * (dailyChangePct / 100.0);
                                synchronized (totalPnl) {
                                    totalPnl[0] += pnl;
                                    dailyPnl[0] += dPnl;
                                }
                            }
                        } catch (Exception ignored) {}
                        if (remaining.decrementAndGet() == 0)
                            showSummary(totalPnl[0], dailyPnl[0], totalInvested[0]);
                    }
                });
            } else {
                // Finnhub /quote  c=current d=change dp=change%
                String url = "https://finnhub.io/api/v1/quote?symbol=" + stock.symbol + "&token=" + FINNHUB_KEY;
                httpClient.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
                    @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        if (remaining.decrementAndGet() == 0)
                            showSummary(totalPnl[0], dailyPnl[0], totalInvested[0]);
                    }
                    @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        try {
                            JSONObject obj = new JSONObject(response.body().string());
                            float currentPrice   = (float) obj.getDouble("c");  // current
                            float dailyChangePct = (float) obj.getDouble("dp"); // daily % change
                            if (currentPrice > 0 && stock.buyPrice != 0f) {
                                float totalPct = (currentPrice - stock.buyPrice) / stock.buyPrice * 100f;
                                double pnl     = stock.tradeAmount * (totalPct / 100.0);
                                double dPnl    = stock.tradeAmount * (1 + totalPct / 100.0) * (dailyChangePct / 100.0);
                                synchronized (totalPnl) {
                                    totalPnl[0] += pnl;
                                    dailyPnl[0] += dPnl;
                                }
                            }
                        } catch (Exception ignored) {}
                        if (remaining.decrementAndGet() == 0)
                            showSummary(totalPnl[0], dailyPnl[0], totalInvested[0]);
                    }
                });
            }
        }
    }

    private void showSummary(double totalPnl, double dailyPnl, double totalInvested) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            try {
                int colorGain = requireContext().getColor(R.color.gain);
                int colorLoss = requireContext().getColor(R.color.loss);

                // Total P&L
                if (tvTotalPnl != null) {
                    String sign = totalPnl >= 0 ? "+" : "";
                    tvTotalPnl.setText(String.format(Locale.US, "%s$%.2f", sign, totalPnl));
                    tvTotalPnl.setTextColor(totalPnl >= 0 ? colorGain : colorLoss);
                }

                // Daily P&L amount
                if (tvDailyPnl != null) {
                    String sign = dailyPnl >= 0 ? "+" : "";
                    tvDailyPnl.setText(String.format(Locale.US, "%s$%.2f", sign, dailyPnl));
                    tvDailyPnl.setTextColor(dailyPnl >= 0 ? colorGain : colorLoss);
                }

                // Daily P&L percent (relative to total invested)
                if (tvDailyPct != null && totalInvested > 0) {
                    double dailyPct = (dailyPnl / totalInvested) * 100.0;
                    String pctSign  = dailyPct >= 0 ? "+" : "";
                    tvDailyPct.setText(String.format(Locale.US, "%s%.2f%%", pctSign, dailyPct));
                    tvDailyPct.setTextColor(dailyPct >= 0 ? colorGain : colorLoss);
                }
            } catch (Exception ignored) {}
        });
    }
}
