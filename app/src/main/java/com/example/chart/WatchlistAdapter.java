package com.example.chart;

import android.content.Context;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class WatchlistAdapter extends RecyclerView.Adapter<WatchlistAdapter.ViewHolder> {

    public interface OnWatchStockClickListener {
        void onStockClick(String symbol);
        void onStockDelete(String symbol);
        void onSetPriceAlert(StockWatchData stock);
        void onAlertStateChanged(String symbol, boolean triggered);
    }

    private static final String FINNHUB_KEY = "d0omel1r01qua2guc4ggd0omel1r01qua2guc4h0";

    private final List<StockWatchData> masterList  = new ArrayList<>();
    private final List<StockWatchData> displayList = new ArrayList<>();
    private final OnWatchStockClickListener listener;
    private final OkHttpClient client = new OkHttpClient();

    // Cache: symbol -> [price, dayChange] — מונע קריאות כפולות בגלילה
    private final Map<String, float[]> quoteCache = new HashMap<>();
    // סמבולים שכרגע בטעינה — מונע קריאות מקבילות לאותו סמבול
    private final java.util.Set<String> loading = new java.util.HashSet<>();

    private String currentSearch = "";
    private String currentFilter = "default";

    public WatchlistAdapter(OnWatchStockClickListener listener) {
        this.listener = listener;
    }

    private boolean isCrypto(String sym) {
        return sym != null && sym.contains(":");
    }

    public void updateData(List<StockWatchData> fresh) {
        masterList.clear();
        if (fresh != null) masterList.addAll(fresh);
        applyFilterAndSearch();
    }

    public void refresh() {
        // מנקה cache כדי לאלץ טעינה מחדש
        quoteCache.clear();
        loading.clear();
        applyFilterAndSearch();
    }

    public void setSearch(String query) {
        currentSearch = query == null ? "" : query.toLowerCase().trim();
        applyFilterAndSearch();
    }

    public void setFilter(String filter) {
        currentFilter = filter == null ? "default" : filter;
        applyFilterAndSearch();
    }

    private void applyFilterAndSearch() {
        List<StockWatchData> result = new ArrayList<>();
        for (StockWatchData s : masterList) {
            if (s == null || s.symbol == null) continue;
            if (!currentSearch.isEmpty() && !s.symbol.toLowerCase().contains(currentSearch)) continue;
            switch (currentFilter) {
                case "gain": if (s.dayChange <  0) continue; break;
                case "loss": if (s.dayChange >= 0) continue; break;
            }
            result.add(s);
        }
        if ("alpha".equals(currentFilter))
            Collections.sort(result, (a, b) -> a.symbol.compareToIgnoreCase(b.symbol));
        else if ("gain".equals(currentFilter))
            Collections.sort(result, (a, b) -> Float.compare(b.dayChange, a.dayChange));
        else if ("loss".equals(currentFilter))
            Collections.sort(result, (a, b) -> Float.compare(a.dayChange, b.dayChange));

        displayList.clear();
        displayList.addAll(result);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_watchlist_stock, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        StockWatchData stock = displayList.get(position);
        Context ctx = holder.itemView.getContext();

        int textPrimary   = ctx.getColor(R.color.text_primary);
        int textSecondary = ctx.getColor(R.color.text_secondary);
        int colorGain     = ctx.getColor(R.color.gain);
        int colorLoss     = ctx.getColor(R.color.loss);
        int colorPrimary  = ctx.getColor(R.color.primary);

        String displaySymbol = isCrypto(stock.symbol)
                ? stock.symbol.substring(stock.symbol.indexOf(':') + 1)
                : stock.symbol;
        holder.symbolText.setText(displaySymbol);
        holder.symbolText.setTextColor(textPrimary);

        // אם יש cache — מציגים מיד
        float[] cached = quoteCache.get(stock.symbol);
        if (cached != null) {
            stock.currentPrice = cached[0];
            stock.dayChange    = cached[1];
            holder.priceText.setText(String.format(Locale.US, "$%.2f", cached[0]));
            holder.priceText.setTextColor(colorPrimary);
            bindChange(holder.dayChangeText, cached[1], colorGain, colorLoss);
        } else {
            holder.priceText.setText("...");
            holder.priceText.setTextColor(textSecondary);
            holder.dayChangeText.setText("");

            // אם כבר בטעינה — לא שולחים בקשה כפולה
            if (!loading.contains(stock.symbol)) {
                loading.add(stock.symbol);
                fetchQuote(stock, holder, colorPrimary, textSecondary, colorGain, colorLoss, ctx);
            }
        }

        if (stock.alertEnabled && stock.alertTargetPrice > 0) {
            holder.alertText.setText(String.format(Locale.US, "\uD83D\uDD14 $%.2f", stock.alertTargetPrice));
            holder.alertText.setTextColor(colorPrimary);
        } else {
            holder.alertText.setText("\uD83D\uDD15 Off");
            holder.alertText.setTextColor(textSecondary);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onStockClick(stock.symbol);
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) listener.onStockDelete(stock.symbol);
            return true;
        });
    }

    @Override public int getItemCount() { return displayList.size(); }

    private void fetchQuote(StockWatchData stock, ViewHolder holder,
                            int colorPrimary, int textSecondary,
                            int colorGain, int colorLoss, Context ctx) {
        String url;
        if (isCrypto(stock.symbol)) {
            long to   = System.currentTimeMillis() / 1000L;
            long from = to - (3L * 24 * 60 * 60);
            url = "https://finnhub.io/api/v1/crypto/candle?symbol=" + stock.symbol
                    + "&resolution=D&from=" + from + "&to=" + to
                    + "&token=" + FINNHUB_KEY;
        } else {
            url = "https://finnhub.io/api/v1/quote?symbol=" + stock.symbol
                    + "&token=" + FINNHUB_KEY;
        }

        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                loading.remove(stock.symbol);
                holder.priceText.post(() -> {
                    holder.priceText.setText("$\u2014");
                    holder.priceText.setTextColor(textSecondary);
                    holder.dayChangeText.setText("\u2014");
                    holder.dayChangeText.setTextColor(textSecondary);
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                loading.remove(stock.symbol);
                if (!response.isSuccessful() || response.body() == null) return;
                try {
                    String body = response.body().string();
                    JSONObject json = new JSONObject(body);

                    float price, dayChange;
                    if (isCrypto(stock.symbol)) {
                        // קריפטו: /crypto/candle
                        if (!"ok".equals(json.optString("s"))) return;
                        JSONArray closes = json.getJSONArray("c");
                        int len = closes.length();
                        if (len == 0) return;
                        price = (float) closes.getDouble(len - 1);
                        float prev = len > 1 ? (float) closes.getDouble(len - 2) : price;
                        dayChange = prev > 0 ? ((price - prev) / prev) * 100f : 0f;
                    } else {
                        // מניה: /quote
                        price     = (float) json.getDouble("c");
                        dayChange = (float) json.getDouble("dp");
                        if (price <= 0) return;
                    }

                    // שמירה ב-cache
                    quoteCache.put(stock.symbol, new float[]{price, dayChange});
                    stock.currentPrice = price;
                    stock.dayChange    = dayChange;

                    holder.priceText.post(() -> {
                        holder.priceText.setText(String.format(Locale.US, "$%.2f", price));
                        holder.priceText.setTextColor(colorPrimary);
                    });
                    holder.dayChangeText.post(() ->
                            bindChange(holder.dayChangeText, dayChange, colorGain, colorLoss));

                    processAlert(stock, price, ctx);

                } catch (Exception e) {
                    holder.priceText.post(() -> {
                        holder.priceText.setText("$\u2014");
                        holder.priceText.setTextColor(textSecondary);
                    });
                }
            }
        });
    }

    private void bindChange(TextView tv, float change, int colorGain, int colorLoss) {
        if (change >= 0) {
            tv.setText(String.format(Locale.US, "\u25b2 +%.2f%%", change));
            tv.setTextColor(colorGain);
        } else {
            tv.setText(String.format(Locale.US, "\u25bc %.2f%%", change));
            tv.setTextColor(colorLoss);
        }
    }

    private void processAlert(StockWatchData stock, float price, Context ctx) {
        if (!stock.alertEnabled || stock.alertTargetPrice <= 0 || stock.alertTriggered) return;
        if (price < stock.alertTargetPrice) return;

        String symbol = stock.symbol;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.NotificationChannel ch = new android.app.NotificationChannel(
                    "price_alerts", "Price Alerts",
                    android.app.NotificationManager.IMPORTANCE_HIGH);
            android.app.NotificationManager nm =
                    (android.app.NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(ch);
        }

        String displaySym = isCrypto(symbol)
                ? symbol.substring(symbol.indexOf(':') + 1)
                : symbol;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, "price_alerts")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("\uD83D\uDCC8 Alert: " + displaySym)
                .setContentText(String.format(Locale.US,
                        "%s הגיע ליעד $%.2f! מחיר: $%.2f",
                        displaySym, stock.alertTargetPrice, price))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        try {
            NotificationManagerCompat.from(ctx).notify(symbol.hashCode(), builder.build());
        } catch (SecurityException ignored) {}

        stock.alertTriggered = true;
        stock.alertEnabled   = false;
        if (listener != null) listener.onAlertStateChanged(symbol, true);

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
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            symbolText    = itemView.findViewById(R.id.stockSymbolText);
            priceText     = itemView.findViewById(R.id.stockPriceText);
            dayChangeText = itemView.findViewById(R.id.stockDayChangeText);
            alertText     = itemView.findViewById(R.id.stockAlertText);
        }
    }
}
