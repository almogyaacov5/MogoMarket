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
import java.util.List;
import java.util.Locale;

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
    private DatabaseReference watchlistRef;
    private ValueEventListener watchlistListener;

    private final OkHttpClient httpClient = new OkHttpClient();
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;

    private int lastCheckedChipId = View.NO_ID;

    private ArrayAdapter<ChartFragment.StockSuggestion> suggestionAdapter;

    private AutoCompleteTextView stockAutoInput;
    private MaterialButton addStockBtn;
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
                    watchlistRef.child(sym.replace(":", "_"))
                            .child("alertTriggered")
                            .setValue(triggered);
                }
            }
        });

        watchlistRecyclerView = v.findViewById(R.id.watchlistRecyclerView);
        watchlistRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
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
        btnRefreshWatchlist = v.findViewById(R.id.btnRefreshWatchlist);

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
            btnRefreshWatchlist.setOnClickListener(view -> adapter.refresh());
        }

        com.google.android.material.textfield.TextInputEditText searchInput =
                v.findViewById(R.id.searchInput);

        if (searchInput != null) {
            searchInput.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    adapter.setSearch(s.toString());
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });
        }

        ChipGroup sortChipGroup = v.findViewById(R.id.sortChipGroup);
        Chip chipSortOrder = v.findViewById(R.id.chipSortOrder);

        if (chipSortOrder != null) {
            updateOrderChipText(chipSortOrder);
            chipSortOrder.setOnClickListener(chipView -> {
                adapter.cyclePercentSortOrder();
                updateOrderChipText(chipSortOrder);
            });
        }

        if (sortChipGroup != null) {
            sortChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
                if (checkedIds.isEmpty()) {
                    adapter.setFilter("default");
                    lastCheckedChipId = View.NO_ID;
                    return;
                }

                int id = checkedIds.get(0);

                if (id == lastCheckedChipId) {
                    group.clearCheck();
                    adapter.setFilter("default");
                    lastCheckedChipId = View.NO_ID;
                    return;
                }

                lastCheckedChipId = id;

                if (id == R.id.chipSortGain) {
                    adapter.setFilter("gain");
                } else if (id == R.id.chipSortLoss) {
                    adapter.setFilter("loss");
                } else if (id == R.id.chipSortAlpha) {
                    adapter.setFilter("alpha");
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
                input.setText(sel.symbol);
                input.setSelection(sel.symbol.length());

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

        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name());
            String url = "https://finnhub.io/api/v1/search?q=" + encoded + "&token=" + FINNHUB_KEY;

            Request req = new Request.Builder().url(url).build();
            httpClient.newCall(req).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    if (!isAdded()) return;
                    requireActivity().runOnUiThread(() -> {
                        suggestionAdapter.clear();
                        suggestionAdapter.addAll(baseSuggestions);
                        suggestionAdapter.notifyDataSetChanged();
                        if (!baseSuggestions.isEmpty()) stockAutoInput.showDropDown();
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    ArrayList<ChartFragment.StockSuggestion> all = new ArrayList<>(baseSuggestions);

                    if (response.isSuccessful() && response.body() != null) {
                        try {
                            JSONObject root = new JSONObject(response.body().string());
                            JSONArray arr = root.optJSONArray("result");

                            if (arr != null) {
                                for (int i = 0; i < Math.min(arr.length(), 10); i++) {
                                    JSONObject o = arr.getJSONObject(i);
                                    String sym = o.optString("symbol", "");
                                    String name = o.optString("description", "");
                                    String type = o.optString("type", "");

                                    if (!sym.isEmpty()) {
                                        all.add(new ChartFragment.StockSuggestion(sym, name, type));
                                    }
                                }
                            }
                        } catch (Exception ignored) {
                        }
                    }

                    if (!isAdded()) return;

                    requireActivity().runOnUiThread(() -> {
                        suggestionAdapter.clear();
                        suggestionAdapter.addAll(removeDuplicateSuggestions(all));
                        suggestionAdapter.notifyDataSetChanged();

                        if (!all.isEmpty() && stockAutoInput != null) {
                            stockAutoInput.showDropDown();
                        }
                    });
                }
            });

        } catch (Exception e) {
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                suggestionAdapter.clear();
                suggestionAdapter.addAll(baseSuggestions);
                suggestionAdapter.notifyDataSetChanged();
                if (!baseSuggestions.isEmpty() && stockAutoInput != null) {
                    stockAutoInput.showDropDown();
                }
            });
        }
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
                        if (watchlistRef == null) return;

                        String key = stock.symbol.replace(":", "_");
                        watchlistRef.child(key).child("alertTargetPrice").setValue(target);
                        watchlistRef.child(key).child("alertEnabled").setValue(true);
                        watchlistRef.child(key).child("alertTriggered").setValue(false);
                    } catch (Exception e) {
                        Toast.makeText(getContext(), "Invalid number", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNeutralButton("Disable", (dialog, which) -> {
                    if (watchlistRef == null) return;

                    String key = stock.symbol.replace(":", "_");
                    watchlistRef.child(key).child("alertEnabled").setValue(false);
                    watchlistRef.child(key).child("alertTargetPrice").setValue(0);
                    watchlistRef.child(key).child("alertTriggered").setValue(false);
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