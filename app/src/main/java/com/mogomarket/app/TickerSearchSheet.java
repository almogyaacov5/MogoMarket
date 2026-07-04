package com.mogomarket.app;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

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

public class TickerSearchSheet extends BottomSheetDialogFragment {

    public interface OnTickerSelectedListener {
        void onTickerSelected(String symbol);
    }

    private OnTickerSelectedListener listener;

    public void setOnTickerSelectedListener(OnTickerSelectedListener listener) {
        this.listener = listener;
    }

    private static final String FINNHUB_KEY = "d918pn9r01qr1uqui560d918pn9r01qr1uqui56g";
    private static final long DEBOUNCE_MS = 250L;

    private final OkHttpClient client = new OkHttpClient();
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable pendingSearch;

    private String currentSymbol = "SPY";

    private static final List<ChartFragment.StockSuggestion> POPULAR = new ArrayList<>();
    private static final List<ChartFragment.StockSuggestion> CRYPTO_LIST = new ArrayList<>();
    private static final List<ChartFragment.StockSuggestion> FOREX_LIST = new ArrayList<>();

    static {
        POPULAR.add(new ChartFragment.StockSuggestion("SPY", "SPDR S&P 500 ETF", "ETF"));
        POPULAR.add(new ChartFragment.StockSuggestion("QQQ", "Invesco QQQ Trust", "ETF"));
        POPULAR.add(new ChartFragment.StockSuggestion("AAPL", "Apple Inc.", "Stock"));
        POPULAR.add(new ChartFragment.StockSuggestion("MSFT", "Microsoft Corp.", "Stock"));
        POPULAR.add(new ChartFragment.StockSuggestion("NVDA", "NVIDIA Corp.", "Stock"));
        POPULAR.add(new ChartFragment.StockSuggestion("TSLA", "Tesla Inc.", "Stock"));
        POPULAR.add(new ChartFragment.StockSuggestion("AMZN", "Amazon.com Inc.", "Stock"));
        POPULAR.add(new ChartFragment.StockSuggestion("META", "Meta Platforms", "Stock"));

        CRYPTO_LIST.add(new ChartFragment.StockSuggestion("BTC", "Bitcoin", "Crypto"));
        CRYPTO_LIST.add(new ChartFragment.StockSuggestion("ETH", "Ethereum", "Crypto"));
        CRYPTO_LIST.add(new ChartFragment.StockSuggestion("XRP", "Ripple", "Crypto"));
        CRYPTO_LIST.add(new ChartFragment.StockSuggestion("SOL", "Solana", "Crypto"));
        CRYPTO_LIST.add(new ChartFragment.StockSuggestion("BNB", "Binance Coin", "Crypto"));
        CRYPTO_LIST.add(new ChartFragment.StockSuggestion("DOGE", "Dogecoin", "Crypto"));
        CRYPTO_LIST.add(new ChartFragment.StockSuggestion("ADA", "Cardano", "Crypto"));
        CRYPTO_LIST.add(new ChartFragment.StockSuggestion("AVAX", "Avalanche", "Crypto"));
        CRYPTO_LIST.add(new ChartFragment.StockSuggestion("DOT", "Polkadot", "Crypto"));
        CRYPTO_LIST.add(new ChartFragment.StockSuggestion("LINK", "Chainlink", "Crypto"));
        CRYPTO_LIST.add(new ChartFragment.StockSuggestion("LTC", "Litecoin", "Crypto"));
        CRYPTO_LIST.add(new ChartFragment.StockSuggestion("MATIC", "Polygon", "Crypto"));
        CRYPTO_LIST.add(new ChartFragment.StockSuggestion("UNI", "Uniswap", "Crypto"));

        FOREX_LIST.add(new ChartFragment.StockSuggestion("EURUSD", "Euro / US Dollar", "Forex"));
        FOREX_LIST.add(new ChartFragment.StockSuggestion("USDILS", "US Dollar / Israeli Shekel", "Forex"));
        FOREX_LIST.add(new ChartFragment.StockSuggestion("GBPUSD", "British Pound / US Dollar", "Forex"));
        FOREX_LIST.add(new ChartFragment.StockSuggestion("USDJPY", "US Dollar / Japanese Yen", "Forex"));
        FOREX_LIST.add(new ChartFragment.StockSuggestion("AUDUSD", "Australian Dollar / US Dollar", "Forex"));
        FOREX_LIST.add(new ChartFragment.StockSuggestion("USDCAD", "US Dollar / Canadian Dollar", "Forex"));
        FOREX_LIST.add(new ChartFragment.StockSuggestion("USDCHF", "US Dollar / Swiss Franc", "Forex"));
        FOREX_LIST.add(new ChartFragment.StockSuggestion("NZDUSD", "New Zealand Dollar / US Dollar", "Forex"));
        FOREX_LIST.add(new ChartFragment.StockSuggestion("EURGBP", "Euro / British Pound", "Forex"));
        FOREX_LIST.add(new ChartFragment.StockSuggestion("EURJPY", "Euro / Japanese Yen", "Forex"));
        FOREX_LIST.add(new ChartFragment.StockSuggestion("GBPJPY", "British Pound / Japanese Yen", "Forex"));
        FOREX_LIST.add(new ChartFragment.StockSuggestion("USDINR", "US Dollar / Indian Rupee", "Forex"));
        FOREX_LIST.add(new ChartFragment.StockSuggestion("USDCNY", "US Dollar / Chinese Yuan", "Forex"));
        FOREX_LIST.add(new ChartFragment.StockSuggestion("USDBRL", "US Dollar / Brazilian Real", "Forex"));
        FOREX_LIST.add(new ChartFragment.StockSuggestion("USDMXN", "US Dollar / Mexican Peso", "Forex"));
        FOREX_LIST.add(new ChartFragment.StockSuggestion("EURILS", "Euro / Israeli Shekel", "Forex"));
        FOREX_LIST.add(new ChartFragment.StockSuggestion("GBPILS", "British Pound / Israeli Shekel", "Forex"));
        FOREX_LIST.add(new ChartFragment.StockSuggestion("XAUUSD", "Gold / US Dollar", "Commodity"));
        FOREX_LIST.add(new ChartFragment.StockSuggestion("GOLD", "Gold Futures", "Commodity"));
        FOREX_LIST.add(new ChartFragment.StockSuggestion("XAGUSD", "Silver / US Dollar", "Commodity"));
        FOREX_LIST.add(new ChartFragment.StockSuggestion("SILVER", "Silver Futures", "Commodity"));
        FOREX_LIST.add(new ChartFragment.StockSuggestion("XBRUSD", "Brent Crude Oil", "Commodity"));
        FOREX_LIST.add(new ChartFragment.StockSuggestion("OIL", "Crude Oil Futures", "Commodity"));
        FOREX_LIST.add(new ChartFragment.StockSuggestion("CRUDE", "Crude Oil", "Commodity"));
    }

