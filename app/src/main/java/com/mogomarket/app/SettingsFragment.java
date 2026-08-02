package com.mogomarket.app;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;


public class SettingsFragment extends Fragment {

    private MaterialButton btnThemeToggle;
    private TextView tvThemeStatus;
    private static final String PREFS_NAME       = "app_prefs";
    private static final String KEY_THEME        = "dark_mode";
    private static final String KEY_PRICE_ALERTS = "price_alerts_enabled";
    private static final String KEY_DAILY_EMAIL  = "daily_email_enabled";
    private boolean isDark;
    private SharedPreferences prefs;

    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener =
            (sharedPreferences, key) -> {
                if (KEY_THEME.equals(key)) {
                    isDark = sharedPreferences.getBoolean(KEY_THEME, true);
                    updateThemeUI(isDark);
                }
            };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_settings, container, false);
        // ❌ הוסר: v.setBackgroundColor(...)

        prefs = requireActivity().getSharedPreferences(PREFS_NAME, 0);

        btnThemeToggle = v.findViewById(R.id.btnThemeToggle);
        tvThemeStatus  = v.findViewById(R.id.tvThemeStatus);

        isDark = prefs.getBoolean(KEY_THEME, true);
        updateThemeUI(isDark);

        if (btnThemeToggle != null) {
            btnThemeToggle.setOnClickListener(view -> {
                isDark = !isDark;
                prefs.edit().putBoolean(KEY_THEME, isDark).apply();
                AppCompatDelegate.setDefaultNightMode(
                        isDark ? AppCompatDelegate.MODE_NIGHT_YES
                                : AppCompatDelegate.MODE_NIGHT_NO);
                updateThemeUI(isDark);
                applyThemeToView(requireView(), isDark);  // ✅ צבעים בלי recreate
            });
        }

        // Watchlist navigation
        SwitchMaterial switchWatchlistNav = v.findViewById(R.id.switchWatchlistNav);
        if (switchWatchlistNav != null) {
            switchWatchlistNav.setChecked(prefs.getBoolean(WatchlistFragment.KEY_WATCHLIST_NAV, true));
            switchWatchlistNav.setOnCheckedChangeListener((btn, isChecked) ->
                    prefs.edit().putBoolean(WatchlistFragment.KEY_WATCHLIST_NAV, isChecked).apply());
        }

        // Hide keyboard on add
        SwitchMaterial switchHideKeyboard = v.findViewById(R.id.switchHideKeyboardOnAdd);
        if (switchHideKeyboard != null) {
            switchHideKeyboard.setChecked(prefs.getBoolean(WatchlistFragment.KEY_WATCHLIST_HIDE_KB, true));
            switchHideKeyboard.setOnCheckedChangeListener((btn, isChecked) ->
                    prefs.edit().putBoolean(WatchlistFragment.KEY_WATCHLIST_HIDE_KB, isChecked).apply());
        }

        // Price target alerts
        SwitchMaterial switchPriceAlerts = v.findViewById(R.id.switchPriceAlerts);
        if (switchPriceAlerts != null) {
            boolean alertsEnabled = prefs.getBoolean(KEY_PRICE_ALERTS, false);
            switchPriceAlerts.setChecked(alertsEnabled);
            if (alertsEnabled) PriceAlertScheduler.schedule(requireContext());

            switchPriceAlerts.setOnCheckedChangeListener((btn, isChecked) -> {
                prefs.edit().putBoolean(KEY_PRICE_ALERTS, isChecked).apply();
                if (isChecked) {
                    PriceAlertScheduler.schedule(requireContext());
                    Toast.makeText(requireContext(), "Price target alerts enabled", Toast.LENGTH_SHORT).show();
                } else {
                    PriceAlertScheduler.cancel(requireContext());
                    Toast.makeText(requireContext(), "Price target alerts disabled", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Daily summary email
        SwitchMaterial switchDailyEmail = v.findViewById(R.id.switchDailyEmail);
        if (switchDailyEmail != null) {
            boolean dailyEnabled = prefs.getBoolean(KEY_DAILY_EMAIL, false);
            switchDailyEmail.setChecked(dailyEnabled);
            if (dailyEnabled) DailySummaryEmailService.scheduleDailySummary(requireContext());

            switchDailyEmail.setOnCheckedChangeListener((btn, isChecked) -> {
                prefs.edit().putBoolean(KEY_DAILY_EMAIL, isChecked).apply();
                if (isChecked) {
                    DailySummaryEmailService.scheduleDailySummary(requireContext());
                    Toast.makeText(requireContext(), "Daily summary email enabled (every day at 08:00)", Toast.LENGTH_LONG).show();
                } else {
                    DailySummaryEmailService.cancelDailySummary(requireContext());
                    Toast.makeText(requireContext(), "Daily summary email disabled", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Button: send daily summary now
        MaterialButton btnSendEmailNow = v.findViewById(R.id.btnSendDailyEmailNow);
        if (btnSendEmailNow != null) {
            btnSendEmailNow.setOnClickListener(view -> {
                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                if (user == null || user.isAnonymous() || user.getEmail() == null || user.getEmail().trim().isEmpty()) {
                    Toast.makeText(requireContext(), "No valid user email found", Toast.LENGTH_SHORT).show();
                    return;
                }
                Toast.makeText(requireContext(), "Sending daily summary email...", Toast.LENGTH_SHORT).show();
                DailySummaryEmailService.sendNow(requireContext(), true);
            });
        }

        setupDefaultSymbolSection(v);
        setupStartPageSelector(v);

        // Account email
        TextView tvEmail = v.findViewById(R.id.tvUserEmail);
        if (tvEmail != null) {
            // ❌ הוסר: tvEmail.setTextColor(...)
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null && user.isAnonymous()) {
                tvEmail.setText("Guest");
            } else {
                tvEmail.setText(user != null && user.getEmail() != null ? user.getEmail() : "Guest");
            }
        }

        // App version
        TextView tvVersion = v.findViewById(R.id.tvAppVersion);
        if (tvVersion != null) {
            // ❌ הוסר: tvVersion.setTextColor(...)
            try {
                String ver = requireContext().getPackageManager()
                        .getPackageInfo(requireContext().getPackageName(), 0).versionName;
                tvVersion.setText("v" + ver);
            } catch (Exception e) {
                tvVersion.setText("v1.0");
            }
        }

        // Logout
        MaterialButton btnLogout = v.findViewById(R.id.btnSettingsLogout);
        if (btnLogout != null) {
            // ❌ הוסרו: setBackgroundTintList + setTextColor
            btnLogout.setOnClickListener(view -> {
                FirebaseAuth.getInstance().signOut();
                Intent intent = new Intent(requireActivity(), AuthLogin.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            });
        }

        // Delete Account
        MaterialButton btnDeleteAccount = v.findViewById(R.id.btnDeleteAccount);
        if (btnDeleteAccount != null) {
            btnDeleteAccount.setOnClickListener(view -> showDeleteAccountDialog());
        }

        return v;
    }

    // ─── Delete Account ───────────────────────────────────────────────────────

    private void showDeleteAccountDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Account")
                .setMessage("This will permanently delete your account and ALL your data (portfolio, watchlist, trades).\n\nThis cannot be undone. Are you sure?")
                .setPositiveButton("Delete", (dialog, which) -> deleteAccount())
                .setNegativeButton("Cancel", null)
                .show();
    }


    private void deleteAccount() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        String uid = user.getUid();

        ProgressDialog pd = new ProgressDialog(requireContext());
        pd.setTitle("Deleting Account");
        pd.setMessage("Please wait...");
        pd.setCancelable(false);
        pd.show();

        // מחיקת נתונים ב-Realtime Database ואז מחיקת חשבון
        FirebaseDatabase.getInstance()
                .getReference("users")
                .child(uid)
                .removeValue()
                .addOnCompleteListener(task -> {
                    user.delete().addOnCompleteListener(authTask -> {
                        pd.dismiss();
                        if (authTask.isSuccessful()) {
                            Toast.makeText(requireContext(),
                                    "Account deleted successfully",
                                    Toast.LENGTH_SHORT).show();
                            Intent intent = new Intent(requireActivity(), AuthLogin.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                        } else {
                            String msg = authTask.getException() != null
                                    ? authTask.getException().getMessage()
                                    : "Unknown error";
                            Toast.makeText(requireContext(),
                                    "Failed to delete account: " + msg,
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                });
    }

    // ─── Default Symbol ───────────────────────────────────────────────────────

    private void setupDefaultSymbolSection(View v) {
        android.widget.EditText etDefaultSymbol = v.findViewById(R.id.etDefaultSymbol);
        MaterialButton btnSaveSymbol = v.findViewById(R.id.btnSaveDefaultSymbol);
        MaterialButton btnSymbolMode = v.findViewById(R.id.btnSymbolMode);

        if (etDefaultSymbol != null) {
            String current = prefs.getString(MainActivity.KEY_DEFAULT_SYMBOL, "SPY");
            etDefaultSymbol.setText(current);
        }

        updateSymbolModeButton(btnSymbolMode);

        if (btnSaveSymbol != null) {
            btnSaveSymbol.setOnClickListener(view -> {
                if (etDefaultSymbol == null) return;
                String sym = etDefaultSymbol.getText().toString().trim().toUpperCase();
                if (sym.isEmpty()) sym = "SPY";
                prefs.edit().putString(MainActivity.KEY_DEFAULT_SYMBOL, sym).apply();
                if ("fixed".equals(prefs.getString(ChartFragment.KEY_SYMBOL_MODE, "last"))) {
                    prefs.edit().putString(MainActivity.KEY_LAST_SYMBOL, sym).apply();
                }
                Toast.makeText(requireContext(), "\u2705 Default symbol saved: " + sym, Toast.LENGTH_SHORT).show();
            });
        }

        if (btnSymbolMode != null) {
            btnSymbolMode.setOnClickListener(view -> {
                String current = prefs.getString(ChartFragment.KEY_SYMBOL_MODE, "last");
                String next = "last".equals(current) ? "fixed" : "last";
                prefs.edit().putString(ChartFragment.KEY_SYMBOL_MODE, next).apply();
                updateSymbolModeButton(btnSymbolMode);
                String msg = "fixed".equals(next)
                        ? "\uD83D\uDCCC Switched to fixed default symbol"
                        : "\uD83D\uDD04 Switched to last viewed symbol";
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void updateSymbolModeButton(MaterialButton btn) {
        if (btn == null) return;
        String mode = prefs.getString(ChartFragment.KEY_SYMBOL_MODE, "last");
        if ("fixed".equals(mode)) {
            btn.setText("\uD83D\uDCCC Fixed");
            btn.setStrokeColorResource(R.color.primary);
            btn.setStrokeWidth(6);
        } else {
            btn.setText("\uD83D\uDD04 Last");
            btn.setStrokeColorResource(R.color.text_secondary);
            btn.setStrokeWidth(2);
        }
    }

    // ─── Start Page Selector ─────────────────────────────────────────────────

    private void setupStartPageSelector(View v) {
        int[] btnIds = {
                R.id.btnStartChart,
                R.id.btnStartWatchlist,
                R.id.btnStartPortfolio,
                R.id.btnStartTrades,
                R.id.btnStartSettings
        };
        int[] navIds = {
                R.id.nav_chart,
                R.id.nav_stocks,
                R.id.nav_portfolio,
                R.id.nav_closed_trades,
                R.id.nav_settings
        };

        int savedNavId = prefs.getInt(MainActivity.KEY_START_PAGE, R.id.nav_chart);

        for (int i = 0; i < btnIds.length; i++) {
            final int navId = navIds[i];
            MaterialButton btn = v.findViewById(btnIds[i]);
            if (btn == null) continue;

            btn.setStrokeColorResource(navId == savedNavId ? R.color.primary : R.color.text_secondary);
            btn.setStrokeWidth(navId == savedNavId ? 4 : 1);

            btn.setOnClickListener(click -> {
                prefs.edit().putInt(MainActivity.KEY_START_PAGE, navId).apply();
                Toast.makeText(requireContext(), "\u2705 Start page saved", Toast.LENGTH_SHORT).show();
                for (int j = 0; j < btnIds.length; j++) {
                    MaterialButton b = v.findViewById(btnIds[j]);
                    if (b == null) continue;
                    boolean selected = navIds[j] == navId;
                    b.setStrokeColorResource(selected ? R.color.primary : R.color.text_secondary);
                    b.setStrokeWidth(selected ? 4 : 1);
                }
            });
        }
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    public void onStart() {
        super.onStart();
        if (prefs != null) prefs.registerOnSharedPreferenceChangeListener(prefListener);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (prefs != null) prefs.unregisterOnSharedPreferenceChangeListener(prefListener);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (prefs == null) return;
        isDark = prefs.getBoolean(KEY_THEME, true);
        updateThemeUI(isDark);
        applyThemeToView(requireView(), isDark);
    }

    private void updateThemeUI(boolean dark) {
        if (btnThemeToggle == null || tvThemeStatus == null) return;

        int primary = requireContext().getColor(R.color.primary);

        tvThemeStatus.setText(dark ? "Dark" : "Light");
        tvThemeStatus.setTextColor(primary);

        btnThemeToggle.setText(dark ? "🌙 Dark" : "☀ Light");
        btnThemeToggle.setStrokeColorResource(R.color.primary);
        btnThemeToggle.setStrokeWidth(2);
        btnThemeToggle.setTextColor(primary);
        btnThemeToggle.setIconTintResource(R.color.primary);

        btnThemeToggle.animate()
                .scaleX(1.02f).scaleY(1.02f).setDuration(120)
                .withEndAction(() -> btnThemeToggle.animate()
                        .scaleX(1f).scaleY(1f).setDuration(120).start())
                .start();
    }

    private void setChildTextColors(LinearLayout layout, int color) {
        for (int i = 0; i < layout.getChildCount(); i++) {
            View child = layout.getChildAt(i);
            if (child instanceof TextView) ((TextView) child).setTextColor(color);
        }
    }

    private void applyThemeToView(View root, boolean dark) {
        int bgPrimary     = dark ? 0xFF0B0F14 : 0xFFF0F4F8;
        int bgSecondary   = dark ? 0xFF111826 : 0xFFFFFFFF;
        int bgCard        = dark ? 0xFF151C2E : 0xFFFFFFFF;
        int textPrimary   = dark ? 0xFFE6EDF3 : 0xFF0D1117;
        int textSecondary = dark ? 0xFF8B98A5 : 0xFF4A5568;
        int colorPrimary  = 0xFF4DA3FF;  // זהה ב-light וב-dark
        int colorBorder   = dark ? 0xFF1E2A3A : 0xFFE2E8F0;

        // רקע הכרטיסייה הראשית
        root.setBackgroundColor(bgPrimary);

        // כל ה-CardView ברקע bg_card
        applyToAllCards(root, bgCard, colorBorder);

        // כל ה-TextViews
        applyToAllTextViews(root, textPrimary, textSecondary, colorPrimary);

        // Bottom nav
        if (getActivity() != null) {
            com.google.android.material.bottomnavigation.BottomNavigationView nav =
                    getActivity().findViewById(R.id.bottom_navigation);
            if (nav != null) {
                nav.setBackgroundColor(bgSecondary);
            }
        }
    }

    private void applyToAllCards(View root, int bgCard, int borderColor) {
        if (root instanceof androidx.cardview.widget.CardView) {
            ((androidx.cardview.widget.CardView) root).setCardBackgroundColor(bgCard);
        } else if (root instanceof com.google.android.material.card.MaterialCardView) {
            ((com.google.android.material.card.MaterialCardView) root).setCardBackgroundColor(bgCard);
        }
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyToAllCards(group.getChildAt(i), bgCard, borderColor);
            }
        }
    }

    private void applyToAllTextViews(View root, int textPrimary, int textSecondary, int colorPrimary) {
        if (root instanceof android.widget.TextView) {
            android.widget.TextView tv = (android.widget.TextView) root;
            String tag = tv.getTag() != null ? tv.getTag().toString() : "";
            if ("muted".equals(tag)) {
                tv.setTextColor(textSecondary);
            } else if ("accent".equals(tag)) {
                tv.setTextColor(colorPrimary);
            } else {
                tv.setTextColor(textPrimary);
            }
        }
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyToAllTextViews(group.getChildAt(i), textPrimary, textSecondary, colorPrimary);
            }
        }
    }
}