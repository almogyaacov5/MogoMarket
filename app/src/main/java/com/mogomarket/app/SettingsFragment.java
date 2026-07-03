package com.mogomarket.app;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
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

    private SharedPreferences prefs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        prefs = requireActivity().getSharedPreferences(ChartFragment.PREFS_NAME, 0);

        // ── Theme toggle ──────────────────────────────────────────────────────
        LinearLayout btnLightMode = view.findViewById(R.id.btnLightMode);
        LinearLayout btnDarkMode  = view.findViewById(R.id.btnDarkMode);
        TextView tvThemeStatus    = view.findViewById(R.id.tvThemeStatus);

        boolean isDark = prefs.getBoolean(ChartFragment.KEY_THEME, true);
        updateThemeUI(btnLightMode, btnDarkMode, tvThemeStatus, isDark);

        if (btnLightMode != null) btnLightMode.setOnClickListener(v -> setTheme(false, btnLightMode, btnDarkMode, tvThemeStatus));
        if (btnDarkMode  != null) btnDarkMode.setOnClickListener(v  -> setTheme(true,  btnLightMode, btnDarkMode, tvThemeStatus));

        // ── Account ──────────────────────────────────────────────────────────
        TextView tvUserEmail = view.findViewById(R.id.tvUserEmail);
        if (tvUserEmail != null) {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) {
                if (user.isAnonymous()) {
                    tvUserEmail.setText("Guest");
                } else {
                    tvUserEmail.setText(user.getEmail() != null ? user.getEmail() : "Unknown");
                }
            }
        }

        MaterialButton btnLogout = view.findViewById(R.id.btnSettingsLogout);
        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> {
                FirebaseAuth.getInstance().signOut();
                startActivity(new android.content.Intent(requireContext(), AuthLogin.class));
                requireActivity().finish();
            });
        }

        // ── Navigation switches ───────────────────────────────────────────────
        SwitchMaterial switchWatchlistNav = view.findViewById(R.id.switchWatchlistNav);
        if (switchWatchlistNav != null) {
            switchWatchlistNav.setChecked(prefs.getBoolean("watchlist_nav_to_chart", true));
            switchWatchlistNav.setOnCheckedChangeListener((btn, checked) ->
                    prefs.edit().putBoolean("watchlist_nav_to_chart", checked).apply());
        }

        SwitchMaterial switchHideKeyboard = view.findViewById(R.id.switchHideKeyboardOnAdd);
        if (switchHideKeyboard != null) {
            switchHideKeyboard.setChecked(prefs.getBoolean("hide_keyboard_on_add", true));
            switchHideKeyboard.setOnCheckedChangeListener((btn, checked) ->
                    prefs.edit().putBoolean("hide_keyboard_on_add", checked).apply());
        }

        // ── Default symbol ────────────────────────────────────────────────────
        EditText etDefaultSymbol     = view.findViewById(R.id.etDefaultSymbol);
        MaterialButton btnSaveSymbol = view.findViewById(R.id.btnSaveDefaultSymbol);
        if (etDefaultSymbol != null) {
            String current = prefs.getString(ChartFragment.KEY_DEFAULT_SYMBOL, "SPY");
            etDefaultSymbol.setText(current);
        }
        if (btnSaveSymbol != null) {
            btnSaveSymbol.setOnClickListener(v -> {
                if (etDefaultSymbol == null) return;
                String sym = etDefaultSymbol.getText().toString().trim().toUpperCase();
                if (sym.isEmpty()) {
                    Toast.makeText(requireContext(), "Enter a symbol", Toast.LENGTH_SHORT).show();
                    return;
                }
                prefs.edit().putString(ChartFragment.KEY_DEFAULT_SYMBOL, sym).apply();
                Toast.makeText(requireContext(), "Default symbol set to " + sym, Toast.LENGTH_SHORT).show();
            });
        }

        // ── Start page ────────────────────────────────────────────────────────
        setupStartPageButton(view, R.id.btnStartChart,     R.id.nav_chart);
        setupStartPageButton(view, R.id.btnStartWatchlist, R.id.nav_watchlist);
        setupStartPageButton(view, R.id.btnStartPortfolio, R.id.nav_portfolio);
        setupStartPageButton(view, R.id.btnStartTrades,    R.id.nav_trades);
        setupStartPageButton(view, R.id.btnStartSettings,  R.id.nav_settings);
        updateStartPageButtons(view);

        // ── App version ───────────────────────────────────────────────────────
        TextView tvAppVersion = view.findViewById(R.id.tvAppVersion);
        if (tvAppVersion != null) {
            try {
                String vName = requireContext().getPackageManager()
                        .getPackageInfo(requireContext().getPackageName(), 0).versionName;
                tvAppVersion.setText(vName);
            } catch (Exception ignored) {}
        }
    }

    private void setupStartPageButton(View root, int btnResId, int navId) {
        MaterialButton btn = root.findViewById(btnResId);
        if (btn == null) return;
        btn.setOnClickListener(v -> {
            prefs.edit().putInt(MainActivity.KEY_START_PAGE, navId).apply();
            updateStartPageButtons(root);
            Toast.makeText(requireContext(), "Start page updated", Toast.LENGTH_SHORT).show();
        });
    }

    private void updateStartPageButtons(View root) {
        int currentStart = prefs.getInt(MainActivity.KEY_START_PAGE, R.id.nav_chart);
        int[] btnIds = {R.id.btnStartChart, R.id.btnStartWatchlist,
                        R.id.btnStartPortfolio, R.id.btnStartTrades, R.id.btnStartSettings};
        int[] navIds = {R.id.nav_chart, R.id.nav_watchlist,
                        R.id.nav_portfolio, R.id.nav_trades, R.id.nav_settings};
        for (int i = 0; i < btnIds.length; i++) {
            MaterialButton btn = root.findViewById(btnIds[i]);
            if (btn == null) continue;
            boolean selected = (navIds[i] == currentStart);
            btn.setAlpha(selected ? 1f : 0.45f);
            btn.setStrokeWidth(selected ? 3 : 0);
        }
    }

    private void setTheme(boolean dark,
                          LinearLayout btnLight, LinearLayout btnDark,
                          TextView tvStatus) {
        prefs.edit().putBoolean(ChartFragment.KEY_THEME, dark).apply();
        AppCompatDelegate.setDefaultNightMode(
                dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        updateThemeUI(btnLight, btnDark, tvStatus, dark);
    }

    private void updateThemeUI(LinearLayout btnLight, LinearLayout btnDark,
                               TextView tvStatus, boolean isDark) {
        if (tvStatus != null)
            tvStatus.setText(isDark ? "Dark mode active" : "Light mode active");
        // אין צורך לשנות backgrounds כאן — AppCompatDelegate מחדש את ה-Activity
    }
}
