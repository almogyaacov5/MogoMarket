package com.mogomarket.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class WatchlistFragment extends Fragment {

    private static final String ALERT_CHANNEL_ID = "stock_price_alerts";
    private static final String PREFS_NAME = "app_prefs";
    public static final String KEY_WATCHLIST_NAV = "watchlist_navigate_to_chart";
    public static final String KEY_WATCHLIST_HIDE_KB = "watchlist_hide_keyboard_on_add";

    private static final String FINNHUB_KEY = "d918pn9r01qr1uqui560d918pn9r01qr1uqui56g";
    private static final long SEARCH_DEBOUNCE_MS = 300L;

    private WatchlistAdapter adapter;
    private SharedPreferences watchlistPreferences;
    private String watchlistPreferencePrefix;
    private DatabaseReference watchlistRef;
    private ValueEventListener watchlistListener;

    private final OkHttpClient httpClient = new OkHttpClient();
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;
    private int suggestionSearchGeneration;
    private String selectedSuggestionSymbol;

    private int lastCheckedChipId = View.NO_ID;

    private ArrayAdapter<ChartFragment.StockSuggestion> suggestionAdapter;

    private AutoCompleteTextView stockAutoInput;
    private MaterialButton addStockBtn;
    private MaterialButton btnToggleAddStock;
    private View addStockSection;
    private MaterialButton btnRefreshWatchlist;
    private RecyclerView watchlistRecyclerView;

    private boolean isGuest() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user != null && user.isAnonymous();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_watchlist, container, false);
        createNotificationChannel();

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(getContext(), "You must sign in to use watchlist", Toast.LENGTH_SHORT).show();
            return v;
        }

        watchlistRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(user.getUid())
                .child("watchlist-stocks");

        watchlistPreferences = requireActivity()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        watchlistPreferencePrefix = "watchlist_" + user.getUid() + "_";

        adapter = new WatchlistAdapter(new WatchlistAdapter.OnWatchStockClickListener() {
            @Override
            public void onStockClick(String symbol) {
                SharedViewModel vm = new ViewModelProvider(requireActivity())
                        .get(SharedViewModel.class);

                String mappedSymbol = mapSymbolForChart(symbol);

                SharedPreferences prefs = requireActivity()
                        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

                String mode = prefs.getString(ChartFragment.KEY_SYMBOL_MODE, "last");

                // שמור ב-last רק אם לא במצב fixed
                if (!"fixed".equals(mode)) {
                    requireActivity()
                            .getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
                            .edit()
                            .putString(MainActivity.KEY_LAST_SYMBOL, mappedSymbol)
                            .apply();
                }

                // תמיד עדכן את ה-ViewModel כדי שהגרף יציג את המניה שנבחרה
                vm.setSelectedSymbol(mappedSymbol);

                boolean navigateToChart = prefs.getBoolean(KEY_WATCHLIST_NAV, true);
                if (navigateToChart) {
                    Navigation.findNavController(requireView()).navigate(R.id.nav_chart);
                }
            }

            @Override
            public void onStockDelete(String symbol) {
                deleteStock(symbol);
            }

            @Override
            public void onSetPriceAlert(StockWatchData stock) {
                showPriceAlertDialog(stock);
            }

            @Override
            public void onAlertStateChanged(String sym, boolean triggered) {
                if (watchlistRef != null) {
                    DatabaseReference stockRef = watchlistRef.child(sym.replace(":", "_"));
                    stockRef.child("alertTriggered").setValue(triggered);
                    if (triggered) stockRef.child("alertEnabled").setValue(false);
                }
            }
        });
        adapter.setManualOrder(readSavedManualOrder());
        adapter.setFilter(watchlistPreferences.getString(watchlistPreferencePrefix + "filter", "default"));
        adapter.setPercentSortMode(watchlistPreferences.getInt(watchlistPreferencePrefix + "percent_sort", 0));
        adapter.setManualOrderChangedListener(this::saveManualOrder);

        watchlistRecyclerView = v.findViewById(R.id.watchlistRecyclerView);
        watchlistRecyclerView.setLayoutManager(
                new androidx.recyclerview.widget.GridLayoutManager(requireContext(), 2));
        watchlistRecyclerView.setAdapter(adapter);

        ItemTouchHelper.SimpleCallback dragCallback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN,
                0
        ) {
            @Override
            public boolean onMove(@NonNull RecyclerView rv,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                int from = viewHolder.getAdapterPosition();
                int to = target.getAdapterPosition();
                adapter.moveItem(from, to);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }
        };
        new ItemTouchHelper(dragCallback).attachToRecyclerView(watchlistRecyclerView);

        stockAutoInput = v.findViewById(R.id.stockAutoInput);
        addStockBtn = v.findViewById(R.id.addStockBtn);
        btnToggleAddStock = v.findViewById(R.id.btnToggleAddStock);
        addStockSection = v.findViewById(R.id.addStockSection);
        btnRefreshWatchlist = v.findViewById(R.id.btnRefreshWatchlist);

        if (btnToggleAddStock != null && addStockSection != null) {
            btnToggleAddStock.setOnClickListener(view -> {
                boolean isExpanded = addStockSection.getVisibility() == View.VISIBLE;
                addStockSection.setVisibility(isExpanded ? View.GONE : View.VISIBLE);
                btnToggleAddStock.setIconResource(
                        isExpanded ? R.drawable.ic_add_24 : R.drawable.baseline_close_24);
                btnToggleAddStock.setContentDescription(
                        isExpanded ? "Open add stock" : "Close add stock");
                if (isExpanded) {
                    if (stockAutoInput != null) stockAutoInput.dismissDropDown();
                    hideKeyboard();
                } else if (stockAutoInput != null) {
                    stockAutoInput.post(() -> {
                        if (!isAdded() || stockAutoInput == null
                                || addStockSection == null
                                || addStockSection.getVisibility() != View.VISIBLE) {
                            return;
                        }
                        stockAutoInput.requestFocus();
                        InputMethodManager inputMethodManager =
                                (InputMethodManager) requireContext()
                                        .getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (inputMethodManager != null) {
                            inputMethodManager.showSoftInput(
                                    stockAutoInput, InputMethodManager.SHOW_IMPLICIT);
                        }
                    });
                }
            });
        }

        if (stockAutoInput != null) {
            setupAutoComplete(stockAutoInput);
        }

        if (addStockBtn != null) {
            addStockBtn.setOnClickListener(view -> {
                if (isGuest()) {
                    Toast.makeText(
                            getContext(),
                            "Guest mode cannot save a watchlist. Please sign in.",
                            Toast.LENGTH_LONG
                    ).show();
                    return;
                }

                String raw = stockAutoInput != null
                        ? stockAutoInput.getText().toString().trim()
                        : "";

                if (raw.isEmpty()) {
                    Toast.makeText(getContext(), "Enter a symbol", Toast.LENGTH_SHORT).show();
                    return;
                }

                addStock(raw);
            });
        }

        if (btnRefreshWatchlist != null) {
            btnRefreshWatchlist.setOnClickListener(view -> {
                view.animate().rotationBy(360f).setDuration(450L).start();
                adapter.refresh();
            });
        }

        ChipGroup sortChipGroup = v.findViewById(R.id.sortChipGroup);
        Chip chipSortOrder = v.findViewById(R.id.chipSortOrder);

        if (chipSortOrder != null) {
            updateOrderChipText(chipSortOrder);
            chipSortOrder.setOnClickListener(chipView -> {
                adapter.cyclePercentSortOrder();
                saveSortSettings(adapter.getCurrentFilter());
                updateOrderChipText(chipSortOrder);
            });
        }

        if (sortChipGroup != null) {
            int restoredChipId = getFilterChipId(adapter.getCurrentFilter());
            if (restoredChipId != View.NO_ID) {
                sortChipGroup.check(restoredChipId);
                lastCheckedChipId = restoredChipId;
            }
            sortChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
                if (checkedIds.isEmpty()) {
                    adapter.setFilter("default");
                    lastCheckedChipId = View.NO_ID;
                    saveSortSettings("default");
                    return;
                }

                int id = checkedIds.get(0);

                if (id == lastCheckedChipId) {
                    group.clearCheck();
                    adapter.setFilter("default");
                    lastCheckedChipId = View.NO_ID;
                    saveSortSettings("default");
                    return;
                }

                lastCheckedChipId = id;

                if (id == R.id.chipSortGain) {
                    adapter.setFilter("gain");
                    saveSortSettings("gain");
                } else if (id == R.id.chipSortLoss) {
                    adapter.setFilter("loss");
                    saveSortSettings("loss");
                } else if (id == R.id.chipSortAlpha) {
                    adapter.setFilter("alpha");
                    saveSortSettings("alpha");
                }
            });
        }

        loadWatchlist();

        return v;
    }

    private void updateOrderChipText(@Nullable Chip chipSortOrder) {
        if (chipSortOrder == null || adapter == null) return;

        int mode = adapter.getPercentSortMode();

        if (mode == 1) {
            chipSortOrder.setText("↑ Top Gainers");
        } else if (mode == 2) {
            chipSortOrder.setText("↓ Top Losers");
        } else {
            chipSortOrder.setText("↺ Default");
        }
    }

    private List<String> readSavedManualOrder() {
        List<String> symbols = new ArrayList<>();
        String json = watchlistPreferences.getString(watchlistPreferencePrefix + "order", "[]");
        try {
            JSONArray saved = new JSONArray(json);
            for (int i = 0; i < saved.length(); i++) {
                String symbol = saved.optString(i, "").trim();
                if (!symbol.isEmpty()) symbols.add(symbol);
            }
        } catch (org.json.JSONException e) {
            Log.w("WatchlistFragment", "Ignoring invalid saved watchlist order", e);
        }
        return symbols;
    }

    private void saveManualOrder(List<String> symbols) {
        if (watchlistPreferences == null || watchlistPreferencePrefix == null) return;
        JSONArray json = new JSONArray();
        for (String symbol : symbols) json.put(symbol);
        watchlistPreferences.edit()
                .putString(watchlistPreferencePrefix + "order", json.toString())
                .apply();
    }

    private void saveSortSettings(String filter) {
        if (watchlistPreferences == null || watchlistPreferencePrefix == null || adapter == null) return;
        watchlistPreferences.edit()
                .putString(watchlistPreferencePrefix + "filter", filter)
                .putInt(watchlistPreferencePrefix + "percent_sort", adapter.getPercentSortMode())
                .apply();
    }

    private int getFilterChipId(String filter) {
        switch (filter) {
            case "gain": return R.id.chipSortGain;
            case "loss": return R.id.chipSortLoss;
            case "alpha": return R.id.chipSortAlpha;
            default: return View.NO_ID;
        }
    }

    private String normalizeUserSymbol(String raw) {
        if (raw == null) return "";
        return raw.trim()
                .toUpperCase(Locale.US)
                .replace(" ", "")
                .replace("/", "")
                .replace("-", "")
                .replace("_", "");
    }

    private String mapSymbolForChart(String raw) {
        String normalized = normalizeUserSymbol(raw);
        if (normalized.isEmpty()) return "";

        String crypto = ChartFragment.CRYPTO_MAP.get(normalized);
        if (crypto != null) return crypto;

        String forex = ChartFragment.FOREX_MAP.get(normalized);
        if (forex != null) return forex;

        return normalized;
    }

    private void addStock(String raw) {
        if (watchlistRef == null) return;

        String symbol = mapSymbolForChart(raw);
        if (symbol.isEmpty()) return;

        String firebaseKey = symbol.replace(":", "_");

        StockWatchData stock = new StockWatchData();
        stock.symbol = symbol;
        stock.currentPrice = 0f;
        stock.dayChange = 0f;
        stock.alertEnabled = false;
        stock.alertTriggered = false;
        stock.alertTargetPrice = 0f;

        watchlistRef.child(firebaseKey).setValue(stock)
                .addOnSuccessListener(unused -> {
                    if (stockAutoInput != null) stockAutoInput.setText("");
                    if (addStockSection != null) addStockSection.setVisibility(View.GONE);
                    if (btnToggleAddStock != null) {
                        btnToggleAddStock.setIconResource(R.drawable.ic_add_24);
                        btnToggleAddStock.setContentDescription("Open add stock");
                    }

                    SharedPreferences prefs = requireActivity()
                            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

                    boolean hideKb = prefs.getBoolean(KEY_WATCHLIST_HIDE_KB, true);
                    if (hideKb) hideKeyboard();

                    Toast.makeText(getContext(), symbol + " added", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void loadWatchlist() {
        if (watchlistRef == null) return;

        if (watchlistListener != null) {
            watchlistRef.removeEventListener(watchlistListener);
        }

        watchlistListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<StockWatchData> list = new ArrayList<>();

                for (DataSnapshot child : snapshot.getChildren()) {
                    StockWatchData data = child.getValue(StockWatchData.class);
                    if (data == null) continue;

                    if (data.symbol == null || data.symbol.trim().isEmpty()) {
                        String key = child.getKey();
                        data.symbol = key != null ? key.replace("_", ":") : "";
                    }

                    list.add(data);
                }

                adapter.setItems(list);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(getContext(),
                        "Failed to load watchlist: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        };

        watchlistRef.addValueEventListener(watchlistListener);
    }

    private void setupAutoComplete(AutoCompleteTextView input) {
        suggestionAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                new ArrayList<>()
        );

        input.setAdapter(suggestionAdapter);
        input.setThreshold(1);

        input.setOnItemClickListener((parent, view, position, id) -> {
            ChartFragment.StockSuggestion sel = suggestionAdapter.getItem(position);
            if (sel != null) {
                selectedSuggestionSymbol = sel.symbol;
                suggestionSearchGeneration++;
                searchHandler.removeCallbacksAndMessages(null);
                input.setText(sel.symbol);
                input.setSelection(sel.symbol.length());
                input.dismissDropDown();
                clearSuggestions();

                SharedPreferences prefs = requireActivity()
                        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

                boolean hideKb = prefs.getBoolean(KEY_WATCHLIST_HIDE_KB, true);
                if (hideKb) hideKeyboard();
            }
        });

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String q = s.toString().trim();

                searchHandler.removeCallbacksAndMessages(null);

                if (selectedSuggestionSymbol != null
                        && q.equalsIgnoreCase(selectedSuggestionSymbol)) {
                    input.dismissDropDown();
                    return;
                }
                selectedSuggestionSymbol = null;

                if (q.isEmpty()) {
                    clearSuggestions();
                    return;
                }

                pendingSearch = () -> fetchSuggestions(q);
                searchHandler.postDelayed(pendingSearch, SEARCH_DEBOUNCE_MS);
            }
        });
    }

    private void fetchSuggestions(String query) {
        String upper = query.trim().toUpperCase(Locale.US);

        ArrayList<ChartFragment.StockSuggestion> baseSuggestions = new ArrayList<>();
        baseSuggestions.add(new ChartFragment.StockSuggestion(
                upper, "Use symbol: " + upper, "Symbol"));

        for (java.util.Map.Entry<String, String> entry : ChartFragment.CRYPTO_MAP.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (key.contains(upper) || value.contains(upper)) {
                baseSuggestions.add(new ChartFragment.StockSuggestion(
                        key,
                        key + " Crypto",
                        "Crypto"
                ));
            }
        }

        for (java.util.Map.Entry<String, String> entry : ChartFragment.FOREX_MAP.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (key.contains(upper) || value.contains(upper)) {
                baseSuggestions.add(new ChartFragment.StockSuggestion(
                        key,
                        key + " Forex",
                        "Forex"
                ));
            }
        }

        final int generation = ++suggestionSearchGeneration;
        final List<ChartFragment.StockSuggestion> all =
                Collections.synchronizedList(new ArrayList<>(baseSuggestions));
        final AtomicInteger completedSearches = new AtomicInteger();
        final Runnable postResults = () -> {
            if (completedSearches.incrementAndGet() == 2) {
                updateSuggestions(query, generation, all);
            }
        };

        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name());
            Request finnhub = new Request.Builder()
                    .url("https://finnhub.io/api/v1/search?q=" + encoded + "&token=" + FINNHUB_KEY)
                    .build();
            httpClient.newCall(finnhub).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    postResults.run();
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (response.isSuccessful() && response.body() != null) {
                        try {
                            JSONArray results = new JSONObject(response.body().string()).optJSONArray("result");
                            if (results != null) {
                                for (int i = 0; i < results.length(); i++) {
                                    JSONObject item = results.optJSONObject(i);
                                    if (item == null) continue;
                                    String symbol = item.optString("symbol", "").trim();
                                    if (!symbol.isEmpty()) {
                                        all.add(new ChartFragment.StockSuggestion(
                                                symbol,
                                                item.optString("description", symbol),
                                                item.optString("type", "Stock")));
                                    }
                                }
                            }
                        } catch (org.json.JSONException ignored) {
                        }
                    }
                    postResults.run();
                }
            });

            Request yahoo = new Request.Builder()
                    .url("https://query1.finance.yahoo.com/v1/finance/search?q=" + encoded
                            + "&quotesCount=25&newsCount=0")
                    .header("User-Agent", "Mozilla/5.0")
                    .build();
            httpClient.newCall(yahoo).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    postResults.run();
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (response.isSuccessful() && response.body() != null) {
                        try {
                            JSONArray quotes = new JSONObject(response.body().string()).optJSONArray("quotes");
                            if (quotes != null) {
                                for (int i = 0; i < quotes.length(); i++) {
                                    JSONObject item = quotes.optJSONObject(i);
                                    if (item == null) continue;
                                    String symbol = item.optString("symbol", "").trim();
                                    if (!symbol.isEmpty()) {
                                        all.add(new ChartFragment.StockSuggestion(
                                                symbol,
                                                item.optString("longname",
                                                        item.optString("shortname", symbol)),
                                                item.optString("exchDisp",
                                                        item.optString("quoteType", "Stock"))));
                                    }
                                }
                            }
                        } catch (org.json.JSONException ignored) {
                        }
                    }
                    postResults.run();
                }
            });
        } catch (Exception e) {
            updateSuggestions(query, generation, all);
        }
    }

    private void updateSuggestions(String query, int generation,
                                   List<ChartFragment.StockSuggestion> suggestions) {
        if (!isAdded() || generation != suggestionSearchGeneration
                || stockAutoInput == null
                || !query.equalsIgnoreCase(stockAutoInput.getText().toString().trim())) return;

        requireActivity().runOnUiThread(() -> {
            if (!isAdded() || generation != suggestionSearchGeneration
                    || stockAutoInput == null
                    || !query.equalsIgnoreCase(stockAutoInput.getText().toString().trim())) return;
            List<ChartFragment.StockSuggestion> unique = removeDuplicateSuggestions(suggestions);
            suggestionAdapter.clear();
            suggestionAdapter.addAll(unique);
            suggestionAdapter.notifyDataSetChanged();
            if (!unique.isEmpty()) stockAutoInput.showDropDown();
        });
    }

    private List<ChartFragment.StockSuggestion> removeDuplicateSuggestions(List<ChartFragment.StockSuggestion> input) {
        List<ChartFragment.StockSuggestion> out = new ArrayList<>();
        java.util.HashSet<String> seen = new java.util.HashSet<>();

        for (ChartFragment.StockSuggestion s : input) {
            if (s == null || s.symbol == null) continue;

            String key = s.symbol.trim().toUpperCase(Locale.US);
            if (seen.contains(key)) continue;

            seen.add(key);
            out.add(s);
        }

        return out;
    }

    private void clearSuggestions() {
        if (suggestionAdapter != null) {
            suggestionAdapter.clear();
            suggestionAdapter.notifyDataSetChanged();
        }
    }

    private void deleteStock(String symbol) {
        if (watchlistRef == null) return;
        watchlistRef.child(symbol.replace(":", "_")).removeValue();
    }

    private void showPriceAlertDialog(StockWatchData stock) {
        final EditText input = new EditText(requireContext());
        input.setHint("Target price");
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);

        new AlertDialog.Builder(requireContext())
                .setTitle("Set price alert")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String txt = input.getText().toString().trim();
                    if (txt.isEmpty()) return;

                    try {
                        float target = Float.parseFloat(txt);
                        if (!Float.isFinite(target) || target <= 0f || watchlistRef == null) {
                            Toast.makeText(getContext(), "Enter a price greater than 0",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }

                        String key = stock.symbol.replace(":", "_");
                        Map<String, Object> alertUpdate = new HashMap<>();
                        alertUpdate.put("alertTargetPrice", target);
                        alertUpdate.put("alertEnabled", true);
                        alertUpdate.put("alertTriggered", false);
                        watchlistRef.child(key).updateChildren(alertUpdate)
                                .addOnSuccessListener(unused -> {
                                    if (!isAdded()) return;
                                    SharedPreferences preferences = requireContext()
                                            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                                    if (!preferences.getBoolean(
                                            PriceAlertScheduler.KEY_ENABLED, false)) {
                                        preferences.edit().putBoolean(
                                                PriceAlertScheduler.KEY_ENABLED, true).apply();
                                        PriceAlertScheduler.schedule(requireContext());
                                        Toast.makeText(getContext(),
                                                "Price alerts enabled for your watchlist",
                                                Toast.LENGTH_SHORT).show();
                                    }
                                })
                                .addOnFailureListener(error -> {
                                    if (isAdded()) Toast.makeText(getContext(),
                                            "Could not save price alert: " + error.getMessage(),
                                            Toast.LENGTH_LONG).show();
                                });
                    } catch (NumberFormatException e) {
                        Toast.makeText(getContext(), "Invalid number", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNeutralButton("Disable", (dialog, which) -> {
                    if (watchlistRef == null) return;

                    String key = stock.symbol.replace(":", "_");
                    Map<String, Object> alertUpdate = new HashMap<>();
                    alertUpdate.put("alertEnabled", false);
                    alertUpdate.put("alertTargetPrice", 0);
                    alertUpdate.put("alertTriggered", false);
                    watchlistRef.child(key).updateChildren(alertUpdate)
                            .addOnFailureListener(error -> {
                                if (isAdded()) Toast.makeText(getContext(),
                                        "Could not disable price alert: " + error.getMessage(),
                                        Toast.LENGTH_LONG).show();
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationChannel channel = new NotificationChannel(
                ALERT_CHANNEL_ID,
                "Price Alerts",
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("Notifications for watchlist price alerts");

        NotificationManager manager =
                requireContext().getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private void hideKeyboard() {
        View view = requireActivity().getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager)
                    requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (watchlistRef != null && watchlistListener != null) {
            watchlistRef.removeEventListener(watchlistListener);
        }
        searchHandler.removeCallbacksAndMessages(null);
    }
}