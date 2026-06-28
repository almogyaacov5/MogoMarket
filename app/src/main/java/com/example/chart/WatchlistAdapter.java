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

    // displayList = מה שמוצג כרגע, originalList = כל הנתונים המלאים
    private final List<StockWatchData> displayList;
    private final List<StockWatchData> originalList;
    private final OnWatchStockClickListener listener;
    private final OkHttpClient httpClient = new OkHttpClient();
    private final String API_KEY = "0518811f0d394fa39842a8024a25c049";

    // מצב פילטר נוכחי
    private String currentFilter = "default"; // default / gain / loss / alpha
    private String currentSearch = "";

    public WatchlistAdapter(List<StockWatchData> stocks, OnWatchStockClickListener listener) {
        this.originalList = stocks;
        this.displayList  = new ArrayList<>(stocks);
        this.listener     = listener;
    }

    // ---- Public API לפילטור ומיון ----

    public void setFilter(String filter) {
        currentFilter = filter;
        applyFilterAndSort();
    }

    public void setSearch(String query) {
        currentSearch = query == null ? "" : query.trim().toUpperCase();
        applyFilterAndSort();
    }

    /** קורא לזה כשה-originalList עודכנה (לאחר notifyDataSetChanged ב-Fragment) */
    public void refresh() {
        applyFilterAndSort();
    }

    private void applyFilterAndSort() {
        // 1. סינון לפי חיפוש
        List<StockWatchData> filtered = new ArrayList<>();
        for (StockWatchData s : originalList) {
            if (s.symbol != null && s.symbol.toUpperCase().contains(currentSearch)) {
                filtered.add(s);
            }
        }

        // 2. מיון לפי פילטר
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
            default: // default - סדר הוספה
                break;
        }

        displayList.clear();
        displayList.addAll(filtered);
        notifyDataSetChanged();
    }

    // ---- Adapter ----

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

        // אם כבר יש dayChange שמור - נציג אותו מיד (לפני הקריאה)
        if (stock.dayChange != 0f || stock.currentPrice != 0f) {
            holder.priceText.setText(String.format(Locale.US, "$%.2f", stock.currentPrice));
            holder.priceText.setTextColor(colorPrimary);
            String arrow = stock.dayChange >= 0 ? "▲" : "▼";
            holder.dayChangeText.setText(
                    String.format(Locale.US, "%s %.2f%%", arrow, Math.abs(stock.dayChange)));
            holder.dayChangeText.setTextColor(stock.dayChange >= 0 ? colorGain : colorLoss);
        } else {
            holder.priceText.setText("...");
            holder.priceText.setTextColor(textSecondary);
            holder.dayChangeText.setText("");
        }

        fetchStockData(stock.symbol, "1day", new StockDataCallback() {
            @Override
            public void onDataReceived(float price, float dayChange) {
                // שמור במודל כדי שהמיון יעבוד
                stock.currentPrice = price;
                stock.dayChange    = dayChange;

                holder.priceText.post(() -> {
                    holder.priceText.setText(String.format(Locale.US, "$%.2f", price));
                    holder.priceText.setTextColor(colorPrimary);
                });
                holder.dayChangeText.post(() -> {
                    String arrow = dayChange >= 0 ? "▲" : "▼";
                    holder.dayChangeText.setText(
                            String.format(Locale.US, "%s %.2f%%", arrow, Math.abs(dayChange)));
                    holder.dayChangeText.setTextColor(dayChange >= 0 ? colorGain : colorLoss);
                });
                processAlert(stock, price, ctx);
            }

            @Override
            public void onError(Exception e) {
                holder.priceText.post(() -> {
                    holder.priceText.setText("$—");
                    holder.priceText.setTextColor(textSecondary);
                });
                holder.dayChangeText.post(() -> {
                    holder.dayChangeText.setText("—");
                    holder.dayChangeText.setTextColor(textSecondary);
                });
            }
        });

        holder.itemView.setOnClickListener(view -> listener.onStockClick(stock.symbol));
        holder.deleteButton.setOnClickListener(view -> listener.onStockDelete(stock.symbol));
        holder.alertButton.setOnClickListener(view -> listener.onSetPriceAlert(stock));
    }

    @Override
    public int getItemCount() { return displayList != null ? displayList.size() : 0; }

    private void updateAlertText(WatchViewHolder holder, StockWatchData stock,
                                  int colorPrimary, int textSecondary) {
        if (stock.alertEnabled && stock.alertTargetPrice > 0f) {
            holder.alertText.setText(String.format(Locale.US, "🔔 $%.2f", stock.alertTargetPrice));
            holder.alertText.setTextColor(colorPrimary);
        } else {
            holder.alertText.setText("🔕 Off");
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
            @Override public void onFailure(@NonNull Call call, @NonNull okhttp3.IOException e) { callback.onError(e); }
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
