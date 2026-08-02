package com.mogomarket.app;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    public static final String PREFS_NAME         = "app_prefs";
    public static final String KEY_THEME          = "dark_mode";
    public static final String KEY_LAST_SYMBOL    = "last_chart_symbol";
    public static final String KEY_DEFAULT_SYMBOL = "default_chart_symbol";
    public static final String KEY_START_PAGE     = "start_page_nav_id";

    private static final int RC_NOTIF = 1002;

    private NavController navController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // הגדרת מצב כהה/בהיר לפני יצירת ה-Activity
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isDarkMode = prefs.getBoolean(KEY_THEME, true);
        AppCompatDelegate.setDefaultNightMode(
                isDarkMode ? AppCompatDelegate.MODE_NIGHT_YES
                        : AppCompatDelegate.MODE_NIGHT_NO);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        requestNotificationPermission();

        // NavHost + NavController
        NavHostFragment navHostFragment =
                (NavHostFragment) getSupportFragmentManager()
                        .findFragmentById(R.id.nav_host);

        if (navHostFragment == null) {
            throw new IllegalStateException("NavHostFragment with id nav_host not found");
        }

        navController = navHostFragment.getNavController();

        // BottomNavigationView + NavigationUI
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        NavigationUI.setupWithNavController(bottomNav, navController);

        // עמוד התחלתי לפי ההגדרה השמורה
        if (savedInstanceState == null) {
            int startPageId = prefs.getInt(KEY_START_PAGE, R.id.nav_chart);

            // וידוא שה-ID הוא destination חוקי ב-NavGraph לפני ניווט
            boolean isValidDestination = false;
            try {
                isValidDestination = (navController.getGraph().findNode(startPageId) != null);
            } catch (Exception e) {
                isValidDestination = false;
            }

            if (isValidDestination && startPageId != R.id.nav_chart) {
                navController.navigate(startPageId);
            } else if (!isValidDestination) {
                // נקה ערך פגום מה-SharedPreferences
                prefs.edit().remove(KEY_START_PAGE).apply();
            }
        }

        // שירותי רקע קיימים
        DailySummaryEmailService.scheduleDailySummary(this);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        RC_NOTIF
                );
            }
        }
    }

    /**
     * מעבר לגרף עם סימבול מסוים.
     * מעדכן גם SharedViewModel וגם SharedPreferences.
     */
    public void showChartWithSymbol(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) return;

        String cleanSymbol = symbol.trim().toUpperCase();

        // שמירה ב-SharedPreferences (לשימוש עתידי)
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString(KEY_LAST_SYMBOL, cleanSymbol)
                .apply();

        // SharedViewModel משותף
        SharedViewModel vm = new ViewModelProvider(this)
                .get(SharedViewModel.class);
        vm.setSelectedSymbol(cleanSymbol);

        // ניווט למסך הגרף
        if (navController != null) {
            navController.navigate(R.id.nav_chart);
        }
    }

    /**
     * פתיחת מחשבון PnL כ-screen בתוך ה-NavGraph.
     */
    public void openPnlCalculator() {
        if (navController != null) {
            navController.navigate(R.id.pnlCalculatorFragment);
        }
    }

    /**
     * פתיחת Settings דרך ה-NavController.
     */
    public void openSettings() {
        if (navController != null) {
            navController.navigate(R.id.nav_settings);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // אפס את ה-session symbol כדי שפתיחה הבאה תתחיל מ-fixed
        SharedViewModel vm = new ViewModelProvider(this).get(SharedViewModel.class);
        vm.clearSessionSymbol();
    }

    public void applyThemeToAllFragments(boolean isDark) {
        // עדכן את כל ה-Fragments הקיימים
        for (Fragment f : getSupportFragmentManager().getFragments()) {
            if (f instanceof ChartFragment) {
                ((ChartFragment) f).applyThemeExternal(isDark);
            }
            // ניתן להוסיף כאן WatchlistFragment, PortfolioFragment וכו' לפי הצורך
        }

        // עדכן את צבע ה-BottomNavigationView
        int bgColor = isDark ? 0xFF151C2E : 0xFFFFFFFF;
        View bottomNav = findViewById(R.id.bottom_navigation);

        if (bottomNav != null) bottomNav.setBackgroundColor(bgColor);

        // עדכן את הרקע הכללי
        View root = findViewById(android.R.id.content);
        if (root != null) root.setBackgroundColor(isDark ? 0xFF0B0F14 : 0xFFF0F4F8);
    }
}
