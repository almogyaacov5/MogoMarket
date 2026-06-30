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

    public interface OnStockClickListener {
        void onStockClick(StockWatchData stock);
    }

    public interface OnStockLongClickListener {
        void onStockLongClick(StockWatchData stock, int position);
    }

    private static final String API_KEY = "d0omel1r01qua2guc4ggd0omel1r01qua2guc4h0";

    private final List<StockWatchData>   originalList;
    private       List<StockWatchData>   filteredList;
    private final OnStockClickListener     clickListener;
    private final OnStockLongClickListener longClickListener;

    public WatchlistAdapter(List<StockWatchData> stocks,
                            OnStockClickListener click,
                            OnStockLongClickListener longClick) {
        this.originalList      = stocks;
        this.filteredList      = new ArrayList<>(stocks);
        this.clickListener     = click;
        this.longClickListener = longClick;
    }

    // ─── filter / sort ────────────────────────────────────────────────────────

    public void filter(String query) {
        filteredList.clear();
        if (query == null || query.isEmpty()) {
            filteredList.addAll(originalList);
        } else {
            String q = query.toLowerCase();
            for (StockWatchData s : originalList)
                if (s.symbol.toLowerCase().contains(q)) filteredList.add(s);
        }
        notifyDataSetChanged();
    }

    public void sortByChangeDesc() {
        List<StockWatchData> filtered = new ArrayList<>(filteredList);
        Collections.sort(filtered, (a, b) -> Float.compare(b.dayChange, a.dayChange));
        filteredList = filtered;
        notifyDataSetChanged();
    }

    public void sortByChangeAsc() {
        List<StockWatchData> filtered = new ArrayList<>(filteredList);
        Collections.sort(filtered, (a, b) -> Float.compare(a.dayChange, b.dayChange));
        filteredList = filtered;
        notifyDataSetChanged();
    }

    public void removeItem(int position) {
        if (position >= 0 && position < filteredList.size()) {
            StockWatchData removed = filteredList.remove(position);
            originalList.remove(removed);
            notifyItemRemoved(position);
        }
    }

    // ─── RecyclerView ──────────────────────────────────────────────────────────

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_watchlist_stock, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        StockWatchData stock = filteredList.get(position);
        Context ctx = holder.itemView.getContext();

        int textPrimary   = ctx.getColor(R.color.text_primary);
        int textSecondary = ctx.getColor(R.color.text_secondary);
        int colorGain     = ctx.getColor(R.color.gain);
        int colorLoss     = ctx.getColor(R.color.loss);
        int colorPrimary  = ctx.getColor(R.color.primary);

        holder.symbolText.setText(stock.symbol);
        holder.symbolText.setTextColor(textPrimary);

        if (stock.currentPrice > 0) {
            holder.priceText.setText(String.format(Locale.US, "$%.2f", stock.currentPrice));
            holder.priceText.setTextColor(colorPrimary);
            if (stock.dayChange >= 0) {
                holder.dayChangeText.setText(String.format(Locale.US, "\u25b2 +%.2f%%", stock.dayChange));
                holder.dayChangeText.setTextColor(colorGain);
            } else {
                holder.dayChangeText.setText(String.format(Locale.US, "\u25bc %.2f%%", stock.dayChange));
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
                        holder.dayChangeText.setText(String.format(Locale.US, "\u25bc %.2f%%", dayChange));
                        holder.dayChangeText.setTextColor(colorLoss);
                    }
                });
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

        if (stock.alertEnabled && stock.alertTargetPrice > 0) {
            holder.alertText.setText(String.format(Locale.US, "\uD83D\uDD14 $%.2f", stock.alertTargetPrice));
            holder.alertText.setTextColor(colorPrimary);
        } else {
            holder.alertText.setText("\uD83D\uDD15 Off");
            holder.alertText.setTextColor(textSecondary);
        }

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onStockClick(stock);
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onStockLongClick(stock, holder.getAdapterPosition());
                return true;
            }
            return false;
        });
    }

    @Override public int getItemCount() { return filteredList.size(); }

    // ─── Alert helper ──────────────────────────────────────────────────────────

    private void processAlert(StockWatchData stock, float price, Context ctx) {
        // לא מפעיל אם ההתראה כבויה, אין יעד, או כבר הופעלה
        if (!stock.alertEnabled || stock.alertTargetPrice <= 0 || stock.alertTriggered) return;

        // ההתראה תופעל כאשר המחיר גבוה מהיעד (עלייה) או נמוך ממנו (ירידה)
        // כיוון ההתראה נקבע לפי היעד לעומת המחיר הנוכחי שנשמר
        boolean triggered = (price >= stock.alertTargetPrice);
        if (!triggered) return;

        String symbol = stock.symbol;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.NotificationChannel channel = new android.app.NotificationChannel(
                    "price_alerts", "Price Alerts",
                    android.app.NotificationManager.IMPORTANCE_HIGH);
            android.app.NotificationManager nm =
                    (android.app.NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, "price_alerts")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("\uD83D\uDCC8 Stock Alert: " + symbol)
                .setContentText(String.format(Locale.US,
                        "%s hit your target of $%.2f! Current: $%.2f",
                        symbol, stock.alertTargetPrice, price))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        try (NotificationManagerCompat nm = NotificationManagerCompat.from(ctx)) {
            nm.notify(symbol.hashCode(), builder.build());
        } catch (SecurityException ignored) {}

        // סמן כהופעלה כדי לא לחזור שוב
        stock.alertTriggered = true;
        stock.alertEnabled   = false;
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            DatabaseReference ref = FirebaseDatabase.getInstance()
                    .getReference("users/" + auth.getCurrentUser().getUid()
                            + "/watchlist/" + symbol);
            ref.child("alertEnabled").setValue(false);
            ref.child("alertTriggered").setValue(true);
        }
    }

    // ─── Quote fetch ───────────────────────────────────────────────────────────

    private void fetchQuote(String symbol, QuoteCallback callback) {
        new Thread(() -> {
            try {
                String url = "https://finnhub.io/api/v1/quote?symbol=" + symbol + "&token=" + API_KEY;
                HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
                con.setRequestMethod("GET");
                BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                JSONObject obj = new JSONObject(sb.toString());
                float price     = (float) obj.getDouble("c");
                float dayChange = (float) obj.getDouble("dp");
                callback.onQuoteReceived(price, dayChange);
            } catch (Exception e) {
                callback.onError(e);
            }
        }).start();
    }

    interface QuoteCallback {
        void onQuoteReceived(float price, float dayChange);
        void onError(Exception e);
    }

    // ─── ViewHolder ────────────────────────────────────────────────────────────

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
