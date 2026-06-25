package com.example.chart;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "app_prefs";
    private static final String KEY_THEME  = "dark_mode";

    // ── צבעי ניווט תחתון ──────────────────────────────────
    private static final int COLOR_NAV_ACTIVE   = 0xFF4DA3FF;  // כחול ניאון
    private static final int COLOR_NAV_INACTIVE = 0xFF8B98A5;  // אפור משני
    private static final int COLOR_NAV_BG       = 0xFF111826;  // bg_secondary
    // ──────────────────────────────────────────────────────

    private boolean isDarkMode = true;
    private int currentNavId = -1;

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    isGranted -> { if (isGranted) PriceAlertScheduler.schedule(this); });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        isDarkMode = prefs.getBoolean(KEY_THEME, true);
        AppCompatDelegate.setDefaultNightMode(
                isDarkMode ? AppCompatDelegate.MODE_NIGHT_YES
                           : AppCompatDelegate.MODE_NIGHT_NO);

        setContentView(R.layout.activity_main);

        // הסתר ActionBar — אנחנו משתמשים ב-Custom TopBar
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        // צבע רקע לניווט תחתון
        LinearLayout bottomNav = findViewById(R.id.bottom_nav_bar);
        if (bottomNav != null) bottomNav.setBackgroundColor(COLOR_NAV_BG);

        requestNotificationPermissionIfNeeded();
        PriceAlertScheduler.schedule(this);
        setupBottomNav();

        if (savedInstanceState == null) {
            navigateTo(R.id.nav_chart);
        }
    }

    // ─── Bottom Nav ───────────────────────────────────────
    private void setupBottomNav() {
        findViewById(R.id.nav_btn_chart).setOnClickListener(v     -> navigateTo(R.id.nav_chart));
        findViewById(R.id.nav_btn_stocks).setOnClickListener(v    -> navigateTo(R.id.nav_stocks));
        findViewById(R.id.nav_btn_portfolio).setOnClickListener(v -> navigateTo(R.id.nav_portfolio));
        findViewById(R.id.nav_btn_closed).setOnClickListener(v    -> navigateTo(R.id.nav_closed_trades));
        findViewById(R.id.nav_btn_simulator).setOnClickListener(v -> navigateTo(R.id.nav_simulator));
        findViewById(R.id.nav_btn_settings).setOnClickListener(v  -> navigateTo(R.id.nav_settings));
    }

    private void navigateTo(int id) {
        if (id == currentNavId) return;
        currentNavId = id;

        Fragment fragment = null;
        String   title    = "";

        if      (id == R.id.nav_chart)         { fragment = new ChartFragment();        title = "Chart"; }
        else if (id == R.id.nav_stocks)        { fragment = new WatchlistFragment();    title = "Watchlist"; }
        else if (id == R.id.nav_portfolio)     { fragment = new PortfolioFragment();    title = "Portfolio"; }
        else if (id == R.id.nav_closed_trades) { fragment = new ClosedTradesFragment(); title = "Closed Trades"; }
        else if (id == R.id.nav_simulator)     { fragment = new SimulatorFragment();    title = "Simulator"; }
        else if (id == R.id.nav_settings)      { fragment = new SettingsFragment();     title = "Settings"; }

        if (fragment != null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .commit();
            setTitle(title);
            updateNavHighlight(id);
        }
    }

    private void updateNavHighlight(int selectedId) {
        int[][] navMap = {
            {R.id.nav_chart,         R.id.nav_icon_chart,     R.id.nav_label_chart},
            {R.id.nav_stocks,        R.id.nav_icon_stocks,    R.id.nav_label_stocks},
            {R.id.nav_portfolio,     R.id.nav_icon_portfolio, R.id.nav_label_portfolio},
            {R.id.nav_closed_trades, R.id.nav_icon_closed,    R.id.nav_label_closed},
            {R.id.nav_simulator,     R.id.nav_icon_simulator, R.id.nav_label_simulator},
            {R.id.nav_settings,      R.id.nav_icon_settings,  R.id.nav_label_settings},
        };

        for (int[] entry : navMap) {
            boolean   active = (entry[0] == selectedId);
            int       color  = active ? COLOR_NAV_ACTIVE : COLOR_NAV_INACTIVE;
            ImageView icon   = findViewById(entry[1]);
            TextView  label  = findViewById(entry[2]);
            if (icon  != null) icon.setColorFilter(color);
            if (label != null) label.setTextColor(color);
        }
    }

    public void showChartWithSymbol(String symbol) {
        ChartFragment chartFragment = new ChartFragment();
        Bundle args = new Bundle();
        args.putString("symbol", symbol);
        chartFragment.setArguments(args);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, chartFragment)
                .commit();
        setTitle("Chart");
        updateNavHighlight(R.id.nav_chart);
        currentNavId = R.id.nav_chart;
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }
}
