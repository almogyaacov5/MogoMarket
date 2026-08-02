package com.mogomarket.app;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class ForgotPasswordActivity extends AppCompatActivity {

    private static final String TAG = "ForgotPassword";

    private EditText editTextEmail;
    private Button btnSendReset;
    private ProgressBar progressBar;
    private TextView tvSuccess;
    private ImageButton btnBack;

    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        mAuth = FirebaseAuth.getInstance();

        editTextEmail = findViewById(R.id.editTextResetEmail);
        btnSendReset  = findViewById(R.id.btnSendResetEmail);
        progressBar   = findViewById(R.id.progressBarReset);
        tvSuccess     = findViewById(R.id.tvResetSuccess);
        btnBack       = findViewById(R.id.btnBackForgot);

        // Pre-fill email if passed from login screen
        String prefillEmail = getIntent().getStringExtra("email");
        if (prefillEmail != null && !prefillEmail.isEmpty()) {
            editTextEmail.setText(prefillEmail);
        }

        btnBack.setOnClickListener(v -> finish());

        btnSendReset.setOnClickListener(v -> sendResetEmail());
    }

    private void sendResetEmail() {
        String email = editTextEmail.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            editTextEmail.setError("Please enter your email address");
            editTextEmail.requestFocus();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            editTextEmail.setError("Please enter a valid email address");
            editTextEmail.requestFocus();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        btnSendReset.setEnabled(false);
        tvSuccess.setVisibility(View.GONE);

        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    progressBar.setVisibility(View.GONE);
                    btnSendReset.setEnabled(true);

                    if (task.isSuccessful()) {
                        tvSuccess.setVisibility(View.VISIBLE);
                        tvSuccess.setText("Reset email sent to " + email + ".\nCheck your inbox (and spam folder).");
                        btnSendReset.setText("Resend Email");
                        Toast.makeText(this,
                                "Password reset email sent!",
                                Toast.LENGTH_LONG).show();
                    } else {
                        String msg = task.getException() != null
                                ? task.getException().getMessage()
                                : "Failed to send reset email";
                        Toast.makeText(this, "Error: " + msg, Toast.LENGTH_LONG).show();
                    }
                });
    }
}
