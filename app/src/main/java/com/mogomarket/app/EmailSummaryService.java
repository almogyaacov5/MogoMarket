package com.mogomarket.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * EmailSummaryService - שירות לשליחת סיכומים יומיים ושבועיים באימייל.
 *
 * שימוש: קרא ל-EmailSummaryService.scheduleDailySummary(context) בהפעלת האפליקציה.
 * הסיכום היומי נשלח כל יום בשעה 20:00.
 * הסיכום השבועי נשלח כל יום ראשון בשעה 20:00.
 *
 * שליחת האימייל מתבצעת דרך EmailJS (emailjs.com) - שירות חינמי.
 * הגדר את הקבועים EMAILJS_SERVICE_ID, EMAILJS_TEMPLATE_DAILY וכו'.
 *
 * ⚠️ FINNHUB_KEY: הגדר ב-local.properties → finnhub.api.key=YOUR_KEY
 *    ואז ב-build.gradle: buildConfigField "String","FINNHUB_KEY","\"${localProps['finnhub.api.key']}\""
 */
public class EmailSummaryService extends BroadcastReceiver {

    private static final String TAG = "EmailSummaryService";

    // ── Actions ──────────────────────────────────────────────────────────────
    public static final String ACTION_DAILY  = "com.mogomarket.app.DAILY_SUMMARY";
    public static final String ACTION_WEEKLY = "com.mogomarket.app.WEEKLY_SUMMARY";

    // ── Request codes ─────────────────────────────────────────────────────────
    private static final int RC_DAILY  = 2001;
    private static final int RC_WEEKLY = 2002;

    // ── EmailJS credentials ───────────────────────────────────────────────────
    private static final String EMAILJS_URL          = "https://api.emailjs.com/api/v1.0/email/send";
    private static final String EMAILJS_SERVICE_ID   = "YOUR_SERVICE_ID";          // ← שנה
    private static final String EMAILJS_USER_ID      = "YOUR_PUBLIC_KEY";          // ← שנה
    private static final String EMAILJS_TEMPLATE_DAILY  = "template_daily_summary";  // ← שנה
    private static final String EMAILJS_TEMPLATE_WEEKLY = "template_weekly_summary"; // ← שנה

    // ⚠️  Move this key to local.properties and expose via BuildConfig.FINNHUB_KEY
    //     Example build.gradle line:
    //       buildConfigField "String", "FINNHUB_KEY", "\"${localProps['finnhub.api.key']}\""
    //     Then replace the line below with:
    //       private static final String FINNHUB_KEY = BuildConfig.FINNHUB_KEY;
    private static final String FINNHUB_KEY = BuildConfig.FINNHUB_KEY;

    private final OkHttpClient httpClient = new OkHttpClient();

    // ─────────────────────────────────────────────────────────────────────────
    // Scheduling
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * מתזמן סיכום יומי כל יום ב-20:00 וסיכום שבועי כל ראשון ב-20:05.
     * קרא לפונקציה זו ב-MainActivity.onCreate().
     */
    public static void scheduleDailySummary(Context context) {
        scheduleAlarm(context, ACTION_DAILY,  RC_DAILY,  Calendar.SUNDAY, 20, 0);
        scheduleAlarm(context, ACTION_WEEKLY, RC_WEEKLY, Calendar.SUNDAY, 20, 5);
    }

