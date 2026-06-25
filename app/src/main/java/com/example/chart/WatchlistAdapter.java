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

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class WatchlistAdapter extends RecyclerView.Adapter<WatchlistAdapter.WatchViewHolder> {

    // ── Trading Dark Theme colors ──────────────────────────
    private static final int BG_CARD       = 0xFF151C2E;
    private static final int TEXT_PRIMARY  = 0xFFE6EDF3;
    private static final int TEXT_SECONDARY= 0xFF8B98A5;
    private static final int COLOR_GAIN    = 0xFF00C896;
    private static final int COLOR_LOSS    = 0xFFFF4D4D;
    private static final int COLOR_PRIMARY = 0xFF4DA3FF;
    // ──────────────────────────────────────────────────────

    public interface OnWatchStockClickListener {
        void onStockClick(String symbol);
        void onStockDelete(String symbol);
        void onSetPriceAlert(StockWatchData stock);
        void onAlertStateChanged(String symbol, boolean triggered);
    }

    public static final String ALERT_CHANNEL_ID = "stock_alert_channel";

    private final List<StockWatchData> stocks;
    private final OnWatchStockClickListener listener;
    private final OkHttpClient httpClient = new OkHttpClient();
    private final String API_KEY = "0518811f0d394fa39842a8024a25c049";

    public WatchlistAdapter(List<StockWatchData> stocks, OnWatchStockClickListener listener) {
        this.stocks = stocks;
        this.listener = listener;
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
        StockWatchData stock = stocks.get(position);

        // ── עיצוב כרטיס ───────────────────────────────────
        holder.itemView.setBackgroundColor(BG_CARD);

        holder.symbolText.setText(stock.symbol);
        holder.symbolText.setTextColor(TEXT_PRIMARY);
        updateAlertText(holder, stock);

        // ── שליפת מחיר ───────────────────────────────────
        fetchStockData(stock.symbol, "1day", new StockDataCallback() {
            @Override
            public void onDataReceived(float price, float dayChange) {
                holder.priceText.post(() -> {
                    holder.priceText.setText(String.format(Locale.US, "$%.2f", price));
                    holder.priceText.setTextColor(COLOR_PRIMARY);
                });
                holder.dayChangeText.post(() -> {
                    String arrow = dayChange >= 0 ? "▲" : "▼";
                    holder.dayChangeText.setText(
                            String.format(Locale.US, "%s %.2f%%", arrow, Math.abs(dayChange)));
                    holder.dayChangeText.setTextColor(dayChange >= 0 ? COLOR_GAIN : COLOR_LOSS);
                });
                processAlert(stock, price, holder.itemView.getContext());
            }

            @Override
            public void onError(Exception e) {
                holder.priceText.post(() -> {
                    holder.priceText.setText("$—");
                    holder.priceText.setTextColor(TEXT_SECONDARY);
                });
                holder.dayChangeText.post(() -> {
                    holder.dayChangeText.setText("—");
                    holder.dayChangeText.setTextColor(TEXT_SECONDARY);
                });
            }
        });

        holder.itemView.setOnClickListener(view -> listener.onStockClick(stock.symbol));
        holder.deleteButton.setOnClickListener(view -> listener.onStockDelete(stock.symbol));
        holder.alertButton.setOnClickListener(view -> listener.onSetPriceAlert(stock));
    }

    @Override
    public int getItemCount() { return stocks != null ? stocks.size() : 0; }

    private void updateAlertText(WatchViewHolder holder, StockWatchData stock) {
        if (stock.alertEnabled && stock.alertTargetPrice > 0f) {
            holder.alertText.setText(String.format(Locale.US, "🔔 $%.2f", stock.alertTargetPrice));
            holder.alertText.setTextColor(COLOR_PRIMARY);
        } else {
            holder.alertText.setText("🔕 Off");
            holder.alertText.setTextColor(TEXT_SECONDARY);
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

    private void showPriceAlertNotification(Context context, String symbol,
                                              float targetPrice, float currentPrice) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("🚨 Stock Alert: " + symbol)
                .setContentText(String.format(Locale.US,
                        "Price crossed $%.2f (now $%.2f)", targetPrice, currentPrice))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        NotificationManagerCompat manager = NotificationManagerCompat.from(context);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                        == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            manager.notify(symbol.hashCode(), builder.build());
        }
    }

    static class WatchViewHolder extends RecyclerView.ViewHolder {
        TextView symbolText, priceText, dayChangeText, alertText;
        ImageButton deleteButton, alertButton;

        public WatchViewHolder(@NonNull View itemView) {
            super(itemView);
            symbolText    = itemView.findViewById(R.id.stockSymbolText);
            priceText     = itemView.findViewById(R.id.stockPriceText);
            dayChangeText = itemView.findViewById(R.id.stockDayChangeText);
            alertText     = itemView.findViewById(R.id.stockAlertText);
            deleteButton  = itemView.findViewById(R.id.btnDeleteStock);
            alertButton   = itemView.findViewById(R.id.btnSetAlert);
        }
    }

    private void fetchStockData(String symbol, String interval, StockDataCallback callback) {
        String url = "https://api.twelvedata.com/time_series?symbol=" + symbol
                + "&interval=" + interval + "&apikey=" + API_KEY + "&outputsize=2";
        httpClient.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) { callback.onError(e); }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String body = response.body().string();
                try {
                    org.json.JSONObject json = new org.json.JSONObject(body);
                    if (json.has("values")) {
                        org.json.JSONArray arr = json.getJSONArray("values");
                        if (arr.length() >= 2) {
                            float lastClose = Float.parseFloat(arr.getJSONObject(0).getString("close"));
                            float prevClose = Float.parseFloat(arr.getJSONObject(1).getString("close"));
                            float dayChange = (lastClose - prevClose) / prevClose * 100f;
                            callback.onDataReceived(lastClose, dayChange);
                            return;
                        }
                    }
                    callback.onError(new Exception("No data"));
                } catch (Exception e) { callback.onError(e); }
            }
        });
    }

    public interface StockDataCallback {
        void onDataReceived(float price, float dayChange);
        void onError(Exception e);
    }
}