    public void setCurrentSymbol(String symbol) {
        if (symbol != null && !symbol.trim().isEmpty()) {
            currentSymbol = symbol.trim().toUpperCase(Locale.US);
        }
    }

    @NonNull
    @Override
    public android.app.Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> {
            BottomSheetDialog d1 = (BottomSheetDialog) d;
            View sheet = d1.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);

                ViewGroup.LayoutParams params = sheet.getLayoutParams();
                params.height = WindowManager.LayoutParams.MATCH_PARENT;
                sheet.setLayoutParams(params);
            }
        });
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_ticker_search, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        AutoCompleteTextView searchInput = view.findViewById(R.id.searchTickerInput);
        ImageButton btnBack = view.findViewById(R.id.btnSearchBack);
        ImageButton btnClear = view.findViewById(R.id.btnClearSearch);
        TextView labelResults = view.findViewById(R.id.labelResults);
        ListView resultsList = view.findViewById(R.id.searchResultsList);

        labelResults.setText("Suggestions");

        ArrayAdapter<ChartFragment.StockSuggestion> adapter =
                new ArrayAdapter<ChartFragment.StockSuggestion>(
                        requireContext(),
                        android.R.layout.simple_list_item_1,
                        new ArrayList<>()
                ) {
                    @NonNull
                    @Override
                    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                        ChartFragment.StockSuggestion item = getItem(position);

                        if (item != null && item.isSectionHeader) {
                            TextView header = new TextView(requireContext());
                            header.setLayoutParams(new AbsListView.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                            ));
                            header.setText(item.sectionTitle);
                            header.setTextSize(13f);
                            header.setTypeface(null, android.graphics.Typeface.BOLD);
                            header.setPadding(32, 24, 32, 8);
                            header.setTextColor(0xFF8B98A5);
                            return header;
                        }

                        LinearLayoutCompat row = new LinearLayoutCompat(requireContext());
                        row.setOrientation(LinearLayoutCompat.HORIZONTAL);
                        row.setLayoutParams(new AbsListView.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                        ));
                        row.setPadding(28, 22, 28, 22);
                        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

                        TextView logo = new TextView(requireContext());
                        LinearLayoutCompat.LayoutParams logoLp =
                                new LinearLayoutCompat.LayoutParams(dpToPx(42), dpToPx(42));
                        logo.setLayoutParams(logoLp);
                        logo.setGravity(android.view.Gravity.CENTER);
                        logo.setTextSize(15f);
                        logo.setTypeface(null, android.graphics.Typeface.BOLD);
                        logo.setTextColor(0xFFFFFFFF);
                        logo.setBackgroundResource(R.drawable.bg_symbol_circle);

                        LinearLayoutCompat textBox = new LinearLayoutCompat(requireContext());
                        textBox.setOrientation(LinearLayoutCompat.VERTICAL);
                        LinearLayoutCompat.LayoutParams textLp =
                                new LinearLayoutCompat.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                        textLp.setMargins(dpToPx(12), 0, dpToPx(12), 0);
                        textBox.setLayoutParams(textLp);

                        TextView companyName = new TextView(requireContext());
                        companyName.setTextSize(14f);
                        companyName.setTypeface(null, android.graphics.Typeface.BOLD);
                        companyName.setTextColor(0xFFE6EDF3);

                        TextView symbolText = new TextView(requireContext());
                        symbolText.setTextSize(12f);
                        symbolText.setTextColor(0xFF8B98A5);

                        textBox.addView(companyName);
                        textBox.addView(symbolText);

                        TextView dailyChange = new TextView(requireContext());
                        dailyChange.setTextSize(12f);
                        dailyChange.setTypeface(null, android.graphics.Typeface.BOLD);

                        row.addView(logo);
                        row.addView(textBox);
                        row.addView(dailyChange);

                        if (item != null) {
                            String displaySymbol = item.symbol == null ? "" : item.symbol;
                            String displayName = item.name == null || item.name.trim().isEmpty()
                                    ? displaySymbol
                                    : item.name;

                            logo.setText(getLogoText(displaySymbol));
                            companyName.setText(displayName);
                            symbolText.setText(displaySymbol);

                            String pct = String.format(Locale.US, "%+.2f%%", item.dailyChangePercent);
                            dailyChange.setText(pct);
                            dailyChange.setTextColor(item.dailyChangePercent >= 0 ? 0xFF00C896 : 0xFFFF4D4D);
                        }

                        return row;
                    }

                    @Override
                    public boolean isEnabled(int position) {
                        ChartFragment.StockSuggestion item = getItem(position);
                        return item != null && !item.isSectionHeader;
                    }
                };

        resultsList.setAdapter(adapter);
        renderDefaultSuggestions(adapter);

        resultsList.setOnItemClickListener((parent, itemView, position, id) -> {
            ChartFragment.StockSuggestion s = adapter.getItem(position);
            if (s == null || s.isSectionHeader) return;

            hideKeyboard(searchInput);
            saveRecentSearch(s.symbol);

            if (listener != null) {
                listener.onTickerSelected(s.symbol);
            }
            dismiss();
        });

        btnBack.setOnClickListener(v -> dismiss());
        btnClear.setOnClickListener(v -> searchInput.setText(""));

        searchInput.setOnKeyListener((v, keyCode, event) -> {
            if (event != null && event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    hideKeyboard(searchInput);
                    resultsList.requestFocus();
                    return false;
                }
            }
            return false;
        });

        resultsList.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) hideKeyboard(searchInput);
        });

        resultsList.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view1, int scrollState) {
                if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
                    hideKeyboard(searchInput);
                }
            }

            @Override
            public void onScroll(AbsListView view12, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
            }
        });

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                btnClear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
            }

            @Override
            public void afterTextChanged(Editable s) {
                String q = s.toString().trim();

                handler.removeCallbacksAndMessages(null);

                if (q.isEmpty()) {
                    labelResults.setText("Suggestions");
                    renderDefaultSuggestions(adapter);
                    return;
                }

                labelResults.setText("Results");
                pendingSearch = () -> fetchAll(q, adapter);
                handler.postDelayed(pendingSearch, DEBOUNCE_MS);
            }
        });

        searchInput.requestFocus();
        searchInput.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager)
                    requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 150);
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager)
                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && view != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
        if (view != null) view.clearFocus();
    }

