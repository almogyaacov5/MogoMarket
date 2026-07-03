package com.mogomarket.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * מתזמן בדיקות מחיר תקופתיות עם AlarmManager.
 * משתמש ב-inexact alarm כדי לא לדרוש הרשאת SCHEDULE_EXACT_ALARM שפותחת הגדרות.
 */
public class PriceAlertScheduler {

    private static final long INTERVAL_MS = 15 * 60 * 1000L;
    private static final String ACTION    = "com.example.chart.CHECK_PRICE_ALERTS";
    private static final int REQUEST_CODE = 1001;

    public static void schedule(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        PendingIntent pendingIntent = buildPendingIntent(context);
        long triggerAtMillis = System.currentTimeMillis() + INTERVAL_MS;

        // תמיד משתמשים ב-inexact (ללא צורך בהרשאה מיוחדת)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
        }
    }

    public static void cancel(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        alarmManager.cancel(buildPendingIntent(context));
    }

    private static PendingIntent buildPendingIntent(Context context) {
        Intent intent = new Intent(context, PriceAlertReceiver.class);
        intent.setAction(ACTION);
        return PendingIntent.getBroadcast(
                context, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
