package com.mogomarket.app;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

public class MainActivity extends AppCompatActivity {

    public static final String PREFS_NAME         = "app_prefs";
    public static final String KEY_THEME          = "dark_mode";
    public static final String KEY_LAST_SYMBOL    = "last_chart_symbol";
    public static final String KEY_DEFAULT_SYMBOL = "default_chart_symbol";
    public static final String KEY_START_PAGE     = "start_page_nav_id";
    private static final String KEY_FIRST_RUN_GUIDE_SHOWN = "first_run_guide_shown";

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
        EdgeToEdgeHelper.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(systemBars.left, systemBars.top,
                    systemBars.right, systemBars.bottom);
            return insets;
        });

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

        setupNavigationDock();
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
            if (!prefs.getBoolean(KEY_FIRST_RUN_GUIDE_SHOWN, false)) {
                showFirstRunGuide(prefs);
            }
        }

        // שירותי רקע קיימים
        if (prefs.getBoolean(DailySummaryEmailService.KEY_ENABLED, false)) {
            DailySummaryEmailService.scheduleDailySummary(this);
        }
    }

    private void setupNavigationDock() {
        int[] buttonIds = {
                R.id.nav_chart_button,
                R.id.nav_portfolio_button,
                R.id.nav_watchlist_button,
                R.id.nav_trades_button,
                R.id.nav_simulator_button,
                R.id.nav_settings_button
        };
        int[] destinationIds = {
                R.id.nav_chart,
                R.id.nav_portfolio,
                R.id.nav_stocks,
                R.id.nav_closed_trades,
                R.id.nav_simulator,
                R.id.nav_settings
        };
        View[] buttons = new View[buttonIds.length];
        for (int i = 0; i < buttonIds.length; i++) {
            buttons[i] = findViewById(buttonIds[i]);
            final int destinationId = destinationIds[i];
            buttons[i].setOnClickListener(view -> {
                NavOptions options = new NavOptions.Builder()
                        .setLaunchSingleTop(true)
                        .setRestoreState(true)
                        .setPopUpTo(navController.getGraph().getStartDestinationId(), false, true)
                        .build();
                navController.navigate(destinationId, null, options);
            });
        }

        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            for (int i = 0; i < buttons.length; i++) {
                boolean selected = destination.getId() == destinationIds[i];
                buttons[i].setSelected(selected);
                float scale = selected ? 1.18f : 1f;
                buttons[i].animate()
                        .scaleX(scale)
                        .scaleY(scale)
                        .setDuration(160)
                        .start();
            }
        });
    }

    private void showFirstRunGuide(SharedPreferences preferences) {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, padding);

        TextView title = new TextView(this);
        title.setText("Welcome to Mogo Market");
        title.setTextSize(20f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        content.addView(title);

        TextView pages = new TextView(this);
        pages.setText(
                "Chart — View prices, choose a timeframe, and compare trends.\n\n"
                + "Watchlist — Track selected stocks and set price alerts.\n\n"
                + "Portfolio — Manage open positions, cash, performance, and screenshots.\n\n"
                + "Trades — Review closed trades, analytics, and trade history.\n\n"
                + "Simulator — Practice trading with simulated positions.\n\n"
                + "Settings — Customize the app, chart defaults, and notifications.\n\n"
                + "PnL Calculator — Estimate position size and risk/reward.\n\n"
                + "Add Trade — Record an open position or import a trade screenshot.");
        pages.setTextSize(15f);
        LinearLayout.LayoutParams pageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pageParams.topMargin = Math.round(12 * getResources().getDisplayMetrics().density);
        content.addView(pages, pageParams);
        scrollView.addView(content);

        new AlertDialog.Builder(this)
                .setView(scrollView)
                .setPositiveButton("Get started", (dialog, which) ->
                        preferences.edit().putBoolean(KEY_FIRST_RUN_GUIDE_SHOWN, true).apply())
                .setCancelable(false)
                .show();
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

        View dockBackground = findViewById(R.id.navigation_dock_background);
        if (dockBackground != null) {
            dockBackground.setBackgroundResource(R.drawable.bg_liquid_glass);
        }

        // עדכן את הרקע הכללי
        View root = findViewById(android.R.id.content);
        if (root != null) root.setBackgroundColor(isDark ? 0xFF07090D : 0xFFF0F4F8);
    }

}
