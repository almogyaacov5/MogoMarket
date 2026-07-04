package com.mogomarket.app;

import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.CandleStickChart;
import com.google.android.material.button.MaterialButton;
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
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import it.xabaras.android.recyclerview.swipedecorator.RecyclerViewSwipeDecorator;

public class WatchlistFragment extends Fragment {

    public static final String PREFS_NAME            = "app_prefs";
    public static final String KEY_WATCHLIST_SORT    = "watchlist_sort";
    public static final String KEY_WATCHLIST_NAV     = "watchlist_navigate_to_chart";
    public static final String KEY_WATCHLIST_HIDE_KB = "watchlist_hide_keyboard_on_add";

    private static final String FINNHUB_KEY = "d918pn9r01qr1uqui560d918pn9r01qr1uqui56g";

    private EditText etSearch;
    private AutoCompleteTextView etAddStock;
    private ImageButton btnAdd;
    private MaterialButton btnSort;
    private RecyclerView recyclerView;
    private WatchlistAdapter adapter;

    private DatabaseReference watchlistRef;
    private ValueEventListener watchlistListener;

    private final OkHttpClient client = new OkHttpClient();

    private ArrayAdapter<ChartFragment.StockSuggestion> suggestionAdapter;

    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable pendingSearch;
    private static final long DEBOUNCE_MS = 250L;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_watchlist, container, false);
        createNotificationChannel();

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(getContext(), "צריך להתחבר כדי להשתמש ברשימת מעקב", Toast.LENGTH_SHORT).show();
            return v;
        }

        watchlistRef = FirebaseDatabase.getInstance()
                .getReference("users").child(user.getUid()).child("watchlist-stocks");

        adapter = new WatchlistAdapter(new WatchlistAdapter.OnWatchStockClickListener() {
            @Override
            public void onStockClick(String symbol) {
                SharedViewModel vm = new ViewModelProvider(requireActivity())
                        .get(SharedViewModel.class);

                String mappedSymbol = mapSymbolForChart(symbol);
                vm.setSelectedSymbol(mappedSymbol);

                requireActivity().getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putString(MainActivity.KEY_LAST_SYMBOL, mappedSymbol)
                        .apply();

                SharedPreferences prefs = requireActivity()
                        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

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
            public void onSetPriceAlert(StockWatchData s) {
                showPriceAlertDialog(s);
            }

            @Override
            public void onAlertStateChanged(String sym, boolean t) {
                if (watchlistRef != null) {
                    watchlistRef.child(sym.replace(":", "_")).child("alertTriggered").setValue(t);
                }
            }
        });

        recyclerView = v.findViewById(R.id.watchlistRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        etSearch = v.findViewById(R.id.etSearchWatchlist);
        etAddStock = v.findViewById(R.id.etAddWatchlistStock);
        btnAdd = v.findViewById(R.id.btnAddWatchlist);
        btnSort = v.findViewById(R.id.btnWatchlistSort);

        setupSearchBox();
        setupAddBox();
        setupSortButton();
        setupSwipeToDelete();
        loadWatchlist();

        return v;
    }

    private void setupSearchBox() {
        if (etSearch == null) return;

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (adapter != null) {
                    adapter.setSearchQuery(s.toString());
                }
            }
        });
    }

    private void setupAddBox() {
        if (etAddStock == null) return;

        suggestionAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                new ArrayList<>()
        );

        etAddStock.setAdapter(suggestionAdapter);
        etAddStock.setThreshold(1);

        etAddStock.setOnItemClickListener((parent, view, position, id) -> {
            ChartFragment.StockSuggestion sel = suggestionAdapter.getItem(position);
            if (sel != null) {
                etAddStock.setText(sel.symbol);
                etAddStock.setSelection(sel.symbol.length());
                boolean hideKb = requireActivity()
                        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getBoolean(KEY_WATCHLIST_HIDE_KB, true);
                if (hideKb) hideKeyboard();
            }
        });

        etAddStock.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}

            @Override
            public void afterTextChanged(Editable s) {
                String q = s.toString().trim();
                handler.removeCallbacks(pendingSearch);

                if (q.isEmpty()) {
                    clearSuggestions();
                    return;
                }

                pendingSearch = () -> fetchSuggestions(q);
                handler.postDelayed(pendingSearch, DEBOUNCE_MS);
            }
        });

        if (btnAdd != null) {
            btnAdd.setOnClickListener(v -> {
                String symbol = etAddStock.getText().toString().trim();
                if (symbol.isEmpty()) {
                    Toast.makeText(getContext(), "Enter a symbol", Toast.LENGTH_SHORT).show();
                    return;
                }
                addStock(symbol);
            });
        }

        etAddStock.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                etAddStock.showDropDown();
            }
            return false;
        });
    }

    private void setupSortButton() {
        if (btnSort == null) return;

        SharedPreferences prefs = requireActivity()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        String savedSort = prefs.getString(KEY_WATCHLIST_SORT, "default");
        if (adapter != null) adapter.setSort(savedSort);

        btnSort.setOnClickListener(v -> {
            final String[] items = {
                    "Default",
                    "Symbol A-Z",
                    "Symbol Z-A",
                    "Price Low-High",
                    "Price High-Low",
                    "Change Low-High",
                    "Change High-Low"
            };

            final String[] values = {
                    "default",
                    "symbol_asc",
                    "symbol_desc",
                    "price_asc",
                    "price_desc",
                    "change_asc",
                    "change_desc"
            };

            String current = prefs.getString(KEY_WATCHLIST_SORT, "default");
            int checked = 0;
            for (int i = 0; i < values.length; i++) {
                if (values[i].equals(current)) {
                    checked = i;
                    break;
                }
            }

            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Sort watchlist")
                    .setSingleChoiceItems(items, checked, (dialog, which) -> {
                        String val = values[which];
                        prefs.edit().putString(KEY_WATCHLIST_SORT, val).apply();
                        if (adapter != null) adapter.setSort(val);
                        dialog.dismiss();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void setupSwipeToDelete() {
        ItemTouchHelper.SimpleCallback dragCallback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN,
                ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT
        ) {
            @Override
            public boolean onMove(@NonNull RecyclerView rv,
                                  @NonNull RecyclerView.ViewHolder vh,
                                  @NonNull RecyclerView.ViewHolder target) {
                int from = vh.getAdapterPosition();
                int to = target.getAdapterPosition();

                List<StockWatchData> list = adapter.getCurrentItems();
                if (from < 0 || to < 0 || from >= list.size() || to >= list.size()) return false;

                Collections.swap(list, from, to);
                adapter.notifyItemMoved(from, to);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
                int pos = vh.getAdapterPosition();
                List<StockWatchData> list = adapter.getCurrentItems();
                if (pos >= 0 && pos < list.size()) {
                    deleteStock(list.get(pos).symbol);
                }
            }

            @Override
            public void onChildDraw(@NonNull Canvas c,
                                    @NonNull RecyclerView rv,
                                    @NonNull RecyclerView.ViewHolder vh,
                                    float dX,
                                    float dY,
                                    int actionState,
                                    boolean isCurrentlyActive) {
                new RecyclerViewSwipeDecorator.Builder(c, rv, vh, dX, dY, actionState, isCurrentlyActive)
                        .addBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
                        .addActionIcon(android.R.drawable.ic_menu_delete)
                        .create()
                        .decorate();

                super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive);
            }
        };

        new ItemTouchHelper(dragCallback).attachToRecyclerView(recyclerView);
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
                    String key = child.getKey();

                    if (data == null) continue;

                    data.symbol = (key != null) ? key.replace("_", ":") : "";
                    list.add(data);
                }

                if (adapter != null) {
                    adapter.setItems(list);
                }
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

    private void addStock(String rawSymbol) {
        if (watchlistRef == null) return;

        String symbol = rawSymbol.trim().toUpperCase(Locale.US);
        if (symbol.isEmpty()) return;

        String mappedSymbol = mapSymbolForChart(symbol);
        String firebaseKey = mappedSymbol.replace(":", "_");

        StockWatchData stock = new StockWatchData();
        stock.symbol = mappedSymbol;
        stock.currentPrice = 0f;
        stock.dayChange = 0f;
        stock.alertEnabled = false;
        stock.alertTriggered = false;
        stock.alertTargetPrice = 0f;

        watchlistRef.child(firebaseKey).setValue(stock)
                .addOnSuccessListener(unused -> {
                    etAddStock.setText("");
                    clearSuggestions();
                    Toast.makeText(getContext(), "Added to watchlist", Toast.LENGTH_SHORT).show();
                    hideKeyboard();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(),
                                "Failed to add: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
    }

    private void deleteStock(String symbol) {
        if (watchlistRef == null) return;
        String key = symbol.replace(":", "_");
        watchlistRef.child(key).removeValue();
    }

    private void clearSuggestions() {
        if (suggestionAdapter != null) {
            suggestionAdapter.clear();
            suggestionAdapter.notifyDataSetChanged();
        }
    }

    private String mapSymbolForChart(String raw) {
        if (raw == null) return "";
        String upper = raw.trim().toUpperCase(Locale.US);

        String crypto = ChartFragment.CRYPTO_MAP.get(upper);
        if (crypto != null) return crypto;

        String forex = ChartFragment.FOREX_MAP.get(upper);
        if (forex != null) return forex;

        return upper;
    }

    private void handleStockClick(String symbol) {
        SharedViewModel vm = new ViewModelProvider(requireActivity())
                .get(SharedViewModel.class);

        String mappedSymbol = mapSymbolForChart(symbol);
        vm.setSelectedSymbol(mappedSymbol);

        requireActivity().getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(MainActivity.KEY_LAST_SYMBOL, mappedSymbol)
                .apply();

        SharedPreferences prefs = requireActivity()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        boolean navigateToChart = prefs.getBoolean(KEY_WATCHLIST_NAV, true);

        if (navigateToChart) {
            Navigation.findNavController(requireView()).navigate(R.id.nav_chart);
        }
    }

    private void fetchSuggestions(String query) {
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name());
            String url = "https://finnhub.io/api/v1/search?q=" + encoded + "&token=" + FINNHUB_KEY;

            Request req = new Request.Builder().url(url).build();
            client.newCall(req).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (!response.isSuccessful() || response.body() == null) return;

                    try {
                        JSONObject root = new JSONObject(response.body().string());
                        JSONArray arr = root.optJSONArray("result");

                        ArrayList<ChartFragment.StockSuggestion> list = new ArrayList<>();

                        if (arr != null) {
                            for (int i = 0; i < Math.min(arr.length(), 12); i++) {
                                JSONObject o = arr.getJSONObject(i);
                                String sym = o.optString("symbol", "");
                                String name = o.optString("description", "");
                                String type = o.optString("type", "");
                                if (!sym.isEmpty()) {
                                    list.add(new ChartFragment.StockSuggestion(sym, name, type));
                                }
                            }
                        }

                        requireActivity().runOnUiThread(() -> {
                            suggestionAdapter.clear();
                            suggestionAdapter.addAll(list);
                            suggestionAdapter.notifyDataSetChanged();
                            if (!list.isEmpty()) etAddStock.showDropDown();
                        });

                    } catch (Exception ignored) {
                    }
                }
            });
        } catch (Exception ignored) {
        }
    }

    private void showPriceAlertDialog(StockWatchData stock) {
        final EditText input = new EditText(requireContext());
        input.setHint("Target price");

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
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
                "price_alerts",
                "Price Alerts",
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("Notifications for watchlist target prices");

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
            if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }
}