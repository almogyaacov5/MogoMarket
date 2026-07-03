package com.mogomarket.app;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

import java.util.concurrent.Executor;

public class AuthLogin extends AppCompatActivity {

    private static final String TAG = "AuthLogin";
    private android.widget.EditText editTextEmailAddress, editTextPassword;
    private FirebaseAuth refAuth;
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;

    // Google Sign-In
    private GoogleSignInClient googleSignInClient;
    private ActivityResultLauncher<Intent> googleSignInLauncher;

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

        // ── Google Sign-In Setup ──────────────────────────────────────────────
        // IMPORTANT: Replace "YOUR_WEB_CLIENT_ID" with the Web Client ID from:
        // Firebase Console → Project Settings → General → Your apps → Web client ID
        // (also found in google-services.json under client → oauth_client → client_id where client_type == 3)
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                this::handleGoogleSignInResult);

        android.view.View btnGoogle = findViewById(R.id.btnGoogleSignIn);
        if (btnGoogle != null) {
            btnGoogle.setOnClickListener(v -> signInWithGoogle());
        }

        // כניסה רגילה
        findViewById(R.id.button).setOnClickListener(v -> loginUser());

        // הרשמה
        findViewById(R.id.btnNoUser).setOnClickListener(v ->
                startActivity(new Intent(AuthLogin.this, AuthRegister.class)));

        // ── כניסה כאורח ──────────────────────────────────────────────────────
        android.view.View btnGuest = findViewById(R.id.btnGuestLogin);
        if (btnGuest != null) {
            btnGuest.setOnClickListener(v -> loginAsGuest());
        } else {
            Log.e(TAG, "btnGuestLogin not found in layout!");
        }

        setupBiometricPrompt();

        // ביומטרי
        android.view.View btnBio = findViewById(R.id.btnBiometricLogin);
        if (btnBio != null) {
            btnBio.setOnClickListener(v -> {
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
    }

    // ── Google Sign-In ────────────────────────────────────────────────────────
    private void signInWithGoogle() {
        // נתק חשבון קודם כדי לאפשר בחירת חשבון מחדש
        googleSignInClient.signOut().addOnCompleteListener(this, task ->
                googleSignInLauncher.launch(googleSignInClient.getSignInIntent()));
    }

    private void handleGoogleSignInResult(ActivityResult result) {
        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
        try {
            GoogleSignInAccount account = task.getResult(ApiException.class);
            if (account == null) return;
            firebaseAuthWithGoogle(account.getIdToken());
        } catch (ApiException e) {
            Log.e(TAG, "Google Sign-In failed: " + e.getStatusCode(), e);
            Toast.makeText(this, "כניסה עם Google נכשלה: " + e.getStatusCode(), Toast.LENGTH_LONG).show();
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        ProgressDialog pd = new ProgressDialog(this);
        pd.setTitle("כניסה עם Google");
        pd.setMessage("מתחבר...");
        pd.setCancelable(false);
        pd.show();

        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        refAuth.signInWithCredential(credential).addOnCompleteListener(this, task -> {
            pd.dismiss();
            if (task.isSuccessful()) {
                FirebaseUser user = refAuth.getCurrentUser();
                String name = (user != null && user.getDisplayName() != null)
                        ? user.getDisplayName() : "";
                Log.d(TAG, "Google Sign-In OK: " + name);
                Toast.makeText(this, "ברוך הבא, " + name + "!", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(AuthLogin.this, MainActivity.class));
                finish();
            } else {
                String err = task.getException() != null
                        ? task.getException().getMessage() : "Unknown error";
                Log.e(TAG, "Firebase Google auth failed: " + err);
                Toast.makeText(this, "שגיאה: " + err, Toast.LENGTH_LONG).show();
            }
        });
    }

    // ── כניסה אנונימית ────────────────────────────────────────────────────────
    private void loginAsGuest() {
        ProgressDialog pd = new ProgressDialog(this);
        pd.setTitle("כניסה כאורח");
        pd.setMessage("מתחבר...");
        pd.setCancelable(false);
        pd.show();

        refAuth.signInAnonymously().addOnCompleteListener(this, task -> {
            pd.dismiss();
            if (task.isSuccessful()) {
                Log.d(TAG, "Guest login OK");
                Toast.makeText(this, "נכנסת כאורח", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(AuthLogin.this, MainActivity.class));
                finish();
            } else {
                Exception ex = task.getException();
                String err = ex != null ? ex.getMessage() : "Unknown error";
                Log.e(TAG, "Guest login failed: " + err, ex);
                String msg = (err != null && err.contains("CONFIGURATION_NOT_FOUND"))
                        ? "כניסה כאורח לא מופעלת.\nפנה למנהל האפליקציה."
                        : "שגיאה: " + err;
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    // ── כניסה רגילה ──────────────────────────────────────────────────────────
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

    // ── Biometric ─────────────────────────────────────────────────────────────
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
