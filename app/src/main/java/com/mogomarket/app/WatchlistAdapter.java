package com.mogomarket.app;

import android.content.Context;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class WatchlistAdapter extends RecyclerView.Adapter<WatchlistAdapter.ViewHolder> {

    private static final String FINNHUB_KEY = "d918pn9r01qr1uqui560d918pn9r01qr1uqui56g";

    public interface OnWatchStockClickListener {
        void onStockClick(String symbol);
        void onStockDelete(String symbol);
        void onSetPriceAlert(StockWatchData stock);
        void onAlertStateChanged(String symbol, boolean triggered);
    }

    private final OnWatchStockClickListener listener;
    private final List<StockWatchData> fullList = new ArrayList<>();
    private final List<StockWatchData> displayList = new ArrayList<>();
    private final OkHttpClient client = new OkHttpClient();

    private final Map<String, float[]> quoteCache = new HashMap<>();
    private final Set<String> loading = new HashSet<>();

    private String currentSearch = "";
    private String currentFilter = "default";
    private static final int PERCENT_SORT_DEFAULT = 0;
    private static final int PERCENT_SORT_GAIN_FIRST = 1;
    private static final int PERCENT_SORT_LOSS_FIRST = 2;

    private boolean ascending = true;
    private int percentSortMode = PERCENT_SORT_DEFAULT;

    public WatchlistAdapter(OnWatchStockClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<StockWatchData> items) {
        fullList.clear();
        if (items != null) fullList.addAll(items);
        applyFilters();
    }

    public void setSearch(String query) {
        currentSearch = query == null ? "" : query.trim().toLowerCase(Locale.US);
        applyFilters();
    }

    public void setFilter(String filter) {
        currentFilter = filter == null ? "default" : filter;
        applyFilters();
    }

    public void cyclePercentSortOrder() {
        percentSortMode++;

        if (percentSortMode > PERCENT_SORT_LOSS_FIRST) {
            percentSortMode = PERCENT_SORT_DEFAULT;
        }

        applyFilters();
    }

    public int getPercentSortMode() {
        return percentSortMode;
    }

    public boolean isAscending() {
        return ascending;
    }

    public void refresh() {
        quoteCache.clear();
        loading.clear();
        notifyDataSetChanged();
    }

    public void moveItem(int from, int to) {
        if (from < 0 || to < 0 || from >= displayList.size() || to >= displayList.size()) return;
        Collections.swap(displayList, from, to);
        notifyItemMoved(from, to);
    }

    private void applyFilters() {
        displayList.clear();

        for (StockWatchData s : fullList) {
            if (s == null || s.symbol == null) continue;

            String symbolLower = s.symbol.toLowerCase(Locale.US);
            if (!currentSearch.isEmpty() && !symbolLower.contains(currentSearch)) continue;

            if ("gain".equals(currentFilter) && s.dayChange < 0f) continue;
            if ("loss".equals(currentFilter) && s.dayChange >= 0f) continue;

            displayList.add(s);
        }

        switch (currentFilter) {
            case "gain":
            case "loss":
                if (percentSortMode == PERCENT_SORT_GAIN_FIRST) {
                    Collections.sort(displayList, (a, b) ->
                            Float.compare(b.dayChange, a.dayChange));
                } else if (percentSortMode == PERCENT_SORT_LOSS_FIRST) {
                    Collections.sort(displayList, (a, b) ->
                            Float.compare(a.dayChange, b.dayChange));
                }
                break;

            case "alpha":
                Collections.sort(displayList, (a, b) ->
                        ascending
                                ? a.symbol.compareToIgnoreCase(b.symbol)
                                : b.symbol.compareToIgnoreCase(a.symbol));
                break;

            default:
                if (percentSortMode == PERCENT_SORT_GAIN_FIRST) {
                    Collections.sort(displayList, (a, b) ->
                            Float.compare(b.dayChange, a.dayChange));
                } else if (percentSortMode == PERCENT_SORT_LOSS_FIRST) {
                    Collections.sort(displayList, (a, b) ->
                            Float.compare(a.dayChange, b.dayChange));
                }
                break;
        }

        notifyDataSetChanged();
    }

    public String getCurrentFilter() {
        return currentFilter;
    }

    private boolean isCrypto(String symbol) {
        return symbol != null && symbol.contains(":");
    }

    private boolean isForex(String symbol) {
        if (symbol == null) return false;

        String upper = symbol.trim().toUpperCase(Locale.US);

        return upper.endsWith("=X")
                || upper.equals("GC=F")
                || upper.equals("SI=F")
                || upper.equals("BZ=F")
                || upper.equals("CL=F")
                || ChartFragment.FOREX_MAP.containsKey(upper)
                || ChartFragment.FOREX_MAP.containsValue(upper);
    }

    private String normalizeUserSymbol(String raw) {
        if (raw == null) return "";
        return raw.trim()
                .toUpperCase(Locale.US)
                .replace(" ", "")
                .replace("/", "")
                .replace("-", "")
                .replace("_", "");
    }

    private String mapSymbolForQuote(String raw) {
        String normalized = normalizeUserSymbol(raw);
        if (normalized.isEmpty()) return "";

        String crypto = ChartFragment.CRYPTO_MAP.get(normalized);
        if (crypto != null) return crypto;

        String forex = ChartFragment.FOREX_MAP.get(normalized);
        if (forex != null) return forex;

        return normalized;
    }

    private String formatPrice(String symbol, float price) {
        return String.format(Locale.US, isForex(symbol) ? "$%.4f" : "$%.2f", price);
    }

    private String displaySymbol(String symbol) {
        if (symbol == null) return "";
        if (isCrypto(symbol)) return symbol.substring(symbol.indexOf(':') + 1);
        return symbol.replace("=X", "").replace("=F", "");
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_watchlist_stock, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        StockWatchData stock = displayList.get(position);
        Context ctx = holder.itemView.getContext();

        holder.itemView.setTag(stock.symbol);

        int textPrimary = ctx.getColor(R.color.text_primary);
        int textSecondary = ctx.getColor(R.color.text_secondary);
        int colorGain = ctx.getColor(R.color.gain);
        int colorLoss = ctx.getColor(R.color.loss);
        int colorPrimary = ctx.getColor(R.color.primary);

        holder.symbolText.setText(displaySymbol(stock.symbol));
        holder.symbolText.setTextColor(textPrimary);

        float[] cached = quoteCache.get(stock.symbol);
        if (cached != null) {
            stock.currentPrice = cached[0];
            stock.dayChange = cached[1];
            holder.priceText.setText(formatPrice(stock.symbol, cached[0]));
            holder.priceText.setTextColor(colorPrimary);
            bindChange(holder.dayChangeText, cached[1], colorGain, colorLoss);
        } else {
            holder.priceText.setText("...");
            holder.priceText.setTextColor(textSecondary);
            holder.dayChangeText.setText("");

            if (!loading.contains(stock.symbol)) {
                loading.add(stock.symbol);
                final String targetSymbol = stock.symbol;
                fetchQuote(stock, holder, targetSymbol, colorPrimary, textSecondary, colorGain, colorLoss, ctx);
            }
        }

        if (stock.alertEnabled && stock.alertTargetPrice > 0) {
            holder.alertText.setText(String.format(
                    Locale.US,
                    isForex(stock.symbol) ? "🔔 $%.4f" : "🔔 $%.2f",
                    stock.alertTargetPrice
            ));
            holder.alertText.setTextColor(colorPrimary);
        } else {
            holder.alertText.setText("🔕 Off");
            holder.alertText.setTextColor(textSecondary);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onStockClick(stock.symbol);
        });

        if (holder.btnDelete != null) {
            holder.btnDelete.setOnClickListener(v -> {
                if (listener != null) listener.onStockDelete(stock.symbol);
            });
        }

        if (holder.btnAlert != null) {
            holder.btnAlert.setOnClickListener(v -> {
                if (listener != null) listener.onSetPriceAlert(stock);
            });
        }
    }

    @Override
    public int getItemCount() {
        return displayList.size();
    }

    private void fetchQuote(StockWatchData stock,
                            ViewHolder holder,
                            String targetSymbol,
                            int colorPrimary,
                            int textSecondary,
                            int colorGain,
                            int colorLoss,
                            Context ctx) {

        final String requestSymbol = mapSymbolForQuote(stock.symbol);

        if (isCrypto(requestSymbol)) {
            final String pair = requestSymbol.contains(":")
                    ? requestSymbol.substring(requestSymbol.indexOf(':') + 1)
                    : requestSymbol;

            String url = "https://api.binance.com/api/v3/klines?symbol=" + pair + "&interval=1d&limit=2";

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    loading.remove(stock.symbol);
                    if (!targetSymbol.equals(holder.itemView.getTag())) return;
                    showDash(holder, textSecondary);
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    loading.remove(stock.symbol);

                    if (response.body() == null) {
                        if (!targetSymbol.equals(holder.itemView.getTag())) return;
                        showDash(holder, textSecondary);
                        return;
                    }

                    String body = response.body().string();

                    if (!response.isSuccessful()) {
                        if (!targetSymbol.equals(holder.itemView.getTag())) return;
                        showDash(holder, textSecondary);
                        return;
                    }

                    try {
                        JSONArray arr = new JSONArray(body);

                        if (arr.length() < 2) {
                            if (targetSymbol.equals(holder.itemView.getTag())) {
                                showDash(holder, textSecondary);
                            }
                            return;
                        }

                        JSONArray prevBar = arr.getJSONArray(arr.length() - 2);
                        JSONArray lastBar = arr.getJSONArray(arr.length() - 1);

                        float prevClose = Float.parseFloat(prevBar.getString(4));
                        float lastClose = Float.parseFloat(lastBar.getString(4));

                        if (lastClose <= 0f) {
                            if (targetSymbol.equals(holder.itemView.getTag())) {
                                showDash(holder, textSecondary);
                            }
                            return;
                        }

                        float computedDayChange = prevClose > 0f
                                ? ((lastClose - prevClose) / prevClose) * 100f
                                : 0f;

                        final float finalPrice = lastClose;
                        final float finalDayChange = computedDayChange;

                        quoteCache.put(stock.symbol, new float[]{finalPrice, finalDayChange});
                        stock.currentPrice = finalPrice;
                        stock.dayChange = finalDayChange;

                        holder.priceText.post(() -> {
                            if (!targetSymbol.equals(holder.itemView.getTag())) return;
                            holder.priceText.setText(formatPrice(stock.symbol, finalPrice));
                            holder.priceText.setTextColor(colorPrimary);
                        });

                        holder.dayChangeText.post(() -> {
                            if (!targetSymbol.equals(holder.itemView.getTag())) return;
                            bindChange(holder.dayChangeText, finalDayChange, colorGain, colorLoss);
                        });

                        processAlert(stock, finalPrice, ctx);

                    } catch (Exception e) {
                        if (targetSymbol.equals(holder.itemView.getTag())) {
                            showDash(holder, textSecondary);
                        }
                    }
                }
            });

            return;
        }

        String url = "https://query1.finance.yahoo.com/v8/finance/chart/" + requestSymbol
                + "?interval=1d&range=5d&includePrePost=false";

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                loading.remove(stock.symbol);
                if (!targetSymbol.equals(holder.itemView.getTag())) return;
                showDash(holder, textSecondary);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                loading.remove(stock.symbol);

                if (response.body() == null) {
                    if (!targetSymbol.equals(holder.itemView.getTag())) return;
                    showDash(holder, textSecondary);
                    return;
                }

                String body = response.body().string();

                if (!response.isSuccessful()) {
                    if (!targetSymbol.equals(holder.itemView.getTag())) return;
                    showDash(holder, textSecondary);
                    return;
                }

                try {
                    JSONObject root = new JSONObject(body);
                    JSONArray result = root.getJSONObject("chart").optJSONArray("result");

                    if (result == null || result.length() == 0) {
                        if (targetSymbol.equals(holder.itemView.getTag())) {
                            showDash(holder, textSecondary);
                        }
                        return;
                    }

                    JSONObject item = result.getJSONObject(0);
                    JSONObject indicators = item.getJSONObject("indicators");
                    JSONArray quoteArray = indicators.getJSONArray("quote");

                    if (quoteArray.length() == 0) {
                        if (targetSymbol.equals(holder.itemView.getTag())) {
                            showDash(holder, textSecondary);
                        }
                        return;
                    }

                    JSONObject quote = quoteArray.getJSONObject(0);
                    JSONArray closes = quote.getJSONArray("close");

                    float tempLastClose = 0f;
                    float tempPrevClose = 0f;

                    for (int i = closes.length() - 1; i >= 0; i--) {
                        if (!closes.isNull(i)) {
                            tempLastClose = (float) closes.getDouble(i);

                            for (int j = i - 1; j >= 0; j--) {
                                if (!closes.isNull(j)) {
                                    tempPrevClose = (float) closes.getDouble(j);
                                    break;
                                }
                            }
                            break;
                        }
                    }

                    if (tempLastClose <= 0f) {
                        if (targetSymbol.equals(holder.itemView.getTag())) {
                            showDash(holder, textSecondary);
                        }
                        return;
                    }

                    float computedDayChange = tempPrevClose > 0f
                            ? ((tempLastClose - tempPrevClose) / tempPrevClose) * 100f
                            : 0f;

                    final float finalLastClose = tempLastClose;
                    final float finalDayChange = computedDayChange;

                    quoteCache.put(stock.symbol, new float[]{finalLastClose, finalDayChange});
                    stock.currentPrice = finalLastClose;
                    stock.dayChange = finalDayChange;

                    holder.priceText.post(() -> {
                        if (!targetSymbol.equals(holder.itemView.getTag())) return;
                        holder.priceText.setText(formatPrice(stock.symbol, finalLastClose));
                        holder.priceText.setTextColor(colorPrimary);
                    });

                    holder.dayChangeText.post(() -> {
                        if (!targetSymbol.equals(holder.itemView.getTag())) return;
                        bindChange(holder.dayChangeText, finalDayChange, colorGain, colorLoss);
                    });

                    processAlert(stock, finalLastClose, ctx);

                } catch (Exception e) {
                    if (targetSymbol.equals(holder.itemView.getTag())) {
                        showDash(holder, textSecondary);
                    }
                }
            }
        });
    }

    private void showDash(ViewHolder holder, int textSecondary) {
        holder.priceText.post(() -> {
            holder.priceText.setText("$—");
            holder.priceText.setTextColor(textSecondary);
        });

        holder.dayChangeText.post(() -> {
            holder.dayChangeText.setText("—");
            holder.dayChangeText.setTextColor(textSecondary);
        });
    }

    private void bindChange(TextView tv, float change, int colorGain, int colorLoss) {
        if (change >= 0) {
            tv.setText(String.format(Locale.US, "▲ +%.2f%%", change));
            tv.setTextColor(colorGain);
        } else {
            tv.setText(String.format(Locale.US, "▼ %.2f%%", change));
            tv.setTextColor(colorLoss);
        }
    }

    private void processAlert(StockWatchData stock, float price, Context ctx) {
        if (!stock.alertEnabled || stock.alertTargetPrice <= 0 || stock.alertTriggered) return;
        if (price < stock.alertTargetPrice) return;

        String symbol = stock.symbol;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.NotificationChannel ch = new android.app.NotificationChannel(
                    "price_alerts",
                    "Price Alerts",
                    android.app.NotificationManager.IMPORTANCE_HIGH
            );
            android.app.NotificationManager nm =
                    (android.app.NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(ch);
        }

        String displaySym = displaySymbol(symbol);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, "price_alerts")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("📈 Alert: " + displaySym)
                .setContentText(String.format(
                        Locale.US,
                        isForex(symbol)
                                ? "%s reached $%.4f, current: $%.4f"
                                : "%s reached $%.2f, current: $%.2f",
                        displaySym,
                        stock.alertTargetPrice,
                        price
                ))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        try {
            NotificationManagerCompat.from(ctx).notify(symbol.hashCode(), builder.build());
        } catch (SecurityException ignored) {
        }

        stock.alertTriggered = true;
        stock.alertEnabled = false;

        if (listener != null) {
            listener.onAlertStateChanged(symbol, true);
        }

        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            String safeKey = symbol.replace(":", "_");
            DatabaseReference ref = FirebaseDatabase.getInstance()
                    .getReference("users/" + auth.getCurrentUser().getUid()
                            + "/watchlist-stocks/" + safeKey);
            ref.child("alertEnabled").setValue(false);
            ref.child("alertTriggered").setValue(true);
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView symbolText, priceText, dayChangeText, alertText;
        ImageButton btnDelete, btnAlert;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            symbolText = itemView.findViewById(R.id.stockSymbolText);
            priceText = itemView.findViewById(R.id.stockPriceText);
            dayChangeText = itemView.findViewById(R.id.stockDayChangeText);
            alertText = itemView.findViewById(R.id.stockAlertText);
            btnDelete = itemView.findViewById(R.id.btnDeleteStock);
            btnAlert = itemView.findViewById(R.id.btnSetAlert);
        }
    }
}