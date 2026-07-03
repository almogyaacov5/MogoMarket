package com.mogomarket.app;

import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
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
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputLayout;
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

    private static final String ALERT_CHANNEL_ID      = "stock_price_alerts";
    private static final String PREFS_NAME            = "app_prefs";
    public  static final String KEY_WATCHLIST_NAV     = "watchlist_navigate_to_chart";
    public  static final String KEY_WATCHLIST_HIDE_KB = "watchlist_hide_keyboard_on_add";

    private static final String FINNHUB_KEY        = "d918pn9r01qr1uqui560d918pn9r01qr1uqui56g";
    private static final long   SEARCH_DEBOUNCE_MS = 300;

    private static final long   AUTO_REFRESH_INTERVAL_MS = 5 * 60 * 1000L;
    private long                lastRefreshTime = 0L;

    private WatchlistAdapter  adapter;
    private DatabaseReference watchlistRef;

    private final OkHttpClient httpClient    = new OkHttpClient();
    private final Handler      searchHandler = new Handler(Looper.getMainLooper());
    private Runnable           pendingSearch;
    private String             latestQuery       = "";
    private boolean            isManualSelection = false;

    private int lastCheckedChipId = View.NO_ID;

    private ArrayAdapter<ChartFragment.StockSuggestion> suggestionAdapter;

    // ─── Helper: בדיקה אם המשתמש הנוכחי הוא אורח אנונימי ───────────────────────
    private boolean isGuest() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user != null && user.isAnonymous();
    }

    // ─── Lifecycle ───────────────────────────────────────────────────────────────────────────

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

                vm.setSelectedSymbol(symbol);

                SharedPreferences prefs = requireActivity()
                        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

                boolean navigateToChart = prefs.getBoolean(KEY_WATCHLIST_NAV, true);

                if (navigateToChart) {
                    Navigation.findNavController(requireView()).navigate(R.id.nav_chart);
                }
            }
            @Override public void onStockDelete(String symbol)      { deleteStock(symbol); }
            @Override public void onSetPriceAlert(StockWatchData s) { showPriceAlertDialog(s); }
            @Override public void onAlertStateChanged(String sym, boolean t) {
                if (watchlistRef != null)
                    watchlistRef.child(sym.replace(":", "_")).child("alertTriggered").setValue(t);
            }
        });

        RecyclerView recyclerView = v.findViewById(R.id.watchlistRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        ItemTouchHelper.SimpleCallback dragCallback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView rv,
                                  @NonNull RecyclerView.ViewHolder vh,
                                  @NonNull RecyclerView.ViewHolder target) {
                int from = vh.getAdapterPosition();
                int to   = target.getAdapterPosition();
                adapter.moveItem(from, to);
                return true;
            }
            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) { /* no swipe */ }
        };
        new ItemTouchHelper(dragCallback).attachToRecyclerView(recyclerView);

        AutoCompleteTextView autoInput = v.findViewById(R.id.stockAutoInput);
        if (autoInput != null) {
            setupAutoComplete(autoInput);
        }

        MaterialButton addStockBtn = v.findViewById(R.id.addStockBtn);
        MaterialButton btnRefresh  = v.findViewById(R.id.btnRefreshWatchlist);

        if (addStockBtn != null) {
            addStockBtn.setOnClickListener(view -> {
                // חסימת אורחים
                if (isGuest()) {
                    Toast.makeText(getContext(),
                            "כניסה כאורח — אין אפשרות לשמור רשימת מעקב. התחבר עם חשבון.",
                            Toast.LENGTH_LONG).show();
                    return;
                }

                String raw = "";
                if (autoInput != null) {
                    raw = autoInput.getText().toString().trim();
                }

                if (raw.isEmpty()) {
                    Toast.makeText(getContext(), "הזן סימבול", Toast.LENGTH_SHORT).show();
                    return;
                }

                String upperRaw     = raw.toUpperCase(Locale.US);
                String cryptoMapped = CryptoHelper.CRYPTO_MAP.get(upperRaw);
                String symbol;
                if (cryptoMapped != null) {
                    symbol = cryptoMapped;
                } else if (raw.contains(":")) {
                    symbol = raw.trim();
                } else {
                    symbol = upperRaw;
                }

                String firebaseKey = symbol.replace(":", "_");
                StockWatchData stock = new StockWatchData(symbol, 0f, 0f);
                watchlistRef.child(firebaseKey).setValue(stock);

                if (autoInput != null) autoInput.setText("");

                SharedPreferences prefs = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                boolean hideKb = prefs.getBoolean(KEY_WATCHLIST_HIDE_KB, true);
                if (hideKb) hideKeyboard();

                Toast.makeText(getContext(), symbol + " נוסף!", Toast.LENGTH_SHORT).show();
            });
        }

        if (btnRefresh != null)
            btnRefresh.setOnClickListener(view -> {
                lastRefreshTime = 0L;
                adapter.refresh();
            });

        com.google.android.material.textfield.TextInputEditText searchInput = v.findViewById(R.id.searchInput);
        if (searchInput != null) {
            searchInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                    adapter.setSearch(s.toString());
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        ChipGroup sortChipGroup = v.findViewById(R.id.sortChipGroup);
        Chip chipOrder = v.findViewById(R.id.chipSortOrder);

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
                } else if (id == R.id.chipSortOrder) {
                    adapter.toggleSortOrder();
                    if (chipOrder != null) {
                        chipOrder.setText(adapter.isSortAscending()
                                ? "\u25b2 \u05e2\u05d5\u05dc\u05d4"
                                : "\u25bc \u05d9\u05d5\u05e8\u05d3");
                    }
                    adapter.setFilter("order");
                }
            });
        }

        watchlistRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<StockWatchData> fresh = new ArrayList<>();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    StockWatchData data = ds.getValue(StockWatchData.class);
                    if (data == null) continue;
                    if (data.symbol == null || data.symbol.isEmpty()) {
                        String key = ds.getKey();
                        data.symbol = (key != null) ? key.replace("_", ":") : "";
                    }
                    if (!data.symbol.isEmpty()) fresh.add(data);
                }
                adapter.updateData(fresh);
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (getContext() != null)
                    Toast.makeText(getContext(), "שגיאה בטעינת רשימת המעקב", Toast.LENGTH_SHORT).show();
            }
        });

        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (adapter == null) return;
        long now = System.currentTimeMillis();
        if (now - lastRefreshTime >= AUTO_REFRESH_INTERVAL_MS) {
            lastRefreshTime = now;
            adapter.refresh();
        }
    }

    // ─── AutoComplete ───────────────────────────────────────────────────────────────────

    private void setupAutoComplete(AutoCompleteTextView input) {
        suggestionAdapter = new ArrayAdapter<ChartFragment.StockSuggestion>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                new ArrayList<>()) {
            @Override
            public android.widget.Filter getFilter() {
                return new android.widget.Filter() {
                    @Override protected FilterResults performFiltering(CharSequence c) {
                        FilterResults r = new FilterResults();
                        r.values = new ArrayList<>(); r.count = 0; return r;
                    }
                    @Override protected void publishResults(CharSequence c, FilterResults r) {}
                };
            }
        };

        input.setAdapter(suggestionAdapter);
        input.setThreshold(1);

        input.setOnItemClickListener((parent, view, position, id) -> {
            ChartFragment.StockSuggestion sel = suggestionAdapter.getItem(position);
            if (sel == null || sel.symbol == null || sel.symbol.trim().isEmpty()) return;
            String picked = sel.symbol.contains(":")
                    ? sel.symbol.trim()
                    : sel.symbol.trim().toUpperCase(Locale.US);
            isManualSelection = true;
            input.setText(picked);
            input.setSelection(picked.length());
            input.dismissDropDown();
            clearSuggestions();
            isManualSelection = false;
        });

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                if (isManualSelection) return;
                scheduleSymbolSearch(s == null ? "" : s.toString().trim(), input);
            }
        });
    }

    private void scheduleSymbolSearch(String q, AutoCompleteTextView input) {
        if (pendingSearch != null) searchHandler.removeCallbacks(pendingSearch);
        latestQuery = q;
        if (q.length() < 1) { clearSuggestions(); return; }
        pendingSearch = () -> fetchSymbolSuggestions(q, input);
        searchHandler.postDelayed(pendingSearch, SEARCH_DEBOUNCE_MS);
    }

    private void fetchSymbolSuggestions(final String query, AutoCompleteTextView input) {
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name());
            String url = "https://finnhub.io/api/v1/search?q=" + encoded + "&token=" + FINNHUB_KEY;
            httpClient.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
                @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {}
                @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (!response.isSuccessful() || response.body() == null) return;
                    ArrayList<ChartFragment.StockSuggestion> list = new ArrayList<>();
                    try {
                        JSONObject json   = new JSONObject(response.body().string());
                        JSONArray  result = json.optJSONArray("result");
                        if (result == null) return;
                        for (int i = 0; i < result.length(); i++) {
                            JSONObject o = result.optJSONObject(i);
                            if (o == null) continue;
                            String sym  = o.optString("symbol",      "").trim();
                            String name = o.optString("description", "");
                            String type = o.optString("type",        "");
                            if (sym.isEmpty()) continue;
                            list.add(new ChartFragment.StockSuggestion(sym, name, type));
                            if (list.size() >= 8) break;
                        }
                    } catch (Exception ignored) {}
                    final ArrayList<ChartFragment.StockSuggestion> finalList = list;
                    if (getActivity() == null) return;
                    if (!query.equals(latestQuery)) return;
                    getActivity().runOnUiThread(() -> {
                        suggestionAdapter.clear();
                        suggestionAdapter.addAll(finalList);
                        suggestionAdapter.notifyDataSetChanged();
                        if (!finalList.isEmpty() && input.isAttachedToWindow()) input.showDropDown();
                    });
                }
            });
        } catch (Exception ignored) {}
    }

    private void clearSuggestions() {
        if (suggestionAdapter != null) {
            suggestionAdapter.clear();
            suggestionAdapter.notifyDataSetChanged();
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────────────────

    private void handleStockClick(String symbol) {
        SharedViewModel vm = new ViewModelProvider(requireActivity())
                .get(SharedViewModel.class);

        vm.setSelectedSymbol(symbol);

        SharedPreferences prefs = requireActivity()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        boolean navigateToChart = prefs.getBoolean(KEY_WATCHLIST_NAV, true);

        if (navigateToChart) {
            Navigation.findNavController(requireView()).navigate(R.id.nav_chart);
        }
    }

    private void deleteStock(String symbol) {
        // חסימת אורחים
        if (isGuest()) {
            Toast.makeText(getContext(),
                    "כניסה כאורח — לא ניתן לבצע שינויים. התחבר עם חשבון.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (watchlistRef == null) return;
        String key = symbol.replace(":", "_");
        watchlistRef.child(key).removeValue();
        Toast.makeText(getContext(), symbol + " הוסר", Toast.LENGTH_SHORT).show();
    }

    private void showPriceAlertDialog(StockWatchData stock) {
        // חסימת אורחים
        if (isGuest()) {
            Toast.makeText(getContext(),
                    "כניסה כאורח — לא ניתן להגדיר התראות. התחבר עם חשבון.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (getContext() == null) return;
        EditText et = new EditText(getContext());
        et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        et.setHint("Target price (e.g. 200.00)");
        if (stock.alertTargetPrice > 0)
            et.setText(String.format(Locale.US, "%.2f", stock.alertTargetPrice));

        new AlertDialog.Builder(getContext())
                .setTitle("Set Price Alert for " + stock.symbol)
                .setView(et)
                .setPositiveButton("Set", (d, w) -> {
                    try {
                        float target = Float.parseFloat(et.getText().toString().trim());
                        if (watchlistRef == null) return;
                        String key = stock.symbol.replace(":", "_");
                        watchlistRef.child(key).child("alertTargetPrice").setValue(target);
                        watchlistRef.child(key).child("alertEnabled").setValue(true);
                        watchlistRef.child(key).child("alertTriggered").setValue(false);
                        stock.alertTargetPrice = target;
                        stock.alertEnabled     = true;
                        stock.alertTriggered   = false;
                        Toast.makeText(getContext(),
                                "Alert set at $" + target + " for " + stock.symbol,
                                Toast.LENGTH_SHORT).show();
                    } catch (NumberFormatException e) {
                        Toast.makeText(getContext(), "Invalid price", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNeutralButton("Remove", (d, w) -> {
                    if (watchlistRef == null) return;
                    String key = stock.symbol.replace(":", "_");
                    watchlistRef.child(key).child("alertEnabled").setValue(false);
                    watchlistRef.child(key).child("alertTargetPrice").setValue(0);
                    stock.alertEnabled     = false;
                    stock.alertTargetPrice = 0;
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && getContext() != null) {
            NotificationChannel channel = new NotificationChannel(
                    ALERT_CHANNEL_ID,
                    "Stock Price Alerts",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Alerts when stocks hit target price");
            NotificationManager nm = getContext().getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private void hideKeyboard() {
        if (getActivity() == null) return;
        View view = getActivity().getCurrentFocus();
        if (view == null) view = getView();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager)
                    getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }
}
