package com.example.chart;

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
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.chart.TradingMarkerView;
import com.github.mikephil.charting.charts.BarLineChartBase;
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
import com.github.mikephil.charting.listener.ChartTouchListener;
import com.github.mikephil.charting.listener.OnChartGestureListener;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ChartFragment extends Fragment implements TimeFrameFragment.TimeFrameListener {

    // ==================== THEME COLORS ====================
    private static final int DARK_BG        = 0xFF0B0F14;
    private static final int DARK_CARD      = 0xFF151C2E;
    private static final int DARK_TEXT_PRI  = 0xFFE6EDF3;
    private static final int DARK_TEXT_SEC  = 0xFF8B98A5;
    private static final int LIGHT_BG       = 0xFFF0F4F8;
    private static final int LIGHT_CARD     = 0xFFFFFFFF;
    private static final int LIGHT_TEXT_PRI = 0xFF1A1D23;
    private static final int LIGHT_TEXT_SEC = 0xFF6B7280;
    private static final int COLOR_PRIMARY   = 0xFF4DA3FF;
    private static final int COLOR_GAIN      = 0xFF00C896;
    private static final int COLOR_LOSS      = 0xFFFF4D4D;
    private static final int COLOR_FILL      = 0xFF1C6DD0;
    // ======================================================

    private boolean isDarkTheme   = true;
    private boolean isFullscreen  = false;

    // UI Views
    private CandleStickChart candleStickChart;
    private LineChart        lineChart;
    private AutoCompleteTextView tickerInput;
    private Button   btnLoad, btnTimeFrame, btnToggleChart, btnAIAnalysis;
    private ImageButton btnChartRefresh, btnExpandChart, btnExitFullscreen;
    private com.google.android.material.button.MaterialButton btnThemeToggle;
    private ImageButton btnSettings; // <-- תיקון: הכרזת המשתנה החסר
    private LinearLayout headerSection, searchSection, controlsSection, bottomBar;
    private FrameLayout chartContainer;
    private View chartRootLayout;
    private TextView timeFrameText, tickerText, priceText, changeText, currentPriceDisplay;
    private ProgressBar progressAI;

    private final OkHttpClient client  = new OkHttpClient();
    private final String API_KEY       = "0518811f0d394fa39842a8024a25c049";

    private String  symbol      = "SPY";
    private String  interval    = "1day";
    private boolean isCandleStick = true;
    private final DecimalFormat df = new DecimalFormat("#.##");
    private String  latestQuery = "";

    private LLMService llmService;
    private final List<CandleEntry> currentEntries = new ArrayList<>();
    private final List<Float>       fullCloses     = new ArrayList<>();
    private float   lastPrice          = 0f;
    private boolean isManualSelection  = false;

    private ArrayAdapter<StockSuggestion> suggestionAdapter;
    private final Handler  searchHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;
    private static final long SEARCH_DEBOUNCE_MS = 50;

    public static class StockSuggestion {
        public final String symbol, name, exchange;
        public StockSuggestion(String symbol, String name, String exchange) {
            this.symbol = symbol; this.name = name; this.exchange = exchange;
        }
        @NonNull @Override
        public String toString() {
            String s = symbol == null ? "" : symbol;
            String n = name   == null ? "" : name;
            String ex= exchange==null ? "" : exchange;
            if (n.isEmpty() && ex.isEmpty()) return s;
            if (ex.isEmpty()) return s + " \u2014 " + n;
            return s + " \u2014 " + n + " (" + ex + ")";
        }
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_chart, container, false);

        chartRootLayout    = v.findViewById(R.id.chartRootLayout);
        candleStickChart   = v.findViewById(R.id.stock_chart);
        lineChart          = v.findViewById(R.id.line_chart);
        tickerInput        = v.findViewById(R.id.ticker_input);
        btnLoad            = v.findViewById(R.id.btnLoad);
        btnTimeFrame       = v.findViewById(R.id.btnSelectTimeFrame);
        btnToggleChart     = v.findViewById(R.id.btnToggleChart);
        btnChartRefresh    = v.findViewById(R.id.btnChartRefresh);
        btnAIAnalysis      = v.findViewById(R.id.btnAIAnalysis);
        btnThemeToggle     = v.findViewById(R.id.btnThemeToggle);
        btnSettings        = v.findViewById(R.id.btnSettings);
        if (btnSettings != null) {
            btnSettings.setOnClickListener(vv -> {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).openSettings();
                }
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
        if (progressAI      != null) progressAI.setVisibility(View.GONE);
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
        return v;
    }

    // ========================= SMART PINCH ZOOM =========================

    private static class SmartPinchTouchListener implements View.OnTouchListener {

        private final BarLineChartBase<?> chart;

        private static final int STATE_IDLE  = 0;
        private static final int STATE_PINCH = 1;
        private static final int STATE_DRAG  = 2;
        private int state = STATE_IDLE;

        private float pinchStartX0, pinchStartY0;
        private float pinchStartX1, pinchStartY1;
        private boolean axisDetermined = false;
        private boolean isAxisX = true;

        private float dragStartX, dragStartY;

        private static final float AXIS_THRESHOLD = 10f;

        SmartPinchTouchListener(BarLineChartBase<?> chart) {
            this.chart = chart;
            chart.setTouchEnabled(false);
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            int pointerCount = event.getPointerCount();
            int action = event.getActionMasked();

            switch (action) {

                case MotionEvent.ACTION_DOWN:
                    state = STATE_DRAG;
                    dragStartX = event.getX();
                    dragStartY = event.getY();
                    return true;

                case MotionEvent.ACTION_POINTER_DOWN:
                    if (pointerCount == 2) {
                        state = STATE_PINCH;
                        axisDetermined = false;
                        pinchStartX0 = event.getX(0);
                        pinchStartY0 = event.getY(0);
                        pinchStartX1 = event.getX(1);
                        pinchStartY1 = event.getY(1);
                    }
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if (state == STATE_PINCH && pointerCount == 2) {
                        handlePinchMove(event);
                    } else if (state == STATE_DRAG && pointerCount == 1) {
                        handleDragMove(event);
                        dragStartX = event.getX();
                        dragStartY = event.getY();
                    }
                    return true;

                case MotionEvent.ACTION_POINTER_UP:
                    if (pointerCount <= 2) {
                        state = STATE_DRAG;
                        axisDetermined = false;
                        if (event.getActionIndex() == 0 && pointerCount > 1) {
                            dragStartX = event.getX(1);
                            dragStartY = event.getY(1);
                        } else {
                            dragStartX = event.getX(0);
                            dragStartY = event.getY(0);
                        }
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    state = STATE_IDLE;
                    axisDetermined = false;
                    return true;
            }
            return false;
        }

        private void handlePinchMove(MotionEvent event) {
            float curX0 = event.getX(0), curY0 = event.getY(0);
            float curX1 = event.getX(1), curY1 = event.getY(1);

            float curDistX  = Math.abs(curX1 - curX0);
            float curDistY  = Math.abs(curY1 - curY0);
            float initDistX = Math.abs(pinchStartX1 - pinchStartX0);
            float initDistY = Math.abs(pinchStartY1 - pinchStartY0);

            if (!axisDetermined) {
                float spreadDX = Math.abs(curDistX - initDistX);
                float spreadDY = Math.abs(curDistY - initDistY);
                if (spreadDX + spreadDY < AXIS_THRESHOLD) return;
                isAxisX = spreadDX >= spreadDY;
                axisDetermined = true;
            }

            if (isAxisX) {
                if (initDistX < 1f) return;
                float scaleX = curDistX / initDistX;
                chart.zoom(scaleX, 1f, (curX0 + curX1) / 2f, (curY0 + curY1) / 2f);
            } else {
                if (initDistY < 1f) return;
                float scaleY = curDistY / initDistY;
                chart.zoom(1f, scaleY, (curX0 + curX1) / 2f, (curY0 + curY1) / 2f);
            }

            pinchStartX0 = curX0; pinchStartY0 = curY0;
            pinchStartX1 = curX1; pinchStartY1 = curY1;
        }

        private void handleDragMove(MotionEvent event) {
            float dx = event.getX() - dragStartX;

            Matrix matrix = chart.getViewPortHandler().getMatrixTouch();
            float[] vals = new float[9];
            matrix.getValues(vals);

            float scaleX        = vals[Matrix.MSCALE_X];
            float transX        = vals[Matrix.MTRANS_X];
            float offsetLeft    = chart.getViewPortHandler().offsetLeft();
            float offsetRight   = chart.getViewPortHandler().offsetRight();
            float chartWidth    = chart.getViewPortHandler().getChartWidth();
            float contentWidth  = chartWidth - offsetLeft - offsetRight;

            float maxTransX = offsetLeft;
            float minTransX = -(contentWidth * scaleX - contentWidth) + offsetLeft;

            float newTransX = transX + dx;
            newTransX = Math.min(maxTransX, Math.max(minTransX, newTransX));

            vals[Matrix.MTRANS_X] = newTransX;
            matrix.setValues(vals);
            chart.getViewPortHandler().refresh(matrix, chart, true);
        }
    }

    // ========================= THEME =========================

    private void applyTheme() {
        int bgColor   = isDarkTheme ? DARK_BG       : LIGHT_BG;
        int cardColor = isDarkTheme ? DARK_CARD      : LIGHT_CARD;
        int textPri   = isDarkTheme ? DARK_TEXT_PRI  : LIGHT_TEXT_PRI;
        int textSec   = isDarkTheme ? DARK_TEXT_SEC  : LIGHT_TEXT_SEC;

        if (chartRootLayout != null) chartRootLayout.setBackgroundColor(bgColor);
        if (headerSection   != null) headerSection.setBackgroundColor(cardColor);
        if (controlsSection != null) controlsSection.setBackgroundColor(cardColor);
        if (bottomBar       != null) bottomBar.setBackgroundColor(cardColor);
        if (chartContainer  != null) chartContainer.setBackgroundColor(cardColor);
        if (tickerText      != null) tickerText.setTextColor(textPri);
        if (timeFrameText   != null) timeFrameText.setTextColor(textSec);
        if (tickerInput     != null) {
            tickerInput.setBackgroundColor(cardColor);
            tickerInput.setTextColor(textPri);
            tickerInput.setHintTextColor(textSec);
        }
        if (candleStickChart != null) {
            candleStickChart.setBackgroundColor(cardColor);
            candleStickChart.getXAxis().setTextColor(textSec);
            candleStickChart.getAxisLeft().setTextColor(textSec);
            candleStickChart.invalidate();
        }
        if (lineChart != null) {
            lineChart.setBackgroundColor(cardColor);
            lineChart.getXAxis().setTextColor(textSec);
            lineChart.getAxisLeft().setTextColor(textSec);
            lineChart.invalidate();
        }
        if (btnThemeToggle != null) {
            btnThemeToggle.setIconResource(
                    isDarkTheme ? R.drawable.ic_nav_theme_dark : R.drawable.ic_nav_theme_light);
            btnThemeToggle.setIconTint(android.content.res.ColorStateList.valueOf(textSec));
        }
    }

    // ========================= FULLSCREEN =========================

    private void toggleFullscreen() {
        isFullscreen = !isFullscreen;
        if (isFullscreen) {
            animateVisibility(headerSection,   false);
            animateVisibility(searchSection,   false);
            animateVisibility(controlsSection, false);
            animateVisibility(bottomBar,       false);
            if (chartContainer != null) {
                ViewGroup.LayoutParams p = chartContainer.getLayoutParams();
                if (p instanceof LinearLayout.LayoutParams) {
                    ((LinearLayout.LayoutParams) p).setMargins(0,0,0,0);
                    ((LinearLayout.LayoutParams) p).weight = 1;
                }
                chartContainer.setLayoutParams(p);
                chartContainer.setPadding(0,0,0,0);
            }
            if (btnExitFullscreen != null) btnExitFullscreen.setVisibility(View.VISIBLE);
        } else {
            animateVisibility(headerSection,   true);
            animateVisibility(searchSection,   true);
            animateVisibility(controlsSection, true);
            animateVisibility(bottomBar,       true);
            if (chartContainer != null) {
                ViewGroup.LayoutParams p = chartContainer.getLayoutParams();
                if (p instanceof LinearLayout.LayoutParams) {
                    int m = dpToPx(12);
                    ((LinearLayout.LayoutParams) p).setMargins(m, dpToPx(8), m, 0);
                }
                chartContainer.setLayoutParams(p);
                chartContainer.setPadding(dpToPx(4),dpToPx(4),dpToPx(4),dpToPx(4));
            }
            if (btnExitFullscreen != null) btnExitFullscreen.setVisibility(View.GONE);
        }
    }

    private void animateVisibility(View view, boolean show) {
        if (view == null) return;
        if (show) {
            view.setVisibility(View.VISIBLE);
            view.setAlpha(0f);
            view.animate().alpha(1f).setDuration(220).start();
        } else {
            view.animate().alpha(0f).setDuration(180)
                    .withEndAction(() -> view.setVisibility(View.GONE)).start();
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    // ========================= CHART STYLING =========================

    private void setupCandleChartStyle() {
        if (candleStickChart == null) return;
        int cardColor = isDarkTheme ? DARK_CARD : LIGHT_CARD;
        int textSec   = isDarkTheme ? DARK_TEXT_SEC : LIGHT_TEXT_SEC;

        candleStickChart.setBackgroundColor(cardColor);
        candleStickChart.setDrawGridBackground(false);
        candleStickChart.getDescription().setEnabled(false);
        candleStickChart.getLegend().setEnabled(false);
        candleStickChart.setTouchEnabled(false);
        candleStickChart.setOnTouchListener(new SmartPinchTouchListener(candleStickChart));

        candleStickChart.setHighlightPerTapEnabled(true);
        candleStickChart.setHighlightPerDragEnabled(true);

        XAxis xAxis = candleStickChart.getXAxis();
        xAxis.setDrawGridLines(false);
        xAxis.setDrawAxisLine(false);
        xAxis.setTextColor(textSec);
        xAxis.setTextSize(10f);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setLabelCount(5, true);

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
        int cardColor = isDarkTheme ? DARK_CARD : LIGHT_CARD;
        int textSec   = isDarkTheme ? DARK_TEXT_SEC : LIGHT_TEXT_SEC;

        lineChart.setBackgroundColor(cardColor);
        lineChart.setDrawGridBackground(false);
        lineChart.getDescription().setEnabled(false);
        lineChart.getLegend().setEnabled(false);
        lineChart.setTouchEnabled(false);
        lineChart.setOnTouchListener(new SmartPinchTouchListener(lineChart));

        lineChart.setHighlightPerTapEnabled(true);
        lineChart.setHighlightPerDragEnabled(true);

        XAxis xAxis = lineChart.getXAxis();
        xAxis.setDrawGridLines(false);
        xAxis.setDrawAxisLine(false);
        xAxis.setTextColor(textSec);
        xAxis.setTextSize(10f);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setLabelCount(5, true);

        YAxis leftAxis = lineChart.getAxisLeft();
        leftAxis.setDrawGridLines(false);
        leftAxis.setDrawAxisLine(false);
        leftAxis.setTextColor(textSec);
        leftAxis.setTextSize(10f);
        leftAxis.setLabelCount(5, false);

        lineChart.getAxisRight().setEnabled(false);
    }

    // ========================= CLICK LISTENERS =========================

    private void setupClickListeners() {
        if (btnLoad != null) {
            btnLoad.setOnClickListener(v -> {
                String u = tickerInput == null ? "" : tickerInput.getText().toString().trim();
                if (!u.isEmpty()) openChartFromInput(u);
                hideKeyboard();
                if (tickerInput != null) tickerInput.clearFocus();
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
        if (btnAIAnalysis != null) {
            btnAIAnalysis.setOnClickListener(v -> analyzeWithAI());
        }
        if (btnThemeToggle != null) {
            btnThemeToggle.setOnClickListener(v -> {
                isDarkTheme = !isDarkTheme;
                applyTheme();
                setupCandleChartStyle();
                setupLineChartStyle();
                if (!currentEntries.isEmpty()) {
                    if (isCandleStick) updateCandleChart(new ArrayList<>(currentEntries));
                    else               updateLineChart(new ArrayList<>(currentEntries));
                }
                Toast.makeText(requireContext(),
                        isDarkTheme ? "\uD83C\uDF11 \u05de\u05e6\u05d1 \u05db\u05d4\u05d4" : "\u2600\uFE0F \u05de\u05e6\u05d1 \u05d1\u05d4\u05d9\u05e8",
                        Toast.LENGTH_SHORT).show();
            });
        }
        if (btnExpandChart != null) btnExpandChart.setOnClickListener(v -> toggleFullscreen());
        if (btnExitFullscreen != null) btnExitFullscreen.setOnClickListener(v -> toggleFullscreen());
    }

    // ========================= AUTO COMPLETE =========================

    private void setupAutoComplete() {
        if (tickerInput == null) return;
        suggestionAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, new ArrayList<>());
        tickerInput.setAdapter(suggestionAdapter);
        tickerInput.setThreshold(1);

        tickerInput.setOnItemClickListener((parent, view, position, id) -> {
            StockSuggestion sel = suggestionAdapter.getItem(position);
            if (sel == null || sel.symbol == null || sel.symbol.trim().isEmpty()) return;
            String picked = sel.symbol.trim().toUpperCase(Locale.US);
            isManualSelection = true;
            tickerInput.setText(picked);
            tickerInput.setSelection(picked.length());
            tickerInput.dismissDropDown();
            setSymbolAndLoad(picked);
            tickerInput.setText("");
            tickerInput.clearFocus();
            hideKeyboard();
            clearSuggestions();
            tickerInput.postDelayed(() -> isManualSelection = false, 300);
        });

        tickerInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isManualSelection) return;
                scheduleSymbolSearch(s == null ? "" : s.toString().trim());
            }
        });
    }

    // ========================= CHART DATA =========================

    private void updateCandleChart(List<CandleEntry> entries) {
        CandleDataSet dataSet = new CandleDataSet(entries, "");
        dataSet.setIncreasingColor(COLOR_GAIN);
        dataSet.setDecreasingColor(COLOR_LOSS);
        dataSet.setIncreasingPaintStyle(Paint.Style.FILL);
        dataSet.setDecreasingPaintStyle(Paint.Style.FILL);
        dataSet.setShadowColor(isDarkTheme ? DARK_TEXT_SEC : LIGHT_TEXT_SEC);
        dataSet.setShadowWidth(1f);
        dataSet.setDrawValues(false);
        dataSet.setHighlightEnabled(true);
        dataSet.setHighLightColor(COLOR_PRIMARY);
        dataSet.enableDashedHighlightLine(10f, 5f, 0f);
        candleStickChart.setData(new CandleData(dataSet));
        TradingMarkerView candleMarker = new TradingMarkerView(requireContext());
        candleMarker.setChartView(candleStickChart);
        candleStickChart.setMarker(candleMarker);
        candleStickChart.animateX(400);
        candleStickChart.invalidate();
    }

    private void updateLineChart(List<CandleEntry> candleEntries) {
        List<Entry> lineEntries = new ArrayList<>();
        for (CandleEntry c : candleEntries)
            lineEntries.add(new Entry(c.getX(), c.getClose()));
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
        ds.setFillAlpha(isDarkTheme ? 90 : 50);
        lineChart.setData(new LineData(ds));
        TradingMarkerView lineMarker = new TradingMarkerView(requireContext());
        lineMarker.setChartView(lineChart);
        lineChart.setMarker(lineMarker);
        lineChart.animateX(400);
        lineChart.invalidate();
    }

    // ========================= DATA FETCHING =========================

    private void fetchStockData(String symbol, String interval) {
        String url = "https://api.twelvedata.com/time_series?symbol=" + symbol
                + "&interval=" + interval + "&apikey=" + API_KEY + "&outputsize=252";
        client.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {}
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) return;
                try {
                    JSONObject json   = new JSONObject(response.body().string());
                    JSONArray  series = json.getJSONArray("values");
                    fullCloses.clear();
                    List<CandleEntry> candleEntries = new ArrayList<>();
                    float lastClose = 0, prevClose = 0;
                    if (series.length() > 0)
                        lastClose = Float.parseFloat(series.getJSONObject(0).getString("close"));
                    if (series.length() > 1)
                        prevClose = Float.parseFloat(series.getJSONObject(1).getString("close"));
                    lastPrice = lastClose;
                    for (int i = 0; i < series.length(); i++)
                        fullCloses.add(Float.parseFloat(series.getJSONObject(i).getString("close")));
                    if (fullCloses.isEmpty()) return;
                    int chartIndex = 0;
                    int startIndex = Math.max(0, series.length() - 252);
                    for (int i = series.length() - 1; i >= startIndex; i--) {
                        JSONObject data = series.getJSONObject(i);
                        candleEntries.add(new CandleEntry(chartIndex++,
                                Float.parseFloat(data.getString("high")),
                                Float.parseFloat(data.getString("low")),
                                Float.parseFloat(data.getString("open")),
                                Float.parseFloat(data.getString("close"))));
                    }
                    currentEntries.clear();
                    currentEntries.addAll(candleEntries);
                    float change    = lastClose - prevClose;
                    float changePct = (prevClose != 0) ? (change / prevClose) * 100 : 0;
                    final float fClose = lastClose, fChng = change, fPct = changePct;
                    final String sym = symbol;
                    final List<CandleEntry> fin = new ArrayList<>(candleEntries);
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (isCandleStick) updateCandleChart(fin);
                            else               updateLineChart(fin);
                            if (priceText != null) { priceText.setText("$" + df.format(fClose)); priceText.setTextColor(COLOR_PRIMARY); }
                            if (changeText != null) {
                                String sign = fChng >= 0 ? "+" : "";
                                changeText.setText(sign + String.format(Locale.US, "%.2f", fChng)
                                        + " (" + sign + String.format(Locale.US, "%.2f", fPct) + "%)");
                                changeText.setTextColor(fChng >= 0 ? COLOR_GAIN : COLOR_LOSS);
                            }
                            if (timeFrameText != null) timeFrameText.setText("Timeframe: " + interval);
                            if (tickerText    != null) tickerText.setText(sym + " / USD");
                        });
                    }
                } catch (Exception e) { e.printStackTrace(); }
            }
        });
    }

    // ========================= AI =========================

    private void analyzeWithAI() {
        if (fullCloses.isEmpty() || fullCloses.size() < 2) {
            Toast.makeText(requireContext(), "\u05d8\u05e2\u05df \u05e0\u05ea\u05d5\u05e0\u05d9 \u05d2\u05e8\u05e3 \u05e7\u05d5\u05d3\u05dd", Toast.LENGTH_SHORT).show(); return;
        }
        showCustomAIDialog();
    }

    private void showCustomAIDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_ai_chat, null);
        TextView    tvHint     = dialogView.findViewById(R.id.tv_hint);
        ProgressBar progress   = dialogView.findViewById(R.id.progress_ai);
        TextView    tvResponse = dialogView.findViewById(R.id.tv_response);
        Button      btnSend    = dialogView.findViewById(R.id.btn_send);
        android.widget.EditText etQuestion = dialogView.findViewById(R.id.et_question);
        tvHint.setText("\u05d3\u05d5\u05d2\u05de\u05d0\u05d5\u05ea: '\u05de\u05d4 \u05d3\u05e2\u05ea\u05da \u05e2\u05dc \u05d4\u05e9\u05e7\u05e2\u05d4 \u05e7\u05e6\u05e8\u05ea \u05d8\u05d5\u05d5\u05d7?' \u05d0\u05d5 '\u05d4\u05d0\u05dd \u05dc\u05e7\u05e0\u05d5\u05ea \u05e2\u05db\u05e9\u05d9\u05d5?'");
        AlertDialog dialog = builder.setView(dialogView).setNegativeButton("\u05d1\u05d9\u05d8\u05d5\u05dc", null).create();
        btnSend.setOnClickListener(vv -> {
            String question = etQuestion.getText().toString().trim();
            if (question.isEmpty()) { Toast.makeText(requireContext(), "\u05d4\u05e7\u05dc\u05d3 \u05e9\u05d0\u05dc\u05d4", Toast.LENGTH_SHORT).show(); return; }
            sendQuestionToAI(question, tvResponse, progress, etQuestion, dialog);
        });
        dialog.show();
        etQuestion.requestFocus();
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    }

    private void sendQuestionToAI(String question, TextView tvResponse, ProgressBar progressBar,
                                  android.widget.EditText etQuestion, AlertDialog dialog) {
        progressBar.setVisibility(View.VISIBLE);
        etQuestion.setEnabled(false);
        String context = String.format(Locale.US, "\u05de\u05e0\u05d9\u05d4: %s | \u05de\u05d7\u05d9\u05e8: $%.2f | \u05d8\u05d5\u05d5\u05d7: %s | %d \u05e0\u05e7\u05d5\u05d3\u05d5\u05ea",
                symbol, lastPrice, interval, fullCloses.size());
        llmService.askQuestion(symbol, question, context, fullCloses, new LLMService.AnalysisCallback() {
            @Override public void onAnalysisReceived(String analysis) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    etQuestion.setEnabled(true); etQuestion.setText("");
                    tvResponse.setText(analysis); tvResponse.setVisibility(View.VISIBLE);
                });
            }
            @Override public void onError(String error) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    etQuestion.setEnabled(true);
                    tvResponse.setText("\u274c \u05e9\u05d2\u05d9\u05d0\u05d4: " + error);
                    tvResponse.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    // ========================= SYMBOL SEARCH =========================

    private void openChartFromInput(String userInput) {
        String q = userInput.trim();
        if (q.isEmpty()) return;
        if (q.matches("^[A-Za-z0-9./-]{1,20}$") && !q.contains(" ")) { setSymbolAndLoad(q.toUpperCase(Locale.US)); return; }
        resolveFirstMatchAndOpen(q);
    }

    private void setSymbolAndLoad(String sym) {
        symbol = sym;
        if (tickerText != null) tickerText.setText(symbol + " / USD");
        if (getActivity() != null) getActivity().setTitle("Chart: " + symbol);
        fetchStockData(symbol, interval);
        hideKeyboard();
    }

    private void resolveFirstMatchAndOpen(String query) {
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name());
            String url = "https://api.twelvedata.com/symbol_search?symbol=" + encoded
                    + "&outputsize=1&country=US&exchange=NYSE&apikey=" + API_KEY;
            client.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
                @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {}
                @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (!response.isSuccessful() || response.body() == null) return;
                    try {
                        JSONObject json = new JSONObject(response.body().string());
                        JSONArray data  = json.optJSONArray("data");
                        if (data == null || data.length() == 0) return;
                        String sym = data.optJSONObject(0).optString("symbol", "").trim();
                        if (sym.isEmpty()) return;
                        String finalSym = sym.toUpperCase(Locale.US);
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {
                            if (tickerInput != null) { tickerInput.setText(finalSym); tickerInput.setSelection(finalSym.length()); }
                            setSymbolAndLoad(finalSym);
                        });
                    } catch (Exception ignored) {}
                }
            });
        } catch (Exception ignored) {}
    }

    private void scheduleSymbolSearch(String q) {
        if (q.length() == 1 && !Character.isLetterOrDigit(q.charAt(0))) { clearSuggestions(); return; }
        if (pendingSearch != null) searchHandler.removeCallbacks(pendingSearch);
        q = q.trim(); latestQuery = q;
        if (q.length() < 1 || q.length() > 50) { clearSuggestions(); return; }
        final String finalQ = q;
        pendingSearch = () -> fetchSymbolSuggestions(finalQ);
        searchHandler.postDelayed(pendingSearch, SEARCH_DEBOUNCE_MS);
    }

    private void clearSuggestions() {
        if (suggestionAdapter != null) { suggestionAdapter.clear(); suggestionAdapter.notifyDataSetChanged(); }
        if (tickerInput != null) tickerInput.dismissDropDown();
    }

    private void fetchSymbolSuggestions(String query) {
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name());
            if (suggestionAdapter != null) { suggestionAdapter.clear(); suggestionAdapter.notifyDataSetChanged(); }
            final java.util.HashSet<String> addedSymbols = new java.util.HashSet<>();
            fetchSymbolSuggestionsOneExchange(encoded, query, "NYSE",   addedSymbols);
            fetchSymbolSuggestionsOneExchange(encoded, query, "NASDAQ", addedSymbols);
        } catch (Exception ignored) {}
    }

    private void fetchSymbolSuggestionsOneExchange(String encoded, String originalQuery,
                                                   String exchange, java.util.HashSet<String> addedSymbols) {
        String url = "https://api.twelvedata.com/symbol_search?symbol=" + encoded
                + "&outputsize=20&country=US&exchange=" + exchange + "&apikey=" + API_KEY;
        client.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {}
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) return;
                ArrayList<StockSuggestion> list = new ArrayList<>();
                try {
                    JSONObject json = new JSONObject(response.body().string());
                    JSONArray data  = json.optJSONArray("data");
                    if (data == null) return;
                    for (int i = 0; i < data.length(); i++) {
                        JSONObject o = data.optJSONObject(i);
                        if (o == null) continue;
                        String sym = o.optString("symbol", "").trim();
                        if (sym.isEmpty()) continue;
                        String name = o.optString("instrument_name", "");
                        String ex   = o.optString("exchange", "");
                        if (!"NYSE".equalsIgnoreCase(ex) && !"NASDAQ".equalsIgnoreCase(ex)) continue;
                        synchronized (addedSymbols) {
                            if (addedSymbols.contains(sym)) continue;
                            addedSymbols.add(sym);
                        }
                        list.add(new StockSuggestion(sym, name, ex));
                    }
                } catch (Exception ignored) {}
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    if (!originalQuery.equals(latestQuery) || suggestionAdapter == null) return;
                    suggestionAdapter.addAll(list);
                    suggestionAdapter.notifyDataSetChanged();
                    if (tickerInput == null) return;
                    CharSequence cs = tickerInput.getText();
                    if (cs == null) return;
                    if (tickerInput.enoughToFilter()) {
                        suggestionAdapter.getFilter().filter(cs, count -> {
                            if (count > 0) tickerInput.showDropDown();
                            else           tickerInput.dismissDropDown();
                        });
                    }
                    if (tickerInput.hasFocus() && suggestionAdapter.getCount() > 0)
                        tickerInput.post(tickerInput::showDropDown);
                });
            }
        });
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
            InputMethodManager imm = (InputMethodManager)
                    getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    public static ChartFragment newInstance(String symbol) {
        ChartFragment f = new ChartFragment();
        Bundle args = new Bundle();
        args.putString("symbol", symbol);
        f.setArguments(args);
        return f;
    }
}
