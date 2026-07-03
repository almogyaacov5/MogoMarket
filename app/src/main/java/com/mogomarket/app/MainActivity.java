package com.mogomarket.app;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    static final String PREFS_NAME          = ChartFragment.PREFS_NAME;
    static final String KEY_LAST_SYMBOL     = ChartFragment.KEY_LAST_SYMBOL;
    static final String KEY_DEFAULT_SYMBOL  = ChartFragment.KEY_DEFAULT_SYMBOL;
    static final String KEY_START_PAGE      = "start_page_nav_id";

    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // החל theme לפני setContentView
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isDark = prefs.getBoolean(ChartFragment.KEY_THEME, true);
        AppCompatDelegate.setDefaultNightMode(
                isDark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bottomNav = findViewById(R.id.bottom_navigation);

        if (savedInstanceState == null) {
            // קבע את דף הפתיחה לפי הגדרות המשתמש
            int startNavId = prefs.getInt(KEY_START_PAGE, R.id.nav_chart);
            loadFragmentForNavId(startNavId);
            bottomNav.setSelectedItemId(startNavId);
        }

        bottomNav.setOnItemSelectedListener(item -> {
            loadFragmentForNavId(item.getItemId());
            return true;
        });
    }

    private void loadFragmentForNavId(int navId) {
        Fragment fragment;
        if (navId == R.id.nav_chart) {
            fragment = new ChartFragment();
        } else if (navId == R.id.nav_watchlist) {
            fragment = new WatchlistFragment();
        } else if (navId == R.id.nav_portfolio) {
            fragment = new PortfolioFragment();
        } else if (navId == R.id.nav_trades) {
            fragment = new ClosedTradesFragment();
        } else if (navId == R.id.nav_settings) {
            fragment = new SettingsFragment();
        } else {
            fragment = new ChartFragment();
        }
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    public void navigateToChart(String symbol) {
        Bundle args = new Bundle();
        args.putString("symbol", symbol);
        ChartFragment chartFragment = new ChartFragment();
        chartFragment.setArguments(args);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, chartFragment)
                .addToBackStack(null)
                .commit();
        bottomNav.setSelectedItemId(R.id.nav_chart);
    }

    public void openSettings() {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new SettingsFragment())
                .addToBackStack(null)
                .commit();
        bottomNav.setSelectedItemId(R.id.nav_settings);
    }
}
