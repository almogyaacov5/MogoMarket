package com.mogomarket.app;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class TickerSearchSheet extends BottomSheetDialogFragment {
    private static final String TAG = "TickerSearchSheet";

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
    private int searchGeneration;

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
                behavior.setDraggable(false);

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
                            header.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(6));
                            header.setTextColor(requireContext().getColor(R.color.text_secondary));
                            return header;
                        }

                        android.widget.LinearLayout row = new android.widget.LinearLayout(requireContext());
                        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
                        row.setLayoutParams(new AbsListView.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                        ));
                        row.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));
                        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

                        TextView logo = new TextView(requireContext());
                        android.widget.LinearLayout.LayoutParams logoLp =
                                new android.widget.LinearLayout.LayoutParams(dpToPx(42), dpToPx(42));
                        logo.setLayoutParams(logoLp);
                        logo.setGravity(android.view.Gravity.CENTER);
                        logo.setTextSize(14f);
                        logo.setTypeface(null, android.graphics.Typeface.BOLD);
                        logo.setTextColor(0xFFFFFFFF);

                        android.graphics.drawable.GradientDrawable logoBg =
                                new android.graphics.drawable.GradientDrawable();
                        logoBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                        logoBg.setColor(0xFF4DA3FF);
                        logo.setBackground(logoBg);

                        android.widget.LinearLayout textBox = new android.widget.LinearLayout(requireContext());
                        textBox.setOrientation(android.widget.LinearLayout.VERTICAL);
                        android.widget.LinearLayout.LayoutParams textLp =
                                new android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                        textLp.setMargins(dpToPx(12), 0, dpToPx(12), 0);
                        textBox.setLayoutParams(textLp);

                        TextView companyName = new TextView(requireContext());
                        companyName.setTextSize(14f);
                        companyName.setTypeface(null, android.graphics.Typeface.BOLD);
                        companyName.setTextColor(requireContext().getColor(R.color.text_primary));
                        companyName.setSingleLine(true);
                        companyName.setEllipsize(android.text.TextUtils.TruncateAt.END);

                        TextView symbolText = new TextView(requireContext());
                        symbolText.setTextSize(12f);
                        symbolText.setTextColor(requireContext().getColor(R.color.text_secondary));
                        symbolText.setSingleLine(true);
                        symbolText.setEllipsize(android.text.TextUtils.TruncateAt.END);

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

                            String pct = Float.isNaN(item.dailyChangePercent)
                                    ? "—"
                                    : String.format(Locale.US, "%+.2f%%", item.dailyChangePercent);
                            dailyChange.setText(pct);
                            int color = Float.isNaN(item.dailyChangePercent)
                                    ? requireContext().getColor(R.color.text_secondary)
                                    : requireContext().getColor(item.dailyChangePercent >= 0
                                            ? R.color.gain : R.color.loss);
                            dailyChange.setTextColor(color);
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
                int generation = ++searchGeneration;

                handler.removeCallbacksAndMessages(null);

                if (q.isEmpty()) {
                    labelResults.setText("Suggestions");
                    renderDefaultSuggestions(adapter, generation);
                    return;
                }

                labelResults.setText("Results");
                pendingSearch = () -> fetchAll(q, adapter, generation);
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

    private void fetchAll(String query,
                          ArrayAdapter<ChartFragment.StockSuggestion> adapter,
                          int generation) {
        String q = query.toUpperCase(Locale.US);

        List<ChartFragment.StockSuggestion> cryptoMatches = new ArrayList<>();
        for (ChartFragment.StockSuggestion c : CRYPTO_LIST) {
            if (c.symbol != null &&
                    (c.symbol.startsWith(q) || (c.name != null && c.name.toUpperCase(Locale.US).contains(q)))) {
                cryptoMatches.add(copySuggestionWithQuote(c, Float.NaN));
            }
        }

        List<ChartFragment.StockSuggestion> forexMatches = new ArrayList<>();
        for (ChartFragment.StockSuggestion f : FOREX_LIST) {
            if (f.symbol != null &&
                    (f.symbol.contains(q) || (f.name != null && f.name.toUpperCase(Locale.US).contains(q)))) {
                forexMatches.add(copySuggestionWithQuote(f, Float.NaN));
            }
        }

        SearchAccumulator accumulator = new SearchAccumulator(
                query, cryptoMatches, forexMatches, adapter, generation);
        fetchFinnhubStocks(query, accumulator);
        fetchYahooStocks(query, accumulator);
    }

    private void fetchFinnhubStocks(String query, SearchAccumulator accumulator) {
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name());
            String url = "https://finnhub.io/api/v1/search?q=" + encoded + "&token=" + FINNHUB_KEY;
            Request req = new Request.Builder().url(url).build();

            client.newCall(req).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    accumulator.complete(new ArrayList<>());
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    List<ChartFragment.StockSuggestion> stocks = new ArrayList<>();

                    if (response.isSuccessful() && response.body() != null) {
                        try {
                            JSONObject root = new JSONObject(response.body().string());
                            JSONArray arr = root.optJSONArray("result");

                            if (arr != null) {
                                for (int i = 0; i < arr.length(); i++) {
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
                                                Float.NaN
                                        ));
                                    }
                                }
                            }
                        } catch (org.json.JSONException ignored) {
                        }
                    }

                    accumulator.complete(stocks);
                }
            });
        } catch (java.io.UnsupportedEncodingException e) {
            accumulator.complete(new ArrayList<>());
        }
    }

    private void fetchYahooStocks(String query, SearchAccumulator accumulator) {
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name());
            String url = "https://query1.finance.yahoo.com/v1/finance/search?q=" + encoded
                    + "&quotesCount=25&newsCount=0";
            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    accumulator.complete(new ArrayList<>());
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    List<ChartFragment.StockSuggestion> stocks = new ArrayList<>();
                    if (response.isSuccessful() && response.body() != null) {
                        try {
                            JSONObject root = new JSONObject(response.body().string());
                            JSONArray quotes = root.optJSONArray("quotes");
                            if (quotes != null) {
                                for (int i = 0; i < quotes.length(); i++) {
                                    JSONObject quote = quotes.optJSONObject(i);
                                    if (quote == null) continue;
                                    String symbol = quote.optString("symbol", "").trim();
                                    if (symbol.isEmpty()) continue;
                                    String name = quote.optString("longname",
                                                quote.optString("shortname", symbol));
                                    String exchange = quote.optString("exchDisp",
                                                quote.optString("quoteType", ""));
                                    stocks.add(new ChartFragment.StockSuggestion(symbol, name, exchange));
                                }
                            }
                        } catch (org.json.JSONException ignored) {
                        }
                    }
                    accumulator.complete(stocks);
                }
            });
        } catch (java.io.UnsupportedEncodingException e) {
            accumulator.complete(new ArrayList<>());
        }
    }

    private void postResults(SearchAccumulator accumulator) {
        if (getActivity() == null || accumulator.generation != searchGeneration) return;

        getActivity().runOnUiThread(() -> {
            if (!isAdded() || accumulator.generation != searchGeneration) return;

            List<ChartFragment.StockSuggestion> stocks = new ArrayList<>(accumulator.stocks);
            String directSymbol = accumulator.query.trim().toUpperCase(Locale.US);
            if (directSymbol.matches("[A-Z0-9.^=_:-]{1,24}")) {
                stocks.add(new ChartFragment.StockSuggestion(
                        directSymbol, "Use ticker " + directSymbol, "Symbol"));
            }
            stocks = removeDuplicateSuggestions(stocks);

            List<ChartFragment.StockSuggestion> merged = new ArrayList<>();
            if (!stocks.isEmpty()) {
                merged.add(ChartFragment.StockSuggestion.section("Stocks"));
                merged.addAll(stocks);
            }
            if (!accumulator.crypto.isEmpty() || !accumulator.forex.isEmpty()) {
                merged.add(ChartFragment.StockSuggestion.section("Popular Crypto & Forex"));
                merged.addAll(accumulator.crypto);
                merged.addAll(accumulator.forex);
            }

            accumulator.adapter.clear();
            accumulator.adapter.addAll(merged);
            accumulator.adapter.notifyDataSetChanged();
            fetchDailyChanges(merged, accumulator.adapter, accumulator.generation);
        });
    }

    private List<ChartFragment.StockSuggestion> removeDuplicateSuggestions(
            List<ChartFragment.StockSuggestion> suggestions) {
        List<ChartFragment.StockSuggestion> unique = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ChartFragment.StockSuggestion suggestion : suggestions) {
            if (suggestion == null || suggestion.symbol == null) continue;
            String key = suggestion.symbol.trim().toUpperCase(Locale.US);
            if (seen.add(key)) unique.add(suggestion);
        }
        return unique;
    }

    private final class SearchAccumulator {
        private final String query;
        private final List<ChartFragment.StockSuggestion> crypto;
        private final List<ChartFragment.StockSuggestion> forex;
        private final ArrayAdapter<ChartFragment.StockSuggestion> adapter;
        private final int generation;
        private final List<ChartFragment.StockSuggestion> stocks = new ArrayList<>();
        private final AtomicInteger pendingProviders = new AtomicInteger(2);

        private SearchAccumulator(String query,
                                  List<ChartFragment.StockSuggestion> crypto,
                                  List<ChartFragment.StockSuggestion> forex,
                                  ArrayAdapter<ChartFragment.StockSuggestion> adapter,
                                  int generation) {
            this.query = query;
            this.crypto = crypto;
            this.forex = forex;
            this.adapter = adapter;
            this.generation = generation;
        }

        private synchronized void complete(List<ChartFragment.StockSuggestion> results) {
            stocks.addAll(results);
            if (pendingProviders.decrementAndGet() == 0) postResults(this);
        }
    }
    private void renderDefaultSuggestions(ArrayAdapter<ChartFragment.StockSuggestion> adapter) {
        renderDefaultSuggestions(adapter, searchGeneration);
    }

    private void renderDefaultSuggestions(ArrayAdapter<ChartFragment.StockSuggestion> adapter,
                                          int generation) {
        List<ChartFragment.StockSuggestion> merged = new ArrayList<>();

        List<ChartFragment.StockSuggestion> recent = loadRecentSearches();
        if (!recent.isEmpty()) {
            merged.add(ChartFragment.StockSuggestion.section("Recently Searched"));
            merged.addAll(recent);
        }

        merged.add(ChartFragment.StockSuggestion.section("Popular Stocks"));
        for (ChartFragment.StockSuggestion item : POPULAR) {
            merged.add(copySuggestionWithQuote(item, Float.NaN));
        }

        merged.add(ChartFragment.StockSuggestion.section("Popular Crypto & Forex"));
        for (int i = 0; i < Math.min(4, CRYPTO_LIST.size()); i++) {
            merged.add(copySuggestionWithQuote(CRYPTO_LIST.get(i), Float.NaN));
        }
        merged.add(copySuggestionWithQuote(new ChartFragment.StockSuggestion("EURUSD", "Euro / US Dollar", "Forex"), Float.NaN));
        merged.add(copySuggestionWithQuote(new ChartFragment.StockSuggestion("USDILS", "US Dollar / Israeli Shekel", "Forex"), Float.NaN));
        merged.add(copySuggestionWithQuote(new ChartFragment.StockSuggestion("GBPUSD", "British Pound / US Dollar", "Forex"), Float.NaN));
        merged.add(copySuggestionWithQuote(new ChartFragment.StockSuggestion("USDJPY", "US Dollar / Japanese Yen", "Forex"), Float.NaN));

        adapter.clear();
        adapter.addAll(merged);
        adapter.notifyDataSetChanged();
        fetchDailyChanges(merged, adapter, generation);
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

    @Override
    public void onDestroyView() {
        searchGeneration++;
        handler.removeCallbacksAndMessages(null);
        super.onDestroyView();
    }

    private ChartFragment.StockSuggestion copySuggestionWithQuote(ChartFragment.StockSuggestion item, float dailyChangePercent) {
        if (item == null) {
            return new ChartFragment.StockSuggestion("", "", "", false, null, Float.NaN);
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

    private void fetchDailyChanges(List<ChartFragment.StockSuggestion> items,
                                   ArrayAdapter<ChartFragment.StockSuggestion> adapter,
                                   int generation) {
        for (ChartFragment.StockSuggestion item : items) {
            if (item == null || item.isSectionHeader || item.symbol == null) continue;
            String symbol = item.symbol;
            String yahooSymbol = getYahooSymbol(symbol);
            try {
                String encoded = URLEncoder.encode(yahooSymbol, StandardCharsets.UTF_8.name());
                Request request = new Request.Builder()
                        .url("https://query1.finance.yahoo.com/v8/finance/chart/" + encoded
                                + "?range=5d&interval=1d&includePrePost=false")
                        .header("User-Agent", "Mozilla/5.0")
                        .build();
                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        Log.w(TAG, "Failed to fetch daily change for " + symbol, e);
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response)
                            throws IOException {
                        if (!response.isSuccessful() || response.body() == null) {
                            Log.w(TAG, "Daily change request failed for " + symbol
                                    + ": HTTP " + response.code());
                            return;
                        }
                        float change;
                        try {
                            JSONObject root = new JSONObject(response.body().string())
                                    .getJSONObject("chart");
                            JSONArray results = root.optJSONArray("result");
                            if (results == null || results.length() == 0) return;
                            YahooQuoteMath.Quote marketQuote =
                                    YahooQuoteMath.fromChartItem(results.getJSONObject(0));
                            change = (float) marketQuote.percentChange();
                        } catch (Exception e) {
                            Log.w(TAG, "Failed to parse daily change for " + symbol, e);
                            return;
                        }
                        if (!Float.isFinite(change) || getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {
                            if (!isAdded() || generation != searchGeneration) return;
                            for (int i = 0; i < adapter.getCount(); i++) {
                                ChartFragment.StockSuggestion current = adapter.getItem(i);
                                if (current == null || current.isSectionHeader
                                        || !symbol.equalsIgnoreCase(current.symbol)) continue;
                                ChartFragment.StockSuggestion updated = new ChartFragment.StockSuggestion(
                                        current.symbol, current.name, current.exchange,
                                        false, null, change);
                                adapter.setNotifyOnChange(false);
                                adapter.remove(current);
                                adapter.insert(updated, i);
                                adapter.notifyDataSetChanged();
                                adapter.setNotifyOnChange(true);
                                break;
                            }
                        });
                    }
                });
            } catch (Exception e) {
                Log.w(TAG, "Failed to prepare daily change request for " + symbol, e);
            }
        }
    }

    private String getYahooSymbol(String symbol) {
        String normalized = symbol.trim().toUpperCase(Locale.US);
        String crypto = ChartFragment.CRYPTO_MAP.get(normalized);
        if (crypto != null) {
            String pair = crypto.substring(crypto.indexOf(':') + 1);
            if (pair.endsWith("USDT")) return pair.substring(0, pair.length() - 4) + "-USD";
            return pair;
        }
        String forex = ChartFragment.FOREX_MAP.get(normalized);
        if (forex != null) return forex;
        return normalized;
    }
}