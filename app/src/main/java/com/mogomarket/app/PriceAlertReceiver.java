package com.mogomarket.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class PriceAlertReceiver extends BroadcastReceiver {

    public static final String CHANNEL_ID = "stock_price_alerts";
    private static final String TAG = "PriceAlertReceiver";
    private static final OkHttpClient CLIENT = new OkHttpClient();

    @Override
    public void onReceive(Context context, Intent intent) {
        createNotificationChannel(context);

        SharedPreferences preferences = context.getSharedPreferences(
                PriceAlertScheduler.PREFS_NAME, Context.MODE_PRIVATE);
        if (preferences.getBoolean(PriceAlertScheduler.KEY_ENABLED, false)) {
            PriceAlertScheduler.schedule(context);
        }

        if (Intent.ACTION_BOOT_COMPLETED.equals(intent != null ? intent.getAction() : null)) return;

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        PendingResult pendingResult = goAsync();
        AtomicInteger pendingOperations = new AtomicInteger(1);
        AtomicBoolean finished = new AtomicBoolean(false);
        Handler handler = new Handler(Looper.getMainLooper());
        Runnable finish = () -> {
            if (finished.compareAndSet(false, true)) pendingResult.finish();
        };
        Runnable timeout = finish;
        handler.postDelayed(timeout, 8 * 60 * 1000L);
        Runnable operationFinished = () -> {
            if (pendingOperations.decrementAndGet() == 0) {
                handler.removeCallbacks(timeout);
                finish.run();
            }
        };

        DatabaseReference watchlistRef = FirebaseDatabase.getInstance().getReference("users")
                .child(user.getUid())
                .child("watchlist-stocks");
        watchlistRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot child : snapshot.getChildren()) {
                    StockWatchData stock = child.getValue(StockWatchData.class);
                    if (stock == null || !stock.alertEnabled || stock.alertTriggered
                            || stock.alertTargetPrice <= 0f) continue;
                    String firebaseKey = child.getKey();
                    if (firebaseKey != null) {
                        pendingOperations.incrementAndGet();
                        try {
                            checkPrice(context, stock, firebaseKey, watchlistRef, operationFinished);
                        } catch (RuntimeException error) {
                            Log.e(TAG, "Failed to check alert for " + stock.symbol, error);
                            operationFinished.run();
                        }
                    }
                }
                operationFinished.run();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load watchlist alerts", error.toException());
                operationFinished.run();
            }
        });
    }

    private void checkPrice(Context context, StockWatchData stock, String firebaseKey,
                            DatabaseReference watchlistRef, Runnable operationFinished) {
        String symbol = stock.symbol;
        if (symbol == null || symbol.trim().isEmpty()) {
            operationFinished.run();
            return;
        }
        String crypto = ChartFragment.CRYPTO_MAP.get(symbol.toUpperCase(Locale.US));
        String forex = ChartFragment.FOREX_MAP.get(symbol.toUpperCase(Locale.US));
        if (symbol.contains(":")) {
            String pair = symbol.substring(symbol.indexOf(':') + 1);
            fetchPrice("https://api.binance.com/api/v3/ticker/price?symbol=" + pair,
                    "price", (price, error) -> {
                        if (error != null) Log.w(TAG, "Failed to fetch crypto price for " + symbol, error);
                        if (error == null) {
                            checkThreshold(context, stock, firebaseKey, watchlistRef, price,
                                    operationFinished);
                        } else operationFinished.run();
                    });
            return;
        }

        boolean spx = "SPX".equalsIgnoreCase(symbol) || "^SPX".equalsIgnoreCase(symbol)
                || "GSPC".equalsIgnoreCase(symbol) || "^GSPC".equalsIgnoreCase(symbol);
        if (spx || forex != null || symbol.endsWith("=X") || symbol.endsWith("=F")) {
            String yahooSymbol = spx ? "^GSPC" : (forex != null ? forex : symbol);
            try {
                String encoded = URLEncoder.encode(yahooSymbol, StandardCharsets.UTF_8.name());
                String url = "https://query1.finance.yahoo.com/v8/finance/chart/" + encoded
                        + "?range=2d&interval=1d";
                fetchYahooPrice(context, stock, firebaseKey, watchlistRef, url,
                        operationFinished);
            } catch (Exception e) {
                Log.e(TAG, "Failed to prepare quote request for " + symbol, e);
                operationFinished.run();
            }
            return;
        }

        if (crypto != null) {
            String pair = crypto.substring(crypto.indexOf(':') + 1);
            fetchPrice("https://api.binance.com/api/v3/ticker/price?symbol=" + pair,
                    "price", (price, error) -> {
                        if (error != null) Log.w(TAG, "Failed to fetch crypto price for " + symbol, error);
                        if (error == null) {
                            checkThreshold(context, stock, firebaseKey, watchlistRef, price,
                                    operationFinished);
                        } else operationFinished.run();
                    });
            return;
        }

        try {
            String encoded = URLEncoder.encode(symbol, StandardCharsets.UTF_8.name());
            String url = "https://finnhub.io/api/v1/quote?symbol=" + encoded
                    + "&token=" + BuildConfig.FINNHUB_KEY;
            fetchPrice(url, "c", (price, error) -> {
                if (error != null) Log.w(TAG, "Failed to fetch stock price for " + symbol, error);
                if (error == null) {
                    checkThreshold(context, stock, firebaseKey, watchlistRef, price,
                            operationFinished);
                } else operationFinished.run();
            });
        } catch (Exception e) {
            Log.e(TAG, "Failed to prepare quote request for " + symbol, e);
            operationFinished.run();
        }
    }

    private void fetchYahooPrice(Context context, StockWatchData stock, String firebaseKey,
                                 DatabaseReference watchlistRef, String url,
                                 Runnable operationFinished) {
        Request request = new Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build();
        CLIENT.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.w(TAG, "Failed to fetch quote for " + stock.symbol, e);
                operationFinished.run();
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    Log.w(TAG, "Quote request failed for " + stock.symbol + ": HTTP " + response.code());
                    operationFinished.run();
                    return;
                }
                try {
                    JSONObject chart = new JSONObject(response.body().string()).getJSONObject("chart");
                    JSONArray results = chart.optJSONArray("result");
                    if (results == null || results.length() == 0) {
                        operationFinished.run();
                        return;
                    }
                    JSONObject quote = results.getJSONObject(0);
                    JSONObject meta = quote.optJSONObject("meta");
                    float price = meta == null ? Float.NaN
                            : (float) meta.optDouble("regularMarketPrice", Double.NaN);
                    if (!Float.isFinite(price) || price <= 0f) {
                        JSONArray closes = quote.getJSONObject("indicators").getJSONArray("quote")
                                .getJSONObject(0).getJSONArray("close");
                        for (int i = closes.length() - 1; i >= 0; i--) {
                            if (!closes.isNull(i)) {
                                price = (float) closes.getDouble(i);
                                break;
                            }
                        }
                    }
                    if (Float.isFinite(price)) {
                        checkThreshold(context, stock, firebaseKey, watchlistRef, price,
                                operationFinished);
                    } else {
                        operationFinished.run();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse quote for " + stock.symbol, e);
                    operationFinished.run();
                }
            }
        });
    }

    private interface PriceResult {
        void onResult(float price, @Nullable Exception error);
    }

    private void fetchPrice(String url, String priceField, PriceResult callback) {
        Request request = new Request.Builder().url(url).build();
        CLIENT.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                callback.onResult(0f, e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    callback.onResult(0f, new IOException("Quote request failed: HTTP " + response.code()));
                    return;
                }
                try {
                    JSONObject json = new JSONObject(response.body().string());
                    float price = (float) json.getDouble(priceField);
                    if (price <= 0f || !Float.isFinite(price)) {
                        callback.onResult(0f, new IOException("Quote returned an invalid price"));
                    } else {
                        callback.onResult(price, null);
                    }
                } catch (Exception e) {
                    callback.onResult(0f, e);
                }
            }
        });
    }

    private void checkThreshold(Context context, StockWatchData stock, String firebaseKey,
                                DatabaseReference watchlistRef, float price,
                                Runnable operationFinished) {
        if (price < stock.alertTargetPrice) {
            operationFinished.run();
            return;
        }
        DatabaseReference stockRef = watchlistRef.child(firebaseKey);
        stockRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                StockWatchData latest = currentData.getValue(StockWatchData.class);
                if (latest == null || !latest.alertEnabled || latest.alertTriggered
                        || latest.alertTargetPrice <= 0f || price < latest.alertTargetPrice) {
                    return Transaction.abort();
                }
                currentData.child("alertEnabled").setValue(false);
                currentData.child("alertTriggered").setValue(true);
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError error, boolean committed,
                                   @Nullable DataSnapshot currentData) {
                if (error != null) {
                    Log.e(TAG, "Failed to mark alert as triggered for " + stock.symbol, error.toException());
                } else if (committed) {
                    StockWatchData triggered = currentData != null
                            ? currentData.getValue(StockWatchData.class) : null;
                    float target = triggered != null
                            ? triggered.alertTargetPrice : stock.alertTargetPrice;
                    sendNotification(context, stock.symbol, target, price);
                }
                operationFinished.run();
            }
        });
    }

    private void sendNotification(Context context, String symbol, float target, float current) {
        Intent openIntent = new Intent(context, SplashActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, symbol.hashCode(), openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String displaySymbol = symbol.contains(":")
                ? symbol.substring(symbol.indexOf(':') + 1)
                : symbol;
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Price alert: " + displaySymbol)
                .setContentText(String.format(Locale.US,
                        "%s reached $%.2f (target $%.2f)", displaySymbol, current, target))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        try {
            NotificationManagerCompat.from(context).notify(symbol.hashCode(), builder.build());
        } catch (SecurityException e) {
            Log.w(TAG, "Notification permission is unavailable", e);
        }
    }

    private void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Stock Price Alerts", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Price target notifications for watchlist items");
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }
}