//    private void updateCurrentTickerBar() {
//        if (getView() == null) return;
//        TextView txtSymbol = getView().findViewById(R.id.txtCurrentTicker);
//        TextView txtChart = getView().findViewById(R.id.txtCurrentChart);
//
//        if (txtSymbol != null) txtSymbol.setText(currentSymbol);
//        if (txtChart != null) txtChart.setText("Viewing chart");
//    }

    private void fetchAll(String query, ArrayAdapter<ChartFragment.StockSuggestion> adapter) {
        String q = query.toUpperCase(Locale.US);

        List<ChartFragment.StockSuggestion> cryptoMatches = new ArrayList<>();
        for (ChartFragment.StockSuggestion c : CRYPTO_LIST) {
            if (c.symbol != null &&
                    (c.symbol.startsWith(q) || (c.name != null && c.name.toUpperCase(Locale.US).contains(q)))) {
                cryptoMatches.add(copySuggestionWithQuote(c, 0f));
            }
        }

        List<ChartFragment.StockSuggestion> forexMatches = new ArrayList<>();
        for (ChartFragment.StockSuggestion f : FOREX_LIST) {
            if (f.symbol != null &&
                    (f.symbol.contains(q) || (f.name != null && f.name.toUpperCase(Locale.US).contains(q)))) {
                forexMatches.add(copySuggestionWithQuote(f, 0f));
            }
        }

        fetchStocks(query, cryptoMatches, forexMatches, adapter);
    }

    private void fetchStocks(String query,
                             List<ChartFragment.StockSuggestion> cryptoMatches,
                             List<ChartFragment.StockSuggestion> forexMatches,
                             ArrayAdapter<ChartFragment.StockSuggestion> adapter) {
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name());
            String url = "https://finnhub.io/api/v1/search?q=" + encoded + "&token=" + FINNHUB_KEY;
            Request req = new Request.Builder().url(url).build();

            client.newCall(req).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    postResults(cryptoMatches, forexMatches, new ArrayList<>(), adapter);
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    List<ChartFragment.StockSuggestion> stocks = new ArrayList<>();

                    if (response.isSuccessful() && response.body() != null) {
                        try {
                            JSONObject root = new JSONObject(response.body().string());
                            JSONArray arr = root.optJSONArray("result");

                            if (arr != null) {
                                for (int i = 0; i < Math.min(arr.length(), 8); i++) {
                                    JSONObject o = arr.getJSONObject(i);
                                    String sym = o.optString("symbol", "");
                                    String name = o.optString("description", "");
                                    String exch = o.optString("type", "");

                                    if (!sym.isEmpty()) {
                                        stocks.add(new ChartFragment.StockSuggestion(
                                                sym,
                                                name,
                                                exch,
                                                false,
                                                null,
                                                0f
                                        ));
                                    }
                                }
                            }
                        } catch (Exception ignored) {
                        }
                    }

                    postResults(cryptoMatches, forexMatches, stocks, adapter);
                }
            });
        } catch (Exception ignored) {
            postResults(cryptoMatches, forexMatches, new ArrayList<>(), adapter);
        }
    }

    private void postResults(List<ChartFragment.StockSuggestion> crypto,
                             List<ChartFragment.StockSuggestion> forex,
                             List<ChartFragment.StockSuggestion> stocks,
                             ArrayAdapter<ChartFragment.StockSuggestion> adapter) {
        if (getActivity() == null) return;

        getActivity().runOnUiThread(() -> {
            List<ChartFragment.StockSuggestion> merged = new ArrayList<>();

            if (!crypto.isEmpty() || !forex.isEmpty() || !stocks.isEmpty()) {
                if (!stocks.isEmpty()) {
                    merged.add(ChartFragment.StockSuggestion.section("Stocks"));
                    merged.addAll(stocks);
                }

                if (!crypto.isEmpty() || !forex.isEmpty()) {
                    merged.add(ChartFragment.StockSuggestion.section("Popular Crypto & Forex"));
                    merged.addAll(crypto);
                    merged.addAll(forex);
                }
            }

            adapter.clear();
            adapter.addAll(merged);
            adapter.notifyDataSetChanged();
        });
    }
    private void renderDefaultSuggestions(ArrayAdapter<ChartFragment.StockSuggestion> adapter) {
        List<ChartFragment.StockSuggestion> merged = new ArrayList<>();

        List<ChartFragment.StockSuggestion> recent = loadRecentSearches();
        if (!recent.isEmpty()) {
            merged.add(ChartFragment.StockSuggestion.section("Recently Searched"));
            merged.addAll(recent);
        }

        merged.add(ChartFragment.StockSuggestion.section("Popular Stocks"));
        for (ChartFragment.StockSuggestion item : POPULAR) {
            merged.add(copySuggestionWithQuote(item, 0f));
        }

        merged.add(ChartFragment.StockSuggestion.section("Popular Crypto & Forex"));
        for (int i = 0; i < Math.min(4, CRYPTO_LIST.size()); i++) {
            merged.add(copySuggestionWithQuote(CRYPTO_LIST.get(i), 0f));
        }
        merged.add(copySuggestionWithQuote(new ChartFragment.StockSuggestion("EUR/USD", "Euro / US Dollar", "Forex", false, null, 0f), 0f));
        merged.add(copySuggestionWithQuote(new ChartFragment.StockSuggestion("USD/ILS", "US Dollar / Israeli Shekel", "Forex", false, null, 0f), 0f));
        merged.add(copySuggestionWithQuote(new ChartFragment.StockSuggestion("GBP/USD", "British Pound / US Dollar", "Forex", false, null, 0f), 0f));
        merged.add(copySuggestionWithQuote(new ChartFragment.StockSuggestion("USD/JPY", "US Dollar / Japanese Yen", "Forex", false, null, 0f), 0f));

        adapter.clear();
        adapter.addAll(merged);
        adapter.notifyDataSetChanged();
    }

    private List<ChartFragment.StockSuggestion> loadRecentSearches() {
        List<ChartFragment.StockSuggestion> out = new ArrayList<>();

        Context context = getContext();
        if (context == null) return out;

        android.content.SharedPreferences prefs =
                context.getSharedPreferences("ticker_search_prefs", Context.MODE_PRIVATE);

        String raw = prefs.getString("recent_symbols", "");
        if (raw == null || raw.trim().isEmpty()) return out;

        String[] parts = raw.split(",");
        for (String part : parts) {
            String symbol = part.trim();
            if (!symbol.isEmpty()) {
                out.add(new ChartFragment.StockSuggestion(
                        symbol,
                        symbol,
                        "Recent",
                        false,
                        null,
                        0f
                ));
            }
        }

        return out;
    }

    private void saveRecentSearch(String symbol) {
        if (symbol == null || symbol.trim().isEmpty() || getContext() == null) return;

        String clean = symbol.trim().toUpperCase(Locale.US);
        android.content.SharedPreferences prefs =
                requireContext().getSharedPreferences("ticker_search_prefs", Context.MODE_PRIVATE);

        String raw = prefs.getString("recent_symbols", "");
        List<String> items = new ArrayList<>();

        if (raw != null && !raw.trim().isEmpty()) {
            String[] existing = raw.split(",");
            for (String s : existing) {
                String value = s.trim().toUpperCase(Locale.US);
                if (!value.isEmpty() && !value.equals(clean)) {
                    items.add(value);
                }
            }
        }

        items.add(0, clean);

        if (items.size() > 10) {
            items = new ArrayList<>(items.subList(0, 10));
        }

        String joined = android.text.TextUtils.join(",", items);
        prefs.edit().putString("recent_symbols", joined).apply();
    }

    private ChartFragment.StockSuggestion copySuggestionWithQuote(ChartFragment.StockSuggestion item, float dailyChangePercent) {
        if (item == null) {
            return new ChartFragment.StockSuggestion("", "", "", false, null, 0f);
        }

        return new ChartFragment.StockSuggestion(
                item.symbol,
                item.name,
                item.exchange,
                false,
                null,
                dailyChangePercent
        );
    }

    private String getLogoText(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) return "?";

        String clean = symbol.replace("/", "").replace(":", "").trim();
        if (clean.length() >= 2) {
            return clean.substring(0, 2).toUpperCase(Locale.US);
        }
        return clean.substring(0, 1).toUpperCase(Locale.US);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * requireContext().getResources().getDisplayMetrics().density);
    }
}