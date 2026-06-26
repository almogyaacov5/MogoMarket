package com.example.chart;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "app_prefs";
    private static final String KEY_THEME  = "dark_mode";

    private int currentNavId = -1;

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    isGranted -> { if (isGranted) PriceAlertScheduler.schedule(this); });

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

        requestNotificationPermissionIfNeeded();
        PriceAlertScheduler.schedule(this);
        setupBottomNav();

        if (savedInstanceState == null) {
            navigateTo(R.id.nav_chart);
        }
    }

    private void setupBottomNav() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setOnItemSelectedListener(item -> {
            navigateTo(item.getItemId());
            return true;
        });
    }

    private void navigateTo(int id) {
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
            FragmentTransaction tx = getSupportFragmentManager()
                    .beginTransaction()
                    .setCustomAnimations(
                            android.R.anim.fade_in,
                            android.R.anim.fade_out
                    )
                    .replace(R.id.fragment_container, fragment);
            tx.commit();
            setTitle(title);

            // sync bottom nav selected item
            BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
            if (bottomNav != null && bottomNav.getSelectedItemId() != id) {
                bottomNav.setSelectedItemId(id);
            }
        }
    }

    public void showChartWithSymbol(String symbol) {
        ChartFragment chartFragment = new ChartFragment();
        Bundle args = new Bundle();
        args.putString("symbol", symbol);
        chartFragment.setArguments(args);

        FragmentTransaction tx = getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.fragment_container, chartFragment);
        tx.commit();

        setTitle("Chart");
        currentNavId = R.id.nav_chart;

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        if (bottomNav != null) bottomNav.setSelectedItemId(R.id.nav_chart);
    }

    // ── Public helper so any fragment can open P&L Calculator ────────────────
    public void openPnlCalculator() {
        getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(
                        android.R.anim.slide_in_left,
                        android.R.anim.slide_out_right
                )
                .replace(R.id.fragment_container, new PnlCalculatorFragment())
                .addToBackStack(null)
                .commit();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }
}
