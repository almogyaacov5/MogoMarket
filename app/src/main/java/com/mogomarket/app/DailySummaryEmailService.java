package com.mogomarket.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Properties;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

/**
 * BroadcastReceiver שמופעל כל יום בשעה קבועה (מוגדר ב-AlarmManager).
 * קורא את הפורטפוליו והטריידים מ-Firebase ושולח סיכום יומי לאימייל של המשתמש.
 *
 * ═══════════════════════════════════════════════════════════════════
 *  הגדרות נדרשות:
 *  1. הוסף ל-build.gradle (app):
 *       implementation 'com.sun.mail:android-mail:1.6.7'
 *       implementation 'com.sun.mail:android-activation:1.6.7'
 *  2. הוסף את הרשאת INTERNET ל-AndroidManifest.xml (כבר קיימת ברוב האפליקציות)
 *  3. הוסף ל-AndroidManifest.xml בתוך <application>:
 *       <receiver android:name=".DailySummaryEmailService" android:exported="false" />
 *  4. החלף את SENDER_EMAIL ו-SENDER_PASSWORD בפרטי חשבון Gmail שייעודי לשליחה.
 *     בחשבון Gmail: הפעל 2-Factor Auth, ואז צור "App Password" ב:
 *     myaccount.google.com/apppasswords
 * ═══════════════════════════════════════════════════════════════════
 */
public class DailySummaryEmailService extends BroadcastReceiver {

    private static final String TAG = "DailySummaryEmail";

    // ── החלף כאן בפרטי חשבון Gmail שייעודי לשליחה ──────────────────────────
    private static final String SENDER_EMAIL    = "your-app-email@gmail.com";
    private static final String SENDER_PASSWORD = "your-app-password"; // App Password
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void onReceive(Context context, Intent intent) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.d(TAG, "No logged-in user, skipping daily summary.");
            return;
        }

        String uid       = user.getUid();
        String userEmail = user.getEmail();
        if (userEmail == null || userEmail.isEmpty()) {
            Log.d(TAG, "User has no email (anonymous/Google without email).");
            return;
        }

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("users").child(uid);
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                StringBuilder sb = buildSummary(snapshot);
                sendEmail(context, userEmail, sb.toString());
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "Firebase read failed: " + error.getMessage());
            }
        });
    }

    // ── בניית תוכן המייל ─────────────────────────────────────────────────────
    private StringBuilder buildSummary(DataSnapshot snapshot) {
        String date = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date());
        StringBuilder sb = new StringBuilder();
        sb.append("סיכום יומי - MogoMarket | ").append(date).append("\n\n");

        // ── פורטפוליו פתוח ───
        sb.append("📈 פורטפוליו פתוח:\n");
        DataSnapshot portfolio = snapshot.child("portfolio");
        double totalPortfolioValue = 0;
        int openCount = 0;
        for (DataSnapshot item : portfolio.getChildren()) {
            StockData stock = item.getValue(StockData.class);
            if (stock == null) continue;
            double currentVal = stock.currentPrice * (stock.tradeAmount > 0
                    ? stock.tradeAmount / stock.buyPrice : 1);
            sb.append(String.format(Locale.getDefault(),
                    "  %s | קנייה: %.2f | עכשיו: %.2f | %.1f%%\n",
                    stock.symbol, stock.buyPrice, stock.currentPrice, stock.changePercent));
            totalPortfolioValue += currentVal;
            openCount++;
        }
        if (openCount == 0) sb.append("  (אין מניות בפורטפוליו)\n");
        sb.append(String.format(Locale.getDefault(),
                "סה\"כ מניות פתוחות: %d\n\n", openCount));

        // ── טריידים סגורים ───
        sb.append("🔒 טריידים סגורים:\n");
        DataSnapshot closed = snapshot.child("closed_trades");
        double totalPnl = 0;
        int closedCount = 0;
        for (DataSnapshot item : closed.getChildren()) {
            StockData stock = item.getValue(StockData.class);
            if (stock == null) continue;
            double pnl = (stock.sellPrice - stock.buyPrice) * (stock.tradeAmount / stock.buyPrice);
            totalPnl += pnl;
            sb.append(String.format(Locale.getDefault(),
                    "  %s | קנייה: %.2f | מכירה: %.2f | P&L: %.2f\n",
                    stock.symbol, stock.buyPrice, stock.sellPrice, pnl));
            closedCount++;
        }
        if (closedCount == 0) sb.append("  (אין טריידים סגורים)\n");
        sb.append(String.format(Locale.getDefault(),
                "סה\"כ רווח/הפסד סגורים: %.2f$\n\n", totalPnl));

        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("נשלח אוטומטית ע\"י MogoMarket 📊");
        return sb;
    }

    // ── שליחת המייל ב-Thread נפרד ────────────────────────────────────────────
    private void sendEmail(Context context, String toEmail, String body) {
        new Thread(() -> {
            try {
                Properties props = new Properties();
                props.put("mail.smtp.auth",            "true");
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.host",            "smtp.gmail.com");
                props.put("mail.smtp.port",            "587");

                Session session = Session.getInstance(props, new javax.mail.Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(SENDER_EMAIL, SENDER_PASSWORD);
                    }
                });

                String date = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date());
                Message message = new MimeMessage(session);
                message.setFrom(new InternetAddress(SENDER_EMAIL, "MogoMarket"));
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
                message.setSubject("📊 סיכום יומי MogoMarket - " + date);
                message.setText(body);
                Transport.send(message);

                Log.d(TAG, "Daily summary email sent to: " + toEmail);
                showNotification(context, "סיכום יומי נשלח", "המייל נשלח ל-" + toEmail);

            } catch (Exception e) {
                Log.e(TAG, "Failed to send email: " + e.getMessage(), e);
            }
        }).start();
    }

    // ── התראה מקומית לאישור שליחה ────────────────────────────────────────────
    private void showNotification(Context context, String title, String text) {
        String channelId = "daily_summary_channel";
        NotificationManager nm = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId, "Daily Summary", NotificationManager.IMPORTANCE_DEFAULT);
            nm.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true);

        nm.notify(1001, builder.build());
    }

    // ── רישום AlarmManager - קרא לזה מ-MainActivity או SplashActivity ─────────
    /**
     * מפעיל שליחת סיכום יומי בכל יום ב-08:00 בבוקר.
     * יש לקרוא לשיטה זו פעם אחת בעת אתחול האפליקציה.
     *
     * usage (ב-MainActivity.onCreate):
     *   DailySummaryEmailService.scheduleDailySummary(this);
     */
    public static void scheduleDailySummary(Context context) {
        android.app.AlarmManager alarmManager =
                (android.app.AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        Intent intent = new Intent(context, DailySummaryEmailService.class);
        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getBroadcast(
                context, 0, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT |
                android.app.PendingIntent.FLAG_IMMUTABLE);

        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 8);
        calendar.set(java.util.Calendar.MINUTE, 0);
        calendar.set(java.util.Calendar.SECOND, 0);

        // אם כבר עבר 08:00 היום, תכנן למחר
        if (calendar.getTimeInMillis() < System.currentTimeMillis()) {
            calendar.add(java.util.Calendar.DAY_OF_YEAR, 1);
        }

        alarmManager.setRepeating(
                android.app.AlarmManager.RTC_WAKEUP,
                calendar.getTimeInMillis(),
                android.app.AlarmManager.INTERVAL_DAY,
                pendingIntent);

        Log.d(TAG, "Daily summary scheduled for: " + calendar.getTime());
    }
}
