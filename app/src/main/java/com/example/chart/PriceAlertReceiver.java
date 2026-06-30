package com.mogomarket.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * BroadcastReceiver שמופעל ע"י AlarmManager כל 15 דקות.
 * בודק את מחירי המניות ב-Watchlist ושולח Notification אם מחיר חצה יעד.
 */
public class PriceAlertReceiver extends BroadcastReceiver {

    public static final String CHANNEL_ID = "stock_price_alerts";
    private static final String API_KEY   = "d918pn9r01qr1uqui560d918pn9r01qr1uqui56g";

    @Override
    public void onReceive(Context context, Intent intent) {
        createNotificationChannel(context);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        DatabaseReference watchlistRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(user.getUid())
                .child("watchlist-stocks");

        watchlistRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                for (DataSnapshot ds : snapshot.getChildren()) {
                    StockWatchData stock = ds.getValue(StockWatchData.class);
                    if (stock == null) continue;
                    if (!stock.alertEnabled || stock.alertTargetPrice <= 0f || stock.alertTriggered) continue;
                    checkPriceAndNotify(context, stock, watchlistRef);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) { }
        });
    }

    private void checkPriceAndNotify(Context context, StockWatchData stock, DatabaseReference watchlistRef) {
        long toTime   = System.currentTimeMillis() / 1000L;
        long fromTime = toTime - (2L * 24 * 60 * 60); // 2 days back

        String url = "https://finnhub.io/api/v1/stock/candle?symbol=" + stock.symbol
                + "&resolution=D&from=" + fromTime
                + "&to=" + toTime
                + "&token=" + API_KEY;

        OkHttpClient client  = new OkHttpClient();
        Request      request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) return;
                try {
                    JSONObject json   = new JSONObject(response.body().string());
                    String     status = json.optString("s", "");
                    if (!"ok".equals(status)) return;

                    JSONArray closes = json.optJSONArray("c");
                    if (closes == null || closes.length() == 0) return;

                    float currentPrice = (float) closes.getDouble(closes.length() - 1);

                    if (currentPrice >= stock.alertTargetPrice) {
                        sendNotification(context, stock.symbol, stock.alertTargetPrice, currentPrice);
                        watchlistRef.child(stock.symbol).child("alertTriggered").setValue(true);
                    }
                } catch (Exception ignored) { }
            }
        });
    }

    private void sendNotification(Context context, String symbol, float target, float current) {
        Intent openIntent = new Intent(context, AuthLogin.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, symbol.hashCode(), openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("\uD83D\uDCC8 Stock Alert: " + symbol)
                .setContentText(String.format("\u05d4\u05de\u05d7\u05d9\u05e8 \u05d4\u05d2\u05d9\u05e2 \u05dc-$%.2f (\u05d9\u05e2\u05d3: $%.2f)", current, target))
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(String.format(
                                "\u05d4\u05de\u05e0\u05d9\u05d4 %s \u05d7\u05e6\u05ea\u05d4 \u05d0\u05ea \u05de\u05d7\u05d9\u05e8 \u05d4\u05d9\u05e2\u05d3 \u05e9\u05dc\u05da!\n\u05de\u05d7\u05d9\u05e8 \u05e0\u05d5\u05db\u05d7\u05d9: $%.2f\n\u05de\u05d7\u05d9\u05e8 \u05d9\u05e2\u05d3: $%.2f",
                                symbol, current, target)))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManagerCompat manager = NotificationManagerCompat.from(context);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                        == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            manager.notify(symbol.hashCode(), builder.build());
        }
    }

    private void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Stock Price Alerts", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("\u05d4\u05ea\u05e8\u05d0\u05d5\u05ea \u05de\u05d7\u05d9\u05e8 \u05dc\u05de\u05e0\u05d9\u05d5\u05ea \u05d1-Watchlist");
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }
}
