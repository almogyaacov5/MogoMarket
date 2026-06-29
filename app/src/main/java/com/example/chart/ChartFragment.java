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
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.chart.TradingMarkerView;
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
import java.util.List;
import java.util.Locale;

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

    private boolean isDarkTheme = true;
    private boolean isFullscreen = false;

    private CandleStickChart candleStickChart;
    private LineChart lineChart;
    private AutoCompleteTextView tickerInput;
    private Button btnLoad, btnTimeFrame, btnToggleChart, btnAIAnalysis;
    private com.google.android.material.button.MaterialButton btnChartRefresh, btnExpandChart, btnExitFullscreen, btnThemeToggle, btnSettings;

    private View headerSection;
    private View searchSection;
    private View controlsSection;
    private View bottomBar;
    private FrameLayout chartContainer;
    private View chartRootLayout;
    private TextView timeFrameText, tickerText, priceText, changeText, currentPriceDisplay;
    private ProgressBar progressAI;

    private final OkHttpClient client = new OkHttpClient();
    private final String API_KEY = "d918pn9r01qr1uqui560d918pn9r01qr1uqui56g";

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
    private static final long SEARCH_DEBOUNCE_MS = 50;

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
            String n = name == null ? "" : name;
            String ex = exchange == null ? "" : exchange;
            if (n.isEmpty() && ex.isEmpty()) return s;
            if (ex.isEmpty()) return s + " — " + n;
            return s + " — " + n + " (" + ex + ")";
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_chart, container, false);

        chartRootLayout = v.findViewById(R.id.chartRootLayout);
        candleStickChart = v.findViewById(R.id.stock_chart);
        lineChart = v.findViewById(R.id.line_chart);
        tickerInput = v.findViewById(R.id.ticker_input);
        btnLoad = v.findViewById(R.id.btnLoad);
        btnTimeFrame = v.findViewById(R.id.btnSelectTimeFrame);
        btnToggleChart = v.findViewById(R.id.btnToggleChart);
        btnChartRefresh = v.findViewById(R.id.btnChartRefresh);
        btnAIAnalysis = v.findViewById(R.id.btnAIAnalysis);
        btnThemeToggle = v.findViewById(R.id.btnThemeToggle);
        btnSettings = v.findViewById(R.id.btnSettings);
        if (btnSettings != null) {
            btnSettings.setOnClickListener(vv -> {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).openSettings();
                }
            });
        }
        btnExpandChart = v.findViewById(R.id.btnExpandChart);
        btnExitFullscreen = v.findViewById(R.id.btnExitFullscreen);
        progressAI = v.findViewById(R.id.progressAI);
        priceText = v.findViewById(R.id.priceText);
        changeText = v.findViewById(R.id.changeText);
        timeFrameText = v.findViewById(R.id.timeFrameText);
        tickerText = v.findViewById(R.id.tickerText);
        currentPriceDisplay = v.findViewById(R.id.currentPriceDisplay);
        headerSection = v.findViewById(R.id.headerSection);
        searchSection = v.findViewById(R.id.searchSection);
        controlsSection = v.findViewById(R.id.controlsSection);
        bottomBar = v.findViewById(R.id.bottomBar);
        chartContainer = v.findViewById(R.id.chartContainer);

        llmService = new LLMService();
        if (progressAI != null) progressAI.setVisibility(View.GONE);
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

    private void applyTheme() {
        int bgColor = isDarkTheme ? DARK_BG : LIGHT_BG;
        int cardColor = isDarkTheme ? DARK_CARD : LIGHT_CARD;
        int textPri = isDarkTheme ? DARK_TEXT_PRI : LIGHT_TEXT_PRI;
        int textSec = isDarkTheme ? DARK_TEXT_SEC : LIGHT_TEXT_SEC;

        if (chartRootLayout != null) chartRootLayout.setBackgroundColor(bgColor);
        if (headerSection != null) headerSection.setBackgroundColor(cardColor);
        if (controlsSection != null) controlsSection.setBackgroundColor(cardColor);
        if (bottomBar != null) bottomBar.setBackgroundColor(cardColor);
        if (chartContainer != null) chartContainer.setBackgroundColor(cardColor);
        if (tickerText != null) tickerText.setTextColor(textPri);
        if (timeFrameText != null) timeFrameText.setTextColor(textSec);
        if (tickerInput != null) {
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
    }

    private void setupCandleChartStyle() {
        if (candleStickChart == null) return;
        int cardColor = isDarkTheme ? DARK_CARD : LIGHT_CARD;
        int textSec = isDarkTheme ? DARK_TEXT_SEC : LIGHT_TEXT_SEC;

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
            @Override
            public String getFormattedValue(float value) {
                int index = (int) value;
                if (index >= 0 && index < dateLabels.size()) return dateLabels.get(index);
                return "";
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
        int cardColor = isDarkTheme ? DARK_CARD : LIGHT_CARD;
        int textSec = isDarkTheme ? DARK_TEXT_SEC : LIGHT_TEXT_SEC;

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
            @Override
            public String getFormattedValue(float value) {
                int index = (int) value;
                if (index >= 0 && index < dateLabels.size()) return dateLabels.get(index);
                return "";
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
    }

    private void setupAutoComplete() {
        if (tickerInput == null) return;
        suggestionAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, new ArrayList<>());
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
        candleMarker.setDateLabels(dateLabels);
        candleMarker.setChartView(candleStickChart);
        candleStickChart.setMarker(candleMarker);
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
        ds.setFillAlpha(isDarkTheme ? 90 : 50);
        lineChart.setData(new LineData(ds));
        TradingMarkerView lineMarker = new TradingMarkerView(requireContext());
        lineMarker.setDateLabels(dateLabels);
        lineMarker.setChartView(lineChart);
        lineChart.setMarker(lineMarker);
        lineChart.animateX(400);
        lineChart.invalidate();
    }

    private String intervalToFinnhubResolution(String interval) {
        switch (interval) {
            case "1min": return "1";
            case "5min": return "5";
            case "15min": return "15";
            case "30min": return "30";
            case "60min":
            case "1h": return "60";
            case "1day": return "D";
            case "1week": return "W";
            case "1month": return "M";
            default: return "D";
        }
    }

    private long calcFromTime(String resolution, long toTime) {
        switch (resolution) {
            case "1": return toTime - (7L * 24L * 3600L);
            case "5": return toTime - (14L * 24L * 3600L);
            case "15": return toTime - (30L * 24L * 3600L);
            case "30": return toTime - (60L * 24L * 3600L);
            case "60": return toTime - (90L * 24L * 3600L);
            case "W": return toTime - (5L * 365L * 24L * 3600L);
            case "M": return toTime - (10L * 365L * 24L * 3600L);
            default: return toTime - (400L * 24L * 3600L);
        }
    }

    private SimpleDateFormat dateFormatFor(String resolution) {
        switch (resolution) {
            case "1":
            case "5":
            case "15":
            case "30":
            case "60":
                return new SimpleDateFormat("MM/dd HH:mm", Locale.US);
            case "M":
                return new SimpleDateFormat("yyyy-MM", Locale.US);
            default:
                return new SimpleDateFormat("MM-dd", Locale.US);
        }
    }

    private void fetchStockData(String symbol, String interval) {
        String resolution = intervalToFinnhubResolution(interval);
        long toTime = System.currentTimeMillis() / 1000L;
        long fromTime = calcFromTime(resolution, toTime);

        String url = "https://finnhub.io/api/v1/stock/candle?symbol=" + symbol
                + "&resolution=" + resolution
                + "&from=" + fromTime
                + "&to=" + toTime
                + "&token=" + API_KEY;

        Log.d("ChartFragment", "Finnhub URL: " + url);

        client.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (getActivity() != null) getActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(), "שגיאת רשת: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) return;
                try {
                    String body = response.body().string();
                    Log.d("ChartFragment", "Finnhub response: " + body);
                    JSONObject json = new JSONObject(body);

                    String status = json.optString("s", "");
                    if (!"ok".equals(status)) {
                        if (getActivity() != null) getActivity().runOnUiThread(() ->
                                Toast.makeText(requireContext(), "אין נתונים עבור: " + symbol + " (" + resolution + ")", Toast.LENGTH_SHORT).show());
                        return;
                    }

                    JSONArray opens = json.getJSONArray("o");
                    JSONArray highs = json.getJSONArray("h");
                    JSONArray lows = json.getJSONArray("l");
                    JSONArray closes = json.getJSONArray("c");
                    JSONArray timestamps = json.getJSONArray("t");

                    int size = closes.length();
                    if (size == 0) return;

                    fullCloses.clear();
                    dateLabels.clear();
                    List<CandleEntry> candleEntries = new ArrayList<>();

                    float lastClose = (float) closes.getDouble(size - 1);
                    float prevClose = size > 1 ? (float) closes.getDouble(size - 2) : lastClose;
                    lastPrice = lastClose;

                    SimpleDateFormat sdf = dateFormatFor(resolution);
                    for (int i = 0; i < size; i++) {
                        fullCloses.add((float) closes.getDouble(i));
                        long ts = timestamps.getLong(i);
                        Date date = new Date(ts * 1000L);
                        dateLabels.add(sdf.format(date));
                        candleEntries.add(new CandleEntry(i,
                                (float) highs.getDouble(i),
                                (float) lows.getDouble(i),
                                (float) opens.getDouble(i),
                                (float) closes.getDouble(i)));
                    }

                    currentEntries.clear();
                    currentEntries.addAll(candleEntries);

                    float change = lastClose - prevClose;
                    float changePct = (prevClose != 0f) ? (change / prevClose) * 100f : 0f;
                    final float fClose = lastClose, fChng = change, fPct = changePct;
                    final String sym = symbol;
                    final List<CandleEntry> fin = new ArrayList<>(candleEntries);

                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (isCandleStick) updateCandleChart(fin);
                            else updateLineChart(fin);
                            if (priceText != null) {
                                priceText.setText("$" + df.format(fClose));
                                priceText.setTextColor(COLOR_PRIMARY);
                            }
                            if (changeText != null) {
                                String sign = fChng >= 0 ? "+" : "";
                                changeText.setText(sign + String.format(Locale.US, "%.2f", fChng)
                                        + " (" + sign + String.format(Locale.US, "%.2f", fPct) + "%)");
                                changeText.setTextColor(fChng >= 0 ? COLOR_GAIN : COLOR_LOSS);
                            }
                            if (timeFrameText != null) timeFrameText.setText("Timeframe: " + interval);
                            if (tickerText != null) tickerText.setText(sym + " / USD");
                        });
                    }
                } catch (Exception e) {
                    Log.e("ChartFragment", "parse error", e);
                }
            }
        });
    }

    private void analyzeWithAI() {
        if (fullCloses.isEmpty() || fullCloses.size() < 2) {
            Toast.makeText(requireContext(), "טען נתוני גרף קודם", Toast.LENGTH_SHORT).show();
            return;
        }
        showCustomAIDialog();
    }

    private void showCustomAIDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_ai_chat, null);
        TextView tvHint = dialogView.findViewById(R.id.tv_hint);
        ProgressBar progress = dialogView.findViewById(R.id.progress_ai);
        TextView tvResponse = dialogView.findViewById(R.id.tv_response);
        Button btnSend = dialogView.findViewById(R.id.btn_send);
        android.widget.EditText etQuestion = dialogView.findViewById(R.id.et_question);
        tvHint.setText("דוגמאות: 'מה דעתך על השקעה קצרת טווח?' או 'האם לקנות עכשיו?'");
        AlertDialog dialog = builder.setView(dialogView).setNegativeButton("ביטול", null).create();
        btnSend.setOnClickListener(vv -> {
            String question = etQuestion.getText().toString().trim();
            if (question.isEmpty()) {
                Toast.makeText(requireContext(), "הקלד שאלה", Toast.LENGTH_SHORT).show();
                return;
            }
            sendQuestionToAI(question, tvResponse, progress, etQuestion, dialog);
        });
        dialog.show();
    }

    private void sendQuestionToAI(String question, TextView tvResponse, ProgressBar progressBar,
                                  android.widget.EditText etQuestion, AlertDialog dialog) {
        progressBar.setVisibility(View.VISIBLE);
        etQuestion.setEnabled(false);
        String context = String.format(Locale.US, "מניה: %s | מחיר: $%.2f | טווח: %s | %d נקודות",
                symbol, lastPrice, interval, fullCloses.size());
        llmService.askQuestion(symbol, question, context, fullCloses, new LLMService.AnalysisCallback() {
            @Override
            public void onAnalysisReceived(String analysis) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    etQuestion.setEnabled(true);
                    etQuestion.setText("");
                    tvResponse.setText(analysis);
                    tvResponse.setVisibility(View.VISIBLE);
                });
            }

            @Override
            public void onError(String error) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    etQuestion.setEnabled(true);
                    tvResponse.setText("❌ שגיאה: " + error);
                    tvResponse.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private void openChartFromInput(String userInput) {
        String q = userInput.trim();
        if (q.isEmpty()) return;
        if (q.matches("^[A-Za-z0-9./-]{1,20}$") && !q.contains(" ")) {
            setSymbolAndLoad(q.toUpperCase(Locale.US));
            return;
        }
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
            String url = "https://finnhub.io/api/v1/search?q=" + encoded + "&token=" + API_KEY;
            client.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
                @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {}
                @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (!response.isSuccessful() || response.body() == null) return;
                    try {
                        JSONObject json = new JSONObject(response.body().string());
                        JSONArray result = json.optJSONArray("result");
                        if (result == null || result.length() == 0) return;
                        String sym = result.optJSONObject(0).optString("symbol", "").trim();
                        if (sym.isEmpty()) return;
                        String finalSym = sym.toUpperCase(Locale.US);
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {
                            if (tickerInput != null) {
                                tickerInput.setText(finalSym);
                                tickerInput.setSelection(finalSym.length());
                            }
                            setSymbolAndLoad(finalSym);
                        });
                    } catch (Exception ignored) {}
                }
            });
        } catch (Exception ignored) {}
    }

    private void scheduleSymbolSearch(String q) {
        if (pendingSearch != null) searchHandler.removeCallbacks(pendingSearch);
        q = q.trim();
        latestQuery = q;
        if (q.length() < 1 || q.length() > 50) {
            clearSuggestions();
            return;
        }
        final String finalQ = q;
        pendingSearch = () -> fetchSymbolSuggestions(finalQ);
        searchHandler.postDelayed(pendingSearch, SEARCH_DEBOUNCE_MS);
    }

    private void clearSuggestions() {
        if (suggestionAdapter != null) {
            suggestionAdapter.clear();
            suggestionAdapter.notifyDataSetChanged();
        }
        if (tickerInput != null) tickerInput.dismissDropDown();
    }

    private void fetchSymbolSuggestions(String query) {
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name());
            if (suggestionAdapter != null) {
                suggestionAdapter.clear();
                suggestionAdapter.notifyDataSetChanged();
            }

            String url = "https://finnhub.io/api/v1/search?q=" + encoded + "&token=" + API_KEY;
            client.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
                @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {}
                @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (!response.isSuccessful() || response.body() == null) return;
                    ArrayList<StockSuggestion> list = new ArrayList<>();
                    try {
                        JSONObject json = new JSONObject(response.body().string());
                        JSONArray result = json.optJSONArray("result");
                        if (result == null) return;
                        for (int i = 0; i < result.length() && i < 20; i++) {
                            JSONObject o = result.optJSONObject(i);
                            if (o == null) continue;
                            String sym = o.optString("symbol", "").trim();
                            String name = o.optString("description", "");
                            String type = o.optString("type", "");
                            if (sym.isEmpty()) continue;
                            if (!"Common Stock".equals(type) && !"ETF".equals(type)) continue;
                            list.add(new StockSuggestion(sym, name, "US"));
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
        ChartFragment f = new ChartFragment();
        Bundle args = new Bundle();
        args.putString("symbol", symbol);
        f.setArguments(args);
        return f;
    }
}
