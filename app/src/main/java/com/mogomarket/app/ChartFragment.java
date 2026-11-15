package com.mogomarket.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;

import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.github.mikephil.charting.charts.CombinedChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.CandleData;
import com.github.mikephil.charting.data.CandleDataSet;
import com.github.mikephil.charting.data.CandleEntry;
import com.github.mikephil.charting.data.CombinedData;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.listener.ChartTouchListener;
import com.github.mikephil.charting.listener.OnChartGestureListener;


import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ChartFragment extends Fragment implements TimeFrameFragment.TimeFrameListener {

    private static final String PREFS_NAME = "app_prefs";

    private static final String KEY_THEME       = "dark_mode";
    private static final String KEY_LAST_SYMBOL = "last_chart_symbol";

    public static final String KEY_SYMBOL_MODE  = "chart_symbol_mode";
    public static final String KEY_DEFAULT_TIMEFRAME = "chart_default_timeframe";

    private static final int DARK_BG        = 0xFF07090D;
    private static final int DARK_CARD      = 0xFF121720;
    private static final int DARK_TEXT_PRI  = 0xFFF5F7FA;
    private static final int DARK_TEXT_SEC  = 0xFF96A3B5;
    private static final int LIGHT_BG       = 0xFFF0F4F8;
    private static final int LIGHT_CARD     = 0xFFFFFFFF;
    private static final int LIGHT_TEXT_PRI = 0xFF1A1D23;
    private static final int LIGHT_TEXT_SEC = 0xFF6B7280;
    private static final int COLOR_PRIMARY  = 0xFF22D3EE;
    private static final int COLOR_GAIN     = 0xFF10B981;
    private static final int COLOR_LOSS     = 0xFFFB5A75;
    private static final int COLOR_FILL     = 0xFF0891B2;

    private static final long DOUBLE_TAP_TIMEOUT_MS = 350;
    private long lastTapTime = 0;

    private static final String[][] TIMEFRAMES = {
        {"1D",  "1day", "1d", "1d",  "1d"},
        {"1W",  "1day", "1d", "5d",  "1d"},
        {"1M",  "1day", "1d", "1mo", "1d"},
        {"3M",  "1day", "1d", "3mo", "1d"},
        {"6M",  "1day", "1d", "6mo", "1d"},
        {"1Y",  "1day", "1d", "1y",  "1d"},
        {"2Y",  "1day", "1d", "2y",  "1d"},
        {"5Y",  "1day", "1d", "5y",  "1d"},
        {"MAX", "1day", "1d", "max", "1d"},
    };

    static final Map<String, String> CRYPTO_MAP = new HashMap<>();
    static {
        CRYPTO_MAP.put("BTC",      "BINANCE:BTCUSDT");
        CRYPTO_MAP.put("BTCUSD",   "BINANCE:BTCUSDT");
        CRYPTO_MAP.put("BTCUSDT",  "BINANCE:BTCUSDT");
        CRYPTO_MAP.put("ETH",      "BINANCE:ETHUSDT");
        CRYPTO_MAP.put("ETHUSD",   "BINANCE:ETHUSDT");
        CRYPTO_MAP.put("ETHUSDT",  "BINANCE:ETHUSDT");
        CRYPTO_MAP.put("XRP",      "BINANCE:XRPUSDT");
        CRYPTO_MAP.put("XRPUSD",   "BINANCE:XRPUSDT");
        CRYPTO_MAP.put("SOL",      "BINANCE:SOLUSDT");
        CRYPTO_MAP.put("SOLUSD",   "BINANCE:SOLUSDT");
        CRYPTO_MAP.put("BNB",      "BINANCE:BNBUSDT");
        CRYPTO_MAP.put("BNBUSD",   "BINANCE:BNBUSDT");
        CRYPTO_MAP.put("DOGE",     "BINANCE:DOGEUSDT");
        CRYPTO_MAP.put("DOGEUSD",  "BINANCE:DOGEUSDT");
        CRYPTO_MAP.put("DOGEUSDT", "BINANCE:DOGEUSDT");
        CRYPTO_MAP.put("ADA",      "BINANCE:ADAUSDT");
        CRYPTO_MAP.put("ADAUSD",   "BINANCE:ADAUSDT");
        CRYPTO_MAP.put("AVAX",     "BINANCE:AVAXUSDT");
        CRYPTO_MAP.put("AVAXUSD",  "BINANCE:AVAXUSDT");
        CRYPTO_MAP.put("DOT",      "BINANCE:DOTUSDT");
        CRYPTO_MAP.put("DOTUSD",   "BINANCE:DOTUSDT");
        CRYPTO_MAP.put("LINK",     "BINANCE:LINKUSDT");
        CRYPTO_MAP.put("LINKUSD",  "BINANCE:LINKUSDT");
        CRYPTO_MAP.put("LTC",      "BINANCE:LTCUSDT");
        CRYPTO_MAP.put("LTCUSD",   "BINANCE:LTCUSDT");
        CRYPTO_MAP.put("MATIC",    "BINANCE:MATICUSDT");
        CRYPTO_MAP.put("MATICUSD", "BINANCE:MATICUSDT");
        CRYPTO_MAP.put("UNI",      "BINANCE:UNIUSDT");
        CRYPTO_MAP.put("UNIUSD",   "BINANCE:UNIUSDT");
    }

    // ── מפת פורקס: סימבול → Yahoo Finance ticker ────────────────────────────
    static final Map<String, String> FOREX_MAP = new HashMap<>();
    static {
        FOREX_MAP.put("EURUSD",  "EURUSD=X");
        FOREX_MAP.put("USDILS",  "USDILS=X");
        FOREX_MAP.put("GBPUSD",  "GBPUSD=X");
        FOREX_MAP.put("USDJPY",  "USDJPY=X");
        FOREX_MAP.put("AUDUSD",  "AUDUSD=X");
        FOREX_MAP.put("USDCAD",  "USDCAD=X");
        FOREX_MAP.put("USDCHF",  "USDCHF=X");
        FOREX_MAP.put("NZDUSD",  "NZDUSD=X");
        FOREX_MAP.put("EURGBP",  "EURGBP=X");
        FOREX_MAP.put("EURJPY",  "EURJPY=X");
        FOREX_MAP.put("GBPJPY",  "GBPJPY=X");
        FOREX_MAP.put("USDINR",  "USDINR=X");
        FOREX_MAP.put("USDCNY",  "USDCNY=X");
        FOREX_MAP.put("USDBRL",  "USDBRL=X");
        FOREX_MAP.put("USDMXN",  "USDMXN=X");
        FOREX_MAP.put("EURILS",  "EURILS=X");
        FOREX_MAP.put("GBPILS",  "GBPILS=X");
        FOREX_MAP.put("XAUUSD",  "GC=F");
        FOREX_MAP.put("GOLD",    "GC=F");
        FOREX_MAP.put("XAGUSD",  "SI=F");
        FOREX_MAP.put("SILVER",  "SI=F");
        FOREX_MAP.put("XBRUSD",  "BZ=F");
        FOREX_MAP.put("OIL",     "CL=F");
        FOREX_MAP.put("CRUDE",   "CL=F");
    }

    private boolean isDarkTheme;
    private boolean isChartDark;
    private boolean isFullscreen = false;
    private boolean isCrosshairActive = false;

    private int currentTFIndex = 5;

    private com.google.android.material.button.MaterialButton activeTFButton = null;

    private ViewGroup.LayoutParams chartOriginalParams;

    private CombinedChart candleStickChart;
    private LineChart lineChart;
    private AutoCompleteTextView tickerInput;

    private com.google.android.material.button.MaterialButton btnTickerSelect;
    private com.google.android.material.button.MaterialButton btnTimeframePicker;
    private com.google.android.material.button.MaterialButton btnChartRefresh;
    private com.google.android.material.button.MaterialButton btnChartMenu;
    private com.google.android.material.button.MaterialButton btnMagnetMode;
    private Button btnLoad, btnTimeFrame;
    private com.google.android.material.button.MaterialButton btnExpandChart, btnExitFullscreen, btnSettings;
    private Button btnChartThemeToggle;

    private View headerSection;
    private View searchSection;
    private View bottomBar;
    private FrameLayout chartContainer;
    private View chartRootLayout;
    private TextView timeFrameText, tickerText, priceText, changeText, currentPriceDisplay;
    private TextView tickerLabelTop;

    private LinearLayout crosshairInfoBar;
    private TextView crosshairPrice;
    private TextView crosshairDate;
    private FreeCrosshairOverlay freeCrosshairOverlay;
    private float freeCrosshairX;
    private float freeCrosshairY;
    private float crosshairDragStartTouchX;
    private float crosshairDragStartTouchY;
    private float crosshairDragStartX;
    private float crosshairDragStartY;
    private boolean crosshairGestureMoved;
    private boolean crosshairDismissGesture;

    private final OkHttpClient client = new OkHttpClient();
    private final String FINNHUB_KEY = "d918pn9r01qr1uqui560d918pn9r01qr1uqui56g";

    private String symbol = "SPY";
    private String interval = "1day";
    private String visibleChartRange = "1d";
    private boolean isCandleStick = true;
    private final DecimalFormat df = new DecimalFormat("#.##");
    private String latestQuery = "";

    private final List<CandleEntry> currentEntries = new ArrayList<>();
    private final List<Float> fullCloses = new ArrayList<>();
    private final List<String> dateLabels = new ArrayList<>();
    private final List<Long> currentTimestamps = new ArrayList<>();
    private final List<Entry> movingAverageEntries = new ArrayList<>();
    private boolean showMa150;
    private float lastPrice = 0f;
    private boolean isManualSelection = false;
    private boolean magnetMode;
    private int chartRequestGeneration;
    private com.github.mikephil.charting.charts.BarLineChartBase<?> activeCrosshairChart;
    // מונע טעינה כפולה — onCreateView לא יטען, onViewCreated יטען
    private boolean initialLoadDone = false;
    private ArrayAdapter<StockSuggestion> suggestionAdapter;
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;
    private static final long SEARCH_DEBOUNCE_MS = 300;

    public static class StockSuggestion {
        public final String symbol;
        public final String name;
        public final String exchange;

        // שדות חדשים
        public final boolean isSectionHeader;
        public final String sectionTitle;
        public final float dailyChangePercent;

        public StockSuggestion(String symbol, String name, String exchange) {
            this(symbol, name, exchange, false, null, Float.NaN);
        }

        public StockSuggestion(String symbol,
                               String name,
                               String exchange,
                               boolean isSectionHeader,
                               String sectionTitle,
                               float dailyChangePercent) {
            this.symbol            = symbol;
            this.name              = name;
            this.exchange          = exchange;
            this.isSectionHeader   = isSectionHeader;
            this.sectionTitle      = sectionTitle;
            this.dailyChangePercent = dailyChangePercent;
        }

        // יצירת אובייקט section header
        public static StockSuggestion section(String title) {
            return new StockSuggestion(
                    null,
                    null,
                    null,
                    true,
                    title,
                    0f
            );
        }

        @NonNull
        @Override
        public String toString() {
            // משמש רק במקומות ישנים שמשתמשים ב-simple_list_item_1
            if (isSectionHeader) {
                return sectionTitle != null ? sectionTitle : "";
            }

            String s = symbol == null ? "" : symbol;
            String n = name   == null ? "" : name;

            if (n.isEmpty()) return s;
            if (n.length() > 30) n = n.substring(0, 28) + "...";

            return s + "  \u00b7  " + n;
        }
    }





    private String formatPrice(float price, String sym) {
        if (isForexSymbol(sym)) {
            return String.format(Locale.US, "%.4f", price);
        }
        return String.format(Locale.US, "%.2f", price);
    }



    private boolean isCryptoSymbol(String sym) {
        return sym != null && sym.contains(":");
    }

    private boolean isForexSymbol(String sym) {
        return sym != null && (sym.endsWith("=X") || sym.equals("GC=F")
                || sym.equals("SI=F") || sym.equals("BZ=F") || sym.equals("CL=F"));
    }

    private String cleanDisplaySymbol(String sym) {
        if (sym == null) return "";
        if (isCryptoSymbol(sym)) return sym.substring(sym.indexOf(':') + 1);
        return sym.replace("=X", "").replace("=F", "");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_chart, container, false);

        SharedPreferences prefs = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        isDarkTheme = prefs.getBoolean(KEY_THEME, true);
        isChartDark = isDarkTheme;
        showMa150 = prefs.getBoolean("chart_show_ma150", false);
        currentTFIndex = getTimeframeIndex(
                prefs.getString(KEY_DEFAULT_TIMEFRAME, "1Y"));
        visibleChartRange = TIMEFRAMES[currentTFIndex][3];

        String mode = prefs.getString(KEY_SYMBOL_MODE, "last");
        if ("fixed".equals(mode)) {
            symbol = prefs.getString(MainActivity.KEY_DEFAULT_SYMBOL, "SPY");
            if (symbol.isEmpty()) symbol = "SPY";
        } else {
            symbol = prefs.getString(KEY_LAST_SYMBOL, "SPY");
            if (symbol.isEmpty()) symbol = "SPY";
        }

        if (getArguments() != null && getArguments().containsKey("symbol")) {
            symbol = getArguments().getString("symbol", symbol);
        }

        chartRootLayout    = v.findViewById(R.id.chartRootLayout);
        candleStickChart   = v.findViewById(R.id.stock_chart);
        lineChart          = v.findViewById(R.id.line_chart);
        tickerInput        = null;
        btnLoad            = null;
        btnTimeFrame       = null;
        btnSettings        = v.findViewById(R.id.btnSettings);
        btnChartThemeToggle= v.findViewById(R.id.btnChartThemeToggle);
        btnTickerSelect    = v.findViewById(R.id.btnTickerSelect);
        btnTimeframePicker = v.findViewById(R.id.btnTimeframePicker);
        btnChartRefresh   = v.findViewById(R.id.btnChartRefresh);
        btnChartMenu       = v.findViewById(R.id.btnChartMenu);
        tickerLabelTop     = v.findViewById(R.id.tickerLabelTop);

        crosshairInfoBar   = v.findViewById(R.id.crosshairInfoBar);
        crosshairPrice     = v.findViewById(R.id.crosshairPrice);
        crosshairDate      = v.findViewById(R.id.crosshairDate);
        freeCrosshairOverlay = v.findViewById(R.id.freeCrosshairOverlay);

        if (btnSettings != null) {
            btnSettings.setOnClickListener(vv -> {
                if (getActivity() instanceof MainActivity)
                    ((MainActivity) getActivity()).openSettings();
            });
        }

        btnExpandChart     = v.findViewById(R.id.btnExpandChart);
        btnExitFullscreen  = v.findViewById(R.id.btnExitFullscreen);
        priceText          = v.findViewById(R.id.priceText);
        changeText         = v.findViewById(R.id.changeText);
        timeFrameText      = v.findViewById(R.id.timeFrameText);
        tickerText         = v.findViewById(R.id.tickerText);
        currentPriceDisplay= v.findViewById(R.id.currentPriceDisplay);
        headerSection      = v.findViewById(R.id.headerSection);
        searchSection      = v.findViewById(R.id.searchSection);
        bottomBar          = v.findViewById(R.id.bottomBar);
        chartContainer     = v.findViewById(R.id.chartContainer);

        if (currentPriceDisplay != null) currentPriceDisplay.setVisibility(View.GONE);

        updateTickerButtonLabel();
        updateTickerLabelTop();
        updateTimeframePickerLabel();
        if (getActivity() != null) getActivity().setTitle("Chart: " + cleanDisplaySymbol(symbol));

        applyTheme();
        setupCandleChartStyle();
        setupLineChartStyle();
        setupClickListeners();
        setupChartGestures();

        interval = TIMEFRAMES[currentTFIndex][1];

        updateChartThemeToggleLabel();
        return v;
    }

    private void loadChartForSymbol(String newSymbol) {
        if (newSymbol == null || newSymbol.trim().isEmpty()) return;

        String cleanSymbol = newSymbol.trim().toUpperCase(Locale.US);

        String cryptoMapped = CRYPTO_MAP.get(cleanSymbol);
        if (cryptoMapped != null) {
            cleanSymbol = cryptoMapped;
        } else {
            String forexMapped = FOREX_MAP.get(cleanSymbol);
            if (forexMapped != null) {
                cleanSymbol = forexMapped;
            } else if ("SPX".equals(cleanSymbol) || "^SPX".equals(cleanSymbol)
                    || "GSPC".equals(cleanSymbol)) {
                cleanSymbol = "^GSPC";
            }
        }

        symbol = cleanSymbol;

        if (getActivity() != null) {
            SharedPreferences prefs = getActivity()
                    .getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
            String mode = prefs.getString(KEY_SYMBOL_MODE, "last");
            // שמור "last" רק אם לא במצב fixed
            if (!"fixed".equals(mode)) {
                prefs.edit()
                        .putString(MainActivity.KEY_LAST_SYMBOL, cleanSymbol)
                        .apply();
            }
        }

        updateTickerButtonLabel();
        updateTickerLabelTop();
        hideCrosshairInfo();
        updateTimeframePickerLabel();

        if (getActivity() != null) {
            getActivity().setTitle("Chart: " + cleanDisplaySymbol(cleanSymbol));
        }

        fetchStockData(symbol, interval);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        SharedViewModel vm = new ViewModelProvider(requireActivity())
                .get(SharedViewModel.class);

        SharedPreferences prefs = requireActivity()
                .getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);

        String mode = prefs.getString(KEY_SYMBOL_MODE, "last");

        // קבע מה לטעון בפעם הראשונה
        String symbolToLoad;

        if ("fixed".equals(mode)) {
            String sessionSym = vm.getSessionSymbol();
            String fixedSymbol = prefs.getString(MainActivity.KEY_DEFAULT_SYMBOL, "SPY");
            if (fixedSymbol == null || fixedSymbol.trim().isEmpty()) fixedSymbol = "SPY";

            // אם יש session symbol (הגיע מ-Watchlist) — תשתמש בו
            // אחרת — טען fixed
            symbolToLoad = (sessionSym != null && !sessionSym.trim().isEmpty())
                    ? sessionSym.trim()
                    : fixedSymbol.trim();
        } else {
            String lastSymbol = prefs.getString(MainActivity.KEY_LAST_SYMBOL, "SPY");
            if (lastSymbol == null || lastSymbol.trim().isEmpty()) lastSymbol = "SPY";
            symbolToLoad = lastSymbol.trim();
        }

        // עדכן UI וטען גרף ישירות — בלי VM בכלל בשלב הזה
        loadChartForSymbol(symbolToLoad);

        // observe לשינויים עתידיים בלבד (מ-Watchlist לאחר הטעינה הראשונית)
        vm.getSelectedSymbol().observe(getViewLifecycleOwner(), selected -> {
            if (selected != null && !selected.trim().isEmpty()
                    && !selected.trim().equals(symbol)) {
                loadChartForSymbol(selected.trim());
            }
        });
    }

    public void applyThemeExternal(boolean dark) {
        isDarkTheme = dark;
        isChartDark = dark;
        applyTheme();
        applyChartColors();
        updateChartThemeToggleLabel();
    }
    private void updateTickerLabelTop() {
        if (tickerLabelTop == null) return;
        tickerLabelTop.setText(cleanDisplaySymbol(symbol));
    }

    private void updateTimeframePickerLabel() {
        if (btnTimeframePicker == null) return;
        btnTimeframePicker.setText(TIMEFRAMES[currentTFIndex][0]);
    }

    public static String[] getTimeframeLabels() {
        String[] labels = new String[TIMEFRAMES.length];
        for (int i = 0; i < TIMEFRAMES.length; i++) labels[i] = TIMEFRAMES[i][0];
        return labels;
    }

    private static int getTimeframeIndex(String label) {
        for (int i = 0; i < TIMEFRAMES.length; i++) {
            if (TIMEFRAMES[i][0].equalsIgnoreCase(label)) return i;
        }
        return 5;
    }

    private void selectTimeframe(int timeframeIndex) {
        if (timeframeIndex < 0 || timeframeIndex >= TIMEFRAMES.length) return;
        currentTFIndex = timeframeIndex;
        interval = TIMEFRAMES[timeframeIndex][1];
        visibleChartRange = TIMEFRAMES[timeframeIndex][3];
        updateTimeframePickerLabel();
        hideCrosshairInfo();
        fetchStockData(symbol, interval);
    }

    private void showTimeframeDialog() {
        if (getContext() == null) return;

        final AlertDialog[] dialogHolder = new AlertDialog[1];

        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.bg_dialog_glass);
        int ph = dpToPx(20);
        root.setPadding(ph, dpToPx(20), ph, dpToPx(16));

        TextView title = new TextView(getContext());
        title.setText("Select Timeframe");
        title.setTextSize(16f);
        title.setTextColor(requireContext().getColor(R.color.text_primary));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(dpToPx(4), 0, 0, dpToPx(16));
        root.addView(title);

        int cols = 3;
        int tfCounter = 0;

        for (int row = 0; row < Math.ceil((double) TIMEFRAMES.length / cols); row++) {
            LinearLayout rowLayout = new LinearLayout(getContext());
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.setMargins(0, 0, 0, dpToPx(8));
            rowLayout.setLayoutParams(rowLp);

            for (int col = 0; col < cols; col++) {
                final int idx = tfCounter;
                tfCounter++;

                if (idx >= TIMEFRAMES.length) {
                    android.widget.Space spacer = new android.widget.Space(getContext());
                    LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0,
                            android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                    sp.setMargins(col == 0 ? 0 : dpToPx(6), 0, 0, 0);
                    rowLayout.addView(spacer, sp);
                    continue;
                }

                boolean isSelected = (idx == currentTFIndex);
                String label = TIMEFRAMES[idx][0];

                android.widget.FrameLayout btnWrapper = new android.widget.FrameLayout(getContext());

                android.graphics.drawable.GradientDrawable btnBg =
                        new android.graphics.drawable.GradientDrawable();
                btnBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                btnBg.setCornerRadius(dpToPx(10));

                if (isSelected) {
                    btnBg.setColor(requireContext().getColor(R.color.primary));
                    btnBg.setStroke(0, android.graphics.Color.TRANSPARENT);
                } else {
                    btnBg.setColor(requireContext().getColor(R.color.bg_secondary));
                    btnBg.setStroke(dpToPx(1),
                            requireContext().getColor(R.color.colorBorder));
                }
                btnWrapper.setBackground(btnBg);

                TextView btnText = new TextView(getContext());
                btnText.setText(label);
                btnText.setTextSize(14f);
                btnText.setTextColor(isSelected
                        ? requireContext().getColor(R.color.white)
                        : requireContext().getColor(R.color.text_secondary));
                btnText.setTypeface(null, isSelected ?
                        android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
                btnText.setGravity(android.view.Gravity.CENTER);
                android.widget.FrameLayout.LayoutParams textLp =
                        new android.widget.FrameLayout.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(44));
                btnText.setLayoutParams(textLp);
                btnWrapper.addView(btnText);

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                lp.setMargins(col == 0 ? 0 : dpToPx(6), 0, 0, 0);
                rowLayout.addView(btnWrapper, lp);

                btnWrapper.setOnClickListener(v -> {
                    selectTimeframe(idx);
                    if (dialogHolder[0] != null) dialogHolder[0].dismiss();
                });
            }
            root.addView(rowLayout);
        }

        View divider = new View(getContext());
        divider.setBackgroundColor(requireContext().getColor(R.color.colorBorder));
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(1));
        divLp.setMargins(0, dpToPx(8), 0, dpToPx(12));
        root.addView(divider, divLp);

        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setView(root)
                .setNegativeButton("Cancel", null)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        dialogHolder[0] = dialog;
        dialog.show();
    }

    @Override
    public void onTimeFrameSelected(String tf) {
        interval = tf;
        hideCrosshairInfo();
        fetchStockData(symbol, interval);
    }

    private void setActiveTFButton(com.google.android.material.button.MaterialButton selected) {
        activeTFButton = selected;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() == null) return;
        SharedPreferences prefs = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean savedDark = prefs.getBoolean(KEY_THEME, true);
        if (savedDark != isChartDark) {
            isChartDark = savedDark;
            isDarkTheme = savedDark;
            applyTheme();
            applyChartColors();
            if (!currentEntries.isEmpty()) {
                if (isCandleStick) updateCandleChart(currentEntries);
                else               updateLineChart(currentEntries);
            }
            updateChartThemeToggleLabel();
        }
    }

    private void showCrosshairInfo(float price, String date) {
        if (crosshairInfoBar == null) return;
        isCrosshairActive = true;

        if (crosshairPrice != null) {
            crosshairPrice.setText("$" + formatPrice(price, symbol));
        }

        if (crosshairDate != null) {
            crosshairDate.setText(date);
        }

        crosshairInfoBar.setVisibility(View.VISIBLE);
    }

    private void hideCrosshairInfo() {
        if (crosshairInfoBar == null) return;
        isCrosshairActive = false;
        crosshairInfoBar.setVisibility(View.GONE);
        if (freeCrosshairOverlay != null) freeCrosshairOverlay.hide();
        if (candleStickChart != null) candleStickChart.highlightValue(null);
        if (lineChart        != null) lineChart.highlightValue(null);
    }

    private void setupChartGestures() {
        OnChartGestureListener gestureListener = new OnChartGestureListener() {
            @Override public void onChartGestureStart(MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture) {}
            @Override public void onChartGestureEnd(MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture) {}
            @Override public void onChartLongPressed(MotionEvent me) {
                if (candleStickChart != null && candleStickChart.getVisibility() == View.VISIBLE) {
                    startCrosshair(candleStickChart, me.getX(), me.getY());
                } else if (lineChart != null && lineChart.getVisibility() == View.VISIBLE) {
                    startCrosshair(lineChart, me.getX(), me.getY());
                }
            }
            @Override public void onChartDoubleTapped(MotionEvent me) {
                if (isFullscreen) exitFullscreen(); else enterFullscreen();
            }
            @Override public void onChartSingleTapped(MotionEvent me) {
                if (isCrosshairActive) hideCrosshairInfo();
            }
            @Override public void onChartFling(MotionEvent me1, MotionEvent me2, float velocityX, float velocityY) {}
            @Override public void onChartScale(MotionEvent me, float scaleX, float scaleY) {}
            @Override public void onChartTranslate(MotionEvent me, float dX, float dY) {}
        };
        if (candleStickChart != null) {
            candleStickChart.setOnChartGestureListener(gestureListener);
            attachCrosshairTouchListener(candleStickChart);
        }
        if (lineChart != null) {
            lineChart.setOnChartGestureListener(gestureListener);
            attachCrosshairTouchListener(lineChart);
        }
    }

    private void startCrosshair(com.github.mikephil.charting.charts.BarLineChartBase<?> chart,
                                float touchX, float touchY) {
        if (magnetMode) {
            if (freeCrosshairOverlay != null) freeCrosshairOverlay.hide();
            com.github.mikephil.charting.highlight.Highlight highlight =
                    chart.getHighlightByTouchPoint(touchX, touchY);
            if (highlight != null) {
                isCrosshairActive = true;
                chart.highlightValue(highlight);
            }
            return;
        }

        activeCrosshairChart = chart;
        crosshairDragStartTouchX = touchX;
        crosshairDragStartTouchY = touchY;
        crosshairDragStartX = touchX;
        crosshairDragStartY = touchY;
        crosshairGestureMoved = false;
        crosshairDismissGesture = false;
        chart.setDragEnabled(false);
        chart.setHighlightPerDragEnabled(false);
        updateFreeCrosshair(chart, touchX, touchY);
    }

    private void attachCrosshairTouchListener(
            com.github.mikephil.charting.charts.BarLineChartBase<?> chart) {
        chart.setOnTouchListener((view, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                crosshairDismissGesture = isCrosshairActive && !magnetMode;
            }
            if (action == MotionEvent.ACTION_DOWN && isCrosshairActive && !magnetMode) {
                activeCrosshairChart = chart;
                crosshairDragStartTouchX = event.getX();
                crosshairDragStartTouchY = event.getY();
                crosshairDragStartX = freeCrosshairX;
                crosshairDragStartY = freeCrosshairY;
                crosshairGestureMoved = false;
                chart.setDragEnabled(false);
                chart.setHighlightPerDragEnabled(false);
                return true;
            }

            if (activeCrosshairChart != chart) return false;

            if (action == MotionEvent.ACTION_MOVE) {
                float dx = event.getX() - crosshairDragStartTouchX;
                float dy = event.getY() - crosshairDragStartTouchY;
                if (Math.hypot(dx, dy) > dpToPx(8)) crosshairGestureMoved = true;
                updateFreeCrosshair(
                        chart,
                        crosshairDragStartX + dx,
                        crosshairDragStartY + dy);
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                boolean dismissCrosshair = crosshairDismissGesture
                        && action == MotionEvent.ACTION_UP && !crosshairGestureMoved;
                chart.setDragEnabled(true);
                chart.setHighlightPerDragEnabled(magnetMode);
                activeCrosshairChart = null;
                crosshairDismissGesture = false;
                if (dismissCrosshair) hideCrosshairInfo();
            }
            // Consume the active free-crosshair gesture so the chart cannot snap it to a data point.
            return true;
        });
    }

    private void updateFreeCrosshair(
            com.github.mikephil.charting.charts.BarLineChartBase<?> chart,
            float touchX, float touchY) {
        if (chart.getData() == null || chart.getData().getDataSetCount() == 0 || dateLabels.isEmpty()) return;

        android.graphics.RectF content = chart.getViewPortHandler().getContentRect();
        float x = Math.max(content.left, Math.min(touchX, content.right));
        float y = Math.max(content.top, Math.min(touchY, content.bottom));
        freeCrosshairX = x;
        freeCrosshairY = y;
        com.github.mikephil.charting.utils.MPPointD values =
                chart.getTransformer(com.github.mikephil.charting.components.YAxis.AxisDependency.LEFT)
                        .getValuesByTouchPoint(x, y);

        float dataX = Math.max(0f, Math.min((float) values.x, dateLabels.size() - 1f));
        float price = (float) values.y;
        com.github.mikephil.charting.utils.MPPointD.recycleInstance(values);

        chart.highlightValue(null, false);
        if (freeCrosshairOverlay != null) {
            freeCrosshairOverlay.showAt(x, y, content);
        }
        isCrosshairActive = true;
        int dateIndex = Math.max(0, Math.min(Math.round(dataX), dateLabels.size() - 1));
        showCrosshairInfo(price, dateLabels.get(dateIndex));
    }

    private void updateTickerButtonLabel() {
        if (btnTickerSelect == null) return;
        btnTickerSelect.setText(cleanDisplaySymbol(symbol));
    }

    private void showTickerInputDialog() {
        TickerSearchSheet sheet = new TickerSearchSheet();
        sheet.setOnTickerSelectedListener(sym -> openChartFromInput(sym));
        sheet.show(getParentFragmentManager(), "ticker_search");
    }

    private void enterFullscreen() {
        if (isFullscreen || chartContainer == null) return;
        isFullscreen = true;
        chartOriginalParams = chartContainer.getLayoutParams();
        if (headerSection   != null) headerSection.setVisibility(View.GONE);
        if (searchSection   != null) searchSection.setVisibility(View.GONE);
        if (bottomBar       != null) bottomBar.setVisibility(View.GONE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        lp.setMargins(0, 0, 0, 0);
        chartContainer.setLayoutParams(lp);
        if (btnExitFullscreen != null) btnExitFullscreen.setVisibility(View.VISIBLE);
    }

    private void exitFullscreen() {
        if (!isFullscreen || chartContainer == null) return;
        isFullscreen = false;
        if (chartOriginalParams != null) {
            chartContainer.setLayoutParams(chartOriginalParams);
        } else {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0);
            lp.weight = 1;
            lp.setMargins(dpToPx(12), dpToPx(8), dpToPx(12), 0);
            chartContainer.setLayoutParams(lp);
        }
        if (headerSection   != null) headerSection.setVisibility(View.VISIBLE);
        if (btnExitFullscreen != null) btnExitFullscreen.setVisibility(View.GONE);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void applyTheme() {
        int bgColor   = isDarkTheme ? DARK_BG       : LIGHT_BG;
        int cardColor = isDarkTheme ? DARK_CARD     : LIGHT_CARD;
        int textPri   = isDarkTheme ? DARK_TEXT_PRI : LIGHT_TEXT_PRI;
        int textSec   = isDarkTheme ? DARK_TEXT_SEC : LIGHT_TEXT_SEC;

        if (chartRootLayout != null) chartRootLayout.setBackgroundColor(bgColor);
        if (headerSection instanceof com.google.android.material.card.MaterialCardView) {
            ((com.google.android.material.card.MaterialCardView) headerSection)
                    .setCardBackgroundColor(cardColor);
        }
        if (bottomBar       != null) bottomBar.setBackgroundColor(cardColor);

        if (tickerLabelTop  != null) tickerLabelTop.setTextColor(textPri);
        if (tickerText      != null) tickerText.setTextColor(textPri);
        if (timeFrameText   != null) timeFrameText.setTextColor(textSec);
        applyChartColors();
    }

    private void applyChartColors() {
        int chartCard = isChartDark ? DARK_CARD     : LIGHT_CARD;
        int chartSec  = isChartDark ? DARK_TEXT_SEC : LIGHT_TEXT_SEC;
        if (chartContainer instanceof com.google.android.material.card.MaterialCardView) {
            ((com.google.android.material.card.MaterialCardView) chartContainer)
                    .setCardBackgroundColor(chartCard);
        }
        if (candleStickChart != null) {
            candleStickChart.setBackgroundColor(chartCard);
            candleStickChart.getXAxis().setTextColor(chartSec);
            candleStickChart.getAxisLeft().setTextColor(chartSec);
            candleStickChart.invalidate();
        }
        if (lineChart != null) {
            lineChart.setBackgroundColor(chartCard);
            lineChart.getXAxis().setTextColor(chartSec);
            lineChart.getAxisLeft().setTextColor(chartSec);
            lineChart.invalidate();
        }
    }

    private void updateChartThemeToggleLabel() {
        if (btnChartThemeToggle != null)
            btnChartThemeToggle.setText(isChartDark ? "\u2600\uFE0F" : "\uD83C\uDF19");
    }

    private void setupCandleChartStyle() {
        if (candleStickChart == null) return;
        int cardColor = isChartDark ? DARK_CARD : LIGHT_CARD;
        int textSec   = isChartDark ? DARK_TEXT_SEC : LIGHT_TEXT_SEC;
        candleStickChart.setBackgroundColor(cardColor);
        candleStickChart.setDrawGridBackground(false);
        candleStickChart.getDescription().setEnabled(false);
        candleStickChart.getLegend().setEnabled(false);
        candleStickChart.setTouchEnabled(true);
        candleStickChart.setDragEnabled(true);
        candleStickChart.setScaleEnabled(true);
        candleStickChart.setScaleXEnabled(true);
        candleStickChart.setScaleYEnabled(true);
        candleStickChart.setPinchZoom(false);
        candleStickChart.setDoubleTapToZoomEnabled(false);
        candleStickChart.setDragDecelerationEnabled(true);
        candleStickChart.setDragDecelerationFrictionCoef(0.92f);
        candleStickChart.setHighlightPerTapEnabled(false);
        candleStickChart.setHighlightPerDragEnabled(magnetMode);
        candleStickChart.setExtraTopOffset(4f);
        XAxis xAxis = candleStickChart.getXAxis();
        xAxis.setDrawGridLines(false);
        xAxis.setDrawAxisLine(false);
        xAxis.setTextColor(textSec);
        xAxis.setTextSize(10f);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setSpaceMin(0.5f);
        xAxis.setSpaceMax(0.5f);
        xAxis.setLabelCount(5, true);
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override public String getFormattedValue(float value) {
                int i = (int) value;
                return (i >= 0 && i < dateLabels.size()) ? dateLabels.get(i) : "";
            }
        });
        YAxis leftAxis = candleStickChart.getAxisLeft();
        leftAxis.setDrawGridLines(false);
        leftAxis.setDrawAxisLine(false);
        leftAxis.setTextColor(textSec);
        leftAxis.setTextSize(10f);
        leftAxis.setLabelCount(5, false);
        candleStickChart.getAxisRight().setEnabled(false);
    }

    private void setupLineChartStyle() {
        if (lineChart == null) return;
        int cardColor = isChartDark ? DARK_CARD : LIGHT_CARD;
        int textSec   = isChartDark ? DARK_TEXT_SEC : LIGHT_TEXT_SEC;
        lineChart.setBackgroundColor(cardColor);
        lineChart.setDrawGridBackground(false);
        lineChart.getDescription().setEnabled(false);
        lineChart.getLegend().setEnabled(false);
        lineChart.setTouchEnabled(true);
        lineChart.setDragEnabled(true);
        lineChart.setScaleEnabled(true);
        lineChart.setScaleXEnabled(true);
        lineChart.setScaleYEnabled(true);
        lineChart.setPinchZoom(false);
        lineChart.setDoubleTapToZoomEnabled(false);
        lineChart.setDragDecelerationEnabled(true);
        lineChart.setDragDecelerationFrictionCoef(0.92f);
        lineChart.setHighlightPerTapEnabled(false);
        lineChart.setHighlightPerDragEnabled(magnetMode);
        lineChart.setExtraTopOffset(4f);
        XAxis xAxis = lineChart.getXAxis();
        xAxis.setDrawGridLines(false);
        xAxis.setDrawAxisLine(false);
        xAxis.setTextColor(textSec);
        xAxis.setTextSize(10f);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setSpaceMin(0.5f);
        xAxis.setSpaceMax(0.5f);
        xAxis.setLabelCount(5, true);
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override public String getFormattedValue(float value) {
                int i = (int) value;
                return (i >= 0 && i < dateLabels.size()) ? dateLabels.get(i) : "";
            }
        });
        YAxis leftAxis = lineChart.getAxisLeft();
        leftAxis.setDrawGridLines(false);
        leftAxis.setDrawAxisLine(false);
        leftAxis.setTextColor(textSec);
        leftAxis.setTextSize(10f);
        leftAxis.setLabelCount(5, false);
        lineChart.getAxisRight().setEnabled(false);
    }

    private void setupClickListeners() {
        if (btnTickerSelect != null) {
            btnTickerSelect.setOnClickListener(v -> showTickerInputDialog());
        }

        if (btnTimeframePicker != null) {
            btnTimeframePicker.setOnClickListener(v -> showTimeframeDialog());
        }

        if (btnChartRefresh != null) {
            btnChartRefresh.setOnClickListener(v -> {
                btnChartRefresh.animate().rotationBy(360f).setDuration(450L).start();
                fetchStockData(symbol, interval);
            });
        }

        if (btnChartMenu != null) {
            btnChartMenu.setOnClickListener(this::showChartMenu);
        }

        if (btnMagnetMode != null) {
            updateMagnetModeLabel();
            btnMagnetMode.setOnClickListener(v -> {
                magnetMode = !magnetMode;
                if (candleStickChart != null) candleStickChart.setHighlightPerDragEnabled(magnetMode);
                if (lineChart != null) lineChart.setHighlightPerDragEnabled(magnetMode);
                if (!magnetMode) hideCrosshairInfo();
                updateMagnetModeLabel();
            });
        }

        if (btnChartThemeToggle != null) {
            btnChartThemeToggle.setOnClickListener(v -> {
                isChartDark = !isChartDark;
                isDarkTheme = isChartDark;
                SharedPreferences prefs = requireActivity()
                        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                prefs.edit().putBoolean(KEY_THEME, isChartDark).apply();
                AppCompatDelegate.setDefaultNightMode(
                        isChartDark ? AppCompatDelegate.MODE_NIGHT_YES
                                    : AppCompatDelegate.MODE_NIGHT_NO);
                applyTheme();
                applyChartColors();
                if (!currentEntries.isEmpty()) {
                    if (isCandleStick) updateCandleChart(currentEntries);
                    else               updateLineChart(currentEntries);
                }
                updateChartThemeToggleLabel();
            });
        }

        if (btnExitFullscreen != null) btnExitFullscreen.setOnClickListener(v -> exitFullscreen());
    }

    private void showChartMenu(View anchor) {
        Context context = requireContext();
        int surface = isChartDark ? DARK_CARD : LIGHT_CARD;
        int primaryText = isChartDark ? DARK_TEXT_PRI : LIGHT_TEXT_PRI;
        int secondaryText = isChartDark ? DARK_TEXT_SEC : LIGHT_TEXT_SEC;

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = dpToPx(18);
        content.setPadding(padding, padding, padding, padding);
        android.graphics.drawable.GradientDrawable background =
                new android.graphics.drawable.GradientDrawable();
        background.setColor(surface);
        background.setCornerRadius(dpToPx(20));
        background.setStroke(dpToPx(1), context.getColor(R.color.colorBorder));
        content.setBackground(background);

        TextView title = new TextView(context);
        title.setText("Chart options");
        title.setTextColor(primaryText);
        title.setTextSize(18f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        content.addView(title);

        TextView subtitle = new TextView(context);
        subtitle.setText("Choose an action");
        subtitle.setTextColor(secondaryText);
        subtitle.setTextSize(13f);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.bottomMargin = dpToPx(14);
        content.addView(subtitle, subtitleParams);

        TextView timeframeTitle = new TextView(context);
        timeframeTitle.setText("Timeframe");
        timeframeTitle.setTextColor(primaryText);
        timeframeTitle.setTextSize(13f);
        timeframeTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams timeframeTitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        timeframeTitleParams.bottomMargin = dpToPx(8);
        content.addView(timeframeTitle, timeframeTitleParams);

        LinearLayout timeframeRow = new LinearLayout(context);
        timeframeRow.setOrientation(LinearLayout.VERTICAL);
        timeframeRow.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams timeframeRowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        timeframeRowParams.bottomMargin = dpToPx(14);
        content.addView(timeframeRow, timeframeRowParams);

        String[] timeframeLabels = getTimeframeLabels();
        int[] timeframeIndices = new int[timeframeLabels.length];
        for (int i = 0; i < timeframeIndices.length; i++) timeframeIndices[i] = i;
        AlertDialog[] dialogHolder = new AlertDialog[1];
        com.google.android.material.button.MaterialButton[] timeframeButtons =
                new com.google.android.material.button.MaterialButton[timeframeLabels.length];
        LinearLayout timeframeGridRow = null;
        for (int i = 0; i < timeframeLabels.length; i++) {
            if (i % 3 == 0) {
                timeframeGridRow = new LinearLayout(context);
                timeframeGridRow.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (i > 0) rowParams.topMargin = dpToPx(6);
                timeframeRow.addView(timeframeGridRow, rowParams);
            }
            final int timeframeIndex = timeframeIndices[i];
            com.google.android.material.button.MaterialButton timeframeButton =
                    new com.google.android.material.button.MaterialButton(context);
            timeframeButton.setText(timeframeLabels[i]);
            timeframeButton.setTextSize(12f);
            timeframeButton.setMinWidth(0);
            timeframeButton.setMinHeight(0);
            timeframeButton.setInsetTop(0);
            timeframeButton.setInsetBottom(0);
            timeframeButton.setPadding(dpToPx(8), 0, dpToPx(8), 0);
            timeframeButton.setCornerRadius(dpToPx(16));
            timeframeButton.setStrokeWidth(dpToPx(1));
            timeframeButton.setStrokeColor(android.content.res.ColorStateList.valueOf(
                    context.getColor(R.color.chart_gold)));
            timeframeButtons[i] = timeframeButton;
            LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                    0, dpToPx(38), 1f);
            if (i % 3 > 0) buttonParams.leftMargin = dpToPx(5);
            timeframeGridRow.addView(timeframeButton, buttonParams);
            timeframeButton.setOnClickListener(view -> {
                selectTimeframe(timeframeIndex);
                updateChartMenuTimeframeButtons(timeframeButtons, timeframeIndices);
                if (dialogHolder[0] != null) dialogHolder[0].dismiss();
            });
        }
        updateChartMenuTimeframeButtons(timeframeButtons, timeframeIndices);

        addChartMenuOption(content, isCandleStick ? "▤" : "⌁",
                isCandleStick ? "Line chart" : "Candlestick chart",
                "Change how price data is displayed", primaryText, secondaryText, surface,
                () -> toggleChartType(), dialogHolder);
        addChartMenuOption(content, "◷", "Visible range",
                "Currently " + visibleChartRange.toUpperCase(Locale.US),
                primaryText, secondaryText, surface,
                this::showVisibleRangeDialog, dialogHolder);
        addChartMenuOption(content, "150", "150-day moving average",
                showMa150 ? "Currently on" : "Currently off",
                primaryText, secondaryText, surface,
                this::toggleMa150, dialogHolder);
        addChartMenuOption(content, "✦", "AI analysis",
                "Ask Gemini about " + symbol, primaryText, secondaryText, surface,
                () -> AiChatDialog.show(requireContext(),
                        "נתח את הגרף והסבר את המגמה והסיכונים.",
                        buildChartAiContext()),
                dialogHolder);

        dialogHolder[0] = new AlertDialog.Builder(context)
                .setView(content)
                .setNegativeButton("Close", null)
                .create();
        dialogHolder[0].setOnShowListener(ignored -> {
            if (dialogHolder[0].getWindow() != null) {
                dialogHolder[0].getWindow().setBackgroundDrawable(
                        new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            }
        });
        dialogHolder[0].show();
    }

    private void updateChartMenuTimeframeButtons(
            com.google.android.material.button.MaterialButton[] buttons, int[] timeframeIndices) {
        if (getContext() == null) return;
        for (int i = 0; i < buttons.length; i++) {
            boolean selected = currentTFIndex == timeframeIndices[i];
            buttons[i].setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    requireContext().getColor(selected
                            ? R.color.chart_tf_selected : R.color.chart_tf_unselected)));
            buttons[i].setTextColor(requireContext().getColor(selected
                    ? R.color.primary : R.color.chart_tf_text));
        }
    }

    private void toggleMa150() {
        showMa150 = !showMa150;
        requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean("chart_show_ma150", showMa150).apply();
        movingAverageEntries.clear();
        if (!currentEntries.isEmpty()) {
            if (showMa150) {
                fetchMovingAverage(symbol, currentEntries, currentTimestamps,
                        chartRequestGeneration);
            } else if (isCandleStick) {
                updateCandleChart(currentEntries);
            } else {
                updateLineChart(currentEntries);
            }
        }
    }

    private String buildChartAiContext() {
        StringBuilder context = new StringBuilder()
                .append("סימול: ").append(symbol)
                .append("\nטווח גרף: ").append(visibleChartRange)
                .append("\nמחיר אחרון: ").append(lastPrice)
                .append("\nמחירי סגירה אחרונים (מהישן לחדש): ");
        int start = Math.max(0, fullCloses.size() - 10);
        for (int i = start; i < fullCloses.size(); i++) {
            if (i > start) context.append(", ");
            context.append(fullCloses.get(i));
        }
        return context.toString();
    }

    private void showVisibleRangeDialog() {
        String[] ranges = {"1 day", "5 days", "1 month", "3 months", "6 months",
                "1 year", "5 years", "Maximum"};
        String[] rangeValues = {"1d", "5d", "1mo", "3mo", "6mo", "1y", "5y", "max"};
        int selected = 5;
        for (int i = 0; i < rangeValues.length; i++) {
            if (rangeValues[i].equals(visibleChartRange)) {
                selected = i;
                break;
            }
        }
        new AlertDialog.Builder(requireContext())
                .setTitle("Visible chart range")
                .setSingleChoiceItems(ranges, selected, (dialog, which) -> {
                    visibleChartRange = rangeValues[which];
                    hideCrosshairInfo();
                    dialog.dismiss();
                    fetchStockData(symbol, interval);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void addChartMenuOption(LinearLayout parent, String icon, String label, String description,
                                    int primaryText, int secondaryText, int surface,
                                    Runnable action, AlertDialog[] dialogHolder) {
        Context context = parent.getContext();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));
        row.setClickable(true);
        row.setFocusable(true);

        android.graphics.drawable.GradientDrawable rowBackground =
                new android.graphics.drawable.GradientDrawable();
        rowBackground.setColor(isChartDark ? 0xFF0B0F14 : 0xFFF0F4F8);
        rowBackground.setCornerRadius(dpToPx(14));
        row.setBackground(rowBackground);

        TextView iconView = new TextView(context);
        iconView.setText(icon);
        iconView.setTextSize(20f);
        iconView.setTextColor(COLOR_PRIMARY);
        iconView.setGravity(android.view.Gravity.CENTER);
        android.graphics.drawable.GradientDrawable iconBackground =
                new android.graphics.drawable.GradientDrawable();
        iconBackground.setColor(isChartDark ? 0xFF1E2A3A : 0xFFE1EEFC);
        iconBackground.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        iconView.setBackground(iconBackground);
        row.addView(iconView, new LinearLayout.LayoutParams(dpToPx(42), dpToPx(42)));

        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelsParams.setMargins(dpToPx(12), 0, 0, 0);
        labels.setLayoutParams(labelsParams);

        TextView labelView = new TextView(context);
        labelView.setText(label);
        labelView.setTextColor(primaryText);
        labelView.setTextSize(14f);
        labelView.setTypeface(null, android.graphics.Typeface.BOLD);
        labels.addView(labelView);

        TextView descriptionView = new TextView(context);
        descriptionView.setText(description);
        descriptionView.setTextColor(secondaryText);
        descriptionView.setTextSize(12f);
        labels.addView(descriptionView);
        row.addView(labels);

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin = dpToPx(8);
        parent.addView(row, rowParams);
        row.setOnClickListener(view -> {
            if (dialogHolder[0] != null) dialogHolder[0].dismiss();
            action.run();
        });
    }

    private void toggleChartType() {
        isCandleStick = !isCandleStick;
        if (isCandleStick) {
            candleStickChart.setVisibility(View.VISIBLE);
            lineChart.setVisibility(View.GONE);
        } else {
            candleStickChart.setVisibility(View.GONE);
            lineChart.setVisibility(View.VISIBLE);
        }
        fetchStockData(symbol, interval);
    }

    private void updateMagnetModeLabel() {
        if (btnMagnetMode != null) {
            btnMagnetMode.setText(magnetMode ? "Magnet: On" : "Magnet: Off");
        }
    }

    private void attachMarker(com.github.mikephil.charting.charts.BarLineChartBase<?> chart) {
        TradingMarkerView mv = new TradingMarkerView(requireContext());
        mv.setDateLabels(dateLabels);
        mv.setChartView(chart);
        mv.setOnHighlightListener((price, date) -> {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> showCrosshairInfo(price, date));
            }
        });
        chart.setMarker(mv);
    }

    private void updateCandleChart(List<CandleEntry> entries) {
        CandleDataSet dataSet = new CandleDataSet(entries, "");
        dataSet.setIncreasingColor(COLOR_GAIN);
        dataSet.setDecreasingColor(COLOR_LOSS);
        dataSet.setIncreasingPaintStyle(Paint.Style.FILL);
        dataSet.setDecreasingPaintStyle(Paint.Style.FILL);
        dataSet.setShadowColor(isChartDark ? DARK_TEXT_SEC : LIGHT_TEXT_SEC);
        dataSet.setShadowWidth(1f);
        dataSet.setDrawValues(false);
        dataSet.setHighlightEnabled(true);
        dataSet.setHighLightColor(COLOR_PRIMARY);
        dataSet.enableDashedHighlightLine(10f, 5f, 0f);
        CandleData candleData = new CandleData(dataSet);
        CombinedData combinedData = new CombinedData();
        combinedData.setData(candleData);
        if (!movingAverageEntries.isEmpty()) {
            LineDataSet averageSet = createMovingAverageDataSet();
            combinedData.setData(new LineData(averageSet));
            candleStickChart.setDrawOrder(new CombinedChart.DrawOrder[]{
                    CombinedChart.DrawOrder.CANDLE, CombinedChart.DrawOrder.LINE});
        }
        candleStickChart.setData(combinedData);
        candleStickChart.getLegend().setEnabled(showMa150 && !movingAverageEntries.isEmpty());
        attachMarker(candleStickChart);
        candleStickChart.animateX(400);
        candleStickChart.invalidate();
    }

    private void updateLineChart(List<CandleEntry> candleEntries) {
        List<Entry> lineEntries = new ArrayList<>();
        for (CandleEntry c : candleEntries) lineEntries.add(new Entry(c.getX(), c.getClose()));
        LineDataSet ds = new LineDataSet(lineEntries, showMa150 ? "Price" : "");
        ds.setColor(COLOR_PRIMARY);
        ds.setLineWidth(2.5f);
        ds.setDrawCircles(false);
        ds.setDrawValues(false);
        ds.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        ds.setHighLightColor(COLOR_PRIMARY);
        ds.setHighlightEnabled(true);
        ds.enableDashedHighlightLine(10f, 5f, 0f);
        ds.setDrawFilled(true);
        ds.setFillColor(COLOR_FILL);
        ds.setFillAlpha(isChartDark ? 90 : 50);
        LineData lineData = new LineData(ds);
        if (!movingAverageEntries.isEmpty()) {
            lineData.addDataSet(createMovingAverageDataSet());
        }
        lineChart.setData(lineData);
        lineChart.getLegend().setEnabled(showMa150 && !movingAverageEntries.isEmpty());
        attachMarker(lineChart);
        lineChart.animateX(400);
        lineChart.invalidate();
    }

    private LineDataSet createMovingAverageDataSet() {
        LineDataSet averageSet = new LineDataSet(movingAverageEntries, "150-day MA");
        averageSet.setColor(0xFFFFB74D);
        averageSet.setLineWidth(2f);
        averageSet.setDrawCircles(false);
        averageSet.setDrawValues(false);
        averageSet.setHighlightEnabled(false);
        return averageSet;
    }

    private void fetchStockData(String symbol, String interval) {
        int requestGeneration = ++chartRequestGeneration;
        if (isCryptoSymbol(symbol)) {
            fetchCryptoData(symbol, interval, requestGeneration);
        } else {
            fetchYahooData(symbol, interval, requestGeneration);
        }
    }

    private String[] getYahooParams() {
        return new String[]{"1d", visibleChartRange};
    }

    private SimpleDateFormat dateFormatFor(String yahooInterval) {
        switch (yahooInterval) {
            case "1m": case "5m": case "15m": case "30m": case "1h":
                return new SimpleDateFormat("MM/dd HH:mm", Locale.US);
            case "1mo":
                return new SimpleDateFormat("yyyy-MM", Locale.US);
            default:
                return new SimpleDateFormat("MM/dd/yy", Locale.US);
        }
    }

    private void fetchYahooData(String sym, String ignoredInterval, int requestGeneration) {
        String[] p = getYahooParams();
        String encodedSymbol = URLEncoder.encode(sym, StandardCharsets.UTF_8)
                .replace("+", "%20");
        String url = "https://query1.finance.yahoo.com/v8/finance/chart/" + encodedSymbol
                + "?interval=" + p[0] + "&range=" + p[1] + "&includePrePost=false";

        Request req = new Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build();
        client.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (getActivity() != null) getActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(), "Network error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (requestGeneration != chartRequestGeneration) return;
                if (!response.isSuccessful() || response.body() == null) return;
                try {
                    JSONObject root   = new JSONObject(response.body().string());
                    JSONArray  result = root.getJSONObject("chart").optJSONArray("result");
                    if (result == null || result.length() == 0) {
                        if (getActivity() != null) getActivity().runOnUiThread(() ->
                                Toast.makeText(requireContext(), "No data for: " + sym, Toast.LENGTH_SHORT).show());
                        return;
                    }
                    JSONObject item  = result.getJSONObject(0);
                    JSONArray  ts    = item.getJSONArray("timestamp");
                    JSONObject quote = item.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0);
                    JSONArray  opens = quote.getJSONArray("open");
                    JSONArray  highs = quote.getJSONArray("high");
                    JSONArray  lows  = quote.getJSONArray("low");
                    JSONArray  cls   = quote.getJSONArray("close");
                    int size = ts.length();
                    if (size == 0) return;
                    List<Float> closes = new ArrayList<>();
                    List<String> labels = new ArrayList<>();
                    List<Long> timestamps = new ArrayList<>();
                    List<CandleEntry> entries = new ArrayList<>();
                    SimpleDateFormat sdf = dateFormatFor(p[0]);
                    float lc = 0f; int vc = 0;
                    int startIdx = 0;
                    for (int i = startIdx; i < size; i++) {
                        if (cls.isNull(i)||opens.isNull(i)||highs.isNull(i)||lows.isNull(i)) continue;
                        float o=(float)opens.getDouble(i), h=(float)highs.getDouble(i),
                              l=(float)lows.getDouble(i),  c=(float)cls.getDouble(i);
                        labels.add(sdf.format(new Date(ts.getLong(i)*1000L)));
                        timestamps.add(ts.getLong(i) * 1000L);
                        closes.add(c);
                        entries.add(new CandleEntry(vc, h, l, o, c));
                        lc = c; vc++;
                    }
                    if (vc == 0) return;
                    YahooQuoteMath.Quote marketQuote = YahooQuoteMath.fromChartItem(item);
                    float quotePrice = (float) marketQuote.currentPrice;
                    if (!Float.isFinite(quotePrice) || quotePrice <= 0f) quotePrice = lc;
                    float periodStartPrice = entries.get(0).getOpen();
                    postChartUpdate(sym, entries, labels, timestamps, closes, quotePrice,
                            periodStartPrice, requestGeneration);
                } catch (Exception e) { Log.e("ChartFragment", "Yahoo parse error", e); }
            }
        });
    }

    private String getBinanceInterval() {
        return "1d";
    }

    private void fetchCryptoData(String sym, String ignoredInterval, int requestGeneration) {
        String pair = sym.contains(":") ? sym.substring(sym.indexOf(':') + 1) : sym;
        String binanceInterval = getBinanceInterval();
        int limit = getCryptoCandleLimit(binanceInterval, visibleChartRange);
        String url = "https://api.binance.com/api/v3/klines?symbol=" + pair
                + "&interval=" + binanceInterval + "&limit=" + limit;

        Request req = new Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0").build();

        client.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (getActivity() != null) getActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(), "Crypto error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (requestGeneration != chartRequestGeneration) return;
                if (!response.isSuccessful() || response.body() == null) return;
                try {
                    JSONArray arr = new JSONArray(response.body().string());
                    if (arr.length() == 0) return;
                    List<Float> closes = new ArrayList<>();
                    List<String> labels = new ArrayList<>();
                    List<Long> timestamps = new ArrayList<>();
                    List<CandleEntry> entries = new ArrayList<>();
                    String[] yahooP = getYahooParams();
                    SimpleDateFormat sdf = dateFormatFor(yahooP[0]);
                    float lc = 0f; int vc = 0;
                    for (int i = 0; i < arr.length(); i++) {
                        JSONArray bar = arr.getJSONArray(i);
                        float o = (float) Double.parseDouble(bar.getString(1));
                        float h = (float) Double.parseDouble(bar.getString(2));
                        float l = (float) Double.parseDouble(bar.getString(3));
                        float c = (float) Double.parseDouble(bar.getString(4));
                        long  t = bar.getLong(0);
                        labels.add(sdf.format(new Date(t)));
                        timestamps.add(t);
                        closes.add(c);
                        entries.add(new CandleEntry(vc, h, l, o, c));
                        lc = c; vc++;
                    }
                    if (vc == 0) return;
                    float periodStartPrice = entries.get(0).getOpen();
                    postChartUpdate(sym, entries, labels, timestamps, closes, lc, periodStartPrice,
                            requestGeneration);
                } catch (Exception e) { Log.e("ChartFragment", "Crypto parse error", e); }
            }
        });
    }

    private int getCryptoCandleLimit(String binanceInterval, String range) {
        long rangeSeconds;
        switch (range) {
            case "1d": rangeSeconds = 24L * 60 * 60; break;
            case "5d": rangeSeconds = 7L * 24 * 60 * 60; break;
            case "1mo": rangeSeconds = 30L * 24 * 60 * 60; break;
            case "3mo": rangeSeconds = 90L * 24 * 60 * 60; break;
            case "6mo": rangeSeconds = 180L * 24 * 60 * 60; break;
            case "2y": rangeSeconds = 730L * 24 * 60 * 60; break;
            case "5y": rangeSeconds = 1825L * 24 * 60 * 60; break;
            case "max": return 1000;
            case "1y":
            default: rangeSeconds = 365L * 24 * 60 * 60; break;
        }

        long candleSeconds;
        switch (binanceInterval) {
            case "1m": candleSeconds = 60; break;
            case "5m": candleSeconds = 5L * 60; break;
            case "15m": candleSeconds = 15L * 60; break;
            case "30m": candleSeconds = 30L * 60; break;
            case "1h": candleSeconds = 60L * 60; break;
            case "4h": candleSeconds = 4L * 60 * 60; break;
            case "1w": candleSeconds = 7L * 24 * 60 * 60; break;
            case "1M": candleSeconds = 30L * 24 * 60 * 60; break;
            case "1d":
            default: candleSeconds = 24L * 60 * 60; break;
        }
        return (int) Math.max(1, Math.min(1000, (rangeSeconds + candleSeconds - 1) / candleSeconds));
    }

    private void postChartUpdate(String sym, List<CandleEntry> entries, List<String> labels,
                                 List<Long> timestamps, List<Float> closes,
                                 float lastClose, float periodStartPrice,
                                 int requestGeneration) {
        if (!Float.isFinite(periodStartPrice) || periodStartPrice <= 0f) {
            periodStartPrice = lastClose;
        }
        float changePct = (float) DailyQuoteMath.percentChange(lastClose, periodStartPrice);
        if (!Float.isFinite(changePct)) changePct = 0f;
        boolean gain = changePct >= 0;

        String priceFormatted = formatPrice(lastClose, sym);
        float change = lastClose - periodStartPrice;
        String changeFormatted = formatPrice(Math.abs(change), sym);
        String changeStr = String.format(Locale.US, "%+.2f%% (%s$%s)",
                changePct, gain ? "+" : "-", changeFormatted);

        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                if (requestGeneration != chartRequestGeneration) return;
                currentEntries.clear();
                currentEntries.addAll(entries);
                dateLabels.clear();
                dateLabels.addAll(labels);
                currentTimestamps.clear();
                currentTimestamps.addAll(timestamps);
                fullCloses.clear();
                fullCloses.addAll(closes);
                lastPrice = lastClose;
                if (showMa150) movingAverageEntries.clear();

                if (priceText != null) {
                    priceText.setText("$" + priceFormatted);
                    priceText.setTextColor(gain ? COLOR_GAIN : COLOR_LOSS);
                }

                if (changeText != null) {
                    changeText.setText(changeStr);
                    changeText.setTextColor(gain ? COLOR_GAIN : COLOR_LOSS);
                    android.graphics.drawable.Drawable background =
                            changeText.getBackground().mutate();
                    if (background instanceof android.graphics.drawable.GradientDrawable) {
                        ((android.graphics.drawable.GradientDrawable) background).setStroke(
                                dpToPx(1), gain ? COLOR_GAIN : COLOR_LOSS);
                    }
                }

                if (tickerText != null) {
                    tickerText.setText(cleanDisplaySymbol(sym));
                }

                if (tickerLabelTop != null) {
                    tickerLabelTop.setText(cleanDisplaySymbol(sym));
                }

                if (getActivity() != null) {
                    getActivity().setTitle("Chart: " + cleanDisplaySymbol(sym));
                }

                hideCrosshairInfo();

                if (isCandleStick) {
                    updateCandleChart(entries);
                } else {
                    updateLineChart(entries);
                }
                if (showMa150) {
                    fetchMovingAverage(sym, entries, timestamps, requestGeneration);
                } else {
                    movingAverageEntries.clear();
                }
            });
        }
    }

    private void fetchMovingAverage(String sym, List<CandleEntry> chartEntries,
                                    List<Long> timestamps, int requestGeneration) {
        if (sym == null || sym.contains(":") || timestamps.isEmpty()) {
            movingAverageEntries.clear();
            if (isCandleStick) updateCandleChart(chartEntries);
            else updateLineChart(chartEntries);
            return;
        }
        String encoded = URLEncoder.encode(sym, StandardCharsets.UTF_8)
                .replace("+", "%20");
        String url = "https://query1.finance.yahoo.com/v8/finance/chart/" + encoded
                + "?interval=1d&range=5y&includePrePost=false";
        Request request = new Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0").build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException error) {
                Log.w("ChartFragment", "Could not load 150-day moving average for " + sym, error);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response)
                    throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    Log.w("ChartFragment", "Moving-average request failed: HTTP "
                            + response.code());
                    response.close();
                    return;
                }
                try {
                    JSONObject result = new JSONObject(response.body().string())
                            .getJSONObject("chart").getJSONArray("result").getJSONObject(0);
                    JSONArray dailyTimes = result.getJSONArray("timestamp");
                    JSONArray dailyCloses = result.getJSONObject("indicators")
                            .getJSONArray("quote").getJSONObject(0).getJSONArray("close");
                    TreeMap<Long, Float> averagesByTime = new TreeMap<>();
                    ArrayList<Float> window = new ArrayList<>();
                    double sum = 0;
                    for (int i = 0; i < dailyTimes.length(); i++) {
                        if (dailyCloses.isNull(i)) continue;
                        float close = (float) dailyCloses.optDouble(i, Double.NaN);
                        if (!Float.isFinite(close) || close <= 0f) continue;
                        window.add(close);
                        sum += close;
                        if (window.size() > 150) sum -= window.remove(0);
                        if (window.size() == 150) {
                            averagesByTime.put(dailyTimes.getLong(i) * 1000L,
                                    (float) (sum / 150.0));
                        }
                    }
                    ArrayList<Entry> mappedEntries = new ArrayList<>();
                    for (int i = 0; i < timestamps.size(); i++) {
                        Map.Entry<Long, Float> average =
                                averagesByTime.floorEntry(timestamps.get(i));
                        if (average != null) {
                            mappedEntries.add(new Entry(i, average.getValue()));
                        }
                    }
                    if (getActivity() == null) return;
                    getActivity().runOnUiThread(() -> {
                        if (requestGeneration != chartRequestGeneration || !showMa150) return;
                        movingAverageEntries.clear();
                        movingAverageEntries.addAll(mappedEntries);
                        if (isCandleStick) updateCandleChart(chartEntries);
                        else updateLineChart(chartEntries);
                    });
                } catch (Exception error) {
                    Log.w("ChartFragment", "Could not parse 150-day moving average for " + sym,
                            error);
                } finally {
                    response.close();
                }
            }
        });
    }

    private void openChartFromInput(String raw) {
        String upper = raw.toUpperCase(Locale.US).trim();

        String cryptoSym = CRYPTO_MAP.get(upper);
        if (cryptoSym != null) {
            symbol = cryptoSym;
        } else {
            String forexSym = FOREX_MAP.get(upper);
            symbol = (forexSym != null) ? forexSym : upper;
        }

        SharedPreferences prefs = requireActivity()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String mode = prefs.getString(KEY_SYMBOL_MODE, "last");
        // שמור "last" רק אם לא במצב fixed
        if (!"fixed".equals(mode)) {
            prefs.edit()
                    .putString(KEY_LAST_SYMBOL, symbol)
                    .apply();
        }

        updateTickerButtonLabel();
        updateTickerLabelTop();
        hideCrosshairInfo();
        fetchStockData(symbol, interval);
    }

}