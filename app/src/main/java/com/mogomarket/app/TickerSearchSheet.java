package com.mogomarket.app;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
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
    private static final long DEBOUNCE_MS = 300;

    private OnTickerSelectedListener listener;
    private final OkHttpClient client = new OkHttpClient();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;

    private static final List<ChartFragment.StockSuggestion> POPULAR = Arrays.asList(
        new ChartFragment.StockSuggestion("SPY",  "S&P 500 ETF",        "ETF"),
        new ChartFragment.StockSuggestion("AAPL", "Apple Inc.",          "NASDAQ"),
        new ChartFragment.StockSuggestion("TSLA", "Tesla Inc.",          "NASDAQ"),
        new ChartFragment.StockSuggestion("NVDA", "NVIDIA Corporation",  "NASDAQ"),
        new ChartFragment.StockSuggestion("AMZN", "Amazon.com Inc.",     "NASDAQ"),
        new ChartFragment.StockSuggestion("MSFT", "Microsoft Corp.",     "NASDAQ"),
        new ChartFragment.StockSuggestion("BTC",  "Bitcoin",             "CRYPTO"),
        new ChartFragment.StockSuggestion("ETH",  "Ethereum",            "CRYPTO")
    );

    public void setOnTickerSelectedListener(OnTickerSelectedListener l) {
        this.listener = l;
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

        // פרוס על כל המסך
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
        ListView resultsList             = view.findViewById(R.id.searchResultsList);
        TextView labelResults            = view.findViewById(R.id.labelResults);

        ArrayAdapter<ChartFragment.StockSuggestion> adapter =
            new ArrayAdapter<ChartFragment.StockSuggestion>(
                requireContext(),
                android.R.layout.simple_list_item_2,
                android.R.id.text1,
                new ArrayList<>(POPULAR)) {

            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                if (convertView == null) {
                    convertView = LayoutInflater.from(getContext())
                        .inflate(android.R.layout.simple_list_item_2, parent, false);
                }
                ChartFragment.StockSuggestion item = getItem(position);
                TextView t1 = convertView.findViewById(android.R.id.text1);
                TextView t2 = convertView.findViewById(android.R.id.text2);
                if (item != null) {
                    t1.setText(item.symbol);
                    t1.setTextColor(0xFFE6EDF3);
                    t1.setTextSize(15f);
                    t1.setTypeface(null, Typeface.BOLD);
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
            if (s != null && listener != null) {
                listener.onTickerSelected(s.symbol);
            }
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
                pendingSearch = () -> fetchSuggestions(q, adapter);
                handler.postDelayed(pendingSearch, DEBOUNCE_MS);
            }
        });

        // פתח מקלדת אוטומטית
        searchInput.requestFocus();
        searchInput.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager)
                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null)
                imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT);
        }, 150);
        searchInput.setHintTextColor(0xFF4A5568);
    }

    private void fetchSuggestions(String query,
                                   ArrayAdapter<ChartFragment.StockSuggestion> adapter) {
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name());
            String url = "https://finnhub.io/api/v1/search?q=" + encoded + "&token=" + FINNHUB_KEY;
            Request req = new Request.Builder().url(url).build();
            client.newCall(req).enqueue(new Callback() {
                @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {}
                @Override public void onResponse(@NonNull Call call,
                                                  @NonNull Response response) throws IOException {
                    if (!response.isSuccessful() || response.body() == null) return;
                    try {
                        JSONObject root = new JSONObject(response.body().string());
                        JSONArray arr   = root.optJSONArray("result");
                        if (arr == null) return;
                        List<ChartFragment.StockSuggestion> results = new ArrayList<>();
                        for (int i = 0; i < Math.min(arr.length(), 10); i++) {
                            JSONObject o = arr.getJSONObject(i);
                            String sym  = o.optString("symbol", "");
                            String name = o.optString("description", "");
                            String exch = o.optString("type", "");
                            if (!sym.isEmpty())
                                results.add(new ChartFragment.StockSuggestion(sym, name, exch));
                        }
                        if (getActivity() != null) getActivity().runOnUiThread(() -> {
                            adapter.clear();
                            adapter.addAll(results);
                            adapter.notifyDataSetChanged();
                        });
                    } catch (Exception ignored) {}
                }
            });
        } catch (Exception ignored) {}
    }
}
