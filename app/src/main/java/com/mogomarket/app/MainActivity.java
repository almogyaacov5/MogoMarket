package com.mogomarket.app;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
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
        // ✅ חובה לפני super.onCreate כדי למנוע recreation
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isDarkMode = prefs.getBoolean(KEY_THEME, true);
        AppCompatDelegate.setDefaultNightMode(
                isDarkMode ? AppCompatDelegate.MODE_NIGHT_YES
                        : AppCompatDelegate.MODE_NIGHT_NO);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        requestNotificationPermission();

        // ✅ Navigation Component Setup
        NavHostFragment navHostFragment =
                (NavHostFragment) getSupportFragmentManager()
                        .findFragmentById(R.id.nav_host);

        navController = navHostFragment.getNavController();

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        NavigationUI.setupWithNavController(bottomNav, navController);

        // ✅ עמוד התחלה לפי הגדרות המשתמש
        if (savedInstanceState == null) {
            int startPageId = prefs.getInt(KEY_START_PAGE, R.id.nav_chart);
            if (startPageId != R.id.nav_chart) {
                navController.navigate(startPageId);
            }
        }

        // שירותי רקע
        PriceTargetAlertService.startService(this);
        DailySummaryEmailService.scheduleDailySummary(this);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        RC_NOTIF);
            }
        }
    }

    /**
     * ניווט לגרף עם סמל ספציפי — נשמר ב-SharedPreferences,
     * ה-ChartFragment יקרא את הסמל ב-onViewCreated דרך SharedViewModel.
     */
    public void showChartWithSymbol(String symbol) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString(KEY_LAST_SYMBOL, symbol).apply();

        // ✅ עדכון SharedViewModel כדי שכל הפרגמנטים יסתנכרנו
        SharedViewModel vm = new androidx.lifecycle.ViewModelProvider(this)
                .get(SharedViewModel.class);
        vm.setSelectedSymbol(symbol);

        // ✅ Navigation Component — ניווט בטוח ללא FragmentTransaction ידני
        navController.navigate(R.id.nav_chart);
    }

    /**
     * פתיחת PnL Calculator עם backstack תקין
     */
    public void openPnlCalculator() {
        navController.navigate(R.id.pnlCalculatorFragment);
    }

    /**
     * פתיחת Settings
     */
    public void openSettings() {
        navController.navigate(R.id.nav_settings);
    }
}