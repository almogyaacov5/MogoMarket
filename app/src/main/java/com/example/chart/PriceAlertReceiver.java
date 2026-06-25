package com.example.chart;

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
    private static final String API_KEY = "0518811f0d394fa39842a8024a25c049";

    @Override
    public void onReceive(Context context, Intent intent) {
        createNotificationChannel(context); // יצירת Channel אם לא קיים

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return; // אם אין משתמש מחובר - עוצרים

        DatabaseReference watchlistRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(user.getUid())
                .child("watchlist-stocks");

        // קריאה חד-פעמית לכל הרשימה (לא מאזינים כל הזמן)
        watchlistRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                for (DataSnapshot ds : snapshot.getChildren()) {
                    StockWatchData stock = ds.getValue(StockWatchData.class);
                    if (stock == null) continue;

                    // מדלגים על מניות ללא התראה פעילה, ללא יעד, או שההתראה כבר נשלחה
                    if (!stock.alertEnabled || stock.alertTargetPrice <= 0f || stock.alertTriggered) continue;

                    checkPriceAndNotify(context, stock, watchlistRef);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) { }
        });
    }

    // בודק את המחיר הנוכחי ב-API ושולח התראה אם חצה את היעד
    private void checkPriceAndNotify(Context context, StockWatchData stock, DatabaseReference watchlistRef) {
        String url = "https://api.twelvedata.com/time_series?symbol=" + stock.symbol
                + "&interval=1day&apikey=" + API_KEY + "&outputsize=1";

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) return;
                try {
                    JSONObject json = new JSONObject(response.body().string());
                    JSONArray values = json.optJSONArray("values");
                    if (values == null || values.length() == 0) return;

                    float currentPrice = Float.parseFloat(values.getJSONObject(0).getString("close"));

                    if (currentPrice >= stock.alertTargetPrice) {
                        sendNotification(context, stock.symbol, stock.alertTargetPrice, currentPrice);

                        // מסמן שההתראה נשלחה - למניעת שליחה כפולה
                        watchlistRef.child(stock.symbol).child("alertTriggered").setValue(true);
                    }
                } catch (Exception ignored) { }
            }
        });
    }

    // שליחת Notification למשתמש
    private void sendNotification(Context context, String symbol, float target, float current) {
        Intent openIntent = new Intent(context, AuthLogin.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, symbol.hashCode(), openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("📈 Stock Alert: " + symbol)
                .setContentText(String.format("המחיר הגיע ל-$%.2f (יעד: $%.2f)", current, target))
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(String.format(
                                "המניה %s חצתה את מחיר היעד שלך!\nמחיר נוכחי: $%.2f\nמחיר יעד: $%.2f",
                                symbol, current, target)))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true); // נעלם לאחר לחיצה

        NotificationManagerCompat manager = NotificationManagerCompat.from(context);
        // שליחה רק אם יש הרשאה (בדיקה לAndroid 13+)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                        == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            manager.notify(symbol.hashCode(), builder.build());
        }
    }

    // יצירת Notification Channel (חובה ב-Android 8+)
    private void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Stock Price Alerts", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("התראות מחיר למניות ב-Watchlist");
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }
}