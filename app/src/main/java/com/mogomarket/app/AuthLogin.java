package com.mogomarket.app;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;
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
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

import java.util.concurrent.Executor;

public class AuthLogin extends AppCompatActivity {

    private static final String TAG = "AuthLogin";

    private EditText editTextEmailAddress, editTextPassword;
    private FirebaseAuth refAuth;
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;

    private GoogleSignInClient googleSignInClient;
    private ActivityResultLauncher<Intent> googleSignInLauncher;

    @Override
    protected void onStart() {
        super.onStart();

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            Log.d(TAG, "onStart: user already logged in -> " + currentUser.getUid());
            startActivity(new Intent(AuthLogin.this, MainActivity.class));
            finish();
        } else {
            Log.d(TAG, "onStart: no logged-in user");
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

        try {
            FirebaseApp firebaseApp = FirebaseApp.getInstance();
            Log.d(TAG, "FirebaseApp initialized: " + firebaseApp.getName());
            Log.d(TAG, "Firebase projectId: " +
                    (firebaseApp.getOptions() != null ? firebaseApp.getOptions().getProjectId() : "null"));
            Log.d(TAG, "Firebase applicationId: " +
                    (firebaseApp.getOptions() != null ? firebaseApp.getOptions().getApplicationId() : "null"));
            Log.d(TAG, "Firebase apiKey: " +
                    (firebaseApp.getOptions() != null ? firebaseApp.getOptions().getApiKey() : "null"));
        } catch (Exception e) {
            Log.e(TAG, "FirebaseApp initialization check failed", e);
        }

        editTextEmailAddress = findViewById(R.id.editTextEmailAddress);
        editTextPassword = findViewById(R.id.editTextPassword);
        refAuth = FirebaseAuth.getInstance();

        Log.d(TAG, "AuthLogin created");
        Log.d(TAG, "Package name: " + getPackageName());
        Log.d(TAG, "default_web_client_id: " + getString(R.string.default_web_client_id));

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        googleSignInClient = GoogleSignIn.getClient(this, gso);

        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                this::handleGoogleSignInResult
        );

        android.view.View btnGoogle = findViewById(R.id.btnGoogleSignIn);
        if (btnGoogle != null) {
            btnGoogle.setOnClickListener(v -> signInWithGoogle());
        } else {
            Log.e(TAG, "btnGoogleSignIn not found in layout");
        }

        android.view.View btnLogin = findViewById(R.id.button);
        if (btnLogin != null) {
            btnLogin.setOnClickListener(v -> loginUser());
        } else {
            Log.e(TAG, "Login button not found in layout");
        }

        android.view.View btnRegister = findViewById(R.id.btnNoUser);
        if (btnRegister != null) {
            btnRegister.setOnClickListener(v ->
                    startActivity(new Intent(AuthLogin.this, AuthRegister.class)));
        } else {
            Log.e(TAG, "btnNoUser not found in layout");
        }

        android.view.View btnGuest = findViewById(R.id.btnGuestLogin);
        if (btnGuest != null) {
            btnGuest.setOnClickListener(v -> loginAsGuest());
        } else {
            Log.e(TAG, "btnGuestLogin not found in layout");
        }

        setupBiometricPrompt();

