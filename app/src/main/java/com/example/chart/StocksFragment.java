package com.mogomarket.app;

import android.content.Context;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
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
    private EditText stockInput;
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
        stockInput         = view.findViewById(R.id.stockInput);
        addStockBtn        = view.findViewById(R.id.addStockBtn);
        btnRefreshAll      = view.findViewById(R.id.btnRefreshWatchlist);

        adapter = new StocksAdapter(stocksList, this);
        stocksRecyclerView.setAdapter(adapter);
        stocksRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        stocksRef = FirebaseDatabase.getInstance().getReference("user-stocks");

        loadStocks();

        btnRefreshAll.setOnClickListener(v -> reloadAllStocks());

        addStockBtn.setOnClickListener(v -> {
            String symbol = stockInput.getText().toString().trim().toUpperCase();
            if (!symbol.isEmpty()) {
                stocksRef.child(symbol).setValue(true);
                stockInput.setText("");
                stockInput.clearFocus();
                hideKeyboard();
            }
        });

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
        long toTime   = System.currentTimeMillis() / 1000L;
        long fromTime = toTime - (3L * 24 * 60 * 60); // 3 days back

        String url = "https://finnhub.io/api/v1/stock/candle?symbol=" + symbol
                + "&resolution=D&from=" + fromTime
                + "&to=" + toTime
                + "&token=" + API_KEY;

        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    JSONObject json   = new JSONObject(response.body().string());
                    String     status = json.optString("s", "");
                    if (!"ok".equals(status)) return;

                    JSONArray closes = json.getJSONArray("c");
                    if (closes.length() < 2) return;

                    float lastPrice    = (float) closes.getDouble(closes.length() - 1);
                    float prevPrice    = (float) closes.getDouble(closes.length() - 2);
                    float changePercent = (lastPrice - prevPrice) / prevPrice * 100;

                    StockData data = new StockData(symbol, lastPrice, changePercent);
                    if (getActivity() != null) getActivity().runOnUiThread(() -> {
                        stocksList.add(data);
                        adapter.notifyDataSetChanged();
                    });
                } catch (Exception e) { }
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
        Toast.makeText(getContext(), "המניה הוסרה מהרשימה", Toast.LENGTH_SHORT).show();
    }

    // ב-StocksFragment אין עריכת פרטי מניה - מימוש ריק
    @Override
    public void onStockEdit(StockData updatedStock, String oldSymbol) {
        // פרגמנט זה הוא legacy - עריכה לא רלוונטית כאן
    }
}
