package com.mogomarket.app;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

import java.util.Properties;
import java.util.concurrent.Executors;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class ForgotPasswordActivity extends AppCompatActivity {

    private static final String TAG           = "ForgotPassword";
    private static final String SMTP_EMAIL    = BuildConfig.SMTP_EMAIL;
    private static final String SMTP_PASSWORD = BuildConfig.SMTP_PASSWORD;

    private EditText    editTextEmail;
    private Button      btnSendReset;
    private ProgressBar progressBar;
    private TextView    tvBackToLogin;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        mAuth         = FirebaseAuth.getInstance();
        editTextEmail = findViewById(R.id.editTextForgotEmail);
        btnSendReset  = findViewById(R.id.btnSendReset);
        progressBar   = findViewById(R.id.progressBarForgot);
        tvBackToLogin = findViewById(R.id.tvBackToLogin);

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

        // Step 1: Firebase sends the real reset link
        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Firebase reset email sent successfully.");
                        // Step 2: Also send a styled notification via Gmail
                        sendGmailNotification(email);
                    } else {
                        progressBar.setVisibility(View.GONE);
                        btnSendReset.setEnabled(true);
                        String msg = task.getException() != null
                                ? task.getException().getMessage()
                                : "Failed to send reset email.";
                        Log.e(TAG, "Firebase error: " + msg);
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void sendGmailNotification(String toEmail) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                Properties props = new Properties();
                props.put("mail.smtp.host", "smtp.gmail.com");
                props.put("mail.smtp.port", "587");
                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.starttls.enable", "true");

                Session session = Session.getInstance(props, new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(SMTP_EMAIL, SMTP_PASSWORD);
                    }
                });

                MimeMessage message = new MimeMessage(session);
                message.setFrom(new InternetAddress(SMTP_EMAIL, "MogoMarket"));
                message.addRecipient(Message.RecipientType.TO, new InternetAddress(toEmail));
                message.setSubject("MogoMarket - Password Reset Request");
                message.setContent(buildEmailHtml(toEmail), "text/html; charset=utf-8");

                Transport.send(message);
                Log.d(TAG, "Gmail notification sent to " + toEmail);

            } catch (Exception e) {
                Log.e(TAG, "Gmail send failed: ", e);
                // Gmail failure is non-critical — Firebase already sent the link
            }

            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                btnSendReset.setEnabled(true);
                Toast.makeText(this,
                        "Password reset email sent! Check your inbox (and spam folder).",
                        Toast.LENGTH_LONG).show();
                finish();
            });
        });
    }

    private String buildEmailHtml(String toEmail) {
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'>" +
                "<style>" +
                "body{font-family:Arial,sans-serif;background:#f4f4f4;margin:0;padding:20px}" +
                ".container{max-width:480px;margin:auto;background:#fff;border-radius:12px;" +
                "overflow:hidden;box-shadow:0 4px 12px rgba(0,0,0,0.1)}" +
                ".header{background:linear-gradient(135deg,#1565C0,#00695C);padding:30px;text-align:center}" +
                ".header h1{color:#fff;margin:0;font-size:24px}" +
                ".body{padding:30px}" +
                ".body p{color:#333;font-size:15px;line-height:1.6}" +
                ".note{font-size:13px;color:#999;margin-top:16px}" +
        ".footer{background:#f9f9f9;padding:16px;text-align:center;color:#999;font-size:12px}" +
                "</style></head><body>" +
                "<div class='container'>" +
                "<div class='header'><h1>&#128272; MogoMarket</h1></div>" +
                "<div class='body'>" +
                "<p>Hi,</p>" +
                "<p>We received a request to reset the password for your MogoMarket account associated with <strong>" + toEmail + "</strong>.</p>" +
                "<p>A separate email with the reset link has been sent to you from Firebase. " +
                "Please check your inbox — and if you don't see it, check your <strong>spam folder</strong>.</p>" +
                "<p class='note'>If you didn't request a password reset, you can safely ignore this email.</p>" +
                "</div>" +
                "<div class='footer'>MogoMarket &copy; 2026 &bull; Personal Investment Manager</div>" +
                "</div></body></html>";
    }
}