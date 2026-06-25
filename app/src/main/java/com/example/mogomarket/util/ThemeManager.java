package com.example.mogomarket.util;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;

/**
 * ThemeManager - ניהול מצב כהה/בהיר עם שמירת העדפה
 *
 * שימוש בסיסי:
 *   ThemeManager.applyTheme(context);  // קרא ב-onCreate לפני setContentView
 *   ThemeManager.toggleTheme(activity); // קרא מכפתור ה-toggle
 *   ThemeManager.isDarkMode(context);   // בדוק מצב נוכחי
 */
public class ThemeManager {

    private static final String PREFS_NAME = "mogo_prefs";
    private static final String KEY_DARK_MODE = "dark_mode";
    private static final int MODE_UNSET = -1;

    /**
     * החל את ה-Theme השמור — קרא ב-onCreate() לפני setContentView()
     */
    public static void applyTheme(Context context) {
        int savedMode = getSavedMode(context);
        if (savedMode == MODE_UNSET) {
            // ברירת מחדל: עקוב אחרי המערכת
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        } else {
            AppCompatDelegate.setDefaultNightMode(savedMode);
        }
    }

    /**
     * החלף בין כהה לבהיר ושמור העדפה
     */
    public static void toggleTheme(Activity activity) {
        boolean isDark = isDarkMode(activity);
        int newMode = isDark
                ? AppCompatDelegate.MODE_NIGHT_NO
                : AppCompatDelegate.MODE_NIGHT_YES;
        saveMode(activity, newMode);
        AppCompatDelegate.setDefaultNightMode(newMode);
        // Activity.recreate() נקרא אוטומטית ע"י AppCompatDelegate
    }

    /**
     * קבע מצב ספציפי (MODE_NIGHT_YES / MODE_NIGHT_NO / MODE_NIGHT_FOLLOW_SYSTEM)
     */
    public static void setMode(Context context, int mode) {
        saveMode(context, mode);
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    /**
     * האם אנחנו עכשיו במצב כהה?
     */
    public static boolean isDarkMode(Context context) {
        int currentNightMode = context.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    // ==================== Private Helpers ====================

    private static int getSavedMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_DARK_MODE, MODE_UNSET);
    }

    private static void saveMode(Context context, int mode) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_DARK_MODE, mode).apply();
    }
}
