package com.mogomarket.app;

import android.content.Context;
import android.os.Build;
import android.util.Log;
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

    private static final String TAG = "WatchlistAdapter";
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
    private boolean ascending = true;

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

    public void toggleSortOrder() {
        ascending = !ascending;
        applyFilters();
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

            displayList.add(s);
        }

        switch (currentFilter) {
            case "gain":
                Collections.sort(displayList, (a, b) ->
                        ascending
                                ? Float.compare(a.dayChange, b.dayChange)
                                : Float.compare(b.dayChange, a.dayChange));
                break;

            case "loss":
                Collections.sort(displayList, (a, b) ->
                        ascending
                                ? Float.compare(a.dayChange, b.dayChange)
                                : Float.compare(b.dayChange, a.dayChange));
                break;

            case "alpha":
                Collections.sort(displayList, (a, b) ->
                        ascending
                                ? a.symbol.compareToIgnoreCase(b.symbol)
                                : b.symbol.compareToIgnoreCase(a.symbol));
                break;

            default:
                break;
        }

        notifyDataSetChanged();
    }

    private boolean isCrypto(String symbol) {
        return symbol != null && symbol.contains(":");
    }

    private boolean isForex(String symbol) {
        return symbol != null && (symbol.endsWith("=X") || symbol.equals("GC=F")
                || symbol.equals("SI=F") || symbol.equals("BZ=F") || symbol.equals("CL=F")
                || ChartFragment.FOREX_MAP.containsKey(symbol.toUpperCase(Locale.US)));
    }

    private String mapSymbolForQuote(String raw) {
        if (raw == null) return "";
        String upper = raw.trim().toUpperCase(Locale.US);

        String crypto = ChartFragment.CRYPTO_MAP.get(upper);
        if (crypto != null) return crypto;

        String forex = ChartFragment.FOREX_MAP.get(upper);
        if (forex != null) return forex;

        return upper;
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

        String requestSymbol = mapSymbolForQuote(stock.symbol);
        String url;

        if (isCrypto(requestSymbol)) {
            long to = System.currentTimeMillis() / 1000L;
            long from = to - (3L * 24 * 60 * 60);
            url = "https://finnhub.io/api/v1/crypto/candle?symbol=" + requestSymbol
                    + "&resolution=D&from=" + from + "&to=" + to
                    + "&token=" + FINNHUB_KEY;
        } else {
            url = "https://finnhub.io/api/v1/quote?symbol=" + requestSymbol
                    + "&token=" + FINNHUB_KEY;
        }

        Request request = new Request.Builder().url(url).build();
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
                    JSONObject json = new JSONObject(body);
                    float price;
                    float dayChange;

                    if (isCrypto(requestSymbol)) {
                        if (!"ok".equals(json.optString("s"))) {
                            if (targetSymbol.equals(holder.itemView.getTag())) showDash(holder, textSecondary);
                            return;
                        }

                        JSONArray closes = json.getJSONArray("c");
                        int len = closes.length();
                        if (len == 0) {
                            if (targetSymbol.equals(holder.itemView.getTag())) showDash(holder, textSecondary);
                            return;
                        }

                        price = (float) closes.getDouble(len - 1);
                        float prev = len > 1 ? (float) closes.getDouble(len - 2) : price;
                        dayChange = prev > 0 ? ((price - prev) / prev) * 100f : 0f;

                    } else {
                        float c = (float) json.optDouble("c", 0);
                        float pc = (float) json.optDouble("pc", 0);
                        float dp = (float) json.optDouble("dp", 0);

                        price = (c > 0) ? c : pc;
                        dayChange = (c > 0) ? dp : 0f;

                        if (price <= 0) {
                            if (targetSymbol.equals(holder.itemView.getTag())) showDash(holder, textSecondary);
                            return;
                        }
                    }

                    quoteCache.put(stock.symbol, new float[]{price, dayChange});
                    stock.currentPrice = price;
                    stock.dayChange = dayChange;

                    final float fPrice = price;
                    final float fChange = dayChange;

                    holder.priceText.post(() -> {
                        if (!targetSymbol.equals(holder.itemView.getTag())) return;
                        holder.priceText.setText(formatPrice(stock.symbol, fPrice));
                        holder.priceText.setTextColor(colorPrimary);
                    });

                    holder.dayChangeText.post(() -> {
                        if (!targetSymbol.equals(holder.itemView.getTag())) return;
                        bindChange(holder.dayChangeText, fChange, colorGain, colorLoss);
                    });

                    processAlert(stock, price, ctx);

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