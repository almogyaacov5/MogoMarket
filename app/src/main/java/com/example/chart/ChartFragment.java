package com.example.chart;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

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

// הפרגמנט הראשי שמציג את גרף המניה - תומך בגרף נרות (Candle) וגרף קו (Line)
public class ChartFragment extends Fragment implements TimeFrameFragment.TimeFrameListener {
    private CandleStickChart candleStickChart; // גרף הנרות
    private LineChart lineChart;               // גרף הקו
    private AutoCompleteTextView tickerInput;  // שדה חיפוש מניה עם השלמה אוטומטית
    private Button btnLoad, btnTimeFrame, btnToggleChart, btnAIAnalysis;
    private ImageButton btnChartRefresh;       // כפתור רענון הגרף
    private TextView timeFrameText, tickerText, priceText, changeText, currentPriceDisplay;
    private ProgressBar progressAI;

    private final OkHttpClient client = new OkHttpClient(); // HTTP Client לקריאות API
    private final String API_KEY = "0518811f0d394fa39842a8024a25c049"; // מפתח API של TwelveData

    private String symbol = "SPY";     // סמל המניה הנוכחי (ברירת מחדל: SPY)
    private String interval = "1day";  // טווח הזמן של הגרף
    private boolean isCandleStick = true; // האם מוצג גרף נרות (true) או קו (false)
    private final DecimalFormat df = new DecimalFormat("#.##"); // פורמט מספרים לשני ספרות עשרוניות
    private String latestQuery = ""; // השאילתה האחרונה לחיפוש מניה

    private LLMService llmService; // שירות ה-AI לניתוח
    private final List<CandleEntry> currentEntries = new ArrayList<>(); // נתוני הגרף הנוכחי
    private final List<Float> fullCloses = new ArrayList<>();           // רשימת מחירי סגירה לניתוח AI
    private float lastPrice = 0f;                // המחיר האחרון של המניה
    private boolean isManualSelection = false;   // האם המשתמש בחר ידנית מהרשימה

    private ArrayAdapter<StockSuggestion> suggestionAdapter; // אדפטר להצעות חיפוש
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch; // משימת חיפוש ממתינה (debounce)
    private static final long SEARCH_DEBOUNCE_MS = 50; // השהייה לפני ביצוע חיפוש (מניעת spam)

    // מחלקת עזר שמייצגת הצעה בתוצאות החיפוש
    public static class StockSuggestion {
        public final String symbol;
        public final String name;
        public final String exchange;

        public StockSuggestion(String symbol, String name, String exchange) {
            this.symbol = symbol;
            this.name = name;
            this.exchange = exchange;
        }

        // מה שיוצג בשורת ההצעה (AAPL — Apple Inc (NASDAQ))
        @NonNull
        @Override
        public String toString() {
            String s = (symbol == null) ? "" : symbol;
            String n = (name == null) ? "" : name;
            String ex = (exchange == null) ? "" : exchange;

            if (n.isEmpty() && ex.isEmpty()) return s;
            if (ex.isEmpty()) return s + " — " + n;
            return s + " — " + n + " (" + ex + ")";
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // ניפוח ה-Layout מה-XML
        View v = inflater.inflate(R.layout.fragment_chart, container, false);

        // קישור רכיבי UI מה-XML
        candleStickChart = v.findViewById(R.id.stock_chart);
        lineChart = v.findViewById(R.id.line_chart);
        tickerInput = v.findViewById(R.id.ticker_input);
        btnLoad = v.findViewById(R.id.btnLoad);
        btnTimeFrame = v.findViewById(R.id.btnSelectTimeFrame);
        btnToggleChart = v.findViewById(R.id.btnToggleChart);
        btnChartRefresh = v.findViewById(R.id.btnChartRefresh);
        btnAIAnalysis = v.findViewById(R.id.btnAIAnalysis);
        progressAI = v.findViewById(R.id.progressAI);
        priceText = v.findViewById(R.id.priceText);
        changeText = v.findViewById(R.id.changeText);
        timeFrameText = v.findViewById(R.id.timeFrameText);
        tickerText = v.findViewById(R.id.tickerText);
        currentPriceDisplay = v.findViewById(R.id.currentPriceDisplay);

        llmService = new LLMService();
        if (progressAI != null) progressAI.setVisibility(View.GONE);
        if (currentPriceDisplay != null) currentPriceDisplay.setVisibility(View.GONE);

        // אם הפרגמנט נפתח עם סמל מניה ספציפי (מהרשימה/watchlist)
        if (getArguments() != null && getArguments().containsKey("symbol")) {
            symbol = getArguments().getString("symbol", symbol);
            if (tickerInput != null) tickerInput.setText(symbol);
        }

        if (getActivity() != null) getActivity().setTitle("Chart: " + symbol);

        setupAutoComplete();    // הגדרת החיפוש האוטומטי
        setupClickListeners();  // הגדרת כפתורים
        fetchStockData(symbol, interval); // טעינת נתוני הגרף
        return v;
    }

