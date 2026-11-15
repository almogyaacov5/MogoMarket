package com.mogomarket.app;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.FirebaseDatabase;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class DailySummaryEmailService extends BroadcastReceiver {

    private static final String TAG = "DailySummaryEmail";
    private static final String CHANNEL_ID = "daily_summary_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final int REQUEST_CODE = 1002;
    private static final String ACTION_DAILY = "com.mogomarket.app.DAILY_EMAIL_SUMMARY";

    public static final String PREFS_NAME = "app_prefs";
    public static final String KEY_ENABLED = "daily_email_enabled";
    public static final String KEY_HOUR = "daily_email_hour";
    public static final String KEY_MINUTE = "daily_email_minute";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : null;
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                || "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED".equals(action)) {
            if (isEnabled(context)) scheduleDailySummary(context);
            return;
        }

        if (!ACTION_DAILY.equals(action)) return;

        if (!isEnabled(context)) {
            return;
        }

        scheduleDailySummary(context);
        if (isSummaryDay(Calendar.getInstance().get(Calendar.DAY_OF_WEEK))) {
            DailySummaryEmailWorker.enqueue(context, false);
        }
    }

    public static void sendNow(Context context, boolean showSuccessNotification) {
        DailySummaryEmailWorker.enqueueManual(context, showSuccessNotification);
    }

    static boolean sendSummaryNow(Context context, long reportDateMillis,
                                  boolean showSuccessNotification) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.isAnonymous()) {
            Log.w(TAG, "Daily summary skipped: no signed-in user.");
            return false;
        }

        String toEmail = user.getEmail();
        if (toEmail == null || toEmail.trim().isEmpty()) {
            Log.w(TAG, "Daily summary skipped: signed-in user has no email.");
            return false;
        }

        try {
            Calendar reportDate = Calendar.getInstance();
            reportDate.setTimeInMillis(reportDateMillis);
            Calendar nextDay = (Calendar) reportDate.clone();
            nextDay.add(Calendar.DAY_OF_YEAR, 1);
            long startOfReportDay = reportDate.getTimeInMillis();
            long endOfReportDay = nextDay.getTimeInMillis();

            com.google.firebase.database.DatabaseReference userRef =
                    FirebaseDatabase.getInstance().getReference("users").child(user.getUid());
            DataSnapshot eventsSnapshot = Tasks.await(
                    userRef.child("daily-trade-events").get(), 30, TimeUnit.SECONDS);
            DataSnapshot openTradesSnapshot = Tasks.await(
                    userRef.child("portfolio-stocks").get(), 30, TimeUnit.SECONDS);
            DataSnapshot closedTradesSnapshot = Tasks.await(
                    userRef.child("closed-trades").get(), 30, TimeUnit.SECONDS);

            List<DailyTradeEvent> yesterdayTrades = new ArrayList<>();
            for (DataSnapshot child : eventsSnapshot.getChildren()) {
                DailyTradeEvent event = child.getValue(DailyTradeEvent.class);
                if (event != null && event.closedAt >= startOfReportDay
                        && event.closedAt < endOfReportDay) {
                    yesterdayTrades.add(event);
                }
            }

            List<StockData> openTrades = readTrades(openTradesSnapshot);
            List<StockData> closedTrades = readTrades(closedTradesSnapshot);
            PortfolioDailyPerformance.Result portfolioPerformance =
                    PortfolioDailyPerformance.calculate(openTrades, closedTrades,
                            startOfReportDay, endOfReportDay);
            String dateText = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    .format(new Date(startOfReportDay));
            String subject = "MogoMarket Daily Summary - " + dateText;
            String body = buildSummaryBody(dateText, yesterdayTrades, portfolioPerformance);
            return sendEmail(context, toEmail, subject, body, showSuccessNotification);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load daily trading data", e);
            return false;
        }
    }

    private static List<StockData> readTrades(DataSnapshot snapshot) {
        List<StockData> trades = new ArrayList<>();
        for (DataSnapshot child : snapshot.getChildren()) {
            StockData trade = child.getValue(StockData.class);
            if (trade != null) trades.add(trade);
        }
        return trades;
    }

    private static String buildSummaryBody(String date, List<DailyTradeEvent> trades,
                                          PortfolioDailyPerformance.Result portfolio) {
        double totalProfitLoss = 0;
        int wins = 0;
        int losses = 0;
        StringBuilder tradeLines = new StringBuilder();

        for (DailyTradeEvent trade : trades) {
            totalProfitLoss += trade.profitLoss;
            if (trade.profitLoss > 0) wins++;
            else if (trade.profitLoss < 0) losses++;

            tradeLines.append("- ")
                    .append(safe(trade.symbol))
                    .append(" | Buy: ").append(formatMoney(trade.buyPrice))
                    .append(" | Sell: ").append(formatMoney(trade.sellPrice))
                    .append(" | P&L: ").append(formatSignedMoney(trade.profitLoss))
                    .append('\n');
        }

        StringBuilder body = new StringBuilder()
                .append("MogoMarket Daily Summary\n")
                .append("Report for: ").append(date).append("\n\n")
                .append("PORTFOLIO PERFORMANCE YESTERDAY\n")
                .append("Daily P&L from held positions: ")
                .append(formatSignedMoney(portfolio.profitLoss)).append('\n')
                .append("Positions valued: ").append(portfolio.valuedPositions).append('\n');

        if (portfolio.positionsWithoutAmount > 0) {
            body.append("Positions without investment amounts (excluded): ")
                    .append(portfolio.positionsWithoutAmount).append('\n');
        }
        body.append("Cash balance is excluded; no daily cash-flow history is available.\n\n");

        if (trades.isEmpty()) {
            body.append("No trades were closed yesterday.\n");
        } else {
            int winRate = Math.round(wins * 100f / trades.size());
            body.append("CLOSED TRADES YESTERDAY\n")
                    .append("Trades closed: ").append(trades.size()).append('\n')
                    .append("Closed-trade P&L vs. entry: ")
                    .append(formatSignedMoney(totalProfitLoss)).append('\n')
                    .append("Winning trades: ").append(wins).append('\n')
                    .append("Losing trades: ").append(losses).append('\n')
                    .append("Win rate: ").append(winRate).append("%\n\n")
                    .append("Trade details:\n")
                    .append(tradeLines);
        }

        return body.append("\nGenerated automatically by MogoMarket.").toString();
    }

    private static boolean sendEmail(Context context, String toEmail, String subject, String body,
                                     boolean showSuccessNotification) {
        String senderEmail = BuildConfig.SMTP_EMAIL;
        String senderPassword = BuildConfig.SMTP_PASSWORD;
        if (senderEmail.trim().isEmpty() || senderPassword.trim().isEmpty()) {
            Log.e(TAG, "SMTP credentials are missing from the build configuration.");
            return false;
        }

        try {
            Properties props = new Properties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.host", "smtp.gmail.com");
            props.put("mail.smtp.port", "587");
            props.put("mail.smtp.ssl.trust", "smtp.gmail.com");
            props.put("mail.smtp.connectiontimeout", "15000");
            props.put("mail.smtp.timeout", "15000");
            props.put("mail.smtp.writetimeout", "15000");

            Session session = Session.getInstance(props, new javax.mail.Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(senderEmail, senderPassword);
                }
            });

            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(senderEmail, "MogoMarket"));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
            message.setSubject(subject);
            message.setText(body);
            Transport.send(message);

            Log.i(TAG, "Daily summary sent for " + subject);
            if (showSuccessNotification) {
                showNotification(context, "Daily summary sent", "The email was sent to " + toEmail);
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to send daily summary email", e);
            return false;
        }
    }

    private static String formatMoney(double value) {
        return String.format(Locale.US, "$%,.2f", value);
    }

    private static String formatSignedMoney(double value) {
        return String.format(Locale.US, "%s$%,.2f", value >= 0 ? "+" : "-", Math.abs(value));
    }

    private static String safe(String value) {
        return value == null || value.trim().isEmpty() ? "N/A" : value;
    }

    private static boolean isEnabled(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false);
    }

    public static void scheduleDailySummary(Context context) {
        if (!isEnabled(context)) {
            cancelDailySummary(context);
            return;
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            Log.e(TAG, "Cannot schedule daily summary: AlarmManager is unavailable.");
            return;
        }

        SharedPreferences preferences = context.getSharedPreferences(
                PREFS_NAME, Context.MODE_PRIVATE);
        Calendar now = Calendar.getInstance();
        Calendar nextRun = (Calendar) now.clone();
        nextRun.set(Calendar.HOUR_OF_DAY, preferences.getInt(KEY_HOUR, 8));
        nextRun.set(Calendar.MINUTE, preferences.getInt(KEY_MINUTE, 0));
        nextRun.set(Calendar.SECOND, 0);
        nextRun.set(Calendar.MILLISECOND, 0);
        if (nextRun.getTimeInMillis() <= now.getTimeInMillis()) {
            nextRun.add(Calendar.DAY_OF_YEAR, 1);
        }
        while (!isSummaryDay(nextRun.get(Calendar.DAY_OF_WEEK))) {
            nextRun.add(Calendar.DAY_OF_YEAR, 1);
        }

        PendingIntent pendingIntent = buildPendingIntent(context);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    && alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, nextRun.getTimeInMillis(), pendingIntent);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, nextRun.getTimeInMillis(), pendingIntent);
            } else {
                alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP, nextRun.getTimeInMillis(), pendingIntent);
            }

            Log.i(TAG, "Daily summary scheduled for " + nextRun.getTime());
        } catch (SecurityException e) {
            Log.e(TAG, "Exact alarm permission was revoked; scheduling an inexact alarm.", e);
            alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, nextRun.getTimeInMillis(), pendingIntent);
        }
    }

    static boolean isSummaryDay(int dayOfWeek) {
        return dayOfWeek >= Calendar.TUESDAY && dayOfWeek <= Calendar.SATURDAY;
    }

    public static void cancelDailySummary(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) alarmManager.cancel(buildPendingIntent(context));
    }

    private static PendingIntent buildPendingIntent(Context context) {
        Intent intent = new Intent(context, DailySummaryEmailService.class).setAction(ACTION_DAILY);
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void showNotification(Context context, String title, String text) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(new NotificationChannel(
                    CHANNEL_ID, "Daily Summary", NotificationManager.IMPORTANCE_DEFAULT));
        }

        NotificationCompat.Builder notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true);
        try {
            manager.notify(NOTIFICATION_ID, notification.build());
        } catch (SecurityException e) {
            Log.w(TAG, "Notification permission is unavailable.", e);
        }
    }

    static void showFailureNotification(Context context) {
        showNotification(context, "Daily summary failed",
                "Check your sign-in, network, and email settings.");
    }
}
