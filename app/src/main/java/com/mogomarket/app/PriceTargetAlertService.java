package com.mogomarket.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

/**
 * שירות רקע שבודק כל 15 דקות האם מחיר מניה בפורטפוליו הגיע למחיר היעד.
 * כאשר המחיר מגיע ליעד — נשלחת התראה מקומית למשתמש.
 *
 * ═══════════════════════════════════════════════════════════════════
 *  הגדרות נדרשות:
 *  1. הוסף ל-AndroidManifest.xml בתוך <application>:
 *       <service android:name=".PriceTargetAlertService" android:exported="false" />
 *  2. הוסף הרשאות:
 *       <uses-permission android:name="android.permission.INTERNET" />
 *       <uses-permission android:name="android.permission.POST_NOTIFICATIONS" /> (Android 13+)
 *       <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
 *  3. API: משתמש ב-Yahoo Finance API (חינמי, ללא מפתח)
 *     אם API זה מפסיק לעבוד — ניתן להחליף ב-Alpha Vantage / Twelve Data
 *  4. הפעלה מ-MainActivity:
 *       PriceTargetAlertService.startService(this);
 * ═══════════════════════════════════════════════════════════════════
 */
public class PriceTargetAlertService extends Service {

    private static final String TAG          = "PriceTargetAlert";
    private static final String CHANNEL_ID   = "price_alert_channel";
    private static final long   CHECK_INTERVAL = 15 * 60 * 1000L; // 15 דקות
    private static final int    NOTIF_FOREGROUND = 9001;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Set<String> alreadyNotified = new HashSet<>(); // למניעת כפילויות

    private final Runnable checkTask = new Runnable() {
        @Override
        public void run() {
            checkPriceTargets();
            handler.postDelayed(this, CHECK_INTERVAL);
        }
    };

    // ── Lifecycle ────────────────────────────────────────────────────────────
    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIF_FOREGROUND, buildForegroundNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        handler.post(checkTask);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(checkTask);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    // ── בדיקת מחירים ─────────────────────────────────────────────────────────
    private void checkPriceTargets() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        DatabaseReference ref = FirebaseDatabase.getInstance()
                .getReference("users").child(user.getUid()).child("portfolio");

        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                for (DataSnapshot item : snapshot.getChildren()) {
                    StockData stock = item.getValue(StockData.class);
                    if (stock == null || stock.targetPrice <= 0) continue;
                    fetchCurrentPrice(stock, item.getKey());
                }
            }
            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "Firebase read error: " + error.getMessage());
            }
        });
    }

    // ── שליפת מחיר עדכני מ-Yahoo Finance ────────────────────────────────────
    private void fetchCurrentPrice(StockData stock, String firebaseKey) {
        new Thread(() -> {
            try {
                String urlStr = "https://query1.finance.yahoo.com/v8/finance/chart/"
                        + stock.symbol + "?interval=1m&range=1d";
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
                reader.close();

                JSONObject json = new JSONObject(response.toString());
                double currentPrice = json
                        .getJSONObject("chart")
                        .getJSONArray("result")
                        .getJSONObject(0)
                        .getJSONObject("meta")
                        .getDouble("regularMarketPrice");

                Log.d(TAG, stock.symbol + " current: " + currentPrice
                        + " | target: " + stock.targetPrice);

                // האם הגענו ליעד?
                if (currentPrice >= stock.targetPrice) {
                    String alertKey = firebaseKey + "_" + (int) stock.targetPrice;
                    if (!alreadyNotified.contains(alertKey)) {
                        alreadyNotified.add(alertKey);
                        sendPriceAlert(stock.symbol, currentPrice, stock.targetPrice);
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "Price fetch failed for " + stock.symbol + ": " + e.getMessage());
            }
        }).start();
    }

    // ── שליחת התראה ──────────────────────────────────────────────────────────
    private void sendPriceAlert(String symbol, double currentPrice, float targetPrice) {
        NotificationManager nm = (NotificationManager)
                getSystemService(NOTIFICATION_SERVICE);

        String title = "🎯 יעד מחיר הושג! " + symbol;
        String text  = String.format("המחיר הנוכחי %.2f$ הגיע ליעד %.2f$",
                currentPrice, targetPrice);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        int notifId = symbol.hashCode();
        nm.notify(notifId, builder.build());
        Log.d(TAG, "Alert sent: " + title);
    }

    // ── Notification Channel ──────────────────────────────────────────────────
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel alertChannel = new NotificationChannel(
                    CHANNEL_ID, "התראות מחיר יעד",
                    NotificationManager.IMPORTANCE_HIGH);
            alertChannel.setDescription("התראה כאשר מניה מגיעה למחיר היעד");

            NotificationChannel fgChannel = new NotificationChannel(
                    "price_fg_channel", "Price Monitor Running",
                    NotificationManager.IMPORTANCE_LOW);

            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(alertChannel);
            nm.createNotificationChannel(fgChannel);
        }
    }

    private android.app.Notification buildForegroundNotification() {
        return new NotificationCompat.Builder(this, "price_fg_channel")
                .setContentTitle("MogoMarket")
                .setContentText("מנטר מחירי מניות...")
                .setSmallIcon(R.drawable._2)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    // ── Static helper להפעלה מ-Activity ─────────────────────────────────────
    /**
     * usage (ב-MainActivity.onCreate):
     *   PriceTargetAlertService.startService(this);
     */
    public static void startService(android.content.Context context) {
        Intent intent = new Intent(context, PriceTargetAlertService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
        Log.d(TAG, "PriceTargetAlertService started.");
    }
}
