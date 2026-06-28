package com.example.chart;

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

    private List<StockData> stocksList;
    private StocksAdapter adapter;
    private DatabaseReference portfolioRef;
    private DatabaseReference closedTradesRef;

    private final OkHttpClient httpClient = new OkHttpClient();
    private static final String API_KEY = "0518811f0d394fa39842a8024a25c049";

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
                portfolioRef.child(symbol).get().addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        StockData data = snapshot.getValue(StockData.class);
                        if (data != null) {
                            data.sellPrice = sellPrice;
                            closedTradesRef.child(symbol).setValue(data);
                            portfolioRef.child(symbol).removeValue();
                        }
                    }
                });
            }

            @Override
            public void onStockEdit(StockData updatedStock, String oldSymbol) {
                if (!updatedStock.symbol.equals(oldSymbol)) {
                    portfolioRef.child(oldSymbol).removeValue();
                }
                portfolioRef.child(updatedStock.symbol).setValue(updatedStock);
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
                    if (data != null) stocksList.add(data);
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

    // ==================== עדכון סיכום ====================

    private void updateSummary() {
        if (tvOpenCount != null) {
            tvOpenCount.setText(String.valueOf(stocksList.size()));
        }

        if (stocksList.isEmpty()) {
            if (tvTotalPnl != null) tvTotalPnl.setText("$0.00");
            return;
        }

        // סופרים כמה מניות יש עם tradeAmount > 0 (אחרת לא ניתן לחשב P&L)
        List<StockData> withAmount = new ArrayList<>();
        for (StockData s : stocksList) {
            if (s.tradeAmount > 0) withAmount.add(s);
        }

        if (withAmount.isEmpty()) {
            if (tvTotalPnl != null) tvTotalPnl.setText("N/A");
            return;
        }

        // מביאים מחיר עדכני לכל מניה עם סכום ומסכמים
        final double[] totalPnl = {0.0};
        final AtomicInteger remaining = new AtomicInteger(withAmount.size());

        for (StockData stock : withAmount) {
            String url = "https://api.twelvedata.com/price?symbol=" + stock.symbol + "&apikey=" + API_KEY;
            httpClient.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    if (remaining.decrementAndGet() == 0) showTotalPnl(totalPnl[0]);
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    try {
                        String body = response.body().string();
                        JSONObject obj = new JSONObject(body);
                        float price = Float.parseFloat(obj.getString("price"));
                        float pct = (stock.buyPrice != 0f)
                                ? ((price - stock.buyPrice) / stock.buyPrice * 100f) : 0f;
                        double pnl = stock.tradeAmount * (pct / 100.0);
                        synchronized (totalPnl) { totalPnl[0] += pnl; }
                    } catch (Exception ignored) {}
                    if (remaining.decrementAndGet() == 0) showTotalPnl(totalPnl[0]);
                }
            });
        }
    }

    private void showTotalPnl(double pnl) {
        if (getActivity() == null || tvTotalPnl == null) return;
        getActivity().runOnUiThread(() -> {
            String sign = pnl >= 0 ? "+" : "";
            tvTotalPnl.setText(String.format(Locale.US, "%s$%.2f", sign, pnl));
            try {
                int colorGain = requireContext().getColor(R.color.gain);
                int colorLoss = requireContext().getColor(R.color.loss);
                tvTotalPnl.setTextColor(pnl >= 0 ? colorGain : colorLoss);
            } catch (Exception ignored) {}
        });
    }
}
