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
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    public static final String PREFS_NAME        = "app_prefs";
    public static final String KEY_THEME         = "dark_mode";
    public static final String KEY_LAST_SYMBOL   = "last_chart_symbol";
    public static final String KEY_DEFAULT_SYMBOL= "default_chart_symbol";
    public static final String KEY_START_PAGE    = "start_page_nav_id";

    private static final int RC_NOTIF = 1002;
    private int currentNavId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isDarkMode = prefs.getBoolean(KEY_THEME, true);
        AppCompatDelegate.setDefaultNightMode(
                isDarkMode ? AppCompatDelegate.MODE_NIGHT_YES
                           : AppCompatDelegate.MODE_NIGHT_NO);

        setContentView(R.layout.activity_main);

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        requestNotificationPermission();
        setupBottomNav();

        // שירותי רקע
        PriceTargetAlertService.startService(this);
        DailySummaryEmailService.scheduleDailySummary(this);

        if (savedInstanceState == null) {
            int startPageId = prefs.getInt(KEY_START_PAGE, R.id.nav_chart);
            navigateTo(startPageId);
        }
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

    private void setupBottomNav() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setOnItemSelectedListener(item -> {
            navigateTo(item.getItemId());
            return true;
        });
    }

    public void navigateTo(int id) {
        if (id == currentNavId) return;
        currentNavId = id;

        Fragment fragment = null;
        String title = "";

        if      (id == R.id.nav_chart)         { fragment = new ChartFragment();        title = "Chart"; }
        else if (id == R.id.nav_stocks)        { fragment = new WatchlistFragment();    title = "Watchlist"; }
        else if (id == R.id.nav_portfolio)     { fragment = new PortfolioFragment();    title = "Portfolio"; }
        else if (id == R.id.nav_closed_trades) { fragment = new ClosedTradesFragment(); title = "Closed Trades"; }
        else if (id == R.id.nav_simulator)     { fragment = new SimulatorFragment();    title = "Simulator"; }
        else if (id == R.id.nav_settings)      { fragment = new SettingsFragment();     title = "Settings"; }

        if (fragment != null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                    .replace(R.id.fragment_container, fragment)
                    .commit();
            setTitle(title);

            BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
            if (bottomNav != null && bottomNav.getSelectedItemId() != id) {
                bottomNav.setSelectedItemId(id);
            }
        }
    }

    public void showChartWithSymbol(String symbol) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString(KEY_LAST_SYMBOL, symbol).apply();

        ChartFragment chartFragment = new ChartFragment();
        Bundle args = new Bundle();
        args.putString("symbol", symbol);
        chartFragment.setArguments(args);

        getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.fragment_container, chartFragment)
                .commit();

        setTitle("Chart");
        currentNavId = R.id.nav_chart;

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        if (bottomNav != null) bottomNav.setSelectedItemId(R.id.nav_chart);
    }

    public void openPnlCalculator() {
        getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
                .replace(R.id.fragment_container, new PnlCalculatorFragment())
                .addToBackStack(null)
                .commit();
    }

    public void openSettings() {
        navigateTo(R.id.nav_settings);
    }
}
