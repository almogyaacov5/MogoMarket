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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

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

    private String currentSearch = "";
    private String currentFilter = "default";

    public WatchlistAdapter(OnWatchStockClickListener listener) {
        this.listener = listener;
    }

    /** true אם הסמבול הוא קריפטו (מכיל ':') */
    private boolean isCrypto(String sym) {
        return sym != null && sym.contains(":");
    }

    public void updateData(List<StockWatchData> fresh) {
        masterList.clear();
        if (fresh != null) masterList.addAll(fresh);
        applyFilterAndSearch();
    }

    public void refresh() {
        notifyDataSetChanged();
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
        if ("alpha".equals(currentFilter)) {
            Collections.sort(result, (a, b) -> a.symbol.compareToIgnoreCase(b.symbol));
        } else if ("gain".equals(currentFilter)) {
            Collections.sort(result, (a, b) -> Float.compare(b.dayChange, a.dayChange));
        } else if ("loss".equals(currentFilter)) {
            Collections.sort(result, (a, b) -> Float.compare(a.dayChange, b.dayChange));
        }
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

        // תצוגת סמבול — לקריפטו מציג רק את החלק אחרי ':'
        String displaySymbol = isCrypto(stock.symbol)
                ? stock.symbol.substring(stock.symbol.indexOf(':') + 1)
                : stock.symbol;
        holder.symbolText.setText(displaySymbol);
        holder.symbolText.setTextColor(textPrimary);

        if (stock.currentPrice > 0) {
            holder.priceText.setText(String.format(Locale.US, "$%.2f", stock.currentPrice));
            holder.priceText.setTextColor(colorPrimary);
            bindChange(holder.dayChangeText, stock.dayChange, colorGain, colorLoss);
        } else {
            holder.priceText.setText("...");
            holder.priceText.setTextColor(textSecondary);
            holder.dayChangeText.setText("");
        }

        if (stock.alertEnabled && stock.alertTargetPrice > 0) {
            holder.alertText.setText(String.format(Locale.US, "\uD83D\uDD14 $%.2f", stock.alertTargetPrice));
            holder.alertText.setTextColor(colorPrimary);
        } else {
            holder.alertText.setText("\uD83D\uDD15 Off");
            holder.alertText.setTextColor(textSecondary);
        }

        // בחר API מתאים לפי סוג הסמבול
        if (isCrypto(stock.symbol)) {
            fetchCryptoQuote(stock.symbol, new QuoteCallback() {
                @Override
                public void onQuoteReceived(float price, float dayChange) {
                    stock.currentPrice = price;
                    stock.dayChange    = dayChange;
                    holder.priceText.post(() -> {
                        holder.priceText.setText(String.format(Locale.US, "$%.2f", price));
                        holder.priceText.setTextColor(colorPrimary);
                    });
                    holder.dayChangeText.post(() ->
                            bindChange(holder.dayChangeText, dayChange, colorGain, colorLoss));
                    processAlert(stock, price, ctx);
                }
                @Override
                public void onError(Exception e) {
                    holder.priceText.post(() -> {
                        holder.priceText.setText("$\u2014");
                        holder.priceText.setTextColor(textSecondary);
                    });
                    holder.dayChangeText.post(() -> {
                        holder.dayChangeText.setText("\u2014");
                        holder.dayChangeText.setTextColor(textSecondary);
                    });
                }
            });
        } else {
            fetchStockQuote(stock.symbol, new QuoteCallback() {
                @Override
                public void onQuoteReceived(float price, float dayChange) {
                    stock.currentPrice = price;
                    stock.dayChange    = dayChange;
                    holder.priceText.post(() -> {
                        holder.priceText.setText(String.format(Locale.US, "$%.2f", price));
                        holder.priceText.setTextColor(colorPrimary);
                    });
                    holder.dayChangeText.post(() ->
                            bindChange(holder.dayChangeText, dayChange, colorGain, colorLoss));
                    processAlert(stock, price, ctx);
                }
                @Override
                public void onError(Exception e) {
                    holder.priceText.post(() -> {
                        holder.priceText.setText("$\u2014");
                        holder.priceText.setTextColor(textSecondary);
                    });
                    holder.dayChangeText.post(() -> {
                        holder.dayChangeText.setText("\u2014");
                        holder.dayChangeText.setTextColor(textSecondary);
                    });
                }
            });
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

    private void bindChange(TextView tv, float change, int colorGain, int colorLoss) {
        if (change >= 0) {
            tv.setText(String.format(Locale.US, "\u25b2 +%.2f%%", change));
            tv.setTextColor(colorGain);
        } else {
            tv.setText(String.format(Locale.US, "\u25bc -%.2f%%", Math.abs(change)));
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
            // Firebase לא אוהב ':' בנתיב — מחליפים ב-'_'
            String safeKey = symbol.replace(":", "_");
            DatabaseReference ref = FirebaseDatabase.getInstance()
                    .getReference("users/" + auth.getCurrentUser().getUid()
                            + "/watchlist-stocks/" + safeKey);
            ref.child("alertEnabled").setValue(false);
            ref.child("alertTriggered").setValue(true);
        }
    }

    // ─── מניות: Finnhub /quote ─────────────────────────────────
    private void fetchStockQuote(String symbol, QuoteCallback callback) {
        new Thread(() -> {
            try {
                String url = "https://finnhub.io/api/v1/quote?symbol=" + symbol
                        + "&token=" + FINNHUB_KEY;
                HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
                con.setConnectTimeout(6000);
                con.setReadTimeout(6000);
                BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                JSONObject obj  = new JSONObject(sb.toString());
                float price     = (float) obj.getDouble("c");   // מחיר נוכחי
                float dayChange = (float) obj.getDouble("dp");  // שינוי % יומי
                if (price <= 0) { callback.onError(new Exception("price=0")); return; }
                callback.onQuoteReceived(price, dayChange);
            } catch (Exception e) {
                callback.onError(e);
            }
        }).start();
    }

    // ─── קריפטו: Finnhub /crypto/candle ──────────────────────────
    // /quote לא עובד לקריפטו — צריך נרות יומיים ולחשב את השינוי ידנית
    private void fetchCryptoQuote(String symbol, QuoteCallback callback) {
        new Thread(() -> {
            try {
                long toTime   = System.currentTimeMillis() / 1000L;
                long fromTime = toTime - (3L * 24 * 60 * 60); // 3 ימים אחורה
                String url = "https://finnhub.io/api/v1/crypto/candle?symbol=" + symbol
                        + "&resolution=D"
                        + "&from=" + fromTime
                        + "&to="   + toTime
                        + "&token=" + FINNHUB_KEY;

                HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
                con.setConnectTimeout(6000);
                con.setReadTimeout(6000);
                BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                JSONObject json = new JSONObject(sb.toString());
                if (!"ok".equals(json.optString("s"))) {
                    callback.onError(new Exception("status not ok"));
                    return;
                }
                JSONArray closes = json.getJSONArray("c");
                int len = closes.length();
                if (len == 0) { callback.onError(new Exception("empty")); return; }

                float lastPrice = (float) closes.getDouble(len - 1);
                float prevPrice = len > 1 ? (float) closes.getDouble(len - 2) : lastPrice;
                float dayChange = prevPrice > 0
                        ? ((lastPrice - prevPrice) / prevPrice) * 100f
                        : 0f;
                callback.onQuoteReceived(lastPrice, dayChange);
            } catch (Exception e) {
                callback.onError(e);
            }
        }).start();
    }

    interface QuoteCallback {
        void onQuoteReceived(float price, float dayChange);
        void onError(Exception e);
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
