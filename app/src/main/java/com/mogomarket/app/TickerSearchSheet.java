package com.mogomarket.app;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import android.os.Handler;
import android.os.Looper;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class TickerSearchSheet extends BottomSheetDialogFragment {

    public interface OnTickerSelectedListener {
        void onTickerSelected(String symbol);
    }

    private static final String FINNHUB_KEY = "d918pn9r01qr1uqui560d918pn9r01qr1uqui56g";
    private static final long   DEBOUNCE_MS = 300;

    private OnTickerSelectedListener listener;
    private final OkHttpClient client  = new OkHttpClient();
    private final Handler      handler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;
    private String currentSymbol = "SPY";

    // ── Popular ─────────────────────────────────────────────────────────────
    private static final List<ChartFragment.StockSuggestion> POPULAR = Arrays.asList(
            new ChartFragment.StockSuggestion("SPY",     "S&P 500 ETF",        "ETF"),
            new ChartFragment.StockSuggestion("AAPL",    "Apple Inc.",          "NASDAQ"),
            new ChartFragment.StockSuggestion("TSLA",    "Tesla Inc.",          "NASDAQ"),
            new ChartFragment.StockSuggestion("NVDA",    "NVIDIA Corporation",  "NASDAQ"),
            new ChartFragment.StockSuggestion("AMZN",    "Amazon.com Inc.",     "NASDAQ"),
            new ChartFragment.StockSuggestion("MSFT",    "Microsoft Corp.",     "NASDAQ"),
            new ChartFragment.StockSuggestion("BTC",     "Bitcoin",             "CRYPTO"),
            new ChartFragment.StockSuggestion("ETH",     "Ethereum",            "CRYPTO"),
            new ChartFragment.StockSuggestion("SOL",     "Solana",              "CRYPTO"),
            new ChartFragment.StockSuggestion("XRP",     "Ripple",              "CRYPTO"),
            new ChartFragment.StockSuggestion("EURUSD",  "Euro / US Dollar",    "FOREX"),
            new ChartFragment.StockSuggestion("USDILS",  "US Dollar / Israeli Shekel", "FOREX"),
            new ChartFragment.StockSuggestion("GBPUSD",  "British Pound / USD", "FOREX"),
            new ChartFragment.StockSuggestion("USDJPY",  "US Dollar / Japanese Yen", "FOREX")
    );

    // ── רשימת קריפטו מקומית ─────────────────────────────────────────────────
    private static final List<ChartFragment.StockSuggestion> CRYPTO_LIST = Arrays.asList(
            new ChartFragment.StockSuggestion("BTC",   "Bitcoin",          "CRYPTO"),
            new ChartFragment.StockSuggestion("ETH",   "Ethereum",         "CRYPTO"),
            new ChartFragment.StockSuggestion("SOL",   "Solana",           "CRYPTO"),
            new ChartFragment.StockSuggestion("XRP",   "Ripple",           "CRYPTO"),
            new ChartFragment.StockSuggestion("BNB",   "Binance Coin",     "CRYPTO"),
            new ChartFragment.StockSuggestion("ADA",   "Cardano",          "CRYPTO"),
            new ChartFragment.StockSuggestion("DOGE",  "Dogecoin",         "CRYPTO"),
            new ChartFragment.StockSuggestion("AVAX",  "Avalanche",        "CRYPTO"),
            new ChartFragment.StockSuggestion("DOT",   "Polkadot",         "CRYPTO"),
            new ChartFragment.StockSuggestion("LINK",  "Chainlink",        "CRYPTO"),
            new ChartFragment.StockSuggestion("MATIC", "Polygon",          "CRYPTO"),
            new ChartFragment.StockSuggestion("LTC",   "Litecoin",         "CRYPTO"),
            new ChartFragment.StockSuggestion("UNI",   "Uniswap",          "CRYPTO"),
            new ChartFragment.StockSuggestion("SHIB",  "Shiba Inu",        "CRYPTO"),
            new ChartFragment.StockSuggestion("TRX",   "TRON",             "CRYPTO"),
            new ChartFragment.StockSuggestion("ATOM",  "Cosmos",           "CRYPTO"),
            new ChartFragment.StockSuggestion("XLM",   "Stellar",          "CRYPTO"),
            new ChartFragment.StockSuggestion("NEAR",  "NEAR Protocol",    "CRYPTO"),
            new ChartFragment.StockSuggestion("APT",   "Aptos",            "CRYPTO"),
            new ChartFragment.StockSuggestion("OP",    "Optimism",         "CRYPTO")
    );

    // ── רשימת פורקס מקומית ──────────────────────────────────────────────────
    private static final List<ChartFragment.StockSuggestion> FOREX_LIST = Arrays.asList(
            new ChartFragment.StockSuggestion("EURUSD",  "Euro / US Dollar",             "FOREX"),
            new ChartFragment.StockSuggestion("USDILS",  "US Dollar / Israeli Shekel",   "FOREX"),
            new ChartFragment.StockSuggestion("GBPUSD",  "British Pound / US Dollar",    "FOREX"),
            new ChartFragment.StockSuggestion("USDJPY",  "US Dollar / Japanese Yen",     "FOREX"),
            new ChartFragment.StockSuggestion("AUDUSD",  "Australian Dollar / USD",      "FOREX"),
            new ChartFragment.StockSuggestion("USDCAD",  "US Dollar / Canadian Dollar",  "FOREX"),
            new ChartFragment.StockSuggestion("USDCHF",  "US Dollar / Swiss Franc",      "FOREX"),
            new ChartFragment.StockSuggestion("NZDUSD",  "New Zealand Dollar / USD",     "FOREX"),
            new ChartFragment.StockSuggestion("EURGBP",  "Euro / British Pound",         "FOREX"),
            new ChartFragment.StockSuggestion("EURJPY",  "Euro / Japanese Yen",          "FOREX"),
            new ChartFragment.StockSuggestion("GBPJPY",  "British Pound / Japanese Yen", "FOREX"),
            new ChartFragment.StockSuggestion("USDINR",  "US Dollar / Indian Rupee",     "FOREX"),
            new ChartFragment.StockSuggestion("USDCNY",  "US Dollar / Chinese Yuan",     "FOREX"),
            new ChartFragment.StockSuggestion("USDBRL",  "US Dollar / Brazilian Real",   "FOREX"),
            new ChartFragment.StockSuggestion("USDMXN",  "US Dollar / Mexican Peso",     "FOREX"),
            new ChartFragment.StockSuggestion("EURILS",  "Euro / Israeli Shekel",        "FOREX"),
            new ChartFragment.StockSuggestion("GBPILS",  "British Pound / Israeli Shekel","FOREX"),
            new ChartFragment.StockSuggestion("XAUUSD",  "Gold / US Dollar",             "FOREX"),
            new ChartFragment.StockSuggestion("XAGUSD",  "Silver / US Dollar",           "FOREX"),
            new ChartFragment.StockSuggestion("XBRUSD",  "Brent Crude / US Dollar",      "FOREX")
    );

    public void setOnTickerSelectedListener(OnTickerSelectedListener l) { this.listener = l; }

    public void setCurrentSymbol(String symbol) {
        this.currentSymbol = (symbol != null && !symbol.isEmpty()) ? symbol : "SPY";
        if (getView() != null) updateCurrentTickerBar();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_ticker_search, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        BottomSheetDialog bsd = (BottomSheetDialog) requireDialog();
        bsd.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        View bs = bsd.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bs != null) {
            BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bs);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            behavior.setSkipCollapsed(true);
            bs.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
        }

        AutoCompleteTextView searchInput = view.findViewById(R.id.searchTickerInput);
        ImageButton btnBack              = view.findViewById(R.id.btnSearchBack);
        ImageButton btnClear             = view.findViewById(R.id.btnClearSearch);
        ListView    resultsList          = view.findViewById(R.id.searchResultsList);
        TextView    labelResults         = view.findViewById(R.id.labelResults);

        updateCurrentTickerBar();

        ArrayAdapter<ChartFragment.StockSuggestion> adapter =
                new ArrayAdapter<ChartFragment.StockSuggestion>(
                        requireContext(),
                        android.R.layout.simple_list_item_2,
                        android.R.id.text1,
                        new ArrayList<>(POPULAR)) {

                    @NonNull
                    @Override
                    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                        if (convertView == null)
                            convertView = LayoutInflater.from(getContext())
                                    .inflate(android.R.layout.simple_list_item_2, parent, false);

                        ChartFragment.StockSuggestion item = getItem(position);
                        TextView t1 = convertView.findViewById(android.R.id.text1);
                        TextView t2 = convertView.findViewById(android.R.id.text2);
                        if (item != null) {
                            String label;
                            if ("CRYPTO".equals(item.exchange)) {
                                label = "\uD83E\uDE99 " + item.symbol;
                            } else if ("FOREX".equals(item.exchange)) {
                                label = "\uD83D\uDCB1 " + item.symbol;
                            } else {
                                label = item.symbol;
                            }
                            t1.setText(label);
                            t1.setTextColor(0xFFE6EDF3);
                            t1.setTextSize(15f);
                            t1.setTypeface(null, android.graphics.Typeface.BOLD);
                            t2.setText(item.name + (item.exchange.isEmpty() ? "" : "  \u00b7  " + item.exchange));
                            t2.setTextColor(0xFF8B98A5);
                            t2.setTextSize(12f);
                        }
                        convertView.setBackgroundColor(0xFF151C2E);
                        convertView.setPadding(56, 20, 56, 20);
                        return convertView;
                    }
                };

        resultsList.setAdapter(adapter);

        resultsList.setOnItemClickListener((parent, v, position, id) -> {
            ChartFragment.StockSuggestion s = adapter.getItem(position);
            if (s != null && listener != null) listener.onTickerSelected(s.symbol);
            dismiss();
        });

        btnBack.setOnClickListener(v -> dismiss());
        btnClear.setOnClickListener(v -> searchInput.setText(""));

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                btnClear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
            }
            @Override public void afterTextChanged(Editable s) {
                String q = s.toString().trim();
                if (q.isEmpty()) {
                    labelResults.setText("Popular");
                    adapter.clear();
                    adapter.addAll(POPULAR);
                    adapter.notifyDataSetChanged();
                    return;
                }
                labelResults.setText("Results");
                handler.removeCallbacks(pendingSearch);
                pendingSearch = () -> fetchAll(q, adapter);
                handler.postDelayed(pendingSearch, DEBOUNCE_MS);
            }
        });

        searchInput.requestFocus();
        searchInput.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager)
                    requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT);
        }, 150);
    }

    private void updateCurrentTickerBar() {
        if (getView() == null) return;
        TextView txtSymbol = getView().findViewById(R.id.txtCurrentTicker);
        TextView txtChart  = getView().findViewById(R.id.txtCurrentChart);
        if (txtSymbol != null) txtSymbol.setText(currentSymbol);
        if (txtChart  != null) txtChart.setText("Viewing chart");
    }

    // ── חיפוש משולב: פורקס + קריפטו (סינון מקומי) + אקציות (Finnhub) ───────
    private void fetchAll(String query, ArrayAdapter<ChartFragment.StockSuggestion> adapter) {
        String q = query.toUpperCase(Locale.US);

        // 1. סינון קריפטו מקומי (מיידי)
        List<ChartFragment.StockSuggestion> cryptoMatches = new ArrayList<>();
        for (ChartFragment.StockSuggestion c : CRYPTO_LIST) {
            if (c.symbol.startsWith(q) || c.name.toUpperCase(Locale.US).contains(q)) {
                cryptoMatches.add(c);
            }
        }

        // 2. סינון פורקס מקומי (מיידי)
        List<ChartFragment.StockSuggestion> forexMatches = new ArrayList<>();
        for (ChartFragment.StockSuggestion f : FOREX_LIST) {
            if (f.symbol.contains(q) || f.name.toUpperCase(Locale.US).contains(q)) {
                forexMatches.add(f);
            }
        }

        // 3. חיפוש אקציות מהרשת
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
                @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    postResults(cryptoMatches, forexMatches, new ArrayList<>(), adapter);
                }
                @Override public void onResponse(@NonNull Call call,
                                                 @NonNull Response response) throws IOException {
                    List<ChartFragment.StockSuggestion> stocks = new ArrayList<>();
                    if (response.isSuccessful() && response.body() != null) {
                        try {
                            JSONObject root = new JSONObject(response.body().string());
                            JSONArray arr   = root.optJSONArray("result");
                            if (arr != null) {
                                for (int i = 0; i < Math.min(arr.length(), 8); i++) {
                                    JSONObject o = arr.getJSONObject(i);
                                    String sym  = o.optString("symbol", "");
                                    String name = o.optString("description", "");
                                    String exch = o.optString("type", "");
                                    if (!sym.isEmpty())
                                        stocks.add(new ChartFragment.StockSuggestion(sym, name, exch));
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                    postResults(cryptoMatches, forexMatches, stocks, adapter);
                }
            });
        } catch (Exception ignored) {
            postResults(cryptoMatches, forexMatches, new ArrayList<>(), adapter);
        }
    }

    // פורקס וקריפטו קודם, אחר כך אקציות
    private void postResults(List<ChartFragment.StockSuggestion> crypto,
                             List<ChartFragment.StockSuggestion> forex,
                             List<ChartFragment.StockSuggestion> stocks,
                             ArrayAdapter<ChartFragment.StockSuggestion> adapter) {
        List<ChartFragment.StockSuggestion> merged = new ArrayList<>();
        merged.addAll(forex);
        merged.addAll(crypto);
        merged.addAll(stocks);
        if (getActivity() != null) getActivity().runOnUiThread(() -> {
            adapter.clear();
            adapter.addAll(merged);
            adapter.notifyDataSetChanged();
        });
    }
}