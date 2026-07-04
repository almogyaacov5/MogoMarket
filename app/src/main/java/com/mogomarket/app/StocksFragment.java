package com.mogomarket.app;

import android.content.Context;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

// פרגמנט ישן לרשימת מניות (לא מחובר לפי UID - שמור לתאימות אחורה)
// הפרגמנט החדש הוא WatchlistFragment
public class StocksFragment extends Fragment implements StocksAdapter.OnStockClickListener {

    private RecyclerView stocksRecyclerView;
    private AutoCompleteTextView stockInput;   // שונה מ-EditText ל-AutoCompleteTextView
    private Button addStockBtn;
    private ImageButton btnRefreshAll;
    private StocksAdapter adapter;
    private List<StockData> stocksList = new ArrayList<>();
    private DatabaseReference stocksRef;
    private final OkHttpClient client = new OkHttpClient();
    private static final String API_KEY = "d918pn9r01qr1uqui560d918pn9r01qr1uqui56g";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_watchlist, container, false);

        stocksRecyclerView = view.findViewById(R.id.watchlistRecyclerView);
        stockInput         = view.findViewById(R.id.stockAutoInput);  // ID חדש
        addStockBtn        = view.findViewById(R.id.addStockBtn);
        btnRefreshAll      = view.findViewById(R.id.btnRefreshWatchlist);

        adapter = new StocksAdapter(stocksList, this);
        stocksRecyclerView.setAdapter(adapter);
        stocksRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        stocksRef = FirebaseDatabase.getInstance().getReference("user-stocks");

        loadStocks();

        if (btnRefreshAll != null)
            btnRefreshAll.setOnClickListener(v -> reloadAllStocks());

        if (addStockBtn != null) {
            addStockBtn.setOnClickListener(v -> {
                if (stockInput == null) return;

                String symbol = stockInput.getText().toString().trim().toUpperCase();
                if (symbol.isEmpty()) return;

                stocksRef.child(symbol).setValue(true);
                fetchStockInfo(symbol);

                stockInput.setText("");
                stockInput.clearFocus();
                hideKeyboard();
            });
        }

        if (stockInput != null)
            stockInput.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    stockInput.clearFocus();
                    hideKeyboard();
                    return true;
                }
                return false;
            });

        return view;
    }

    private void reloadAllStocks() {
        stocksRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                stocksList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    String symbol = ds.getKey();
                    fetchStockInfo(symbol);
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
    }

    private void hideKeyboard() {
        View view = getActivity().getCurrentFocus();
        if (view == null) view = getView();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void loadStocks() {
        stocksRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                stocksList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    String symbol = ds.getKey();
                    fetchStockInfo(symbol);
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(getContext(), "Failed to load stocks", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchStockInfo(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) return;

        String cleanSymbol = symbol.trim().toUpperCase();

        boolean isCrypto = CryptoHelper.isCryptoSymbol(cleanSymbol);
        boolean isForex = cleanSymbol.contains("/") && !isCrypto;

        String url;

        if (isCrypto) {
            String pair = CryptoHelper.getPair(cleanSymbol);
            url = "https://api.binance.com/api/v3/ticker/24hr?symbol=" + pair;
        } else if (isForex) {
            String forexSymbol = "OANDA:" + cleanSymbol.replace("/", "_");
            url = "https://finnhub.io/api/v1/quote?symbol=" + forexSymbol + "&token=" + API_KEY;
        } else {
            url = "https://finnhub.io/api/v1/quote?symbol=" + cleanSymbol + "&token=" + API_KEY;
        }

        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() ->
                            Toast.makeText(getContext(),
                                    "Failed to load " + cleanSymbol,
                                    Toast.LENGTH_SHORT).show());
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (response.body() == null) return;

                    String responseBody = response.body().string();
                    JSONObject json = new JSONObject(responseBody);

                    float lastPrice;
                    float changePercent;

                    if (isCrypto) {
                        lastPrice = (float) json.optDouble("lastPrice", 0.0);
                        changePercent = (float) json.optDouble("priceChangePercent", 0.0);
                    } else {
                        lastPrice = (float) json.optDouble("c", 0.0);
                        changePercent = (float) json.optDouble("dp", 0.0);
                    }

                    if (lastPrice <= 0f) return;

                    StockData data = new StockData(cleanSymbol, lastPrice, changePercent);

                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            boolean replaced = false;

                            for (int i = 0; i < stocksList.size(); i++) {
                                if (stocksList.get(i).symbol.equalsIgnoreCase(cleanSymbol)) {
                                    stocksList.set(i, data);
                                    replaced = true;
                                    break;
                                }
                            }

                            if (!replaced) {
                                stocksList.add(data);
                            }

                            adapter.notifyDataSetChanged();
                        });
                    }

                } catch (Exception e) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() ->
                                Toast.makeText(getContext(),
                                        "Failed to parse " + cleanSymbol,
                                        Toast.LENGTH_SHORT).show());
                    }
                }
            }
        });
    }

    @Override
    public void onStockClick(String symbol) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).showChartWithSymbol(symbol);
        }
    }

    @Override
    public void onStockDelete(String symbol, double sellPrice) {
        stocksRef.child(symbol).removeValue();
        Toast.makeText(getContext(), "Removed from watchlist", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onStockEdit(StockData updatedStock, String oldSymbol) {
        // פרגמנט זה הוא legacy - עריכה לא רלוונטית כאן
    }
}
