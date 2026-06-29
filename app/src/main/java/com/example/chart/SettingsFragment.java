package com.example.chart;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SettingsFragment extends Fragment {

    private static final String PREFS_NAME = "app_prefs";
    private static final String KEY_THEME  = "dark_mode";

    private LinearLayout btnLightMode, btnDarkMode;
    private TextView tvThemeStatus;
    private boolean isDark;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_settings, container, false);

        // רקע דינמי מה-Theme
        v.setBackgroundColor(requireContext().getColor(R.color.bg_primary));

        // ====== Toggle מצב כהה/בהיר ======
        btnLightMode  = v.findViewById(R.id.btnLightMode);
        btnDarkMode   = v.findViewById(R.id.btnDarkMode);
        tvThemeStatus = v.findViewById(R.id.tvThemeStatus);

        SharedPreferences prefs = requireActivity().getSharedPreferences(PREFS_NAME, 0);
        isDark = prefs.getBoolean(KEY_THEME, true);

        updateThemeUI(isDark);

        btnLightMode.setOnClickListener(view -> {
            if (isDark) {
                isDark = false;
                prefs.edit().putBoolean(KEY_THEME, false).apply();
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                updateThemeUI(false);
            }
        });

        btnDarkMode.setOnClickListener(view -> {
            if (!isDark) {
                isDark = true;
                prefs.edit().putBoolean(KEY_THEME, true).apply();
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                updateThemeUI(true);
            }
        });

        // אימייל משתמש
        TextView tvEmail = v.findViewById(R.id.tvUserEmail);
        if (tvEmail != null) {
            tvEmail.setTextColor(requireContext().getColor(R.color.primary));
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            tvEmail.setText(user != null && user.getEmail() != null
                    ? user.getEmail() : "Guest");
        }

        // גרסת אפליקציה
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

        // כפתור Logout
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

    private void updateThemeUI(boolean dark) {
        if (btnLightMode == null || btnDarkMode == null || tvThemeStatus == null) return;

        int selectedText   = requireContext().getColor(R.color.white);
        int unselectedText = requireContext().getColor(R.color.text_secondary);

        if (dark) {
            btnDarkMode.setBackgroundResource(R.drawable.bg_theme_btn_selected);
            btnLightMode.setBackgroundResource(R.drawable.bg_theme_btn_unselected);
            setChildTextColors(btnDarkMode,  selectedText);
            setChildTextColors(btnLightMode, unselectedText);
            tvThemeStatus.setText("🌙 מצב כהה פעיל");
            tvThemeStatus.setTextColor(requireContext().getColor(R.color.primary));
        } else {
            btnLightMode.setBackgroundResource(R.drawable.bg_theme_btn_selected);
            btnDarkMode.setBackgroundResource(R.drawable.bg_theme_btn_unselected);
            setChildTextColors(btnLightMode, selectedText);
            setChildTextColors(btnDarkMode,  unselectedText);
            tvThemeStatus.setText("\u2600\uFE0F מצב בהיר פעיל");
            tvThemeStatus.setTextColor(requireContext().getColor(R.color.gain));
        }
    }

    private void setChildTextColors(LinearLayout layout, int color) {
        for (int i = 0; i < layout.getChildCount(); i++) {
            View child = layout.getChildAt(i);
            if (child instanceof TextView) {
                ((TextView) child).setTextColor(color);
            }
        }
    }
}
