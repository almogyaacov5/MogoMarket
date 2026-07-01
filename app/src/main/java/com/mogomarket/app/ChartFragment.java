package com.mogomarket.app;

import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

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

    // מיפוי קריפטו: כינויים נפוצים -> פורמט Finnhub
    private static final Map<String, String> CRYPTO_MAP = new HashMap<>();
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

    private ViewGroup.LayoutParams chartOriginalParams;

    private CandleStickChart candleStickChart;
    private LineChart lineChart;
    private AutoCompleteTextView tickerInput;
    private Button btnLoad, btnTimeFrame, btnToggleChart, btnAIAnalysis;
    private com.google.android.material.button.MaterialButton btnChartRefresh, btnExpandChart, btnExitFullscreen, btnSettings;
    private Button btnChartThemeToggle;

    private View headerSection;
    private View searchSection;
    private View controlsSection;
    private View bottomBar;
    private FrameLayout chartContainer;
    private View chartRootLayout;
    private TextView timeFrameText, tickerText, priceText, changeText, currentPriceDisplay;
    private ProgressBar progressAI;

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
            return s + "  ·  " + n;
        }
    }

    private boolean isCryptoSymbol(String sym) {
        return sym != null && sym.contains(":");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_chart, container, false);

        int currentNightMode = getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        isDarkTheme = (currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES);
        isChartDark = isDarkTheme;

        chartRootLayout    = v.findViewById(R.id.chartRootLayout);
        candleStickChart   = v.findViewById(R.id.stock_chart);
        lineChart          = v.findViewById(R.id.line_chart);
        tickerInput        = v.findViewById(R.id.ticker_input);
        btnLoad            = v.findViewById(R.id.btnLoad);
        btnTimeFrame       = v.findViewById(R.id.btnSelectTimeFrame);
        btnToggleChart     = v.findViewById(R.id.btnToggleChart);
        btnChartRefresh    = v.findViewById(R.id.btnChartRefresh);
        btnAIAnalysis      = v.findViewById(R.id.btnAIAnalysis);
        btnSettings        = v.findViewById(R.id.btnSettings);
        btnChartThemeToggle = v.findViewById(R.id.btnChartThemeToggle);

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

        if (getArguments() != null && getArguments().containsKey("symbol")) {
            symbol = getArguments().getString("symbol", symbol);
            if (tickerInput != null) tickerInput.setText(symbol);
        }

        if (getActivity() != null) getActivity().setTitle("Chart: " + symbol);

        applyTheme();
        setupCandleChartStyle();
        setupLineChartStyle();
        setupAutoComplete();
        setupClickListeners();
        fetchStockData(symbol, interval);
        updateChartThemeToggleLabel();
        return v;
    }

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
        if (btnExpandChart    != null) btnExpandChart.setVisibility(View.GONE);
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
        if (searchSection   != null) searchSection.setVisibility(View.VISIBLE);
        if (controlsSection != null) controlsSection.setVisibility(View.VISIBLE);
        if (bottomBar       != null) bottomBar.setVisibility(View.VISIBLE);
        if (btnExpandChart    != null) btnExpandChart.setVisibility(View.VISIBLE);
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
        if (tickerText      != null) tickerText.setTextColor(textPri);
        if (timeFrameText   != null) timeFrameText.setTextColor(textSec);
        if (tickerInput     != null) {
            tickerInput.setBackgroundColor(cardColor);
            tickerInput.setTextColor(textPri);
            tickerInput.setHintTextColor(textSec);
        }
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
            btnChartThemeToggle.setText(isChartDark ? "\u2600\uFE0F Light Chart" : "\uD83C\uDF19 Dark Chart");
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
        candleStickChart.setHighlightPerTapEnabled(true);
        candleStickChart.setHighlightPerDragEnabled(true);
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
        lineChart.setHighlightPerTapEnabled(true);
        lineChart.setHighlightPerDragEnabled(true);
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
        if (btnLoad != null) {
            btnLoad.setOnClickListener(v -> {
                String u = tickerInput == null ? "" : tickerInput.getText().toString().trim();
                if (!u.isEmpty()) openChartFromInput(u);
                hideKeyboard();
                if (tickerInput != null) tickerInput.clearFocus();
            });
        }

        if (tickerInput != null) {
            tickerInput.setOnEditorActionListener((tv, actionId, event) -> {
                String u = tv.getText().toString().trim();
                if (!u.isEmpty()) openChartFromInput(u);
                hideKeyboard();
                return true;
            });
        }

        if (btnChartRefresh != null) {
            btnChartRefresh.setOnClickListener(v -> {
                fetchStockData(symbol, interval);
                Toast.makeText(requireContext(), "Chart refreshed", Toast.LENGTH_SHORT).show();
            });
        }

        if (btnTimeFrame != null) {
            btnTimeFrame.setOnClickListener(v -> {
                TimeFrameFragment dialog = new TimeFrameFragment();
                dialog.show(getChildFragmentManager(), "timeframe");
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
                applyChartColors();
                if (!currentEntries.isEmpty()) {
                    if (isCandleStick) updateCandleChart(currentEntries);
                    else               updateLineChart(currentEntries);
                }
                updateChartThemeToggleLabel();
            });
        }

        if (btnExpandChart    != null) btnExpandChart.setOnClickListener(v -> enterFullscreen());
        if (btnExitFullscreen != null) btnExitFullscreen.setOnClickListener(v -> exitFullscreen());
    }

    private void setupAutoComplete() {
        if (tickerInput == null) return;

        suggestionAdapter = new ArrayAdapter<StockSuggestion>(
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

        tickerInput.setAdapter(suggestionAdapter);
        tickerInput.setThreshold(1);

        tickerInput.setOnItemClickListener((parent, view, position, id) -> {
            StockSuggestion sel = suggestionAdapter.getItem(position);
            if (sel == null || sel.symbol == null || sel.symbol.trim().isEmpty()) return;
            String picked = isCryptoSymbol(sel.symbol)
                    ? sel.symbol.trim()
                    : sel.symbol.trim().toUpperCase(Locale.US);

            isManualSelection = true;
            tickerInput.setText(picked);
            tickerInput.setSelection(picked.length());
            tickerInput.dismissDropDown();
            clearSuggestions();
            hideKeyboard();
            tickerInput.clearFocus();

            setSymbolAndLoad(picked);

            tickerInput.postDelayed(() -> {
                isManualSelection = false;
                tickerInput.setText("");
            }, 400);
        });

        tickerInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                if (isManualSelection) return;
                scheduleSymbolSearch(s == null ? "" : s.toString().trim());
            }
        });
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
        TradingMarkerView mv = new TradingMarkerView(requireContext());
        mv.setDateLabels(dateLabels); mv.setChartView(candleStickChart);
        candleStickChart.setMarker(mv);
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
        TradingMarkerView mv = new TradingMarkerView(requireContext());
        mv.setDateLabels(dateLabels); mv.setChartView(lineChart);
        lineChart.setMarker(mv);
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

    private String[] intervalToYahoo(String interval) {
        switch (interval) {
            case "1min":   return new String[]{"1m",  "1d"};
            case "5min":   return new String[]{"5m",  "5d"};
            case "15min":  return new String[]{"15m", "1mo"};
            case "30min":  return new String[]{"30m", "1mo"};
            case "60min":
            case "1h":     return new String[]{"1h",  "3mo"};
            case "1week":  return new String[]{"1wk", "5y"};
            case "1month": return new String[]{"1mo", "10y"};
            default:       return new String[]{"1d",  "1y"};
        }
    }

    private SimpleDateFormat dateFormatFor(String yahooInterval) {
        switch (yahooInterval) {
            case "1m": case "5m": case "15m": case "30m": case "1h":
                return new SimpleDateFormat("MM/dd HH:mm", Locale.US);
            case "1mo":
                return new SimpleDateFormat("yyyy-MM", Locale.US);
            default:
                return new SimpleDateFormat("MM/dd/yyyy", Locale.US);
        }
    }

    private void fetchYahooData(String symbol, String interval) {
        String[] p = intervalToYahoo(interval);
        String url = "https://query1.finance.yahoo.com/v8/finance/chart/" + symbol
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
                                Toast.makeText(requireContext(), "No data for: " + symbol, Toast.LENGTH_SHORT).show());
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
                    for (int i = 0; i < size; i++) {
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
                    postChartUpdate(symbol, entries, lc, pc);
                } catch (Exception e) { Log.e("ChartFragment", "Yahoo parse error", e); }
            }
        });
    }

    private String intervalToBinance(String interval) {
        switch (interval) {
            case "1min":   return "1m";
            case "5min":   return "5m";
            case "15min":  return "15m";
            case "30min":  return "30m";
            case "1h":
            case "60min":  return "1h";
            case "1week":  return "1w";
            case "1month": return "1M";
            default:       return "1d";
        }
    }

    private void fetchCryptoData(String symbol, String interval) {
        String pair = symbol.contains(":") ? symbol.substring(symbol.indexOf(':') + 1) : symbol;
        String binanceInterval = intervalToBinance(interval);
        String url = "https://api.binance.com/api/v3/klines?symbol=" + pair
                + "&interval=" + binanceInterval
                + "&limit=365";

        Request req = new Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0").build();

        client.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (getActivity() != null) getActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(), "Crypto error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    if (getActivity() != null) getActivity().runOnUiThread(() ->
                            Toast.makeText(requireContext(), "No crypto data for: " + pair, Toast.LENGTH_SHORT).show());
                    return;
                }
                try {
                    JSONArray klines = new JSONArray(response.body().string());
                    int size = klines.length();
                    if (size == 0) return;

                    SimpleDateFormat sdf;
                    switch (binanceInterval) {
                        case "1m": case "5m": case "15m": case "30m": case "1h":
                            sdf = new SimpleDateFormat("MM/dd HH:mm", Locale.US); break;
                        case "1w": case "1M":
                            sdf = new SimpleDateFormat("yyyy-MM", Locale.US); break;
                        default:
                            sdf = new SimpleDateFormat("MM/dd/yy", Locale.US);
                    }

                    fullCloses.clear();
                    dateLabels.clear();
                    List<CandleEntry> entries = new ArrayList<>();
                    float lc = 0f, pc = 0f;

                    for (int i = 0; i < size; i++) {
                        JSONArray k = klines.getJSONArray(i);
                        long openTime = k.getLong(0);
                        float o = (float) k.getDouble(1);
                        float h = (float) k.getDouble(2);
                        float l = (float) k.getDouble(3);
                        float c = (float) k.getDouble(4);

                        dateLabels.add(sdf.format(new Date(openTime)));
                        fullCloses.add(c);
                        entries.add(new CandleEntry(i, h, l, o, c));
                        pc = lc;
                        lc = c;
                    }

                    if (pc == 0f) pc = lc;
                    postChartUpdate(symbol, entries, lc, pc);

                } catch (Exception e) {
                    Log.e("ChartFragment", "Binance parse error", e);
                }
            }
        });
    }

    private void postChartUpdate(String sym, List<CandleEntry> entries, float lc, float pc) {
        currentEntries.clear();
        currentEntries.addAll(entries);
        lastPrice = lc;
        float change = lc - pc;
        float pct    = (pc != 0f) ? (change / pc) * 100f : 0f;
        final float fC = lc, fCh = change, fP = pct;
        final List<CandleEntry> fin = new ArrayList<>(entries);
        if (getActivity() != null) getActivity().runOnUiThread(() -> {
            if (isCandleStick) updateCandleChart(fin);
            else               updateLineChart(fin);
            if (priceText  != null) { priceText.setText("$" + df.format(fC)); priceText.setTextColor(COLOR_PRIMARY); }
            if (changeText != null) {
                String sign = fCh >= 0 ? "+" : "";
                changeText.setText(sign + String.format(Locale.US, "%.2f", fCh)
                        + " (" + sign + String.format(Locale.US, "%.2f", fP) + "%)");
                changeText.setTextColor(fCh >= 0 ? COLOR_GAIN : COLOR_LOSS);
            }
            if (timeFrameText != null) timeFrameText.setText("Timeframe: " + interval);
            String displaySym = isCryptoSymbol(sym)
                    ? sym.substring(sym.indexOf(':') + 1)
                    : sym;
            if (tickerText != null) tickerText.setText(displaySym + " / USD");
        });
    }

    private void analyzeWithAI() {
        if (fullCloses.isEmpty() || fullCloses.size() < 2) {
            Toast.makeText(requireContext(), "Load chart data first", Toast.LENGTH_SHORT).show();
            return;
        }
        showCustomAIDialog();
    }

    private void showCustomAIDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_ai_chat, null);
        TextView tvHint      = dialogView.findViewById(R.id.tv_hint);
        ProgressBar progress = dialogView.findViewById(R.id.progress_ai);
        TextView tvResponse  = dialogView.findViewById(R.id.tv_response);
        Button btnSend       = dialogView.findViewById(R.id.btn_send);
        android.widget.EditText etQ = dialogView.findViewById(R.id.et_question);
        tvHint.setText("Examples: 'Short-term outlook?' or 'Should I buy now?'");
        AlertDialog dialog = builder.setView(dialogView).setNegativeButton("Cancel", null).create();
        btnSend.setOnClickListener(vv -> {
            String q = etQ.getText().toString().trim();
            if (q.isEmpty()) { Toast.makeText(requireContext(), "Type a question", Toast.LENGTH_SHORT).show(); return; }
            sendQuestionToAI(q, tvResponse, progress, etQ, dialog);
        });
        dialog.show();
    }

    private void sendQuestionToAI(String question, TextView tvResponse, ProgressBar progressBar,
                                  android.widget.EditText etQ, AlertDialog dialog) {
        progressBar.setVisibility(View.VISIBLE);
        etQ.setEnabled(false);
        String ctx = String.format(Locale.US, "Asset: %s | Price: $%.2f | Range: %s | %d pts",
                symbol, lastPrice, interval, fullCloses.size());
        llmService.askQuestion(symbol, question, ctx, fullCloses, new LLMService.AnalysisCallback() {
            @Override public void onAnalysisReceived(String analysis) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    etQ.setEnabled(true); etQ.setText("");
                    tvResponse.setText(analysis); tvResponse.setVisibility(View.VISIBLE);
                });
            }
            @Override public void onError(String error) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    etQ.setEnabled(true);
                    tvResponse.setText("\u274C Error: " + error); tvResponse.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private void openChartFromInput(String userInput) {
        String q = userInput.trim();
        if (q.isEmpty()) return;

        String upper = q.toUpperCase(Locale.US);
        if (CRYPTO_MAP.containsKey(upper)) {
            setSymbolAndLoad(CRYPTO_MAP.get(upper));
            return;
        }

        if (isCryptoSymbol(q)) {
            setSymbolAndLoad(q.trim());
            return;
        }

        if (q.matches("^[A-Za-z0-9./-]{1,20}$") && !q.contains(" ")) {
            setSymbolAndLoad(q.toUpperCase(Locale.US));
            return;
        }

        resolveFirstMatchAndOpen(q);
    }

    private void setSymbolAndLoad(String sym) {
        symbol = sym;
        String displaySym = isCryptoSymbol(sym) ? sym.substring(sym.indexOf(':') + 1) : sym;
        if (tickerText   != null) tickerText.setText(displaySym + " / USD");
        if (getActivity()!= null) getActivity().setTitle("Chart: " + displaySym);
        fetchStockData(symbol, interval);
        hideKeyboard();
    }

    private void scheduleSymbolSearch(String q) {
        if (pendingSearch != null) searchHandler.removeCallbacks(pendingSearch);
        latestQuery = q;
        if (q.length() < 1) { clearSuggestions(); return; }
        final String finalQ = q;
        pendingSearch = () -> fetchSymbolSuggestions(finalQ);
        searchHandler.postDelayed(pendingSearch, SEARCH_DEBOUNCE_MS);
    }

    private void fetchSymbolSuggestions(final String query) {
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name());
            String url = "https://finnhub.io/api/v1/search?q=" + encoded + "&token=" + FINNHUB_KEY;

            client.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
                @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {}
                @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (!response.isSuccessful() || response.body() == null) return;
                    ArrayList<StockSuggestion> list = new ArrayList<>();
                    try {
                        JSONObject json   = new JSONObject(response.body().string());
                        JSONArray  result = json.optJSONArray("result");
                        if (result == null) return;
                        for (int i = 0; i < result.length() && i < 25; i++) {
                            JSONObject o = result.optJSONObject(i);
                            if (o == null) continue;
                            String sym      = o.optString("symbol",      "").trim();
                            String name     = o.optString("description", "");
                            String type     = o.optString("type",        "");

                            if (sym.isEmpty()) continue;

                            boolean isStock  = "Common Stock".equals(type);
                            boolean isCrypto = "Crypto".equals(type);
                            if (!isStock && !isCrypto) continue;

                            if (isStock && sym.contains(".")) continue;
                            if (isCrypto && !sym.startsWith("BINANCE:")) continue;
                            if (isCrypto && !sym.endsWith("USDT")) continue;

                            String exchange = isCrypto ? "Crypto" : "US";
                            list.add(new StockSuggestion(sym, name, exchange));
                        }
                    } catch (Exception ignored) {}
                    if (getActivity() == null) return;
                    getActivity().runOnUiThread(() -> {
                        if (!query.equals(latestQuery) || suggestionAdapter == null) return;
                        suggestionAdapter.clear();
                        suggestionAdapter.addAll(list);
                        suggestionAdapter.notifyDataSetChanged();
                        if (tickerInput != null && tickerInput.hasFocus() && suggestionAdapter.getCount() > 0)
                            tickerInput.showDropDown();
                    });
                }
            });
        } catch (Exception ignored) {}
    }

    private void resolveFirstMatchAndOpen(String query) {
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name());
            String url = "https://finnhub.io/api/v1/search?q=" + encoded + "&token=" + FINNHUB_KEY;
            client.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
                @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {}
                @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (!response.isSuccessful() || response.body() == null) return;
                    try {
                        JSONObject json   = new JSONObject(response.body().string());
                        JSONArray  result = json.optJSONArray("result");
                        if (result == null || result.length() == 0) return;
                        String sym = result.optJSONObject(0).optString("symbol", "").trim();
                        if (sym.isEmpty()) return;
                        String finalSym = isCryptoSymbol(sym) ? sym : sym.toUpperCase(Locale.US);
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> setSymbolAndLoad(finalSym));
                    } catch (Exception ignored) {}
                }
            });
        } catch (Exception ignored) {}
    }

    private void clearSuggestions() {
        if (suggestionAdapter != null) {
            suggestionAdapter.clear();
            suggestionAdapter.notifyDataSetChanged();
        }
        if (tickerInput != null) tickerInput.dismissDropDown();
    }

    @Override
    public void onTimeFrameSelected(String interval) {
        this.interval = interval;
        if (timeFrameText != null) timeFrameText.setText("Timeframe: " + interval);
        fetchStockData(symbol, interval);
    }

    private void hideKeyboard() {
        if (getActivity() == null) return;
        View view = getActivity().getCurrentFocus();
        if (view == null) view = getView();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    public static ChartFragment newInstance(String symbol) {
        ChartFragment f    = new ChartFragment();
        Bundle        args = new Bundle();
        args.putString("symbol", symbol);
        f.setArguments(args);
        return f;
    }
}
