package com.mogomarket.app;

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

public class SettingsFragment extends Fragment {

    private static final String PREFS_NAME = "app_prefs";
    private static final String KEY_THEME  = "dark_mode";

    private LinearLayout btnLightMode, btnDarkMode;
    private TextView tvThemeStatus;
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
        v.setBackgroundColor(requireContext().getColor(R.color.bg_primary));

        prefs = requireActivity().getSharedPreferences(PREFS_NAME, 0);

        // ── Theme ──────────────────────────────────────────────────────────────
        btnLightMode  = v.findViewById(R.id.btnLightMode);
        btnDarkMode   = v.findViewById(R.id.btnDarkMode);
        tvThemeStatus = v.findViewById(R.id.tvThemeStatus);

        isDark = prefs.getBoolean(KEY_THEME, true);
        updateThemeUI(isDark);

        btnLightMode.setOnClickListener(view -> {
            isDark = false;
            prefs.edit().putBoolean(KEY_THEME, false).apply();
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            updateThemeUI(false);
        });

        btnDarkMode.setOnClickListener(view -> {
            isDark = true;
            prefs.edit().putBoolean(KEY_THEME, true).apply();
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            updateThemeUI(true);
        });

        // ── Toggle: Watchlist nav ──────────────────────────────────────────────
        SwitchMaterial switchWatchlistNav = v.findViewById(R.id.switchWatchlistNav);
        if (switchWatchlistNav != null) {
            switchWatchlistNav.setChecked(prefs.getBoolean(WatchlistFragment.KEY_WATCHLIST_NAV, true));
            switchWatchlistNav.setOnCheckedChangeListener((btn, isChecked) ->
                    prefs.edit().putBoolean(WatchlistFragment.KEY_WATCHLIST_NAV, isChecked).apply());
        }

        SwitchMaterial switchHideKeyboard = v.findViewById(R.id.switchHideKeyboardOnAdd);
        if (switchHideKeyboard != null) {
            switchHideKeyboard.setChecked(prefs.getBoolean(WatchlistFragment.KEY_WATCHLIST_HIDE_KB, true));
            switchHideKeyboard.setOnCheckedChangeListener((btn, isChecked) ->
                    prefs.edit().putBoolean(WatchlistFragment.KEY_WATCHLIST_HIDE_KB, isChecked).apply());
        }

        // ── Default symbol + mode toggle ───────────────────────────────────────
        setupDefaultSymbolSection(v);

        // ── Start page selector ────────────────────────────────────────────────
        setupStartPageSelector(v);

        // ── Email + Version ────────────────────────────────────────────────────
        TextView tvEmail = v.findViewById(R.id.tvUserEmail);
        if (tvEmail != null) {
            tvEmail.setTextColor(requireContext().getColor(R.color.primary));
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null && user.isAnonymous()) {
                tvEmail.setText("Guest");
            } else {
                tvEmail.setText(user != null && user.getEmail() != null
                        ? user.getEmail() : "Guest");
            }
        }

        TextView tvVersion = v.findViewById(R.id.tvAppVersion);
        if (tvVersion != null) {
            tvVersion.setTextColor(requireContext().getColor(R.color.text_secondary));
            try {
                String ver = requireContext().getPackageManager()
                        .getPackageInfo(requireContext().getPackageName(), 0).versionName;
                tvVersion.setText("v" + ver);
            } catch (Exception e) {
                tvVersion.setText("v1.0");
            }
        }

        // ── Logout ─────────────────────────────────────────────────────────────
        MaterialButton btnLogout = v.findViewById(R.id.btnSettingsLogout);
        if (btnLogout != null) {
            btnLogout.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(
                            requireContext().getColor(R.color.loss)));
            btnLogout.setTextColor(requireContext().getColor(R.color.white));
            btnLogout.setOnClickListener(view -> {
                FirebaseAuth.getInstance().signOut();
                Intent intent = new Intent(requireActivity(), AuthLogin.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            });
        }

        return v;
    }

    /**
     * Default symbol section: text field + save button + mode toggle
     * ("Last Viewed" vs "Fixed Default")
     */
    private void setupDefaultSymbolSection(View v) {
        android.widget.EditText etDefaultSymbol = v.findViewById(R.id.etDefaultSymbol);
        MaterialButton btnSaveSymbol  = v.findViewById(R.id.btnSaveDefaultSymbol);
        MaterialButton btnSymbolMode  = v.findViewById(R.id.btnSymbolMode);

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
                Toast.makeText(requireContext(),
                        "\u2705 Default symbol saved: " + sym,
                        Toast.LENGTH_SHORT).show();
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

    /** Update text and style of the symbol mode button */
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

    /** Start page selector — 5 buttons */
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
                Toast.makeText(requireContext(),
                        "\u2705 Start page saved",
                        Toast.LENGTH_SHORT).show();
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
    }

    private void updateThemeUI(boolean dark) {
        if (btnLightMode == null || btnDarkMode == null || tvThemeStatus == null) return;

        int selectedText   = requireContext().getColor(R.color.white);
        int unselectedText = requireContext().getColor(R.color.text_secondary);

        if (dark) {
            btnDarkMode.setBackgroundResource(R.drawable.bg_theme_btn_selected);
            btnLightMode.setBackgroundResource(R.drawable.bg_theme_btn_unselected);
            setChildTextColors(btnDarkMode,  selectedText);
            setChildTextColors(btnLightMode, unselectedText);
            tvThemeStatus.setText("\uD83C\uDF19 Dark mode active");
            tvThemeStatus.setTextColor(requireContext().getColor(R.color.primary));
        } else {
            btnLightMode.setBackgroundResource(R.drawable.bg_theme_btn_selected);
            btnDarkMode.setBackgroundResource(R.drawable.bg_theme_btn_unselected);
            setChildTextColors(btnLightMode, selectedText);
            setChildTextColors(btnDarkMode,  unselectedText);
            tvThemeStatus.setText("\u2600\uFE0F Light mode active");
            tvThemeStatus.setTextColor(requireContext().getColor(R.color.gain));
        }
    }

    private void setChildTextColors(LinearLayout layout, int color) {
        for (int i = 0; i < layout.getChildCount(); i++) {
            View child = layout.getChildAt(i);
            if (child instanceof TextView)
                ((TextView) child).setTextColor(color);
        }
    }
}
