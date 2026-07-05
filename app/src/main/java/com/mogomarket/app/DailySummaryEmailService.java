package com.mogomarket.app;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class DailySummaryEmailService extends BroadcastReceiver {

    private static final String TAG = "DailySummaryEmail";

    // TEST ONLY - move to backend / Cloud Function for production
    private static final String SENDER_EMAIL = "shoomdavar123@gmail.com";
    private static final String SENDER_PASSWORD = "lpry hxic pgvc gwxl";

    private static final String CHANNEL_ID = "daily_summary_channel";
    private static final int NOTIFICATION_ID = 1001;

    @Override
    public void onReceive(Context context, Intent intent) {
        sendNow(context, false);
    }

    public static void sendNow(Context context, boolean showSuccessNotification) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.d(TAG, "No logged-in user. Skipping daily summary email.");
            return;
        }

        String uid = user.getUid();
        String userEmail = user.getEmail();

        if (userEmail == null || userEmail.trim().isEmpty()) {
            Log.d(TAG, "User email is empty. Skipping daily summary email.");
            return;
        }

        DatabaseReference userRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(uid);

        fetchAndSendSummary(context, userRef, userEmail, showSuccessNotification);
    }

    private static void fetchAndSendSummary(Context context,
                                            DatabaseReference userRef,
                                            String toEmail,
                                            boolean showSuccessNotification) {

        AtomicReference<List<StockData>> portfolioRef = new AtomicReference<>(new ArrayList<>());
        AtomicReference<List<StockData>> watchlistRef = new AtomicReference<>(new ArrayList<>());
        AtomicReference<List<StockData>> closedTradesRef = new AtomicReference<>(new ArrayList<>());
        AtomicInteger completed = new AtomicInteger(0);

        Runnable tryFinish = () -> {
            if (completed.incrementAndGet() == 3) {
                String subject = buildSubject();
                String body = buildSummaryBody(
                        portfolioRef.get(),
                        watchlistRef.get(),
                        closedTradesRef.get()
                );
                sendEmail(context, toEmail, subject, body, showSuccessNotification);
            }
        };

        userRef.child("portfolio-stocks").get()
                .addOnSuccessListener(snapshot -> {
                    portfolioRef.set(parseStockDataList(snapshot));
                    tryFinish.run();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load portfolio-stocks: " + e.getMessage(), e);
                    tryFinish.run();
                });

        userRef.child("watchlist").get()
                .addOnSuccessListener(snapshot -> {
                    watchlistRef.set(parseStockDataList(snapshot));
                    tryFinish.run();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load watchlist: " + e.getMessage(), e);
                    tryFinish.run();
                });

        userRef.child("closed-trades").get()
                .addOnSuccessListener(snapshot -> {
                    List<StockData> closed = parseStockDataList(snapshot);
                    if (closed.isEmpty()) {
                        userRef.child("closed_trades").get()
                                .addOnSuccessListener(snapshot2 -> {
                                    closedTradesRef.set(parseStockDataList(snapshot2));
                                    tryFinish.run();
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed to load closed_trades fallback: " + e.getMessage(), e);
                                    tryFinish.run();
                                });
                    } else {
                        closedTradesRef.set(closed);
                        tryFinish.run();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load closed-trades: " + e.getMessage(), e);
                    userRef.child("closed_trades").get()
                            .addOnSuccessListener(snapshot2 -> {
                                closedTradesRef.set(parseStockDataList(snapshot2));
                                tryFinish.run();
                            })
                            .addOnFailureListener(e2 -> {
                                Log.e(TAG, "Failed to load closed_trades fallback: " + e2.getMessage(), e2);
                                tryFinish.run();
                            });
                });
    }

    private static List<StockData> parseStockDataList(DataSnapshot snapshot) {
        List<StockData> list = new ArrayList<>();
        if (snapshot == null || !snapshot.exists()) return list;

        for (DataSnapshot child : snapshot.getChildren()) {
            StockData item = child.getValue(StockData.class);
            if (item != null) {
                list.add(item);
            }
        }
        return list;
    }

    private static String buildSubject() {
        String date = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date());
        return "MogoMarket Daily Summary - " + date;
    }

    private static String buildSummaryBody(List<StockData> portfolio,
                                           List<StockData> watchlist,
                                           List<StockData> closedTrades) {

        String date = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date());

        double totalInvested = 0.0;
        double totalCurrentValue = 0.0;
        double openProfitLoss = 0.0;
        int openPositions = 0;

        for (StockData stock : portfolio) {
            if (stock == null) continue;
            if (stock.tradeAmount <= 0) continue;

            // משתמש בשדות שנשמרו מהפורטפוליו
            totalInvested += stock.tradeAmount;
            totalCurrentValue += stock.currentValue;
            openProfitLoss += stock.profitLoss;
            openPositions++;
        }

        double closedProfitLoss = 0.0;
        int closedCount = 0;
        int winningTrades = 0;
        int losingTrades = 0;

        for (StockData trade : closedTrades) {
            if (trade == null) continue;

            double invested = trade.tradeAmount > 0 ? trade.tradeAmount : 0.0;
            double pnl = 0.0;

            if (trade.buyPrice > 0 && invested > 0) {
                double quantity = invested / trade.buyPrice;
                pnl = (trade.sellPrice - trade.buyPrice) * quantity;
            }

            closedProfitLoss += pnl;
            closedCount++;

            if (pnl > 0) winningTrades++;
            else if (pnl < 0) losingTrades++;
        }

        StockData topGainer = null;
        StockData topLoser = null;

        for (StockData item : watchlist) {
            if (item == null) continue;

            if (topGainer == null || item.changePercent > topGainer.changePercent) {
                topGainer = item;
            }
            if (topLoser == null || item.changePercent < topLoser.changePercent) {
                topLoser = item;
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("MogoMarket Daily Summary").append("\n");
        sb.append("Date: ").append(date).append("\n\n");

        sb.append("OPEN PORTFOLIO").append("\n");
        sb.append("Open positions: ").append(openPositions).append("\n");
        sb.append("Total invested: ").append(formatMoney(totalInvested)).append("\n");
        sb.append("Current value: ").append(formatMoney(totalCurrentValue)).append("\n");
        sb.append("Open P&L: ").append(formatSignedMoney(openProfitLoss)).append("\n\n");

        if (!portfolio.isEmpty()) {
            sb.append("Portfolio holdings:").append("\n");

            for (StockData stock : portfolio) {
                if (stock == null) continue;

                sb.append("- ")
                        .append(safe(stock.symbol))
                        .append(" | Buy: ").append(formatMoney(stock.buyPrice))
                        .append(" | Current: ").append(formatMoney(stock.currentPrice))
                        .append("\n  ")
                        .append("Day: ")
                        .append(formatPercent((float) stock.dailyProfitLossPercent))
                        .append(" (").append(formatSignedMoney(stock.dailyProfitLoss)).append(")")
                        .append("Total: ")
                        .append(formatPercent((float) stock.profitLossPercent))
                        .append(" (").append(formatSignedMoney(stock.profitLoss)).append(")")
                        .append("\n\n");
            }

            sb.append("\n");
        } else {
            sb.append("No open positions in portfolio.").append("\n\n");
        }

        sb.append("WATCHLIST").append("\n");
        sb.append("Tracked symbols: ").append(watchlist.size()).append("\n");

        if (topGainer != null) {
            sb.append("Top gainer: ")
                    .append(safe(topGainer.symbol))
                    .append(" (").append(formatPercent(topGainer.changePercent)).append(")")
                    .append("\n");
        } else {
            sb.append("Top gainer: N/A").append("\n");
        }

        if (topLoser != null) {
            sb.append("Top loser: ")
                    .append(safe(topLoser.symbol))
                    .append(" (").append(formatPercent(topLoser.changePercent)).append(")")
                    .append("\n");
        } else {
            sb.append("Top loser: N/A").append("\n");
        }

        if (!watchlist.isEmpty()) {
            sb.append("\nWatchlist snapshot:").append("\n");
            for (int i = 0; i < Math.min(watchlist.size(), 10); i++) {
                StockData item = watchlist.get(i);
                if (item == null) continue;

                sb.append("- ")
                        .append(safe(item.symbol))
                        .append(" | Price: ").append(formatMoney(item.currentPrice))
                        .append(" | Change: ").append(formatPercent(item.changePercent))
                        .append("\n");
            }
        } else {
            sb.append("No symbols in watchlist.").append("\n");
        }

        sb.append("\n");


        sb.append("\n");
        sb.append("Generated automatically by MogoMarket.");
        return sb.toString();
    }

    private static void sendEmail(Context context,
                                  String toEmail,
                                  String subject,
                                  String body,
                                  boolean showSuccessNotification) {

        new Thread(() -> {
            try {
                Properties props = new Properties();
                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.host", "smtp.gmail.com");
                props.put("mail.smtp.port", "587");
                props.put("mail.smtp.ssl.trust", "smtp.gmail.com");

                Session session = Session.getInstance(props, new javax.mail.Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(SENDER_EMAIL, SENDER_PASSWORD);
                    }
                });

                Message message = new MimeMessage(session);
                message.setFrom(new InternetAddress(SENDER_EMAIL, "MogoMarket"));
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
                message.setSubject(subject);
                message.setText(body);

                Transport.send(message);

                Log.d(TAG, "Daily summary email sent to: " + toEmail);

                if (showSuccessNotification) {
                    showNotification(context,
                            "Daily summary sent",
                            "The email was sent to " + toEmail);
                }

            } catch (Exception e) {
                Log.e(TAG, "Failed to send email: " + e.getMessage(), e);
                showNotification(context,
                        "Daily summary failed",
                        "Could not send the email");
            }
        }).start();
    }

    private static String formatMoney(double value) {
        return String.format(Locale.US, "$%,.2f", value);
    }

    private static String formatMoney(float value) {
        return String.format(Locale.US, "$%,.2f", value);
    }

    private static String formatSignedMoney(double value) {
        return String.format(Locale.US, "%s$%,.2f", value >= 0 ? "+" : "-", Math.abs(value));
    }

    private static String formatPercent(float value) {
        return String.format(Locale.US, "%s%.2f%%", value >= 0 ? "+" : "", value);
    }

    private static String safe(String value) {
        return value == null || value.trim().isEmpty() ? "N/A" : value;
    }

    private static void showNotification(Context context, String title, String text) {
        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (nm == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Daily Summary",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            nm.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true);

        nm.notify(NOTIFICATION_ID, builder.build());
    }

    public static void scheduleDailySummary(Context context) {
        AlarmManager alarmManager =
                (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        if (alarmManager == null) return;

        Intent intent = new Intent(context, DailySummaryEmailService.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 8);
        calendar.set(java.util.Calendar.MINUTE, 0);
        calendar.set(java.util.Calendar.SECOND, 0);
        calendar.set(java.util.Calendar.MILLISECOND, 0);

        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(java.util.Calendar.DAY_OF_YEAR, 1);
        }

        alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY,
                pendingIntent
        );
    }

    public static void cancelDailySummary(Context context) {
        AlarmManager alarmManager =
                (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        if (alarmManager == null) return;

        Intent intent = new Intent(context, DailySummaryEmailService.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        alarmManager.cancel(pendingIntent);
    }
}