        android.view.View btnBio = findViewById(R.id.btnBiometricLogin);
        if (btnBio != null) {
            btnBio.setOnClickListener(v -> {
                BiometricManager manager = BiometricManager.from(this);
                int canAuth = manager.canAuthenticate(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG
                                | BiometricManager.Authenticators.DEVICE_CREDENTIAL
                );

                Log.d(TAG, "Biometric canAuthenticate result: " + canAuth);

                if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
                    biometricPrompt.authenticate(promptInfo);
                } else {
                    Toast.makeText(this,
                            "Biometric authentication is not available on this device",
                            Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            Log.e(TAG, "btnBiometricLogin not found in layout");
        }
    }

    private void signInWithGoogle() {
        Log.d(TAG, "Starting Google Sign-In flow");

        googleSignInClient.signOut().addOnCompleteListener(this, task -> {
            Log.d(TAG, "Google signOut complete, launching sign-in intent");
            googleSignInLauncher.launch(googleSignInClient.getSignInIntent());
        });
    }

    private void handleGoogleSignInResult(ActivityResult result) {
        Log.d(TAG, "handleGoogleSignInResult called, resultCode=" + result.getResultCode());

        Intent data = result.getData();
        if (data == null) {
            Log.e(TAG, "Google Sign-In returned null intent data");
            Toast.makeText(this, "Google Sign-In failed: empty result", Toast.LENGTH_LONG).show();
            return;
        }

        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);

        try {
            GoogleSignInAccount account = task.getResult(ApiException.class);
            if (account == null) {
                Log.e(TAG, "GoogleSignInAccount is null");
                Toast.makeText(this, "Google Sign-In failed: account is null", Toast.LENGTH_LONG).show();
                return;
            }

            Log.d(TAG, "Google account email: " + account.getEmail());
            Log.d(TAG, "Google idToken is null? " + (account.getIdToken() == null));

            firebaseAuthWithGoogle(account.getIdToken());

        } catch (ApiException e) {
            Log.e(TAG, "Google Sign-In failed, statusCode=" + e.getStatusCode(), e);
            Toast.makeText(this,
                    "Google Sign-In failed: " + e.getStatusCode(),
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e(TAG, "Unexpected Google Sign-In error", e);
            Toast.makeText(this,
                    "Google Sign-In unexpected error: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        if (idToken == null || idToken.trim().isEmpty()) {
            Log.e(TAG, "firebaseAuthWithGoogle: idToken is null or empty");
            Toast.makeText(this, "Google ID token is missing", Toast.LENGTH_LONG).show();
            return;
        }

        ProgressDialog pd = new ProgressDialog(this);
        pd.setTitle("Google Sign-In");
        pd.setMessage("Connecting...");
        pd.setCancelable(false);
        pd.show();

        Log.d(TAG, "Calling Firebase signInWithCredential with Google credential");

        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        refAuth.signInWithCredential(credential).addOnCompleteListener(this, task -> {
            pd.dismiss();

            if (task.isSuccessful()) {
                FirebaseUser user = refAuth.getCurrentUser();
                String name = (user != null && user.getDisplayName() != null)
                        ? user.getDisplayName() : "";
                Log.d(TAG, "Firebase Google auth success, uid=" +
                        (user != null ? user.getUid() : "null"));
                Toast.makeText(this, "Welcome, " + name + "!", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(AuthLogin.this, MainActivity.class));
                finish();
            } else {
                Exception ex = task.getException();
                logFirebaseException("Firebase Google auth failed", ex);
                Toast.makeText(this,
                        "Google Firebase auth failed: " + getReadableError(ex),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void loginAsGuest() {
        ProgressDialog pd = new ProgressDialog(this);
        pd.setTitle("Guest Login");
        pd.setMessage("Connecting...");
        pd.setCancelable(false);
        pd.show();

        Log.d(TAG, "Trying anonymous login");

        refAuth.signInAnonymously().addOnCompleteListener(this, task -> {
            pd.dismiss();

            if (task.isSuccessful()) {
                FirebaseUser user = refAuth.getCurrentUser();
                Log.d(TAG, "Guest login success, uid=" + (user != null ? user.getUid() : "null"));
                Toast.makeText(this, "Signed in as guest", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(AuthLogin.this, MainActivity.class));
                finish();
            } else {
                Exception ex = task.getException();
                logFirebaseException("Guest login failed", ex);

                String err = getReadableError(ex);
                String msg = (err != null && err.contains("CONFIGURATION_NOT_FOUND"))
                        ? "Guest login is not enabled in Firebase"
                        : "Guest login failed: " + err;

                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
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
        pd.setMessage("Logging in...");
        pd.setCancelable(false);
        pd.show();

        Log.d(TAG, "Trying email login for: " + email);

        refAuth.signInWithEmailAndPassword(email, pass)
                .addOnCompleteListener(this, task -> {
                    pd.dismiss();

                    if (task.isSuccessful()) {
                        FirebaseUser user = refAuth.getCurrentUser();
                        Log.d(TAG, "Email login success, uid=" +
                                (user != null ? user.getUid() : "null"));

                        getSharedPreferences("auth_prefs", MODE_PRIVATE).edit()
                                .putString("email", email)
                                .putString("password", pass)
                                .apply();

                        Toast.makeText(this, "User logged in successfully", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(AuthLogin.this, MainActivity.class));
                        finish();
                    } else {
                        Exception ex = task.getException();
                        logFirebaseException("Email login failed", ex);
                        Toast.makeText(this,
                                "Email login failed: " + getReadableError(ex),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void setupBiometricPrompt() {
        Executor executor = ContextCompat.getMainExecutor(this);

        biometricPrompt = new BiometricPrompt(this, executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        Log.d(TAG, "Biometric authentication succeeded");
                        loginWithSavedCredentials();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        Log.e(TAG, "Biometric authentication error: " + errorCode + " / " + errString);

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
                        Log.e(TAG, "Biometric authentication failed");
                        Toast.makeText(getApplicationContext(),
                                "Authentication failed",
                                Toast.LENGTH_SHORT).show();
                    }
                });

        promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Biometric login")
                .setSubtitle("Confirm your identity to sign in")
                .setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG
                                | BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                .build();
    }

    private void loginWithSavedCredentials() {
        SharedPreferences prefs = getSharedPreferences("auth_prefs", MODE_PRIVATE);
        String savedEmail = prefs.getString("email", null);
        String savedPass = prefs.getString("password", null);

        if (savedEmail == null || savedPass == null) {
            Toast.makeText(this,
                    "No saved credentials found. Please log in once with email and password",
                    Toast.LENGTH_LONG).show();
            Log.e(TAG, "No saved credentials for biometric login");
            return;
        }

        ProgressDialog pd = new ProgressDialog(this);
        pd.setTitle("Connecting");
        pd.setMessage("Logging in...");
        pd.setCancelable(false);
        pd.show();

        Log.d(TAG, "Trying biometric login with saved email: " + savedEmail);

        refAuth.signInWithEmailAndPassword(savedEmail, savedPass)
                .addOnCompleteListener(this, task -> {
                    pd.dismiss();

                    if (task.isSuccessful()) {
                        FirebaseUser user = refAuth.getCurrentUser();
                        Log.d(TAG, "Biometric login success, uid=" +
                                (user != null ? user.getUid() : "null"));
                        Toast.makeText(this, "Logged in with biometrics", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(AuthLogin.this, MainActivity.class));
                        finish();
                    } else {
                        Exception ex = task.getException();
                        logFirebaseException("Biometric login failed", ex);
                        Toast.makeText(this,
                                "Biometric login failed: " + getReadableError(ex),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void logFirebaseException(String prefix, Exception ex) {
        if (ex == null) {
            Log.e(TAG, prefix + ": exception is null");
            return;
        }

        if (ex instanceof FirebaseAuthException) {
            FirebaseAuthException authEx = (FirebaseAuthException) ex;
            Log.e(TAG, prefix + " | errorCode=" + authEx.getErrorCode()
                    + " | message=" + authEx.getMessage(), authEx);
        } else {
            Log.e(TAG, prefix + " | message=" + ex.getMessage(), ex);
        }
    }

    private String getReadableError(Exception ex) {
        if (ex == null) return "Unknown error";

        if (ex instanceof FirebaseAuthException) {
            FirebaseAuthException authEx = (FirebaseAuthException) ex;
            return authEx.getErrorCode() + " | " + authEx.getMessage();
        }

        return ex.getMessage() != null ? ex.getMessage() : "Unknown error";
    }
}