    // הגדרת ה-AutoComplete - מציג הצעות מניות בזמן הקלדה
    private void setupAutoComplete() {
        if (tickerInput == null) return;

        suggestionAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                new ArrayList<>()
        );

        tickerInput.setAdapter(suggestionAdapter);
        tickerInput.setThreshold(1); // מתחיל להציג הצעות מאות אחת

        // כשהמשתמש בוחר הצעה מהרשימה
        tickerInput.setOnItemClickListener((parent, view, position, id) -> {
            StockSuggestion sel = suggestionAdapter.getItem(position);
            if (sel == null || sel.symbol == null || sel.symbol.trim().isEmpty()) return;

            String picked = sel.symbol.trim().toUpperCase(Locale.US);

            isManualSelection = true; // מניעת טריגר של TextWatcher בזמן הסלקציה
            tickerInput.setText(picked);
            tickerInput.setSelection(picked.length());
            tickerInput.dismissDropDown();

            setSymbolAndLoad(picked); // טוען את הגרף לסמל שנבחר

            // ניקוי השדה אחרי בחירה
            tickerInput.setText("");
            tickerInput.clearFocus();
            hideKeyboard();
            clearSuggestions();

            tickerInput.postDelayed(() -> isManualSelection = false, 300);
        });

        // מאזין לשינויים בטקסט - מפעיל חיפוש עם debounce
        tickerInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isManualSelection) return; // לא מחפשים אם זו בחירה ידנית
                String q = (s == null) ? "" : s.toString().trim();
                scheduleSymbolSearch(q);
            }
        });
    }

    // הגדרת כפתורים ואירועי לחיצה
    private void setupClickListeners() {
        if (btnLoad != null) {
            btnLoad.setOnClickListener(v -> {
                String userInput = (tickerInput == null) ? "" : tickerInput.getText().toString().trim();
                if (!userInput.isEmpty()) {
                    openChartFromInput(userInput); // פותח גרף לפי מה שהמשתמש הקליד
                }
                hideKeyboard();
                if (tickerInput != null) tickerInput.clearFocus();
            });
        }

        if (btnChartRefresh != null) {
            btnChartRefresh.setOnClickListener(v -> {
                fetchStockData(symbol, interval); // רענון הנתונים
                Toast.makeText(requireContext(), "Chart refreshed", Toast.LENGTH_SHORT).show();
            });
        }

        if (btnTimeFrame != null) {
            btnTimeFrame.setOnClickListener(v -> {
                // פתיחת דיאלוג לבחירת טווח זמן
                TimeFrameFragment dialog = new TimeFrameFragment();
                dialog.show(getChildFragmentManager(), "timeframe");
            });
        }

        if (btnToggleChart != null) {
            btnToggleChart.setOnClickListener(v -> {
                isCandleStick = !isCandleStick; // החלפה בין גרף נרות לקו
                if (isCandleStick) {
                    btnToggleChart.setText("Line chart");
                    candleStickChart.setVisibility(View.VISIBLE);
                    lineChart.setVisibility(View.GONE);
                } else {
                    btnToggleChart.setText("Candle chart");
                    candleStickChart.setVisibility(View.GONE);
                    lineChart.setVisibility(View.VISIBLE);
                }
                fetchStockData(symbol, interval); // טעינה מחדש עם הסוג החדש
            });
        }

        if (btnAIAnalysis != null) {
            btnAIAnalysis.setOnClickListener(v -> analyzeWithAI()); // פתיחת דיאלוג ניתוח AI
        }
    }

    // מחליט אם הקלט הוא ticker ישיר או שם חברה ומפנה בהתאם
    private void openChartFromInput(String userInput) {
        String q = userInput.trim();
        if (q.isEmpty()) return;

        // אם הקלט נראה כמו ticker (1-20 תווים, ללא רווחים)
        boolean looksLikeTicker = q.matches("^[A-Za-z0-9./-]{1,20}$") && !q.contains(" ");
        if (looksLikeTicker) {
            setSymbolAndLoad(q.toUpperCase(Locale.US));
            return;
        }

        // אחרת, מחפש את ה-ticker הראשון שמתאים לשם
        resolveFirstMatchAndOpen(q);
    }

    // מגדיר את הסמל הנוכחי וטוען את הגרף
    private void setSymbolAndLoad(String sym) {
        symbol = sym;
        if (tickerText != null) tickerText.setText("Ticker: " + symbol);
        if (getActivity() != null) getActivity().setTitle("Chart: " + symbol);
        fetchStockData(symbol, interval);
        hideKeyboard();
    }

    // חיפוש ה-ticker הראשון שתואם לשאילתה מה-API ופתיחת הגרף שלו
    private void resolveFirstMatchAndOpen(String query) {
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name());
            String url = "https://api.twelvedata.com/symbol_search?symbol=" + encoded +
                    "&outputsize=1&country=US&exchange=NYSE&apikey=" + API_KEY;

            Request request = new Request.Builder().url(url).build();
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) { }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (!response.isSuccessful() || response.body() == null) return;

                    try {
                        JSONObject json = new JSONObject(response.body().string());
                        JSONArray data = json.optJSONArray("data");
                        if (data == null || data.length() == 0) return;

                        JSONObject first = data.optJSONObject(0);
                        if (first == null) return;

                        String sym = first.optString("symbol", "").trim();
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

                    } catch (Exception ignored) { }
                }
            });

        } catch (Exception ignored) { }
    }

    // תזמון חיפוש עם debounce - מחכה 50ms לפני ביצוע כדי למנוע קריאות מיותרות
    private void scheduleSymbolSearch(String q) {
        if (q.length() == 1 && !Character.isLetterOrDigit(q.charAt(0))) {
            clearSuggestions();
            return;
        }

        if (pendingSearch != null) searchHandler.removeCallbacks(pendingSearch); // ביטול חיפוש קודם
        pendingSearch = null;

        if (q == null) q = "";
        q = q.trim();
        latestQuery = q;

        if (q.length() < 1) {
            clearSuggestions();
            return;
        }

        if (q.length() > 50) {
            clearSuggestions();
            return;
        }

        final String finalQ = q;
        pendingSearch = () -> fetchSymbolSuggestions(finalQ);
        searchHandler.postDelayed(pendingSearch, SEARCH_DEBOUNCE_MS); // דחיית הביצוע
    }

    // ניקוי רשימת ההצעות וסגירת הDropDown
    private void clearSuggestions() {
        if (suggestionAdapter != null) {
            suggestionAdapter.clear();
            suggestionAdapter.notifyDataSetChanged();
        }
        if (tickerInput != null) tickerInput.dismissDropDown();
    }

    // שולח בקשות מקבילות לשני הבורסות (NYSE ו-NASDAQ)
    private void fetchSymbolSuggestions(String query) {
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name());

            if (suggestionAdapter != null) {
                suggestionAdapter.clear();
                suggestionAdapter.notifyDataSetChanged();
            }

            // HashSet למניעת כפילויות בין שתי הבורסות
            final java.util.HashSet<String> addedSymbols = new java.util.HashSet<>();

            fetchSymbolSuggestionsOneExchange(encoded, query, "NYSE", addedSymbols);
            fetchSymbolSuggestionsOneExchange(encoded, query, "NASDAQ", addedSymbols);

        } catch (Exception ignored) { }
    }

    // שולח בקשה לחיפוש מניות מבורסה ספציפית
    private void fetchSymbolSuggestionsOneExchange(String encoded, String originalQuery, String exchange,
                                                   java.util.HashSet<String> addedSymbols) {

        String url = "https://api.twelvedata.com/symbol_search?symbol=" + encoded +
                "&outputsize=20&country=US&exchange=" + exchange +
                "&apikey=" + API_KEY;

        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                // לא צריך לעשות כלום
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {

                if (!response.isSuccessful() || response.body() == null) return;
                ArrayList<StockSuggestion> list = new ArrayList<>();

                try {
                    JSONObject json = new JSONObject(response.body().string());
                    JSONArray data = json.optJSONArray("data");
                    if (data == null) return;

                    for (int i = 0; i < data.length(); i++) {
                        JSONObject o = data.optJSONObject(i);
                        if (o == null) continue;

                        String sym = o.optString("symbol", "").trim();
                        if (sym.isEmpty()) continue;

                        String name = o.optString("instrument_name", "");
                        String ex = o.optString("exchange", "");

                        // מסנן רק NYSE ו-NASDAQ
                        if (!"NYSE".equalsIgnoreCase(ex) && !"NASDAQ".equalsIgnoreCase(ex))
                            continue;

                        // מניעת כפילויות בין שתי הבקשות
                        synchronized (addedSymbols) {
                            if (addedSymbols.contains(sym)) continue;
                            addedSymbols.add(sym);
                        }

                        list.add(new StockSuggestion(sym, name, ex));
                    }

                } catch (Exception ignored) { }

                if (getActivity() == null) return;

                getActivity().runOnUiThread(() -> {

                    // אם הגיעה תשובה ישנה - מתעלמים ממנה
                    if (!originalQuery.equals(latestQuery)) return;
                    if (suggestionAdapter == null) return;

                    suggestionAdapter.addAll(list);
                    suggestionAdapter.notifyDataSetChanged();

                    if (tickerInput == null) return;

                    if (tickerInput == null) return;

                    CharSequence cs = tickerInput.getText();
                    if (cs == null) return;

// מספיק תווים כדי להציג הצעות (threshold) [web:100]
                    if (tickerInput.enoughToFilter()) {
                        suggestionAdapter.getFilter().filter(cs, count -> {
                            // אם יש תוצאות אחרי פילטר – תפתח
                            if (count > 0) {
                                tickerInput.showDropDown();
                            } else {
                                tickerInput.dismissDropDown();
                            }
                        });
                    }



                    if (tickerInput != null &&
                            tickerInput.hasFocus() &&
                            suggestionAdapter.getCount() > 0) {
                        tickerInput.post(() -> tickerInput.showDropDown());
                    }


                });
            }
        });
    }


    // נקרא כשהמשתמש בוחר טווח זמן מהדיאלוג (ממשק TimeFrameListener)
    @Override
    public void onTimeFrameSelected(String interval) {
        this.interval = interval;
        if (timeFrameText != null) timeFrameText.setText("Time frame: " + interval);
        fetchStockData(symbol, interval); // טעינה מחדש עם הטווח החדש
    }

    // בדיקה שיש נתוני גרף לפני פתיחת דיאלוג ה-AI
    private void analyzeWithAI() {
        android.util.Log.d("LLM", "analyzeWithAI נקרא, fullCloses size: " + fullCloses.size());
        android.util.Log.d("LLM", "lastPrice: " + lastPrice);

        if (fullCloses.isEmpty() || fullCloses.size() < 2) {
            Toast.makeText(requireContext(), "טען נתוני גרף קודם (מינימום 2 נקודות)", Toast.LENGTH_SHORT).show();
            return;
        }
        showCustomAIDialog();
    }


    // פתיחת דיאלוג צ'אט AI מותאם אישית
    private void showCustomAIDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_ai_chat, null);

        TextView tvHint = dialogView.findViewById(R.id.tv_hint);
        ProgressBar progressBar = dialogView.findViewById(R.id.progress_ai);
        TextView tvResponse = dialogView.findViewById(R.id.tv_response);
        Button btnSend = dialogView.findViewById(R.id.btn_send);
        android.widget.EditText etQuestion = dialogView.findViewById(R.id.et_question);

        tvHint.setText("דוגמאות: 'מה דעתך על השקעה קצרת טווח?' או 'האם לקנות עכשיו?'");

        AlertDialog dialog = builder.setView(dialogView)
                .setNegativeButton("ביטול", null)
                .create();

        btnSend.setOnClickListener(v -> {
            String question = etQuestion.getText().toString().trim();
            if (question.isEmpty()) {
                Toast.makeText(requireContext(), "הקלד שאלה", Toast.LENGTH_SHORT).show();
                return;
            }
            sendQuestionToAI(question, tvResponse, progressBar, etQuestion, dialog);
        });

        dialog.show();
        etQuestion.requestFocus();
        // הסרת הרקע הלבן של הדיאלוג
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
    }

    // שולח את שאלת המשתמש ל-AI עם הקשר של המניה הנוכחית
    private void sendQuestionToAI(String question, TextView tvResponse,
                                  ProgressBar progressBar, android.widget.EditText etQuestion, AlertDialog dialog) {

        android.util.Log.d("LLM", "sendQuestionToAI נקרא");
        android.util.Log.d("LLM", "sendQuestionToAI נקרא עם שאלה: " + question);
        android.util.Log.d("LLM", "fullCloses size: " + fullCloses.size());

        progressBar.setVisibility(View.VISIBLE);
        etQuestion.setEnabled(false); // נועל את השדה בזמן הטעינה

        // בניית הקשר של המניה לשאלה
        String context = String.format(
                Locale.US,
                "מניה: %s | מחיר נוכחי: $%.2f | טווח זמן: %s | %d נקודות נתונים",
                symbol, lastPrice, interval, fullCloses.size()
        );

        llmService.askQuestion(symbol, question, context, fullCloses, new LLMService.AnalysisCallback() {
            @Override
            public void onAnalysisReceived(String analysis) {
                android.util.Log.d("LLM", "onAnalysisReceived נקרא! אורך תשובה: " + analysis.length());
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    etQuestion.setEnabled(true);
                    etQuestion.setText("");
                    tvResponse.setText(analysis); // הצגת תשובת ה-AI
                    tvResponse.setVisibility(View.VISIBLE);
                });
            }

            @Override
            public void onError(String error) {
                android.util.Log.d("LLM", "onError נקרא: " + error);
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



    // שמירת ניתוח AI ב-Firebase Realtime Database תחת "ai-analyses/{symbol}"
    private void saveAnalysis(String symbol, String analysis) {
        try {
            DatabaseReference analysesRef = FirebaseDatabase.getInstance()
                    .getReference("ai-analyses").child(symbol);
            String key = analysesRef.push().getKey(); // יצירת מפתח ייחודי
            HashMap<String, Object> data = new HashMap<>();
            data.put("timestamp", System.currentTimeMillis());
            data.put("analysis", analysis);
            data.put("symbol", symbol);
            data.put("price", lastPrice);
            analysesRef.child(key).setValue(data);
            Toast.makeText(requireContext(), "נשמר בהיסטוריה ✅", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(requireContext(), "שגיאת שמירה", Toast.LENGTH_SHORT).show();
        }
    }

    // הסתרת מקלדת המסך
    private void hideKeyboard() {
        if (getActivity() == null) return;

        View view = getActivity().getCurrentFocus();
        if (view == null) view = getView();

        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    // קריאת ה-API לקבלת נתוני מחירי המניה
    private void fetchStockData(String symbol, String interval) {
        String url = "https://api.twelvedata.com/time_series?symbol=" + symbol +
                "&interval=" + interval + "&apikey=" + API_KEY + "&outputsize=252";

        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) { }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) return;

                try {
                    JSONObject json = new JSONObject(response.body().string());
                    JSONArray series = json.getJSONArray("values"); // מערך הנתונים מה-API

                    fullCloses.clear();
                    List<CandleEntry> candleEntries = new ArrayList<>();
                    float lastClose = 0, prevClose = 0;

                    // המחיר האחרון ולפניו (לחישוב שינוי יומי)
                    if (series.length() > 0) {
                        lastClose = Float.parseFloat(series.getJSONObject(0).getString("close"));
                        lastPrice = lastClose;
                    }
                    if (series.length() > 1) {
                        prevClose = Float.parseFloat(series.getJSONObject(1).getString("close"));
                    }

                    // שמירת כל מחירי הסגירה לניתוח AI
                    for (int i = 0; i < series.length(); i++) {
                        JSONObject data = series.getJSONObject(i);
                        float close = Float.parseFloat(data.getString("close"));
                        fullCloses.add(close);
                    }

                    if (fullCloses.isEmpty()) return;

                    // בניית נתוני הנרות (40 אחרונים, מסודרים מישן לחדש עבור הגרף)
                    int chartIndex = 0;
                    int startIndex = Math.max(0, series.length() - 252);
                    for (int i = series.length() - 1; i >= startIndex; i--) {
                        JSONObject data = series.getJSONObject(i);
                        float open = Float.parseFloat(data.getString("open"));
                        float high = Float.parseFloat(data.getString("high"));
                        float low = Float.parseFloat(data.getString("low"));
                        float close = Float.parseFloat(data.getString("close"));
                        candleEntries.add(new CandleEntry(chartIndex++, high, low, open, close));
                    }

                    currentEntries.clear();
                    currentEntries.addAll(candleEntries);

                    // חישוב שינוי יומי בדולרים ובאחוזים
                    float change = lastClose - prevClose;
                    float changePercent = (prevClose != 0) ? (change / prevClose) * 100 : 0;

                    final float dispClose = lastClose;
                    final float dispChange = change;
                    final float dispChangePercent = changePercent;
                    final String currentSymbol = symbol;
                    final List<CandleEntry> finalEntries = new ArrayList<>(candleEntries);

                    // עדכון ממשק המשתמש מה-Main Thread
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (isCandleStick) updateCandleChart(finalEntries);
                            else updateLineChart(finalEntries);

                            if (priceText != null) priceText.setText("Current price: $" + df.format(dispClose));
                            if (changeText != null) {
                                changeText.setText("Daily change: $" + String.format(Locale.US, "%.2f", dispChange) +
                                        " (" + String.format(Locale.US, "%.2f", dispChangePercent) + "%)");
                            }
                            if (timeFrameText != null) timeFrameText.setText("Time frame: " + interval);
                            if (tickerText != null) tickerText.setText("Ticker: " + currentSymbol);
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    // עדכון גרף הנרות עם הנתונים החדשים
    private void updateCandleChart(List<CandleEntry> entries) {
        CandleDataSet dataSet = new CandleDataSet(entries, "Stock candle chart");
        dataSet.setDecreasingColor(Color.RED);   // נר יורד = אדום
        dataSet.setIncreasingColor(Color.GREEN);  // נר עולה = ירוק
        dataSet.setIncreasingPaintStyle(Paint.Style.FILL);
        dataSet.setShadowColor(Color.DKGRAY);
        dataSet.setDrawValues(false); // לא מציג ערכים על כל נר

        // הסרת קווי רשת מהגרף
        XAxis xAxis = candleStickChart.getXAxis();
        YAxis leftAxis = candleStickChart.getAxisLeft();
        YAxis rightAxis = candleStickChart.getAxisRight();
        xAxis.setDrawGridLines(false);
        leftAxis.setDrawGridLines(false);
        rightAxis.setDrawGridLines(false);

        CandleData data = new CandleData(dataSet);
        candleStickChart.setData(data);
        candleStickChart.invalidate(); // רענון הגרף
    }

    // עדכון גרף הקו - ממיר נתוני נרות לנקודות קו (לפי מחיר סגירה)
    private void updateLineChart(List<CandleEntry> candleEntries) {
        List<Entry> lineEntries = new ArrayList<>();
        for (CandleEntry c : candleEntries) {
            lineEntries.add(new Entry(c.getX(), c.getClose())); // X=אינדקס, Y=מחיר סגירה
        }
        LineDataSet lineDataSet = new LineDataSet(lineEntries, "Line chart");
        lineDataSet.setColor(Color.BLUE);
        lineDataSet.setDrawCircles(false); // ללא עיגולים על כל נקודה
        lineDataSet.setDrawValues(false);

        LineData lineData = new LineData(lineDataSet);
        lineChart.setData(lineData);
        lineChart.invalidate(); // רענון הגרף
    }

    // Factory method - יצירת פרגמנט עם סמל מניה ספציפי
    public static ChartFragment newInstance(String symbol) {
        ChartFragment fragment = new ChartFragment();
        Bundle args = new Bundle();
        args.putString("symbol", symbol);
        fragment.setArguments(args);
        return fragment;
    }
}