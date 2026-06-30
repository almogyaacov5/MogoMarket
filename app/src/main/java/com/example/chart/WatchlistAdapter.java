package com.example.chart;

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

import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class WatchlistAdapter extends RecyclerView.Adapter<WatchlistAdapter.WatchViewHolder> {

    public interface OnWatchStockClickListener {
        void onStockClick(String symbol);
        void onStockDelete(String symbol);
        void onSetPriceAlert(StockWatchData stock);
        void onAlertStateChanged(String symbol, boolean triggered);
    }

    public static final String ALERT_CHANNEL_ID = "stock_alert_channel";

    private final List<StockWatchData> originalList = new ArrayList<>();
    private final List<StockWatchData> displayList  = new ArrayList<>();
    private final OnWatchStockClickListener listener;
    private final OkHttpClient httpClient = new OkHttpClient();
    private static final String API_KEY = "d918pn9r01qr1uqui560d918pn9r01qr1uqui56g";

    private String currentFilter = "default";
    private String currentSearch = "";

    public WatchlistAdapter(OnWatchStockClickListener listener) {
        this.listener = listener;
    }

    public void updateData(List<StockWatchData> newData) {
        originalList.clear();
        originalList.addAll(newData);
        applyFilterAndSort();
    }

    public void setFilter(String filter) {
        currentFilter = filter;
        applyFilterAndSort();
    }

    public void setSearch(String query) {
        currentSearch = query == null ? "" : query.trim().toUpperCase();
        applyFilterAndSort();
    }

    public void refresh() {
        applyFilterAndSort();
    }

    private void applyFilterAndSort() {
        List<StockWatchData> filtered = new ArrayList<>();
        for (StockWatchData s : originalList) {
            if (s.symbol != null && s.symbol.toUpperCase().contains(currentSearch)) {
                filtered.add(s);
            }
        }

        switch (currentFilter) {
            case "gain":
                Collections.sort(filtered, (a, b) -> Float.compare(b.dayChange, a.dayChange));
                break;
            case "loss":
                Collections.sort(filtered, (a, b) -> Float.compare(a.dayChange, b.dayChange));
                break;
            case "alpha":
                Collections.sort(filtered, (a, b) -> {
                    if (a.symbol == null) return 1;
                    if (b.symbol == null) return -1;
                    return a.symbol.compareTo(b.symbol);
                });
                break;
            default:
                break;
        }

        displayList.clear();
        displayList.addAll(filtered);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public WatchViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_watchlist_stock, parent, false);
        return new WatchViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull WatchViewHolder holder, int position) {
        StockWatchData stock = displayList.get(position);
        Context ctx = holder.itemView.getContext();

        int textPrimary   = ctx.getColor(R.color.text_primary);
        int textSecondary = ctx.getColor(R.color.text_secondary);
        int colorGain     = ctx.getColor(R.color.gain);
        int colorLoss     = ctx.getColor(R.color.loss);
        int colorPrimary  = ctx.getColor(R.color.primary);

        holder.symbolText.setText(stock.symbol);
        holder.symbolText.setTextColor(textPrimary);
        updateAlertText(holder, stock, colorPrimary, textSecondary);

        if (stock.currentPrice != 0f) {
            holder.priceText.setText(String.format(Locale.US, "$%.2f", stock.currentPrice));
            holder.priceText.setTextColor(colorPrimary);
            // מניה עולה: חץ + ▲ +X.XX%, מניה יורדת: חץ ▼ -X.XX%
            if (stock.dayChange >= 0) {
                holder.dayChangeText.setText(String.format(Locale.US, "\u25b2 +%.2f%%", stock.dayChange));
                holder.dayChangeText.setTextColor(colorGain);
            } else {
                holder.dayChangeText.setText(String.format(Locale.US, "\u25bc -%.2f%%", Math.abs(stock.dayChange)));
                holder.dayChangeText.setTextColor(colorLoss);
            }
        } else {
            holder.priceText.setText("...");
            holder.priceText.setTextColor(textSecondary);
            holder.dayChangeText.setText("");
        }

        fetchQuote(stock.symbol, new QuoteCallback() {
            @Override
            public void onQuoteReceived(float price, float dayChange) {
                stock.currentPrice = price;
                stock.dayChange    = dayChange;
                holder.priceText.post(() -> {
                    holder.priceText.setText(String.format(Locale.US, "$%.2f", price));
                    holder.priceText.setTextColor(colorPrimary);
                });
                holder.dayChangeText.post(() -> {
                    if (dayChange >= 0) {
                        holder.dayChangeText.setText(String.format(Locale.US, "\u25b2 +%.2f%%", dayChange));
                        holder.dayChangeText.setTextColor(colorGain);
                    } else {
                        holder.dayChangeText.setText(String.format(Locale.US, "\u25bc -%.2f%%", Math.abs(dayChange)));
                        holder.dayChangeText.setTextColor(colorLoss);
                    }
                });
                processAlert(stock, price, ctx);
            }
            @Override
            public void onError(Exception e) {
                holder.priceText.post(() -> { holder.priceText.setText("$\u2014"); holder.priceText.setTextColor(textSecondary); });
                holder.dayChangeText.post(() -> { holder.dayChangeText.setText("\u2014"); holder.dayChangeText.setTextColor(textSecondary); });
            }
        });

        holder.itemView.setOnClickListener(v -> listener.onStockClick(stock.symbol));
        holder.deleteButton.setOnClickListener(v -> listener.onStockDelete(stock.symbol));
        holder.alertButton.setOnClickListener(v -> listener.onSetPriceAlert(stock));
    }

    @Override
    public int getItemCount() { return displayList.size(); }

    private void updateAlertText(WatchViewHolder holder, StockWatchData stock, int colorPrimary, int textSecondary) {
        if (stock.alertEnabled && stock.alertTargetPrice > 0f) {
            holder.alertText.setText(String.format(Locale.US, "\uD83D\uDD14 $%.2f", stock.alertTargetPrice));
            holder.alertText.setTextColor(colorPrimary);
        } else {
            holder.alertText.setText("\uD83D\uDD15 Off");
            holder.alertText.setTextColor(textSecondary);
        }
    }

    private void processAlert(StockWatchData stock, float currentPrice, Context context) {
        if (!stock.alertEnabled || stock.alertTargetPrice <= 0f) return;
        if (currentPrice >= stock.alertTargetPrice && !stock.alertTriggered) {
            showPriceAlertNotification(context, stock.symbol, stock.alertTargetPrice, currentPrice);
            stock.alertTriggered = true;
            listener.onAlertStateChanged(stock.symbol, true);
        } else if (currentPrice < stock.alertTargetPrice && stock.alertTriggered) {
            stock.alertTriggered = false;
            listener.onAlertStateChanged(stock.symbol, false);
        }
    }

    private void showPriceAlertNotification(Context context, String symbol, float targetPrice, float currentPrice) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("\uD83D\uDCC8 Stock Alert: " + symbol)
                .setContentText(String.format(Locale.US, "Price crossed $%.2f (now $%.2f)", targetPrice, currentPrice))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);
        NotificationManagerCompat manager = NotificationManagerCompat.from(context);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                        == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            manager.notify(symbol.hashCode(), builder.build());
        }
    }

    // ========================= FETCH QUOTE (Finnhub /quote) =========================
    // משתמש ב-/quote במקום /stock/candle - עובד תמיד גם בשוק סגור
    private void fetchQuote(String symbol, QuoteCallback callback) {
        String url = "https://finnhub.io/api/v1/quote?symbol=" + symbol + "&token=" + API_KEY;
        httpClient.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) { callback.onError(e); }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    JSONObject obj = new JSONObject(response.body().string());
                    float price     = (float) obj.getDouble("c");  // current price
                    float dayChange = (float) obj.getDouble("dp"); // daily % change
                    if (price == 0f) { callback.onError(new Exception("price=0")); return; }
                    callback.onQuoteReceived(price, dayChange);
                } catch (Exception e) { callback.onError(e); }
            }
        });
    }

    public interface QuoteCallback {
        void onQuoteReceived(float price, float dayChange);
        void onError(Exception e);
    }

    // תאימות אחורה
    public interface StockDataCallback {
        void onDataReceived(float price, float dayChange);
        void onError(Exception e);
    }

    static class WatchViewHolder extends RecyclerView.ViewHolder {
        TextView symbolText, priceText, dayChangeText, alertText;
        ImageButton deleteButton, alertButton;
        WatchViewHolder(@NonNull View itemView) {
            super(itemView);
            symbolText    = itemView.findViewById(R.id.stockSymbolText);
            priceText     = itemView.findViewById(R.id.stockPriceText);
            dayChangeText = itemView.findViewById(R.id.stockDayChangeText);
            alertText     = itemView.findViewById(R.id.stockAlertText);
            deleteButton  = itemView.findViewById(R.id.btnDeleteStock);
            alertButton   = itemView.findViewById(R.id.btnSetAlert);
        }
    }
}
