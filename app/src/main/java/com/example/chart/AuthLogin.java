package com.example.chart;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
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

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.concurrent.Executor;

public class AuthLogin extends AppCompatActivity {

    private EditText editTextEmailAddress, editTextPassword;
    private Button button;
    private Button btnBiometricLogin;
    private Button btnNoUser;
    private FirebaseAuth refAuth;

    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            Intent intent = new Intent(AuthLogin.this, MainActivity.class);
            startActivity(intent);
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
        editTextPassword = findViewById(R.id.editTextPassword);
        button = findViewById(R.id.button);
        btnBiometricLogin = findViewById(R.id.btnBiometricLogin);
        btnNoUser = findViewById(R.id.btnNoUser);

        refAuth = FirebaseAuth.getInstance();

        button.setOnClickListener(v -> loginUser());

        btnNoUser.setOnClickListener(v -> {
            Intent intent = new Intent(AuthLogin.this, AuthRegister.class);
            startActivity(intent);
        });

        setupBiometricPrompt();

        btnBiometricLogin.setOnClickListener(v -> {
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

    private void loginUser() {
        String email = editTextEmailAddress.getText().toString().trim();
        String pass = editTextPassword.getText().toString().trim();

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
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        pd.dismiss();
                        if (task.isSuccessful()) {
                            Log.i("AuthLogin", "signInWithEmailAndPassword: success");

                            SharedPreferences prefs = getSharedPreferences("auth_prefs", MODE_PRIVATE);
                            prefs.edit()
                                    .putString("email", email)
                                    .putString("password", pass)
                                    .apply();

                            Toast.makeText(getApplicationContext(),
                                    "User logged in successfully",
                                    Toast.LENGTH_SHORT).show();

                            Intent intent = new Intent(AuthLogin.this, MainActivity.class);
                            startActivity(intent);
                            finish();
                        } else {
                            String errorMsg = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                            Log.e("AuthLogin", "failure: " + errorMsg);
                            Toast.makeText(getApplicationContext(),
                                    errorMsg,
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void setupBiometricPrompt() {
        Executor executor = ContextCompat.getMainExecutor(this);

        biometricPrompt = new BiometricPrompt(
                this,
                executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(
                            @NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        loginWithSavedCredentials();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode,
                                                      @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        // errorCode 10 = USER_CANCELED - לא נציג הודעת שגיאה
                        if (errorCode != BiometricPrompt.ERROR_USER_CANCELED
                                && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                            Toast.makeText(getApplicationContext(),
                                    "Biometric error: " + errString,
                                    Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        Toast.makeText(getApplicationContext(),
                                "Authentication failed",
                                Toast.LENGTH_SHORT).show();
                    }
                }
        );

        // תיקון הקריסה: כשמשתמשים ב-DEVICE_CREDENTIAL אסור להגדיר NegativeButton
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
        String savedPass = prefs.getString("password", null);

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
                        Toast.makeText(getApplicationContext(),
                                "Logged in with biometrics",
                                Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(AuthLogin.this, MainActivity.class);
                        startActivity(intent);
                        finish();
                    } else {
                        Toast.makeText(getApplicationContext(),
                                "Firebase login failed",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
