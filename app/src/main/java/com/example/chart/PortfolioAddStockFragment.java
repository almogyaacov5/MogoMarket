package com.example.chart;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class PortfolioAddStockFragment extends Fragment {

    private AutoCompleteTextView editTicker;
    private EditText editBuyPrice, editTargetPrice, editTradeAmount, editNotes;
    private Button btnAddStock;
    private DatabaseReference stocksRef;

    private final OkHttpClient client = new OkHttpClient();
    private final String API_KEY = "0518811f0d394fa39842a8024a25c049";

    private ArrayAdapter<ChartFragment.StockSuggestion> suggestionAdapter;
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;
    private String latestQuery = "";
    private boolean isManualSelection = false;

    private String selectedCompanyName = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.portfolio_add_stock_fragment, container, false);

        editTicker      = v.findViewById(R.id.editTicker);
        editBuyPrice    = v.findViewById(R.id.editBuyPrice);
        editTargetPrice = v.findViewById(R.id.editTargetPrice);
        editTradeAmount = v.findViewById(R.id.editTradeAmount);
        editNotes       = v.findViewById(R.id.editNotes);
        btnAddStock     = v.findViewById(R.id.btnAddStock);

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        stocksRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(uid)
                .child("portfolio-stocks");

        setupAutoComplete();
        btnAddStock.setOnClickListener(view -> addStock());
        return v;
    }

    // ==================== AutoComplete ====================

    private void setupAutoComplete() {
        suggestionAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                new ArrayList<>()
        );

        editTicker.setAdapter(suggestionAdapter);
        editTicker.setThreshold(1);

        editTicker.setOnItemClickListener((parent, view, position, id) -> {
            ChartFragment.StockSuggestion sel = suggestionAdapter.getItem(position);
            if (sel == null || sel.symbol == null) return;

            // קריפטו: שמור בפורמט BINANCE:BTCUSDT. מניה: UPPERCASE
            String picked = CryptoHelper.isCryptoSymbol(sel.symbol)
                    ? sel.symbol.trim()
                    : sel.symbol.trim().toUpperCase(Locale.US);

            selectedCompanyName = (sel.name != null) ? sel.name.trim() : "";

            isManualSelection = true;
            editTicker.setText(picked);
            editTicker.setSelection(picked.length());
            editTicker.dismissDropDown();
            editBuyPrice.requestFocus();
            editTicker.postDelayed(() -> isManualSelection = false, 300);
        });

        editTicker.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isManualSelection) return;
                selectedCompanyName = "";
                String q = s == null ? "" : s.toString().trim();
                scheduleSearch(q);
            }
        });
    }

    private void scheduleSearch(String q) {
        if (pendingSearch != null) searchHandler.removeCallbacks(pendingSearch);
        latestQuery = q;
        if (q.length() < 1) { clearSuggestions(); return; }
        pendingSearch = () -> fetchSuggestions(q);
        searchHandler.postDelayed(pendingSearch, 300);
    }

    private void clearSuggestions() {
        if (suggestionAdapter != null) {
            suggestionAdapter.clear();
            suggestionAdapter.notifyDataSetChanged();
        }
        if (editTicker != null) editTicker.dismissDropDown();
    }

    private void fetchSuggestions(String query) {
        // 1. קריפטו מקומי — מיידי
        List<CryptoHelper.CryptoSuggestion> cryptoList = CryptoHelper.searchLocal(query);
        ArrayList<ChartFragment.StockSuggestion> localResults = new ArrayList<>();
        for (CryptoHelper.CryptoSuggestion cs : cryptoList) {
            localResults.add(new ChartFragment.StockSuggestion(
                    cs.binanceSymbol, cs.fullName + " (Crypto)", "Crypto"));
        }
        if (getActivity() != null && !localResults.isEmpty()) {
            getActivity().runOnUiThread(() -> {
                if (!query.equals(latestQuery) || suggestionAdapter == null) return;
                suggestionAdapter.clear();
                suggestionAdapter.addAll(localResults);
                suggestionAdapter.notifyDataSetChanged();
                if (editTicker != null && editTicker.hasFocus())
                    editTicker.post(() -> editTicker.showDropDown());
            });
        }

        // 2. מניות מ-TwelveData — ברקע
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name());
            HashSet<String> added = new HashSet<>();
            fetchFromExchange(encoded, query, "NYSE",   added, localResults);
            fetchFromExchange(encoded, query, "NASDAQ", added, localResults);
        } catch (Exception ignored) {}
    }

    private void fetchFromExchange(String encoded, String originalQuery,
                                   String exchange, HashSet<String> added,
                                   List<ChartFragment.StockSuggestion> cryptoBase) {
        String url = "https://api.twelvedata.com/symbol_search?symbol=" + encoded
                + "&outputsize=20&country=US&exchange=" + exchange
                + "&apikey=" + API_KEY;

        client.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {}
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) return;
                ArrayList<ChartFragment.StockSuggestion> combined = new ArrayList<>(cryptoBase);
                try {
                    JSONObject json = new JSONObject(response.body().string());
                    JSONArray data  = json.optJSONArray("data");
                    if (data != null) {
                        for (int i = 0; i < data.length(); i++) {
                            JSONObject o = data.optJSONObject(i);
                            if (o == null) continue;
                            String sym = o.optString("symbol", "").trim();
                            if (sym.isEmpty()) continue;
                            String ex = o.optString("exchange", "");
                            if (!"NYSE".equalsIgnoreCase(ex) && !"NASDAQ".equalsIgnoreCase(ex)) continue;
                            synchronized (added) {
                                if (added.contains(sym)) continue;
                                added.add(sym);
                            }
                            String name = o.optString("instrument_name", "");
                            combined.add(new ChartFragment.StockSuggestion(sym, name, ex));
                        }
                    }
                } catch (Exception ignored) {}

                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    if (!originalQuery.equals(latestQuery) || suggestionAdapter == null) return;
                    suggestionAdapter.clear();
                    suggestionAdapter.addAll(combined);
                    suggestionAdapter.notifyDataSetChanged();
                    if (editTicker != null && editTicker.hasFocus() && suggestionAdapter.getCount() > 0)
                        editTicker.post(() -> editTicker.showDropDown());
                });
            }
        });
    }

    // ==================== הוספת מניה/קריפטו ====================

    private void addStock() {
        String ticker  = editTicker.getText().toString().trim();
        String priceStr  = editBuyPrice.getText().toString().trim();
        String targetStr = editTargetPrice.getText().toString().trim();
        String amountStr = editTradeAmount.getText().toString().trim();
        String notes     = editNotes.getText().toString().trim();

        // קריפטו: שמור כ-BINANCE:BTCUSDT. מניה: UPPERCASE
        if (!CryptoHelper.isCryptoSymbol(ticker)) {
            ticker = ticker.toUpperCase(Locale.US);
        }

        // בדוק במיפוי קריפטו
        String mapped = CryptoHelper.CRYPTO_MAP.get(ticker.toUpperCase(Locale.US));
        if (mapped != null) ticker = mapped;

        if (ticker.isEmpty() || priceStr.isEmpty()) {
            Toast.makeText(getContext(), "Enter a ticker and price", Toast.LENGTH_SHORT).show();
            return;
        }

        float price;
        try { price = Float.parseFloat(priceStr); }
        catch (NumberFormatException e) {
            Toast.makeText(getContext(), "Invalid price", Toast.LENGTH_SHORT).show();
            return;
        }

        float targetPrice = 0;
        if (!targetStr.isEmpty()) {
            try { targetPrice = Float.parseFloat(targetStr); }
            catch (NumberFormatException e) {
                Toast.makeText(getContext(), "Invalid target price", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        double tradeAmount = 0;
        if (!amountStr.isEmpty()) {
            try { tradeAmount = Double.parseDouble(amountStr); }
            catch (NumberFormatException e) {
                Toast.makeText(getContext(), "Invalid trade amount", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        StockData data  = new StockData(ticker, price, price);
        data.tradeAmount = tradeAmount;
        data.targetPrice = targetPrice;
        data.notes       = notes;
        data.name        = selectedCompanyName;

        // Firebase key: BINANCE:BTCUSDT -> BINANCE_BTCUSDT
        String firebaseKey = ticker.replace(":", "_");

        stocksRef.child(firebaseKey).setValue(data)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(getContext(), "נוסף לתיק!", Toast.LENGTH_SHORT).show();
                    if (getActivity() != null)
                        getActivity().getSupportFragmentManager().popBackStack();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "שגיאה בהוספה", Toast.LENGTH_SHORT).show());
    }
}
