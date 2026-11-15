package com.mogomarket.app;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.graphics.drawable.GradientDrawable;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class PortfolioAddStockFragment extends Fragment {

    private View rootView;
    private View cardView;
    private AutoCompleteTextView editTicker;
    private Spinner spinnerStrategy;
    private Spinner spinnerSector;
    private EditText editBuyPrice, editTargetPrice, editStopLoss, editTradeAmount,
            editShares, editEntryFees, editNotes;
    private Button btnAddStock;
    private Button btnEntryScreenshot;
    private Button btnOpenDate;
    private long selectedOpenedAt;
    private Uri entryScreenshotUri;
    private DatabaseReference userRef;
    private DatabaseReference stocksRef;

    private final OkHttpClient client = new OkHttpClient();

    private ArrayAdapter<ChartFragment.StockSuggestion> suggestionAdapter;
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;
    private int searchGeneration;
    private boolean isManualSelection = false;
    private boolean syncingTradeInputs;
    private boolean sharesArePrimary;
    private String metadataLookupTicker = "";

    private String selectedCompanyName = "";
    private final ActivityResultLauncher<String> screenshotPicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                entryScreenshotUri = uri;
                if (btnEntryScreenshot != null) {
                    btnEntryScreenshot.setText(uri == null
                            ? "Import / attach trade screenshot" : "Screenshot selected");
                    if (uri != null) scanTradeScreenshot(uri);
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.portfolio_add_stock_fragment, container, false);

        rootView        = v.findViewById(R.id.portfolioAddRoot);
        cardView        = v.findViewById(R.id.portfolioAddCard);
        editTicker      = v.findViewById(R.id.editTicker);
        spinnerStrategy = v.findViewById(R.id.spinnerStrategy);
        spinnerSector = v.findViewById(R.id.spinnerSector);
        editBuyPrice    = v.findViewById(R.id.editBuyPrice);
        editTargetPrice = v.findViewById(R.id.editTargetPrice);
        editStopLoss    = v.findViewById(R.id.editStopLoss);
        editTradeAmount = v.findViewById(R.id.editTradeAmount);
        editShares      = v.findViewById(R.id.editShares);
        editEntryFees = v.findViewById(R.id.editEntryFees);
        editNotes       = v.findViewById(R.id.editNotes);
        btnAddStock     = v.findViewById(R.id.btnAddStock);
        btnEntryScreenshot = v.findViewById(R.id.btnEntryScreenshot);
        btnOpenDate = v.findViewById(R.id.btnOpenDate);
        selectedOpenedAt = startOfToday();
        updateOpenDateButton();
        btnOpenDate.setOnClickListener(view -> showOpenDatePicker());

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        userRef = FirebaseDatabase.getInstance().getReference("users").child(uid);
        stocksRef = userRef.child("portfolio-stocks");

        setupAutoComplete();
        ArrayAdapter<String> strategyAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item,
                new String[]{"Strategy (optional)", "Breakout", "Pullback", "EMA Bounce",
                        "Earnings Play", "Other"});
        strategyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerStrategy.setAdapter(strategyAdapter);
        String[] sectors = {"Sector (optional)", "Technology", "Healthcare", "Financials",
                "Consumer Discretionary", "Consumer Staples", "Industrials", "Energy",
                "Materials", "Utilities", "Real Estate", "Communication Services", "Other"};
        ArrayAdapter<String> sectorAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, sectors);
        sectorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSector.setAdapter(sectorAdapter);
        setupTradeInputSync();
        applyTheme();
        btnAddStock.setOnClickListener(view -> addStock());
        btnEntryScreenshot.setOnClickListener(view -> screenshotPicker.launch("image/*"));
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (rootView != null) applyTheme();
    }

    @Override
    public void onDestroyView() {
        searchGeneration++;
        searchHandler.removeCallbacksAndMessages(null);
        rootView = null;
        cardView = null;
        editTicker = null;
        spinnerStrategy = null;
        spinnerSector = null;
        editBuyPrice = null;
        editTargetPrice = null;
        editStopLoss = null;
        editTradeAmount = null;
        editShares = null;
        editEntryFees = null;
        editNotes = null;
        btnAddStock = null;
        btnEntryScreenshot = null;
        btnOpenDate = null;
        suggestionAdapter = null;
        super.onDestroyView();
    }

    private void applyTheme() {
        SharedPreferences preferences = requireActivity()
                .getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE);
        boolean dark = preferences.getBoolean("dark_mode", true);

        int background = dark ? 0xFF0B0F14 : 0xFFF0F4F8;
        int card = dark ? 0xFF151C2E : 0xFFFFFFFF;
        int field = dark ? 0xFF111826 : 0xFFF3F7FE;
        int primaryText = dark ? 0xFFE6EDF3 : 0xFF1E293B;
        int secondaryText = dark ? 0xFF8B98A5 : 0xFF64748B;

        rootView.setBackgroundColor(background);
        GradientDrawable cardBackground = new GradientDrawable();
        cardBackground.setColor(card);
        cardBackground.setCornerRadius(getResources().getDisplayMetrics().density * 18f);
        cardBackground.setStroke(Math.round(getResources().getDisplayMetrics().density),
                dark ? 0xFF263247 : 0xFFE2E8F0);
        cardView.setBackground(cardBackground);
        for (EditText input : new EditText[]{
                editTicker, editBuyPrice, editTargetPrice, editStopLoss, editTradeAmount, editShares,
                editEntryFees, editNotes
        }) {
            GradientDrawable fieldBackground = new GradientDrawable();
            fieldBackground.setColor(field);
            fieldBackground.setCornerRadius(
                    getResources().getDisplayMetrics().density * 10f);
            input.setBackground(fieldBackground);
            input.setTextColor(primaryText);
            input.setHintTextColor(secondaryText);
        }
        btnOpenDate.setTextColor(primaryText);
        for (Spinner spinner : new Spinner[]{spinnerStrategy, spinnerSector}) {
            GradientDrawable spinnerBackground = new GradientDrawable();
            spinnerBackground.setColor(field);
            spinnerBackground.setCornerRadius(getResources().getDisplayMetrics().density * 10f);
            spinnerBackground.setStroke(Math.round(getResources().getDisplayMetrics().density),
                    dark ? 0xFF263247 : 0xFFD8E1EC);
            spinner.setBackground(spinnerBackground);
        }
        GradientDrawable dropdownBackground = new GradientDrawable();
        dropdownBackground.setColor(card);
        dropdownBackground.setCornerRadius(getResources().getDisplayMetrics().density * 8f);
        editTicker.setDropDownBackgroundDrawable(dropdownBackground);
        btnAddStock.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0xFF2563EB));
    }

    // ==================== AutoComplete ====================

    private void setupAutoComplete() {
        suggestionAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                new ArrayList<>()
        );

        editTicker.setAdapter(suggestionAdapter);
        editTicker.setThreshold(1);

        editTicker.setOnItemClickListener((parent, view, position, id) -> {
            ChartFragment.StockSuggestion sel = suggestionAdapter.getItem(position);
            if (sel == null || sel.symbol == null) return;

            // קריפטו: שמור בפורמט BINANCE:BTCUSDT. מניה: UPPERCASE
            String picked = CryptoHelper.isCryptoSymbol(sel.symbol)
                    ? sel.symbol.trim()
                    : sel.symbol.trim().toUpperCase(Locale.US);

            selectedCompanyName = sel.name != null && !sel.name.startsWith("Use symbol:")
                    ? sel.name.trim() : "";
            metadataLookupTicker = "";

            isManualSelection = true;
            editTicker.setText(picked);
            editTicker.setSelection(picked.length());
            editTicker.dismissDropDown();
            editBuyPrice.requestFocus();
            editTicker.postDelayed(() -> isManualSelection = false, 300);
        });

        editTicker.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isManualSelection) return;
                selectedCompanyName = "";
                metadataLookupTicker = "";
                String q = s == null ? "" : s.toString().trim();
                scheduleSearch(q);
            }
        });
    }

    private void scheduleSearch(String q) {
        if (pendingSearch != null) searchHandler.removeCallbacks(pendingSearch);
        int generation = ++searchGeneration;
        if (q.length() < 1) { clearSuggestions(); return; }
        pendingSearch = () -> fetchSuggestions(q, generation);
        searchHandler.postDelayed(pendingSearch, 300);
    }

    private void clearSuggestions() {
        if (suggestionAdapter != null) {
            suggestionAdapter.clear();
            suggestionAdapter.notifyDataSetChanged();
        }
        if (editTicker != null) editTicker.dismissDropDown();
    }

    private void setupTradeInputSync() {
        TextWatcher amountWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (syncingTradeInputs) return;
                sharesArePrimary = false;
                updateDerivedTradeInput(true);
            }
            @Override public void afterTextChanged(Editable s) {}
        };
        TextWatcher sharesWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (syncingTradeInputs) return;
                sharesArePrimary = true;
                updateDerivedTradeInput(false);
            }
            @Override public void afterTextChanged(Editable s) {}
        };
        TextWatcher priceWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!syncingTradeInputs) updateDerivedTradeInput(sharesArePrimary);
            }
            @Override public void afterTextChanged(Editable s) {}
        };
        editTradeAmount.addTextChangedListener(amountWatcher);
        editShares.addTextChangedListener(sharesWatcher);
        editBuyPrice.addTextChangedListener(priceWatcher);
    }

    private void updateDerivedTradeInput(boolean amountIsSource) {
        String priceText = editBuyPrice.getText().toString().trim();
        String sourceText = (amountIsSource ? editTradeAmount : editShares)
                .getText().toString().trim();
        EditText target = amountIsSource ? editShares : editTradeAmount;
        if (sourceText.isEmpty() || priceText.isEmpty()) {
            syncingTradeInputs = true;
            target.setText("");
            syncingTradeInputs = false;
            return;
        }
        try {
            double price = Double.parseDouble(priceText);
            double source = Double.parseDouble(sourceText);
            if (!Double.isFinite(price) || !Double.isFinite(source) || price <= 0) return;
            double value = amountIsSource ? source / price : source * price;
            if (!Double.isFinite(value)) return;
            String formatted = amountIsSource
                    ? formatShares(value) : String.format(Locale.US, "%.2f", value);
            if (!formatted.equals(target.getText().toString())) {
                syncingTradeInputs = true;
                target.setText(formatted);
                target.setSelection(formatted.length());
                syncingTradeInputs = false;
            }
        } catch (NumberFormatException ignored) {
            syncingTradeInputs = true;
            target.setText("");
            syncingTradeInputs = false;
        }
    }

    private String formatShares(double shares) {
        return String.format(Locale.US, "%.6f", shares)
                .replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private long startOfToday() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private void updateOpenDateButton() {
        if (btnOpenDate == null || !isAdded()) return;
        String date = android.text.format.DateFormat.getDateFormat(requireContext())
                .format(new Date(selectedOpenedAt));
        btnOpenDate.setText("Open date: " + date);
    }

    private void showOpenDatePicker() {
        Calendar selectedDate = Calendar.getInstance();
        selectedDate.setTimeInMillis(selectedOpenedAt);
        DatePickerDialog picker = new DatePickerDialog(requireContext(),
                (view, year, month, dayOfMonth) -> {
                    Calendar chosenDate = Calendar.getInstance();
                    chosenDate.set(year, month, dayOfMonth, 0, 0, 0);
                    chosenDate.set(Calendar.MILLISECOND, 0);
                    selectedOpenedAt = chosenDate.getTimeInMillis();
                    updateOpenDateButton();
                }, selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH),
                selectedDate.get(Calendar.DAY_OF_MONTH));
        picker.getDatePicker().setMaxDate(System.currentTimeMillis());
        picker.show();
    }

    private void scanTradeScreenshot(Uri imageUri) {
        if (btnEntryScreenshot != null) {
            btnEntryScreenshot.setEnabled(false);
            btnEntryScreenshot.setText("Reading trade screenshot...");
        }
        try {
            InputImage image = InputImage.fromFilePath(requireContext(), imageUri);
            com.google.mlkit.vision.text.TextRecognizer recognizer =
                    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            recognizer.process(image)
                    .addOnSuccessListener(result -> {
                        if (!isAdded() || editTicker == null) return;
                        TradeScreenshotParser.ParsedTrade trade =
                                TradeScreenshotParser.parse(result.getText());
                        if (trade == null) {
                            Toast.makeText(getContext(),
                                    "Trade details were not recognized. You can enter them manually; "
                                            + "the screenshot will still be attached.",
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        editTicker.setText(trade.symbol);
                        editBuyPrice.setText(String.format(Locale.US, "%.4f", trade.buyPrice));
                        editShares.setText(formatShares(trade.shares));
                        editTradeAmount.setText(String.format(Locale.US, "%.2f",
                                trade.tradeAmount));
                        editTicker.dismissDropDown();
                        selectedCompanyName = isSpxTicker(trade.symbol)
                                ? "S&P 500 Index" : "";
                        new AlertDialog.Builder(requireContext())
                                .setTitle("Trade detected")
                                .setMessage(String.format(Locale.US,
                                        "%s — %.6f shares at $%.2f (about $%,.2f invested). "
                                                + "Add this trade to your portfolio?",
                                        trade.symbol, trade.shares, trade.buyPrice,
                                        trade.tradeAmount))
                                .setPositiveButton("Add trade", (dialog, which) -> addStock())
                                .setNegativeButton("Review details", null)
                                .show();
                    })
                    .addOnFailureListener(error -> {
                        if (!isAdded()) return;
                        android.util.Log.w("PortfolioAddStock",
                                "Could not recognize trade screenshot", error);
                        Toast.makeText(getContext(),
                                "Could not read the screenshot. Enter trade details manually.",
                                Toast.LENGTH_LONG).show();
                    })
                    .addOnCompleteListener(task -> {
                        recognizer.close();
                        if (btnEntryScreenshot != null) {
                            btnEntryScreenshot.setEnabled(true);
                            btnEntryScreenshot.setText("Screenshot selected");
                        }
                    });
        } catch (Exception error) {
            if (btnEntryScreenshot != null) {
                btnEntryScreenshot.setEnabled(true);
                btnEntryScreenshot.setText("Screenshot selected");
            }
            android.util.Log.w("PortfolioAddStock", "Could not open trade screenshot", error);
            Toast.makeText(getContext(), "Could not open this screenshot",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void fetchSuggestions(String query, int generation) {
        List<CryptoHelper.CryptoSuggestion> cryptoList = CryptoHelper.searchLocal(query);
        ArrayList<ChartFragment.StockSuggestion> localResults = new ArrayList<>();
        for (CryptoHelper.CryptoSuggestion cs : cryptoList) {
            localResults.add(new ChartFragment.StockSuggestion(
                    cs.binanceSymbol, cs.fullName + " (Crypto)", "Crypto"));
        }
        String directSymbol = query.trim().toUpperCase(Locale.US);
        if (!directSymbol.isEmpty() && directSymbol.matches("[A-Z0-9.^=_-]+")) {
            localResults.add(new ChartFragment.StockSuggestion(
                    directSymbol, "Use symbol: " + directSymbol, "Symbol"));
        }
        showSuggestions(query, generation, localResults);

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
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (!response.isSuccessful() || response.body() == null) return;
                    ArrayList<ChartFragment.StockSuggestion> results = new ArrayList<>(localResults);
                    try {
                        JSONArray quotes = new JSONObject(response.body().string()).optJSONArray("quotes");
                        if (quotes != null) {
                            for (int i = 0; i < quotes.length(); i++) {
                                JSONObject quote = quotes.optJSONObject(i);
                                if (quote == null) continue;
                                String symbol = quote.optString("symbol", "").trim();
                                if (symbol.isEmpty()) continue;
                                String name = quote.optString("longname",
                                        quote.optString("shortname", symbol));
                                String exchange = quote.optString("exchDisp",
                                        quote.optString("quoteType", "Stock"));
                                results.add(new ChartFragment.StockSuggestion(symbol, name, exchange));
                            }
                        }
                    } catch (org.json.JSONException ignored) {
                    }
                    showSuggestions(query, generation, results);
                }
            });
        } catch (Exception ignored) {
        }
    }

    private void showSuggestions(String query, int generation,
                                 List<ChartFragment.StockSuggestion> suggestions) {
        if (!isAdded() || generation != searchGeneration
                || suggestionAdapter == null || editTicker == null) return;
        requireActivity().runOnUiThread(() -> {
            if (!isAdded() || generation != searchGeneration || suggestionAdapter == null
                    || editTicker == null
                    || !query.equalsIgnoreCase(editTicker.getText().toString().trim())) return;
            ArrayList<ChartFragment.StockSuggestion> unique = new ArrayList<>();
            HashSet<String> symbols = new HashSet<>();
            for (ChartFragment.StockSuggestion suggestion : suggestions) {
                if (suggestion == null || suggestion.symbol == null) continue;
                String key = suggestion.symbol.trim().toUpperCase(Locale.US);
                if (symbols.add(key)) unique.add(suggestion);
            }
            suggestionAdapter.clear();
            suggestionAdapter.addAll(unique);
            suggestionAdapter.notifyDataSetChanged();
            if (editTicker.hasFocus() && !unique.isEmpty()) {
                editTicker.post(() -> {
                    if (generation == searchGeneration && editTicker.hasFocus()) {
                        editTicker.showDropDown();
                    }
                });
            }
        });
    }

    // ==================== הוספת מניה/קריפטו ====================

    private void addStock() {
        String ticker  = editTicker.getText().toString().trim();
        String priceStr  = editBuyPrice.getText().toString().trim();
        String targetStr = editTargetPrice.getText().toString().trim();
        String stopLossStr = editStopLoss.getText().toString().trim();
        String amountStr = editTradeAmount.getText().toString().trim();
        String sharesStr = editShares.getText().toString().trim();
        String feesStr = editEntryFees.getText().toString().trim();
        String notes     = editNotes.getText().toString().trim();

        // קריפטו: שמור כ-BINANCE:BTCUSDT. מניה: UPPERCASE
        if (!CryptoHelper.isCryptoSymbol(ticker)) {
            ticker = ticker.toUpperCase(Locale.US);
        }

        // בדוק במיפוי קריפטו
        String mapped = CryptoHelper.CRYPTO_MAP.get(ticker.toUpperCase(Locale.US));
        if (mapped != null) ticker = mapped;

        if (ticker.isEmpty() || priceStr.isEmpty()) {
            Toast.makeText(getContext(), "Enter a ticker and price", Toast.LENGTH_SHORT).show();
            return;
        }

        float price;
        try { price = Float.parseFloat(priceStr); }
        catch (NumberFormatException e) {
            Toast.makeText(getContext(), "Invalid price", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!Float.isFinite(price) || price <= 0) {
            Toast.makeText(getContext(), "Price must be greater than 0", Toast.LENGTH_SHORT).show();
            return;
        }

        float targetPrice = 0;
        if (!targetStr.isEmpty()) {
            try { targetPrice = Float.parseFloat(targetStr); }
            catch (NumberFormatException e) {
                Toast.makeText(getContext(), "Invalid target price", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!Float.isFinite(targetPrice) || targetPrice < 0) {
                Toast.makeText(getContext(), "Enter a valid target price", Toast.LENGTH_SHORT).show();
                return;
            }
            if (targetPrice > 0 && targetPrice <= price) {
                Toast.makeText(getContext(), "Target price must be above entry price", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        float stopLossPrice = 0;
        if (!stopLossStr.isEmpty()) {
            try { stopLossPrice = Float.parseFloat(stopLossStr); }
            catch (NumberFormatException e) {
                Toast.makeText(getContext(), "Invalid stop loss price", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!Float.isFinite(stopLossPrice) || stopLossPrice <= 0 || stopLossPrice >= price) {
                Toast.makeText(getContext(), "Stop loss must be above 0 and below entry price",
                        Toast.LENGTH_SHORT).show();
                return;
            }
        }

        double tradeAmount = 0;
        double shares = 0;
        if (!amountStr.isEmpty()) {
            try { tradeAmount = Double.parseDouble(amountStr); }
            catch (NumberFormatException e) {
                Toast.makeText(getContext(), "Invalid trade amount", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        if (!Double.isFinite(tradeAmount) || tradeAmount < 0) {
            Toast.makeText(getContext(), "Enter a valid non-negative trade amount", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!sharesStr.isEmpty()) {
            try { shares = Double.parseDouble(sharesStr); }
            catch (NumberFormatException e) {
                Toast.makeText(getContext(), "Invalid share quantity", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!Double.isFinite(shares) || shares < 0) {
                Toast.makeText(getContext(), "Enter a valid non-negative share quantity",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            if (sharesArePrimary || amountStr.isEmpty()) {
                tradeAmount = Math.round(shares * price * 100.0) / 100.0;
            }
            else shares = tradeAmount / price;
            if (!Double.isFinite(tradeAmount)) {
                Toast.makeText(getContext(), "Trade amount is too large", Toast.LENGTH_SHORT).show();
                return;
            }
        } else if (tradeAmount > 0) {
            shares = tradeAmount / price;
        }

        double entryFees = 0;
        if (!feesStr.isEmpty()) {
            try { entryFees = Double.parseDouble(feesStr); }
            catch (NumberFormatException e) {
                Toast.makeText(getContext(), "Invalid entry fees", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!Double.isFinite(entryFees) || entryFees < 0) {
                Toast.makeText(getContext(), "Enter valid non-negative entry fees",
                        Toast.LENGTH_SHORT).show();
                return;
            }
        }

        if (isSpxTicker(ticker)) {
            selectedCompanyName = "S&P 500 Index";
            metadataLookupTicker = ticker;
        }
        if (!CryptoHelper.isCryptoSymbol(ticker) && !ticker.equalsIgnoreCase(metadataLookupTicker)) {
            final String metadataTicker = ticker;
            metadataLookupTicker = metadataTicker;
            btnAddStock.setEnabled(false);
            lookupCompanyMetadata(metadataTicker, metadata -> {
                if (!isAdded() || editTicker == null || btnAddStock == null) return;
                if (!metadataTicker.equalsIgnoreCase(
                        editTicker.getText().toString().trim())) {
                    btnAddStock.setEnabled(true);
                    return;
                }
                if (metadata.name != null && !metadata.name.trim().isEmpty()) {
                    selectedCompanyName = metadata.name.trim();
                }
                if (metadata.sector != null
                        && spinnerSector != null
                        && "Sector (optional)".equals(
                        spinnerSector.getSelectedItem().toString())) {
                    selectSector(metadata.sector);
                }
                btnAddStock.setEnabled(true);
                addStock();
            });
            return;
        }

        StockData data  = new StockData(ticker, price, price);
        data.tradeAmount = tradeAmount;
        data.shares = shares;
        data.entryFees = entryFees;
        data.targetPrice = targetPrice;
        data.stopLossPrice = stopLossPrice;
        data.openedAt = selectedOpenedAt;
        data.notes       = notes;
        data.name        = selectedCompanyName;
        String strategy = spinnerStrategy.getSelectedItem().toString();
        data.strategyTag = strategy.endsWith("(optional)") ? "" : strategy;
        String sector = spinnerSector.getSelectedItem().toString();
        data.sector = CryptoHelper.isCryptoSymbol(ticker) ? "Cryptocurrency"
                : sector.endsWith("(optional)") ? "Other" : sector;

        // Firebase key: BINANCE:BTCUSDT -> BINANCE_BTCUSDT
        String firebaseKey = ticker.replace(":", "_");

        StockData submittedData = data;
        stocksRef.child(firebaseKey).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;
                StockData existing = snapshot.getValue(StockData.class);
                if (existing == null) {
                    saveStock(firebaseKey, submittedData, false, false);
                } else {
                    showExistingTradeDialog(firebaseKey, existing, submittedData);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Failed to check open trade: " + error.getMessage(),
                            Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private interface MetadataCallback {
        void onResult(CompanyMetadata metadata);
    }

    private static class CompanyMetadata {
        final String name;
        final String sector;

        CompanyMetadata(String name, String sector) {
            this.name = name;
            this.sector = sector;
        }
    }

    private void lookupCompanyMetadata(String ticker, MetadataCallback callback) {
        String token = BuildConfig.FINNHUB_KEY;
        if (token == null || token.trim().isEmpty()) {
            android.util.Log.w("PortfolioAddStock", "Company lookup skipped: API key is missing");
            callback.onResult(new CompanyMetadata("", null));
            return;
        }
        try {
            String encodedSymbol = URLEncoder.encode(ticker, StandardCharsets.UTF_8.name());
            String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8.name());
            Request request = new Request.Builder()
                    .url("https://finnhub.io/api/v1/stock/profile2?symbol="
                            + encodedSymbol + "&token=" + encodedToken)
                    .build();
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException error) {
                    android.util.Log.w("PortfolioAddStock",
                            "Company lookup failed for " + ticker, error);
                    searchHandler.post(() -> callback.onResult(new CompanyMetadata("", null)));
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response)
                        throws IOException {
                    String name = "";
                    String sector = null;
                    try {
                        if (response.isSuccessful() && response.body() != null) {
                            JSONObject profile = new JSONObject(response.body().string());
                            name = profile.optString("name", "");
                            sector = sectorForIndustry(profile.optString("finnhubIndustry", ""));
                        } else {
                            android.util.Log.w("PortfolioAddStock",
                                    "Company lookup returned HTTP " + response.code()
                                            + " for " + ticker);
                        }
                    } catch (org.json.JSONException error) {
                        android.util.Log.w("PortfolioAddStock",
                                "Could not parse company profile for " + ticker, error);
                    }
                    String finalName = name;
                    String finalSector = sector;
                    searchHandler.post(() ->
                            callback.onResult(new CompanyMetadata(finalName, finalSector)));
                }
            });
        } catch (Exception error) {
            android.util.Log.w("PortfolioAddStock", "Could not prepare company lookup", error);
            callback.onResult(new CompanyMetadata("", null));
        }
    }

    private String sectorForIndustry(String industry) {
        String value = industry == null ? "" : industry.toLowerCase(Locale.US);
        if (value.isEmpty()) return null;
        if (containsAny(value, "software", "semiconductor", "technology", "internet",
                "computer", "electronic", "it services")) return "Technology";
        if (containsAny(value, "biotech", "pharma", "health", "medical", "hospital")) {
            return "Healthcare";
        }
        if (containsAny(value, "bank", "insurance", "capital markets", "financial", "finance",
                "investment", "credit")) return "Financials";
        if (containsAny(value, "energy", "oil", "gas", "coal", "renewable")) return "Energy";
        if (containsAny(value, "utility", "utilities", "electric power")) return "Utilities";
        if (containsAny(value, "real estate", "reit")) return "Real Estate";
        if (containsAny(value, "materials", "chemical", "metal", "mining", "paper")) {
            return "Materials";
        }
        if (containsAny(value, "telecom", "communication", "media", "entertainment")) {
            return "Communication Services";
        }
        if (containsAny(value, "consumer staples", "food", "beverage", "household",
                "personal products", "tobacco")) return "Consumer Staples";
        if (containsAny(value, "consumer", "retail", "apparel", "automotive", "travel",
                "leisure", "restaurant")) return "Consumer Discretionary";
        if (containsAny(value, "industrial", "aerospace", "defense", "machinery",
                "transport", "construction", "commercial services", "producer manufacturing")) {
            return "Industrials";
        }
        return null;
    }

    private boolean containsAny(String value, String... fragments) {
        for (String fragment : fragments) {
            if (value.contains(fragment)) return true;
        }
        return false;
    }

    private void selectSector(String sector) {
        if (spinnerSector == null) return;
        for (int i = 0; i < spinnerSector.getCount(); i++) {
            if (sector.equals(spinnerSector.getItemAtPosition(i).toString())) {
                spinnerSector.setSelection(i);
                return;
            }
        }
    }

    private boolean isSpxTicker(String ticker) {
        return "SPX".equalsIgnoreCase(ticker) || "^SPX".equalsIgnoreCase(ticker)
                || "GSPC".equalsIgnoreCase(ticker) || "^GSPC".equalsIgnoreCase(ticker);
    }

    private void showExistingTradeDialog(String key, StockData existing, StockData added) {
        StockData combined = StockData.combine(existing, added, false);
        String message = "כבר יש לך טרייד פתוח ב-" + added.symbol
                + ".\n\nאם תצרף אותו, מחיר הקנייה הממוצע יהיה "
                + String.format(Locale.US, "$%.2f", combined.buyPrice)
                + " והסכום המושקע יהיה "
                + String.format(Locale.US, "$%,.2f", combined.tradeAmount)
                + ".\n\nאיך תרצה להמשיך?";
        new AlertDialog.Builder(requireContext())
                .setTitle("טרייד פתוח קיים")
                .setMessage(message)
                .setPositiveButton("צרף לטרייד הקיים",
                        (dialog, which) -> saveStock(key, added, true, true))
                .setNeutralButton("השתמש רק בנתונים החדשים",
                        (dialog, which) -> saveStock(key, added, false, true))
                .setNegativeButton("ביטול", null)
                .show();
    }

    private void saveStock(String key, StockData data, boolean combineExisting,
                           boolean duplicateWasConfirmed) {
        userRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                MutableData portfolioData = currentData.child("portfolio-stocks");
                MutableData stockData = portfolioData.child(key);
                StockData existing = stockData.getValue(StockData.class);
                if (existing != null && !duplicateWasConfirmed) {
                    return Transaction.abort();
                }

                MutableData cashData = currentData.child("portfolio-cash");
                Double storedCash = cashData.getValue(Double.class);
                double cash = storedCash != null ? storedCash : 0.0;
                double refund = existing != null && !combineExisting
                        ? Math.max(0, existing.tradeAmount) + Math.max(0, existing.entryFees) : 0;
                double availableCash = cash + refund;
                if (!Double.isFinite(cash) || cash < 0
                        || !Double.isFinite(data.tradeAmount)
                        || data.tradeAmount < 0
                        || data.tradeAmount + data.entryFees > availableCash + 0.0000001) {
                    return Transaction.abort();
                }

                StockData updated = existing != null && combineExisting
                        ? StockData.combine(existing, data, false)
                        : data;
                stockData.setValue(updated);
                cashData.setValue(Math.max(0, availableCash - data.tradeAmount - data.entryFees));
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError error, boolean committed,
                                   @Nullable DataSnapshot currentData) {
                if (!isAdded()) return;
                if (error != null) {
                    Toast.makeText(getContext(), "שגיאה בהוספה: " + error.getMessage(),
                            Toast.LENGTH_LONG).show();
                    return;
                }
                if (!committed) {
                    if (currentData == null) {
                        Toast.makeText(getContext(), "Could not open this trade",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    StockData existing = currentData.child("portfolio-stocks")
                            .child(key).getValue(StockData.class);
                    if (existing != null) {
                        if (!duplicateWasConfirmed) {
                            showExistingTradeDialog(key, existing, data);
                        } else {
                            double cash = getCashBalance(currentData);
                            double refund = combineExisting ? 0
                                    : Math.max(0, existing.tradeAmount)
                                    + Math.max(0, existing.entryFees);
                            double availableCash = cash + refund;
                            if (Double.isFinite(availableCash)
                                    && data.tradeAmount + data.entryFees > availableCash + 0.0000001) {
                                Toast.makeText(getContext(),
                                        String.format(Locale.US,
                                                "Insufficient cash. Available: $%,.2f",
                                                Math.max(0, availableCash)),
                                        Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(getContext(), "Could not open this trade",
                                        Toast.LENGTH_LONG).show();
                            }
                        }
                    } else {
                        double cash = getCashBalance(currentData);
                        if (data.tradeAmount + data.entryFees > cash + 0.0000001) {
                            Toast.makeText(getContext(),
                                    String.format(Locale.US,
                                            "Insufficient cash. Available: $%,.2f",
                                            Math.max(0, cash)),
                                    Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(getContext(), "Could not open this trade",
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                    return;
                }
                Toast.makeText(getContext(), "נוסף לתיק!", Toast.LENGTH_SHORT).show();
                if (entryScreenshotUri != null) {
                    Uri imageUri = entryScreenshotUri;
                    entryScreenshotUri = null;
                    FirebaseAuth currentAuth = FirebaseAuth.getInstance();
                    if (currentAuth.getCurrentUser() != null) {
                        String uid = currentAuth.getCurrentUser().getUid();
                        Context appContext = requireContext().getApplicationContext();
                        TradeScreenshotStorage.uploadAndAttach(appContext, imageUri, uid,
                                userRef, "portfolio-stocks/" + key,
                                (success, message) -> android.widget.Toast.makeText(appContext,
                                        message, success ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG)
                                        .show());
                    }
                }
                if (getActivity() != null) {
                    getActivity().getSupportFragmentManager().popBackStack();
                }
            }
        });
    }

    private double getCashBalance(DataSnapshot userSnapshot) {
        Double cash = userSnapshot.child("portfolio-cash").getValue(Double.class);
        return cash != null ? cash : 0.0;
    }
}
