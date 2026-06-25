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

        // --- Dark Mode Switch ---
        SwitchMaterial switchDarkMode = v.findViewById(R.id.switchDarkMode);
        SharedPreferences prefs = requireActivity().getSharedPreferences(PREFS_NAME, 0);
        boolean isDark = prefs.getBoolean(KEY_THEME, true);
        switchDarkMode.setChecked(isDark);

        switchDarkMode.setOnCheckedChangeListener((btn, isChecked) -> {
            // שמירת העדפות
            prefs.edit().putBoolean(KEY_THEME, isChecked).apply();
            // החלת ה-Theme — כיוון שיש configChanges="uiMode" ב-Manifest,
            // ה-Activity לא תתאתחל והשינוי יוחל בצורה חלקה
            AppCompatDelegate.setDefaultNightMode(
                    isChecked ? AppCompatDelegate.MODE_NIGHT_YES
                              : AppCompatDelegate.MODE_NIGHT_NO);
        });

        // --- User Email ---
        TextView tvEmail = v.findViewById(R.id.tvUserEmail);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && user.getEmail() != null) {
            tvEmail.setText(user.getEmail());
        } else {
            tvEmail.setText("Guest");
        }

        // --- App Version ---
        TextView tvVersion = v.findViewById(R.id.tvAppVersion);
        try {
            String versionName = requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0).versionName;
            tvVersion.setText(versionName);
        } catch (Exception e) {
            tvVersion.setText("1.0");
        }

        // --- Logout ---
        MaterialButton btnLogout = v.findViewById(R.id.btnSettingsLogout);
        btnLogout.setOnClickListener(view -> {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(requireActivity(), AuthLogin.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });

        return v;
    }
}
