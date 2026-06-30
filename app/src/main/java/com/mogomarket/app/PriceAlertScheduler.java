package com.mogomarket.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * מחלקת עזר לתזמון בדיקות מחיר תקופתיות עם AlarmManager.
 * קוראים ל-schedule() בעלייה, ול-cancel() אם רוצים לעצור.
 */
public class PriceAlertScheduler {

    private static final long INTERVAL_MS = 15 * 60 * 1000L; // 15 דקות במילישניות
    private static final String ACTION = "com.mogomarket.app.CHECK_PRICE_ALERTS";
    private static final int REQUEST_CODE = 1001;

    /**
     * מתזמן בדיקת מחיר חוזרת כל 15 דקות.
     * אם אין הרשאת Exact Alarm — משתמש ב-setAndAllowWhileIdle (לא מדויק אבל עובד).
     */
    public static void schedule(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        PendingIntent pendingIntent = buildPendingIntent(context);
        long triggerAtMillis = System.currentTimeMillis() + INTERVAL_MS;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ — בדוק הרשאה לפני שימוש ב-Exact Alarm
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            } else {
                // אין הרשאה — השתמש ב-inexact alarm (לא מדויק אבל לא קורס)
                alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
        }
    }

    /**
     * מבטל את כל ה-Alarms הקיימים.
     */
    public static void cancel(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        alarmManager.cancel(buildPendingIntent(context));
    }

    // בניית PendingIntent שמפעיל את PriceAlertReceiver
    private static PendingIntent buildPendingIntent(Context context) {
        Intent intent = new Intent(context, PriceAlertReceiver.class);
        intent.setAction(ACTION);
        return PendingIntent.getBroadcast(
                context, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
