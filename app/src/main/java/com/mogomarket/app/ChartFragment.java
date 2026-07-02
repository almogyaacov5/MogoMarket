package com.mogomarket.app;

import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.mogomarket.app.TradingMarkerView;
import com.github.mikephil.charting.charts.CandleStickChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.CandleData;
import com.github.mikephil.charting.data.CandleDataSet;
import com.github.mikephil.charting.data.CandleEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.listener.ChartTouchListener;
import com.github.mikephil.charting.listener.OnChartGestureListener;
import com.google.firebase.auth.FirebaseAuth;
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
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ChartFragment extends Fragment implements TimeFrameFragment.TimeFrameListener {

    private static final String PREFS_NAME = "app_prefs";
    private static final String KEY_THEME  = "dark_mode";

    private static final int DARK_BG        = 0xFF0B0F14;
    private static final int DARK_CARD      = 0xFF151C2E;
    private static final int DARK_TEXT_PRI  = 0xFFE6EDF3;
    private static final int DARK_TEXT_SEC  = 0xFF8B98A5;
    private static final int LIGHT_BG       = 0xFFF0F4F8;
    private static final int LIGHT_CARD     = 0xFFFFFFFF;
    private static final int LIGHT_TEXT_PRI = 0xFF1A1D23;
    private static final int LIGHT_TEXT_SEC = 0xFF6B7280;
    private static final int COLOR_PRIMARY  = 0xFF4DA3FF;
    private static final int COLOR_GAIN     = 0xFF00C896;
    private static final int COLOR_LOSS     = 0xFFFF4D4D;
    private static final int COLOR_FILL     = 0xFF1C6DD0;

    private static final long DOUBLE_TAP_TIMEOUT_MS = 350;
    private long lastTapTime = 0;

    // Timeframe definitions: label, interval string, Yahoo interval, Yahoo range, Binance interval
    // Each timeframe always shows 252 candles
    private static final String[][] TIMEFRAMES = {
        // {label, internalInterval, yahooInterval, yahooRange, binanceInterval}
        {"1m",  "1min",   "1m",  "1d",  "1m"},
        {"5m",  "5min",   "5m",  "5d",  "5m"},
        {"15m", "15min",  "15m", "5d",  "15m"},
        {"30m", "30min",  "30m", "1mo", "30m"},
        {"1H",  "60min",  "1h",  "5d",  "1h"},
        {"4H",  "4hour",  "1h",  "1mo", "4h"},
        {"1D",  "1day",   "1d",  "1y",  "1d"},
        {"1W",  "1week",  "1wk", "5y",  "1w"},
        {"1M",  "1month", "1mo", "max", "1M"},
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

    private boolean isDarkTheme;
    private boolean isChartDark;
    private boolean isFullscreen = false;
    private boolean isCrosshairActive = false;

    // Current timeframe index into TIMEFRAMES array (default = "1D" = index 6)
    private int currentTFIndex = 6;

    // Kept for backward compat (not used for display)
    private com.google.android.material.button.MaterialButton btnTF1D, btnTF1W, btnTF1M, btnTF3M, btnTF1Y;
    private com.google.android.material.button.MaterialButton activeTFButton = null;

    private ViewGroup.LayoutParams chartOriginalParams;

    private CandleStickChart candleStickChart;
    private LineChart lineChart;
    private AutoCompleteTextView tickerInput;

    private com.google.android.material.button.MaterialButton btnTickerSelect;
    private com.google.android.material.button.MaterialButton btnTimeframePicker;
    private Button btnLoad, btnTimeFrame, btnToggleChart, btnAIAnalysis;
    private com.google.android.material.button.MaterialButton btnChartRefresh, btnExpandChart,
            btnExitFullscreen, btnSettings;
    private Button btnChartThemeToggle;

    private View headerSection;
    private View searchSection;
    private View controlsSection;
    private View bottomBar;
    private FrameLayout chartContainer;
    private View chartRootLayout;
    private TextView timeFrameText, tickerText, priceText, changeText, currentPriceDisplay;
    private ProgressBar progressAI;

    private LinearLayout crosshairInfoBar;
    private TextView crosshairPrice;
    private TextView crosshairDate;

    private final OkHttpClient client = new OkHttpClient();
    private final String FINNHUB_KEY = "d918pn9r01qr1uqui560d918pn9r01qr1uqui56g";

    private String symbol = "SPY";
    private String interval = "1day";
    private boolean isCandleStick = true;
    private final DecimalFormat df = new DecimalFormat("#.##");
    private String latestQuery = "";

    private LLMService llmService;
    private final List<CandleEntry> currentEntries = new ArrayList<>();
    private final List<Float> fullCloses = new ArrayList<>();
    private final List<String> dateLabels = new ArrayList<>();
    private float lastPrice = 0f;
    private boolean isManualSelection = false;

    private ArrayAdapter<StockSuggestion> suggestionAdapter;
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;
    private static final long SEARCH_DEBOUNCE_MS = 300;

    public static class StockSuggestion {
        public final String symbol, name, exchange;
        public StockSuggestion(String symbol, String name, String exchange) {
            this.symbol = symbol;
            this.name = name;
            this.exchange = exchange;
        }
        @NonNull
        @Override
        public String toString() {
            String s = symbol == null ? "" : symbol;
            String n = name   == null ? "" : name;
            if (n.isEmpty()) return s;
            if (n.length() > 30) n = n.substring(0, 28) + "...";
            return s + "  \u00b7  " + n;
        }
    }

    private boolean isCryptoSymbol(String sym) {
        return sym != null && sym.contains(":");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_chart, container, false);

        SharedPreferences prefs = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        isDarkTheme = prefs.getBoolean(KEY_THEME, true);
        isChartDark = isDarkTheme;

        chartRootLayout    = v.findViewById(R.id.chartRootLayout);
        candleStickChart   = v.findViewById(R.id.stock_chart);
        lineChart          = v.findViewById(R.id.line_chart);
        tickerInput        = null;
        btnLoad            = null;
        btnTimeFrame       = null;
        btnToggleChart     = v.findViewById(R.id.btnToggleChart);
        btnChartRefresh    = v.findViewById(R.id.btnChartRefresh);
        btnAIAnalysis      = v.findViewById(R.id.btnAIAnalysis);
        btnSettings        = v.findViewById(R.id.btnSettings);
        btnChartThemeToggle= v.findViewById(R.id.btnChartThemeToggle);
        btnTickerSelect    = v.findViewById(R.id.btnTickerSelect);
        btnTimeframePicker = v.findViewById(R.id.btnTimeframePicker);

        // Backward-compat TF buttons (hidden)
        btnTF1D = v.findViewById(R.id.btnTF1D);
        btnTF1W = v.findViewById(R.id.btnTF1W);
        btnTF1M = v.findViewById(R.id.btnTF1M);
        btnTF3M = v.findViewById(R.id.btnTF3M);
        btnTF1Y = v.findViewById(R.id.btnTF1Y);

        crosshairInfoBar   = v.findViewById(R.id.crosshairInfoBar);
        crosshairPrice     = v.findViewById(R.id.crosshairPrice);
        crosshairDate      = v.findViewById(R.id.crosshairDate);

        if (btnSettings != null) {
            btnSettings.setOnClickListener(vv -> {
                if (getActivity() instanceof MainActivity)
                    ((MainActivity) getActivity()).openSettings();
            });
        }

        btnExpandChart     = v.findViewById(R.id.btnExpandChart);
        btnExitFullscreen  = v.findViewById(R.id.btnExitFullscreen);
        progressAI         = v.findViewById(R.id.progressAI);
        priceText          = v.findViewById(R.id.priceText);
        changeText         = v.findViewById(R.id.changeText);
        timeFrameText      = v.findViewById(R.id.timeFrameText);
        tickerText         = v.findViewById(R.id.tickerText);
        currentPriceDisplay= v.findViewById(R.id.currentPriceDisplay);
        headerSection      = v.findViewById(R.id.headerSection);
        searchSection      = v.findViewById(R.id.searchSection);
        controlsSection    = v.findViewById(R.id.controlsSection);
        bottomBar          = v.findViewById(R.id.bottomBar);
        chartContainer     = v.findViewById(R.id.chartContainer);

        llmService = new LLMService();
        if (progressAI          != null) progressAI.setVisibility(View.GONE);
        if (currentPriceDisplay != null) currentPriceDisplay.setVisibility(View.GONE);

        if (getArguments() != null) {
            if (getArguments().containsKey("symbol")) {
                symbol = getArguments().getString("symbol", symbol);
            }
        }

        updateTickerButtonLabel();
        updateTimeframePickerLabel();
        if (getActivity() != null) getActivity().setTitle("Chart: " + symbol);

        applyTheme();
        setupCandleChartStyle();
        setupLineChartStyle();
        setupClickListeners();
        setupChartGestures();

        // sync interval from currentTFIndex
        interval = TIMEFRAMES[currentTFIndex][1];
        fetchStockData(symbol, interval);

        updateChartThemeToggleLabel();
        return v;
    }

    // ─── Timeframe picker ────────────────────────────────────────────────────

    private void updateTimeframePickerLabel() {
        if (btnTimeframePicker == null) return;
        btnTimeframePicker.setText(TIMEFRAMES[currentTFIndex][0]);
    }

    private void showTimeframeDialog() {
        if (getContext() == null) return;

        // ─── Root container ───────────────────────────────────────────────────
        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(isDarkTheme ? 0xFF151C2E : 0xFFFFFFFF);
        int ph = dpToPx(20);
        root.setPadding(ph, dpToPx(20), ph, dpToPx(16));

        // ─── כותרת ────────────────────────────────────────────────────────────
        TextView title = new TextView(getContext());
        title.setText("Select Timeframe");
        title.setTextSize(16f);
        title.setTextColor(isDarkTheme ? 0xFFE6EDF3 : 0xFF1A1D23);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(dpToPx(4), 0, 0, dpToPx(16));
        root.addView(title);

        // ─── Grid של כפתורי טיים-פריים ────────────────────────────────────────
        // שורה אחרי שורה, 3 כפתורים בשורה
        int cols = 3;
        for (int row = 0; row < Math.ceil((double) TIMEFRAMES.length / cols); row++) {
            LinearLayout rowLayout = new LinearLayout(getContext());
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.setMargins(0, 0, 0, dpToPx(8));
            rowLayout.setLayoutParams(rowLp);

            for (int col = 0; col < cols; col++) {
                int idx = row * cols + col;

                if (idx >= TIMEFRAMES.length) {
                    // תא ריק למילוי שורה
                    android.widget.Space spacer = new android.widget.Space(getContext());
                    LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0,
                            android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                    sp.setMargins(col == 0 ? 0 : dpToPx(6), 0, 0, 0);
                    rowLayout.addView(spacer, sp);
                    continue;
                }

                boolean isSelected = (idx == currentTFIndex);
                String label = TIMEFRAMES[idx][0];
                final int finalIdx = idx;

                // כפתור
                android.widget.FrameLayout btnWrapper = new android.widget.FrameLayout(getContext());

                android.graphics.drawable.GradientDrawable btnBg =
                        new android.graphics.drawable.GradientDrawable();
                btnBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                btnBg.setCornerRadius(dpToPx(10));

                if (isSelected) {
                    btnBg.setColor(0xFF4DA3FF);
                    btnBg.setStroke(0, android.graphics.Color.TRANSPARENT);
                } else {
                    btnBg.setColor(isDarkTheme ? 0xFF0B0F14 : 0xFFF0F4F8);
                    btnBg.setStroke(dpToPx(1), isDarkTheme ? 0xFF1E2A3A : 0xFFE5E7EB);
                }
                btnWrapper.setBackground(btnBg);

                TextView btnText = new TextView(getContext());
                btnText.setText(label);
                btnText.setTextSize(14f);
                btnText.setTextColor(isSelected ? 0xFFFFFFFF :
                        (isDarkTheme ? 0xFF8B98A5 : 0xFF6B7280));
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

                // ─── AlertDialog reference for dismiss ───────────────────────
                // נשמור ref ב-array חד-איברי כדי לגשת מתוך lambda
                final AlertDialog[] dialogRef = new AlertDialog[1];
                btnWrapper.setOnClickListener(v -> {
                    currentTFIndex = finalIdx;
                    interval = TIMEFRAMES[currentTFIndex][1];
                    updateTimeframePickerLabel();
                    hideCrosshairInfo();
                    fetchStockData(symbol, interval);
                    if (dialogRef[0] != null) dialogRef[0].dismiss();
                });

                // שמור ref לאחר יצירת dialog
                root.setTag(btnWrapper);
            }
            root.addView(rowLayout);
        }

        // ─── Divider ──────────────────────────────────────────────────────────
        View divider = new View(getContext());
        divider.setBackgroundColor(isDarkTheme ? 0xFF1E2A3A : 0xFFE5E7EB);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(1));
        divLp.setMargins(0, dpToPx(8), 0, dpToPx(12));
        root.addView(divider, divLp);

        // ─── Dialog ───────────────────────────────────────────────────────────
        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setView(root)
                .setNegativeButton("Cancel", null)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        dialog.show();

        // עדכן את כל ה-listeners עם ה-dialog האמיתי
        for (int i = 0; i < root.getChildCount(); i++) {
            android.view.View child = root.getChildAt(i);
            if (child instanceof LinearLayout) {
                LinearLayout rowL = (LinearLayout) child;
                for (int j = 0; j < rowL.getChildCount(); j++) {
                    android.view.View cell = rowL.getChildAt(j);
                    if (cell instanceof android.widget.FrameLayout) {
                        final int tfIdx = (i) * cols + j;
                        if (tfIdx < TIMEFRAMES.length) {
                            final AlertDialog finalDialog = dialog;
                            cell.setOnClickListener(v -> {
                                currentTFIndex = tfIdx;
                                interval = TIMEFRAMES[currentTFIndex][1];
                                updateTimeframePickerLabel();
                                hideCrosshairInfo();
                                fetchStockData(symbol, interval);
                                finalDialog.dismiss();
                            });
                        }
                    }
                }
            }
        }
    }

    // Not used anymore but kept to satisfy TimeFrameFragment.TimeFrameListener interface
    @Override
    public void onTimeFrameSelected(String tf) {
        interval = tf;
        hideCrosshairInfo();
        fetchStockData(symbol, interval);
    }

    // Kept for backward compat
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
            crosshairPrice.setText("$" + String.format(Locale.US, "%.2f", price));
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
        if (candleStickChart != null) candleStickChart.highlightValue(null);
        if (lineChart        != null) lineChart.highlightValue(null);
    }

    private void setupChartGestures() {
        OnChartGestureListener gestureListener = new OnChartGestureListener() {
            @Override public void onChartGestureStart(MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture) {}
            @Override public void onChartGestureEnd(MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture) {}
            @Override public void onChartLongPressed(MotionEvent me) {
                if (candleStickChart != null && candleStickChart.getVisibility() == View.VISIBLE) {
                    com.github.mikephil.charting.highlight.Highlight h =
                            candleStickChart.getHighlightByTouchPoint(me.getX(), me.getY());
                    if (h != null) { isCrosshairActive = true; candleStickChart.highlightValue(h); }
                } else if (lineChart != null && lineChart.getVisibility() == View.VISIBLE) {
                    com.github.mikephil.charting.highlight.Highlight h =
                            lineChart.getHighlightByTouchPoint(me.getX(), me.getY());
                    if (h != null) { isCrosshairActive = true; lineChart.highlightValue(h); }
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
        if (candleStickChart != null) candleStickChart.setOnChartGestureListener(gestureListener);
        if (lineChart        != null) lineChart.setOnChartGestureListener(gestureListener);
    }

    private void updateTickerButtonLabel() {
        if (btnTickerSelect == null) return;
        String display = isCryptoSymbol(symbol)
                ? symbol.substring(symbol.indexOf(':') + 1)
                : symbol;
        btnTickerSelect.setText(display);
    }

    private void showTickerInputDialog() {
        TickerSearchSheet sheet = new TickerSearchSheet();
        sheet.setOnTickerSelectedListener(sym -> openChartFromInput(sym));
        sheet.show(getParentFragmentManager(), "ticker_search");
    }

    // ─── Fullscreen ─────────────────────────────────────────────────

    private void enterFullscreen() {
        if (isFullscreen || chartContainer == null) return;
        isFullscreen = true;
        chartOriginalParams = chartContainer.getLayoutParams();
        if (headerSection   != null) headerSection.setVisibility(View.GONE);
        if (searchSection   != null) searchSection.setVisibility(View.GONE);
        if (controlsSection != null) controlsSection.setVisibility(View.GONE);
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
        if (controlsSection != null) controlsSection.setVisibility(View.VISIBLE);
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
        if (headerSection   != null) headerSection.setBackgroundColor(cardColor);
        if (controlsSection != null) controlsSection.setBackgroundColor(cardColor);
        if (bottomBar       != null) bottomBar.setBackgroundColor(cardColor);
        if (timeFrameText   != null) timeFrameText.setTextColor(textSec);
        applyChartColors();
    }

    private void applyChartColors() {
        int chartCard = isChartDark ? DARK_CARD     : LIGHT_CARD;
        int chartSec  = isChartDark ? DARK_TEXT_SEC : LIGHT_TEXT_SEC;
        if (chartContainer   != null) chartContainer.setBackgroundColor(chartCard);
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
        candleStickChart.setHighlightPerDragEnabled(true);
        candleStickChart.setExtraTopOffset(4f);
        XAxis xAxis = candleStickChart.getXAxis();
        xAxis.setDrawGridLines(false);
        xAxis.setDrawAxisLine(false);
        xAxis.setTextColor(textSec);
        xAxis.setTextSize(10f);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
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
        lineChart.setHighlightPerDragEnabled(true);
        lineChart.setExtraTopOffset(4f);
        XAxis xAxis = lineChart.getXAxis();
        xAxis.setDrawGridLines(false);
        xAxis.setDrawAxisLine(false);
        xAxis.setTextColor(textSec);
        xAxis.setTextSize(10f);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
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
                fetchStockData(symbol, interval);
                Toast.makeText(requireContext(), "Chart refreshed", Toast.LENGTH_SHORT).show();
            });
        }

        if (btnToggleChart != null) {
            btnToggleChart.setOnClickListener(v -> {
                isCandleStick = !isCandleStick;
                if (isCandleStick) {
                    btnToggleChart.setText("Line chart");
                    candleStickChart.setVisibility(View.VISIBLE);
                    lineChart.setVisibility(View.GONE);
                } else {
                    btnToggleChart.setText("Candle chart");
                    candleStickChart.setVisibility(View.GONE);
                    lineChart.setVisibility(View.VISIBLE);
                }
                fetchStockData(symbol, interval);
            });
        }

        if (btnAIAnalysis != null) btnAIAnalysis.setOnClickListener(v -> analyzeWithAI());

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
        candleStickChart.setData(new CandleData(dataSet));
        attachMarker(candleStickChart);
        candleStickChart.animateX(400);
        candleStickChart.invalidate();
    }

    private void updateLineChart(List<CandleEntry> candleEntries) {
        List<Entry> lineEntries = new ArrayList<>();
        for (CandleEntry c : candleEntries) lineEntries.add(new Entry(c.getX(), c.getClose()));
        LineDataSet ds = new LineDataSet(lineEntries, "");
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
        lineChart.setData(new LineData(ds));
        attachMarker(lineChart);
        lineChart.animateX(400);
        lineChart.invalidate();
    }

    private void fetchStockData(String symbol, String interval) {
        if (isCryptoSymbol(symbol)) {
            fetchCryptoData(symbol, interval);
        } else {
            fetchYahooData(symbol, interval);
        }
    }

    // Returns {yahooInterval, yahooRange} for the current timeframe index
    private String[] getYahooParams() {
        String yahooInterval = TIMEFRAMES[currentTFIndex][2];
        String yahooRange    = TIMEFRAMES[currentTFIndex][3];
        return new String[]{yahooInterval, yahooRange};
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

    private void fetchYahooData(String sym, String ignoredInterval) {
        String[] p = getYahooParams();
        String url = "https://query1.finance.yahoo.com/v8/finance/chart/" + sym
                + "?interval=" + p[0] + "&range=" + p[1] + "&includePrePost=false";

        Request req = new Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build();
        client.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (getActivity() != null) getActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(), "Network error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
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
                    fullCloses.clear(); dateLabels.clear();
                    List<CandleEntry> entries = new ArrayList<>();
                    SimpleDateFormat sdf = dateFormatFor(p[0]);
                    float lc = 0f, pc = 0f; int vc = 0;
                    // Take last 252 candles
                    int startIdx = Math.max(0, size - 252);
                    for (int i = startIdx; i < size; i++) {
                        if (cls.isNull(i)||opens.isNull(i)||highs.isNull(i)||lows.isNull(i)) continue;
                        float o=(float)opens.getDouble(i), h=(float)highs.getDouble(i),
                              l=(float)lows.getDouble(i),  c=(float)cls.getDouble(i);
                        dateLabels.add(sdf.format(new Date(ts.getLong(i)*1000L)));
                        fullCloses.add(c);
                        entries.add(new CandleEntry(vc, h, l, o, c));
                        pc = lc; lc = c; vc++;
                    }
                    if (vc == 0) return;
                    if (pc == 0f) pc = lc;
                    postChartUpdate(sym, entries, lc, pc);
                } catch (Exception e) { Log.e("ChartFragment", "Yahoo parse error", e); }
            }
        });
    }

    private String getBinanceInterval() {
        return TIMEFRAMES[currentTFIndex][4];
    }

    private void fetchCryptoData(String sym, String ignoredInterval) {
        String pair = sym.contains(":") ? sym.substring(sym.indexOf(':') + 1) : sym;
        String binanceInterval = getBinanceInterval();
        int limit = 252;
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
                if (!response.isSuccessful() || response.body() == null) return;
                try {
                    JSONArray arr = new JSONArray(response.body().string());
                    if (arr.length() == 0) return;
                    fullCloses.clear(); dateLabels.clear();
                    List<CandleEntry> entries = new ArrayList<>();
                    String[] yahooP = getYahooParams();
                    SimpleDateFormat sdf = dateFormatFor(yahooP[0]);
                    float lc = 0f, pc = 0f; int vc = 0;
                    for (int i = 0; i < arr.length(); i++) {
                        JSONArray bar = arr.getJSONArray(i);
                        float o = (float) Double.parseDouble(bar.getString(1));
                        float h = (float) Double.parseDouble(bar.getString(2));
                        float l = (float) Double.parseDouble(bar.getString(3));
                        float c = (float) Double.parseDouble(bar.getString(4));
                        long  t = bar.getLong(0);
                        dateLabels.add(sdf.format(new Date(t)));
                        fullCloses.add(c);
                        entries.add(new CandleEntry(vc, h, l, o, c));
                        pc = lc; lc = c; vc++;
                    }
                    if (vc == 0) return;
                    if (pc == 0f) pc = lc;
                    postChartUpdate(sym, entries, lc, pc);
                } catch (Exception e) { Log.e("ChartFragment", "Crypto parse error", e); }
            }
        });
    }

    private void postChartUpdate(String sym, List<CandleEntry> entries, float lastClose, float prevClose) {
        currentEntries.clear();
        currentEntries.addAll(entries);
        lastPrice = lastClose;
        float change  = lastClose - prevClose;
        float changePct = prevClose != 0 ? (change / prevClose) * 100f : 0f;
        boolean gain  = change >= 0;
        String changeStr = String.format(Locale.US, "%s$%.2f (%.2f%%)",
                gain ? "+" : "-", Math.abs(change), Math.abs(changePct));

        if (getActivity() != null) getActivity().runOnUiThread(() -> {
            if (priceText  != null) {
                priceText.setText("$" + String.format(Locale.US, "%.2f", lastClose));
                priceText.setTextColor(gain ? COLOR_GAIN : COLOR_LOSS);
            }
            if (changeText != null) {
                changeText.setText(changeStr);
                changeText.setTextColor(gain ? COLOR_GAIN : COLOR_LOSS);
            }
            if (tickerText  != null) tickerText.setText(sym);
            if (getActivity() != null) getActivity().setTitle("Chart: " + sym);
            hideCrosshairInfo();
            if (isCandleStick) updateCandleChart(entries);
            else               updateLineChart(entries);
        });
    }

    private void openChartFromInput(String raw) {
        String upper = raw.toUpperCase(Locale.US).trim();
        String cryptoSym = CRYPTO_MAP.get(upper);
        symbol = (cryptoSym != null) ? cryptoSym : upper;
        updateTickerButtonLabel();
        hideCrosshairInfo();
        fetchStockData(symbol, interval);
    }

    private void analyzeWithAI() {
        if (fullCloses.isEmpty()) {
            Toast.makeText(requireContext(), "Load chart data first", Toast.LENGTH_SHORT).show();
            return;
        }
        if (progressAI != null) progressAI.setVisibility(View.VISIBLE);
        if (btnAIAnalysis != null) btnAIAnalysis.setEnabled(false);

        int sz = fullCloses.size();
        int from = Math.max(0, sz - 60);
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < sz; i++) {
            sb.append(String.format(Locale.US, "%.2f", fullCloses.get(i)));
            if (i < sz - 1) sb.append(",");
        }
        String prompt = "Analyze this stock price series for " + symbol +
                ": [" + sb + "]. Give a concise technical analysis with trend, support/resistance levels, and outlook.";
        latestQuery = prompt;

        llmService.streamQuery(prompt, new LLMService.StreamCallback() {
            final StringBuilder full = new StringBuilder();
            @Override public void onToken(String token) {
                full.append(token);
                if (getActivity() != null) getActivity().runOnUiThread(() -> {
                    if (changeText != null) changeText.setText(full.toString());
                });
            }
            @Override public void onComplete(String fullResponse) {
                if (getActivity() != null) getActivity().runOnUiThread(() -> {
                    if (progressAI   != null) progressAI.setVisibility(View.GONE);
                    if (btnAIAnalysis != null) btnAIAnalysis.setEnabled(true);
                });
            }
            @Override public void onError(String error) {
                if (getActivity() != null) getActivity().runOnUiThread(() -> {
                    if (progressAI   != null) progressAI.setVisibility(View.GONE);
                    if (btnAIAnalysis != null) btnAIAnalysis.setEnabled(true);
                    Toast.makeText(requireContext(), "AI error: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
}