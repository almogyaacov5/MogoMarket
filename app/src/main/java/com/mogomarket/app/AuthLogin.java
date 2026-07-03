package com.mogomarket.app;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.concurrent.Executor;

public class AuthLogin extends AppCompatActivity {

    private android.widget.EditText editTextEmailAddress, editTextPassword;
    private FirebaseAuth refAuth;
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            startActivity(new Intent(AuthLogin.this, MainActivity.class));
            finish();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_auth_login);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        editTextEmailAddress = findViewById(R.id.editTextEmailAddress);
        editTextPassword     = findViewById(R.id.editTextPassword);
        refAuth              = FirebaseAuth.getInstance();

        // כניסה רגילה
        findViewById(R.id.button).setOnClickListener(v -> loginUser());

        // הרשמה
        findViewById(R.id.btnNoUser).setOnClickListener(v ->
                startActivity(new Intent(AuthLogin.this, AuthRegister.class)));

        // כניסה כאורח
        findViewById(R.id.btnGuestLogin).setOnClickListener(v -> loginAsGuest());

        setupBiometricPrompt();

        // ביומטרי
        findViewById(R.id.btnBiometricLogin).setOnClickListener(v -> {
            BiometricManager manager = BiometricManager.from(this);
            int canAuth = manager.canAuthenticate(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG
                            | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
            if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
                biometricPrompt.authenticate(promptInfo);
            } else {
                Toast.makeText(this,
                        "המכשיר לא תומך או שלא מוגדרת טביעת אצבע",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ── כניסה אנונימית ──────────────────────────────────────────────────
    private void loginAsGuest() {
        ProgressDialog pd = new ProgressDialog(this);
        pd.setTitle("כניסה כאורח");
        pd.setMessage("מתחבר...");
        pd.setCancelable(false);
        pd.show();

        refAuth.signInAnonymously().addOnCompleteListener(this, task -> {
            pd.dismiss();
            if (task.isSuccessful()) {
                Toast.makeText(this, "נכנסת כאורח", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(AuthLogin.this, MainActivity.class));
                finish();
            } else {
                String err = task.getException() != null
                        ? task.getException().getMessage() : "Unknown error";
                Toast.makeText(this, "שגיאה: " + err, Toast.LENGTH_LONG).show();
            }
        });
    }

    // ── כניסה רגילה ─────────────────────────────────────────────────────
    private void loginUser() {
        String email = editTextEmailAddress.getText().toString().trim();
        String pass  = editTextPassword.getText().toString().trim();

        if (email.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, "Please fill out all the fields", Toast.LENGTH_SHORT).show();
            return;
        }

        ProgressDialog pd = new ProgressDialog(this);
        pd.setTitle("Connecting");
        pd.setMessage("Logging in ...");
        pd.setCancelable(false);
        pd.show();

        refAuth.signInWithEmailAndPassword(email, pass)
                .addOnCompleteListener(this, task -> {
                    pd.dismiss();
                    if (task.isSuccessful()) {
                        getSharedPreferences("auth_prefs", MODE_PRIVATE).edit()
                                .putString("email", email)
                                .putString("password", pass)
                                .apply();
                        Toast.makeText(this, "User logged in successfully", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(AuthLogin.this, MainActivity.class));
                        finish();
                    } else {
                        String err = task.getException() != null
                                ? task.getException().getMessage() : "Unknown error";
                        Toast.makeText(this, err, Toast.LENGTH_LONG).show();
                    }
                });
    }

    // ── Biometric ────────────────────────────────────────────────────────
    private void setupBiometricPrompt() {
        Executor executor = ContextCompat.getMainExecutor(this);
        biometricPrompt = new BiometricPrompt(this, executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        loginWithSavedCredentials();
                    }
                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        if (errorCode != BiometricPrompt.ERROR_USER_CANCELED
                                && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                            Toast.makeText(getApplicationContext(),
                                    "Biometric error: " + errString, Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        Toast.makeText(getApplicationContext(),
                                "Authentication failed", Toast.LENGTH_SHORT).show();
                    }
                });

        promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("כניסה עם טביעת אצבע")
                .setSubtitle("אשר זהות כדי להיכנס לחשבון ההשקעות")
                .setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG
                                | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build();
    }

    private void loginWithSavedCredentials() {
        SharedPreferences prefs = getSharedPreferences("auth_prefs", MODE_PRIVATE);
        String savedEmail = prefs.getString("email", null);
        String savedPass  = prefs.getString("password", null);

        if (savedEmail == null || savedPass == null) {
            Toast.makeText(this,
                    "אין פרטי התחברות שמורים, התחבר פעם אחת עם אימייל+סיסמה",
                    Toast.LENGTH_LONG).show();
            return;
        }

        ProgressDialog pd = new ProgressDialog(this);
        pd.setTitle("Connecting");
        pd.setMessage("Logging in ...");
        pd.setCancelable(false);
        pd.show();

        refAuth.signInWithEmailAndPassword(savedEmail, savedPass)
                .addOnCompleteListener(this, task -> {
                    pd.dismiss();
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Logged in with biometrics", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(AuthLogin.this, MainActivity.class));
                        finish();
                    } else {
                        Toast.makeText(this, "Firebase login failed", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
