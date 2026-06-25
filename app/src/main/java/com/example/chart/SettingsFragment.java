package com.example.chart;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_settings, container, false);

        // רקע דינמי מה-Theme
        v.setBackgroundColor(requireContext().getColor(R.color.bg_primary));

        // Dark Mode Switch
        SwitchMaterial switchDarkMode = v.findViewById(R.id.switchDarkMode);
        SharedPreferences prefs = requireActivity().getSharedPreferences(PREFS_NAME, 0);
        boolean isDark = prefs.getBoolean(KEY_THEME, true);
        switchDarkMode.setChecked(isDark);
        switchDarkMode.setThumbTintList(
                android.content.res.ColorStateList.valueOf(
                        requireContext().getColor(R.color.primary)));
        switchDarkMode.setTrackTintList(
                android.content.res.ColorStateList.valueOf(
                        requireContext().getColor(R.color.bg_secondary)));

        switchDarkMode.setOnCheckedChangeListener((btn, isChecked) -> {
            prefs.edit().putBoolean(KEY_THEME, isChecked).apply();
            AppCompatDelegate.setDefaultNightMode(
                    isChecked ? AppCompatDelegate.MODE_NIGHT_YES
                              : AppCompatDelegate.MODE_NIGHT_NO);
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
}