    private static void scheduleAlarm(Context context, String action, int requestCode,
                                      int dayOfWeek, int hour, int minute) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, EmailSummaryService.class);
        intent.setAction(action);
        PendingIntent pi = PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        if (action.equals(ACTION_DAILY)) {
            // Daily: fire today if time hasn't passed, otherwise tomorrow
            if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
                cal.add(Calendar.DAY_OF_YEAR, 1);
            }
        } else {
            // Weekly: fire on the next Sunday at the given time
            int currentDay = cal.get(Calendar.DAY_OF_WEEK);
            int daysUntilSunday = (Calendar.SUNDAY - currentDay + 7) % 7;
            if (daysUntilSunday == 0 && cal.getTimeInMillis() <= System.currentTimeMillis()) {
                daysUntilSunday = 7;
            }
            cal.add(Calendar.DAY_OF_YEAR, daysUntilSunday);
        }

        // FIX: Use setExactAndAllowWhileIdle instead of setRepeating.
        // setRepeating is inexact on API 19+ and flagged by Play Console on API 31+.
        // The BroadcastReceiver reschedules itself for the next occurrence on each fire.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
        }

        Log.d(TAG, "Scheduled " + action + " at " + cal.getTime());
    }

    public static void cancel(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        PendingIntent piDaily = PendingIntent.getBroadcast(context, RC_DAILY,
                new Intent(context, EmailSummaryService.class).setAction(ACTION_DAILY),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent piWeekly = PendingIntent.getBroadcast(context, RC_WEEKLY,
                new Intent(context, EmailSummaryService.class).setAction(ACTION_WEEKLY),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        am.cancel(piDaily);
        am.cancel(piWeekly);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // onReceive — also reschedules the next alarm
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        // Reschedule for the next occurrence
        if (ACTION_DAILY.equals(intent.getAction())) {
            scheduleAlarm(context, ACTION_DAILY, RC_DAILY, Calendar.SUNDAY, 20, 0);
        } else if (ACTION_WEEKLY.equals(intent.getAction())) {
            scheduleAlarm(context, ACTION_WEEKLY, RC_WEEKLY, Calendar.SUNDAY, 20, 5);
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.isAnonymous()) return;

        String email = user.getEmail();
        if (email == null || email.isEmpty()) return;

        String uid = user.getUid();

        if (ACTION_DAILY.equals(intent.getAction())) {
            buildAndSendDailySummary(context, uid, email);
        } else if (ACTION_WEEKLY.equals(intent.getAction())) {
            buildAndSendWeeklySummary(context, uid, email);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Daily Summary
    // ─────────────────────────────────────────────────────────────────────────

    private void buildAndSendDailySummary(Context context, String uid, String email) {
        DatabaseReference closedRef = FirebaseDatabase.getInstance()
                .getReference("users").child(uid).child("closed-trades");
        DatabaseReference portfolioRef = FirebaseDatabase.getInstance()
                .getReference("users").child(uid).child("portfolio-stocks");

        closedRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot closedSnap) {
                List<StockData> allClosed = new ArrayList<>();
                for (DataSnapshot ds : closedSnap.getChildren()) {
                    StockData d = ds.getValue(StockData.class);
                    if (d != null) allClosed.add(d);
                }

                portfolioRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot portfolioSnap) {
                        List<StockData> portfolio = new ArrayList<>();
                        for (DataSnapshot ds : portfolioSnap.getChildren()) {
                            StockData d = ds.getValue(StockData.class);
                            if (d != null) portfolio.add(d);
                        }
                        computeAndSendDaily(context, email, allClosed, portfolio);
                    }
                    @Override public void onCancelled(DatabaseError error) {}
                });
            }
            @Override public void onCancelled(DatabaseError error) {}
        });
    }

    private void computeAndSendDaily(Context context, String email,
                                     List<StockData> closedTrades,
                                     List<StockData> portfolio) {
        double totalClosedPnl = 0;
        int wins = 0, losses = 0;
        StockData bestTrade = null, worstTrade = null;
        double bestPct = Double.MIN_VALUE, worstPct = Double.MAX_VALUE;

        for (StockData t : closedTrades) {
            double pnl = t.sellPrice - t.buyPrice;
            double pct = (t.buyPrice != 0) ? ((t.sellPrice - t.buyPrice) / t.buyPrice) * 100 : 0;
            totalClosedPnl += pnl;
            if (pnl > 0) wins++; else if (pnl < 0) losses++;
            if (pct > bestPct)  { bestPct  = pct;  bestTrade  = t; }
            if (pct < worstPct) { worstPct = pct;  worstTrade = t; }
        }

        int total   = closedTrades.size();
        int winRate = (total > 0) ? (int) ((wins * 100.0) / total) : 0;
        double totalInvested = 0;
        for (StockData s : portfolio) totalInvested += s.tradeAmount;

        String dateStr  = new SimpleDateFormat("dd/MM/yyyy", new Locale("he")).format(new Date());
        String sign     = totalClosedPnl >= 0 ? "+" : "";
        String bestStr  = (bestTrade  != null) ? String.format(Locale.US, "%s (+%.2f%%)", bestTrade.symbol,  bestPct)  : "אין נתונים";
        String worstStr = (worstTrade != null) ? String.format(Locale.US, "%s (%.2f%%)",  worstTrade.symbol, worstPct) : "אין נתונים";

        StringBuilder tradesTable = new StringBuilder();
        for (StockData t : closedTrades) {
            double pct = (t.buyPrice != 0) ? ((t.sellPrice - t.buyPrice) / t.buyPrice) * 100 : 0;
            tradesTable.append(String.format(Locale.US,
                    "| %s | $%.2f | $%.2f | %s%.2f%% |\n",
                    t.symbol, t.buyPrice, t.sellPrice, pct >= 0 ? "+" : "", pct));
        }

        String htmlBody = buildDailyHtml(dateStr, sign, totalClosedPnl, wins, losses, winRate,
                totalInvested, portfolio.size(), bestStr, worstStr, tradesTable.toString());

        sendEmail(context, email, "📊 MogoMarket - סיכום יומי " + dateStr,
                htmlBody, EMAILJS_TEMPLATE_DAILY, htmlBody);
    }

    private String buildDailyHtml(String date, String sign, double totalPnl,
                                   int wins, int losses, int winRate,
                                   double totalInvested, int openPositions,
                                   String best, String worst, String tradesTable) {
        String pnlColor = totalPnl >= 0 ? "#00E676" : "#FF5252";
        return "<!DOCTYPE html><html dir='rtl'><head><meta charset='UTF-8'>" +
                "<style>body{font-family:Arial,sans-serif;background:#1a1a1a;color:#e0e0e0;padding:20px}" +
                ".card{background:#2d2d2d;border-radius:12px;padding:16px;margin:12px 0}" +
                ".header{background:linear-gradient(135deg,#1565C0,#00695C);border-radius:12px;padding:20px;text-align:center}" +
                ".stat{display:inline-block;text-align:center;padding:10px 20px}" +
                ".stat-value{font-size:24px;font-weight:bold}" +
                ".green{color:#00E676}.red{color:#FF5252}.gold{color:#FFD740}" +
                "table{width:100%;border-collapse:collapse}" +
                "th,td{padding:8px;text-align:center;border-bottom:1px solid #444}" +
                "th{background:#1565C0;color:white}" +
                "</style></head><body>" +
                "<div class='header'>" +
                "<h1>📊 סיכום יומי - MogoMarket</h1>" +
                "<p>" + date + "</p>" +
                "</div>" +

                "<div class='card'><h2>💰 תוצאות כלליות</h2>" +
                "<div class='stat'><div class='stat-value' style='color:" + pnlColor + "'>" + sign + String.format(Locale.US, "$%.2f", totalPnl) + "</div><div>סה״כ רווח/הפסד</div></div>" +
                "<div class='stat'><div class='stat-value green'>" + wins + "</div><div>עסקאות מוצלחות</div></div>" +
                "<div class='stat'><div class='stat-value red'>" + losses + "</div><div>עסקאות מפסידות</div></div>" +
                "<div class='stat'><div class='stat-value gold'>" + winRate + "%</div><div>Win Rate</div></div>" +
                "</div>" +

                "<div class='card'><h2>📈 ביצועים</h2>" +
                "<p>🏆 מניה מובילה: <strong class='green'>" + best + "</strong></p>" +
                "<p>📉 מניה חלשה: <strong class='red'>" + worst + "</strong></p>" +
                "<p>💼 פוזיציות פתוחות: <strong>" + openPositions + "</strong></p>" +
                "<p>💵 שווי פורטפוליו מושקע: <strong>$" + String.format(Locale.US, "%.2f", totalInvested) + "</strong></p>" +
                "</div>" +

                (tradesTable.isEmpty() ? "" :
                "<div class='card'><h2>📋 טריידים סגורים</h2>" +
                "<table><tr><th>סימול</th><th>מחיר קנייה</th><th>מחיר מכירה</th><th>תשואה</th></tr>" +
                tradesTable.replace("\n", "</tr><tr>").replace("| ", "<td>").replace(" |", "</td>") +
                "</tr></table></div>") +

                "<div style='text-align:center;color:#666;margin-top:20px'>" +
                "<small>MogoMarket - מערכת ניהול השקעות אישית</small></div>" +
                "</body></html>";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Weekly Summary
    // ─────────────────────────────────────────────────────────────────────────

    private void buildAndSendWeeklySummary(Context context, String uid, String email) {
        DatabaseReference closedRef = FirebaseDatabase.getInstance()
                .getReference("users").child(uid).child("closed-trades");
        DatabaseReference portfolioRef = FirebaseDatabase.getInstance()
                .getReference("users").child(uid).child("portfolio-stocks");

        closedRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot closedSnap) {
                List<StockData> allClosed = new ArrayList<>();
                for (DataSnapshot ds : closedSnap.getChildren()) {
                    StockData d = ds.getValue(StockData.class);
                    if (d != null) allClosed.add(d);
                }

                portfolioRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot portfolioSnap) {
                        List<StockData> portfolio = new ArrayList<>();
                        for (DataSnapshot ds : portfolioSnap.getChildren()) {
                            StockData d = ds.getValue(StockData.class);
                            if (d != null) portfolio.add(d);
                        }
                        computeAndSendWeekly(context, email, allClosed, portfolio);
                    }
                    @Override public void onCancelled(DatabaseError error) {}
                });
            }
            @Override public void onCancelled(DatabaseError error) {}
        });
    }

    private void computeAndSendWeekly(Context context, String email,
                                       List<StockData> closedTrades,
                                       List<StockData> portfolio) {
        double totalPnl = 0;
        double totalInvested = 0;
        int wins = 0, losses = 0;
        StockData bestTrade = null, worstTrade = null;
        double bestPct = Double.MIN_VALUE, worstPct = Double.MAX_VALUE;

        for (StockData t : closedTrades) {
            double pnl = t.sellPrice - t.buyPrice;
            double pct = (t.buyPrice != 0) ? ((t.sellPrice - t.buyPrice) / t.buyPrice) * 100 : 0;
            totalPnl += pnl;
            totalInvested += t.buyPrice;
            if (pnl > 0) wins++; else if (pnl < 0) losses++;
            if (pct > bestPct)  { bestPct  = pct;  bestTrade  = t; }
            if (pct < worstPct) { worstPct = pct;  worstTrade = t; }
        }

        int total   = closedTrades.size();
        int winRate = (total > 0) ? (int) ((wins * 100.0) / total) : 0;
        double roi  = (totalInvested > 0) ? (totalPnl / totalInvested) * 100 : 0;
        double portfolioValue = 0;
        for (StockData s : portfolio) portfolioValue += s.tradeAmount;

        Calendar weekStart = Calendar.getInstance();
        weekStart.add(Calendar.DAY_OF_YEAR, -7);
        Calendar weekEnd = Calendar.getInstance();
        String weekRange = new SimpleDateFormat("dd/MM", new Locale("he")).format(weekStart.getTime())
                + " - " + new SimpleDateFormat("dd/MM/yyyy", new Locale("he")).format(weekEnd.getTime());

        String sign    = totalPnl >= 0 ? "+" : "";
        String roiSign = roi >= 0 ? "+" : "";
        String bestStr  = (bestTrade  != null) ? String.format(Locale.US, "%s (+%.2f%%)", bestTrade.symbol,  bestPct)  : "אין נתונים";
        String worstStr = (worstTrade != null) ? String.format(Locale.US, "%s (%.2f%%)",  worstTrade.symbol, worstPct) : "אין נתונים";

        fetchPortfolioPerformanceAndSendWeekly(context, email, portfolio,
                weekRange, sign, totalPnl, roiSign, roi,
                wins, losses, winRate, total,
                portfolioValue, bestStr, worstStr);
    }

    private void fetchPortfolioPerformanceAndSendWeekly(
            Context context, String email,
            List<StockData> portfolio, String weekRange,
            String sign, double totalPnl, String roiSign, double roi,
            int wins, int losses, int winRate, int totalTrades,
            double portfolioValue, String best, String worst) {

        if (portfolio.isEmpty()) {
            String html = buildWeeklyHtml(weekRange, sign, totalPnl, roiSign, roi,
                    wins, losses, winRate, totalTrades, portfolioValue,
                    best, worst, "0", "0.00%", "");
            sendEmail(context, email,
                    "📈 MogoMarket - סיכום שבועי " + weekRange,
                    html, EMAILJS_TEMPLATE_WEEKLY, html);
            return;
        }

        final double[] livePortfolioValue = {0};
        final double[] livePortfolioPnl   = {0};
        final Object lock = new Object();   // single lock for both arrays + StringBuilder
        final AtomicInteger remaining = new AtomicInteger(portfolio.size());
        final StringBuilder portfolioRows = new StringBuilder();

        for (StockData stock : portfolio) {
            if (stock.buyPrice == 0 || stock.tradeAmount == 0) {
                if (remaining.decrementAndGet() == 0) {
                    finalizeWeeklyEmail(context, email, weekRange,
                            sign, totalPnl, roiSign, roi,
                            wins, losses, winRate, totalTrades,
                            livePortfolioValue[0], livePortfolioPnl[0],
                            best, worst, portfolioRows.toString());
                }
                continue;
            }

            boolean isCrypto = CryptoHelper.isCryptoSymbol(stock.symbol);
            String url = isCrypto
                    ? "https://api.binance.com/api/v3/ticker/24hr?symbol=" + CryptoHelper.getPair(stock.symbol)
                    : "https://finnhub.io/api/v1/quote?symbol=" + stock.symbol + "&token=" + FINNHUB_KEY;

            httpClient.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    if (remaining.decrementAndGet() == 0) {
                        finalizeWeeklyEmail(context, email, weekRange,
                                sign, totalPnl, roiSign, roi,
                                wins, losses, winRate, totalTrades,
                                livePortfolioValue[0], livePortfolioPnl[0],
                                best, worst, portfolioRows.toString());
                    }
                }

                @Override public void onResponse(Call call, Response response) throws IOException {
                    // FIX: always close the ResponseBody to avoid connection leaks
                    try (ResponseBody body = response.body()) {
                        if (body != null) {
                            JSONObject obj = new JSONObject(body.string());
                            double currentPrice = isCrypto
                                    ? obj.getDouble("lastPrice")
                                    : obj.getDouble("c");
                            double weeklyChangePct = isCrypto
                                    ? obj.getDouble("priceChangePercent")
                                    : obj.getDouble("dp");

                            if (currentPrice > 0 && stock.buyPrice > 0) {
                                double totalPct = (currentPrice - stock.buyPrice) / stock.buyPrice * 100;
                                double pnl = stock.tradeAmount * (totalPct / 100.0);
                                double currentValue = stock.tradeAmount + pnl;
                                // FIX: use a single lock object for all shared mutable state
                                synchronized (lock) {
                                    livePortfolioValue[0] += currentValue;
                                    livePortfolioPnl[0]   += pnl;
                                    portfolioRows.append(String.format(Locale.US,
                                            "<tr><td>%s</td><td>$%.2f</td><td>$%.2f</td>" +
                                            "<td style='color:%s'>%s%.2f%%</td>" +
                                            "<td style='color:%s'>%s%.2f%%</td></tr>",
                                            stock.symbol, stock.buyPrice, currentPrice,
                                            totalPct >= 0 ? "#00E676" : "#FF5252",
                                            totalPct >= 0 ? "+" : "", totalPct,
                                            weeklyChangePct >= 0 ? "#00E676" : "#FF5252",
                                            weeklyChangePct >= 0 ? "+" : "", weeklyChangePct));
                                }
                            }
                        }
                    } catch (Exception ignored) {}

                    if (remaining.decrementAndGet() == 0) {
                        double portfolioSnapshot, pnlSnapshot;
                        String rowsSnapshot;
                        synchronized (lock) {
                            portfolioSnapshot = livePortfolioValue[0];
                            pnlSnapshot       = livePortfolioPnl[0];
                            rowsSnapshot      = portfolioRows.toString();
                        }
                        finalizeWeeklyEmail(context, email, weekRange,
                                sign, totalPnl, roiSign, roi,
                                wins, losses, winRate, totalTrades,
                                portfolioSnapshot, pnlSnapshot,
                                best, worst, rowsSnapshot);
                    }
                }
            });
        }
    }

    private void finalizeWeeklyEmail(Context context, String email,
                                      String weekRange,
                                      String sign, double totalPnl,
                                      String roiSign, double roi,
                                      int wins, int losses, int winRate, int totalTrades,
                                      double portfolioValue, double portfolioPnl,
                                      String best, String worst, String portfolioRows) {
        String portfolioSign     = portfolioPnl >= 0 ? "+" : "";
        String portfolioValueStr = String.format(Locale.US, "$%.2f", portfolioValue);
        String portfolioPnlStr   = portfolioSign + String.format(Locale.US, "$%.2f", portfolioPnl);

        String html = buildWeeklyHtml(weekRange, sign, totalPnl, roiSign, roi,
                wins, losses, winRate, totalTrades,
                portfolioValue, best, worst,
                portfolioValueStr, portfolioPnlStr, portfolioRows);

        sendEmail(context, email,
                "📈 MogoMarket - סיכום שבועי " + weekRange,
                html, EMAILJS_TEMPLATE_WEEKLY, html);
    }

    private String buildWeeklyHtml(String weekRange, String sign, double totalPnl,
                                    String roiSign, double roi,
                                    int wins, int losses, int winRate, int totalTrades,
                                    double portfolioValue, String best, String worst,
                                    String portfolioValueStr, String portfolioPnlStr,
                                    String portfolioRows) {
        String pnlColor = totalPnl >= 0 ? "#00E676" : "#FF5252";
        String roiColor = roi >= 0 ? "#00E676" : "#FF5252";
        return "<!DOCTYPE html><html dir='rtl'><head><meta charset='UTF-8'>" +
                "<style>body{font-family:Arial,sans-serif;background:#1a1a1a;color:#e0e0e0;padding:20px}" +
                ".card{background:#2d2d2d;border-radius:12px;padding:16px;margin:12px 0}" +
                ".header{background:linear-gradient(135deg,#6A1B9A,#1565C0);border-radius:12px;padding:20px;text-align:center}" +
                ".stat{display:inline-block;text-align:center;padding:10px 20px;min-width:120px}" +
                ".stat-value{font-size:24px;font-weight:bold}" +
                ".green{color:#00E676}.red{color:#FF5252}.gold{color:#FFD740}.purple{color:#CE93D8}" +
                "table{width:100%;border-collapse:collapse;margin-top:10px}" +
                "th,td{padding:10px;text-align:center;border-bottom:1px solid #444}" +
                "th{background:#6A1B9A;color:white}" +
                ".section-title{color:#CE93D8;border-bottom:1px solid #6A1B9A;padding-bottom:8px}" +
                "</style></head><body>" +
                "<div class='header'>" +
                "<h1>📈 סיכום שבועי - MogoMarket</h1>" +
                "<p>" + weekRange + "</p>" +
                "</div>" +

                "<div class='card'><h2 class='section-title'>💰 ביצועי הטריידים השבוע</h2>" +
                "<div class='stat'><div class='stat-value' style='color:" + pnlColor + "'>" + sign + String.format(Locale.US, "$%.2f", totalPnl) + "</div><div>סה״כ P&L</div></div>" +
                "<div class='stat'><div class='stat-value' style='color:" + roiColor + "'>" + roiSign + String.format(Locale.US, "%.2f%%", roi) + "</div><div>תשואה כוללת</div></div>" +
                "<div class='stat'><div class='stat-value gold'>" + winRate + "%</div><div>Win Rate</div></div>" +
                "<div class='stat'><div class='stat-value purple'>" + totalTrades + "</div><div>סה״כ עסקאות</div></div>" +
                "</div>" +

                "<div class='card'><h2 class='section-title'>📊 פירוט עסקאות</h2>" +
                "<div class='stat'><div class='stat-value green'>" + wins + "</div><div>✅ עסקאות מרוויחות</div></div>" +
                "<div class='stat'><div class='stat-value red'>" + losses + "</div><div>❌ עסקאות מפסידות</div></div>" +
                "</div>" +

                "<div class='card'><h2 class='section-title'>🏆 מניות בולטות</h2>" +
                "<p>📈 מניה מובילה: <strong class='green'>" + best + "</strong></p>" +
                "<p>📉 מניה חלשה ביותר: <strong class='red'>" + worst + "</strong></p>" +
                "</div>" +

                "<div class='card'><h2 class='section-title'>💼 מצב פורטפוליו נוכחי</h2>" +
                "<p>שווי פורטפוליו: <strong>" + portfolioValueStr + "</strong></p>" +
                "<p>רווח/הפסד לא ממומש: <strong style='color:" + (portfolioPnlStr.startsWith("+") ? "#00E676" : "#FF5252") + "'>" + portfolioPnlStr + "</strong></p>" +
                (portfolioRows.isEmpty() ? "" :
                "<table><tr><th>סימול</th><th>מחיר קנייה</th><th>מחיר נוכחי</th><th>תשואה כוללת</th><th>שינוי שבועי</th></tr>" +
                portfolioRows + "</table>") +
                "</div>" +

                "<div style='text-align:center;color:#666;margin-top:20px'>" +
                "<small>MogoMarket - מערכת ניהול השקעות אישית</small></div>" +
                "</body></html>";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Email sending via EmailJS
    // ─────────────────────────────────────────────────────────────────────────

    private void sendEmail(Context context, String toEmail, String subject,
                           String htmlContent, String templateId, String htmlBody) {
        try {
            JSONObject params = new JSONObject();
            params.put("to_email", toEmail);
            params.put("subject", subject);
            params.put("html_content", htmlContent);

            JSONObject data = new JSONObject();
            data.put("service_id",  EMAILJS_SERVICE_ID);
            data.put("template_id", templateId);
            data.put("user_id",     EMAILJS_USER_ID);
            data.put("template_params", params);

            RequestBody body = RequestBody.create(
                    data.toString(),
                    MediaType.parse("application/json"));

            Request request = new Request.Builder()
                    .url(EMAILJS_URL)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Failed to send email: " + e.getMessage());
                }
                @Override public void onResponse(Call call, Response response) throws IOException {
                    Log.d(TAG, "Email sent: " + response.code() + " to " + toEmail);
                    response.close();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error building email: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Manual trigger (for testing from Settings)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * שלח סיכום ידני (לבדיקה מהגדרות).
     * @param type "daily" או "weekly"
     */
    public static void sendManual(Context context, String type) {
        Intent intent = new Intent(context, EmailSummaryService.class);
        intent.setAction("daily".equals(type) ? ACTION_DAILY : ACTION_WEEKLY);
        context.sendBroadcast(intent);
    }
}
