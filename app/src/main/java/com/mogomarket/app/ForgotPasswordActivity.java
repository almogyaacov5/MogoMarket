package com.mogomarket.app;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class ForgotPasswordActivity extends AppCompatActivity {

    private EditText editTextEmail;
    private Button btnSendReset;
    private ProgressBar progressBar;
    private TextView tvBackToLogin;

    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        mAuth = FirebaseAuth.getInstance();

        editTextEmail = findViewById(R.id.editTextForgotEmail);
        btnSendReset  = findViewById(R.id.btnSendReset);
        progressBar   = findViewById(R.id.progressBarForgot);
        tvBackToLogin = findViewById(R.id.tvBackToLogin);

        // Pre-fill email if passed from AuthLogin
        String prefillEmail = getIntent().getStringExtra("email");
        if (prefillEmail != null && !prefillEmail.isEmpty()) {
            editTextEmail.setText(prefillEmail);
            editTextEmail.setSelection(prefillEmail.length());
        }

        btnSendReset.setOnClickListener(v -> sendResetEmail());
        tvBackToLogin.setOnClickListener(v -> finish());
    }

    private void sendResetEmail() {
        String email = editTextEmail.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            editTextEmail.setError("Enter your email address");
            editTextEmail.requestFocus();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        btnSendReset.setEnabled(false);

        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    progressBar.setVisibility(View.GONE);
                    btnSendReset.setEnabled(true);

                    if (task.isSuccessful()) {
                        Toast.makeText(this,
                                "Reset email sent! Check your inbox.",
                                Toast.LENGTH_LONG).show();
                        finish();
                    } else {
                        String msg = task.getException() != null
                                ? task.getException().getMessage()
                                : "Failed to send reset email.";
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                    }
                });
    }
}
