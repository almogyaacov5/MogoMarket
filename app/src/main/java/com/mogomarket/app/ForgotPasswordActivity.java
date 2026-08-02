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

    private static final String TAG = "ForgotPassword";

    // Gmail SMTP credentials
    private static final String SMTP_EMAIL    = "shoomdavar123@gmail.com";
    private static final String SMTP_PASSWORD = "lpry hxic pgvc gwxl";

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

        // Step 1: Generate Firebase reset link, then send via Gmail SMTP
        mAuth.generatePasswordResetLink(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        String resetLink = task.getResult();
                        Log.d(TAG, "Reset link generated, sending via Gmail...");
                        sendViaGmail(email, resetLink);
                    } else {
                        progressBar.setVisibility(View.GONE);
                        btnSendReset.setEnabled(true);
                        String msg = task.getException() != null
                                ? task.getException().getMessage()
                                : "Failed to generate reset link.";
                        Log.e(TAG, "generatePasswordResetLink failed: " + msg);
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void sendViaGmail(String toEmail, String resetLink) {
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
                message.setSubject("\uD83D\uDD10 MogoMarket - איפוס סיסמה");
                message.setContent(buildEmailHtml(resetLink), "text/html; charset=utf-8");

                Transport.send(message);
                Log.d(TAG, "Email sent successfully to " + toEmail);

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnSendReset.setEnabled(true);
                    Toast.makeText(this,
                            "\u2705 נשלח אימייל לאיפוס סיסמה! בדוק את תיבת הדואר.",
                            Toast.LENGTH_LONG).show();
                    finish();
                });

            } catch (Exception e) {
                Log.e(TAG, "Failed to send email via Gmail: ", e);
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnSendReset.setEnabled(true);
                    Toast.makeText(this,
                            "שגיאה בשליחת האימייל. נסה שנית.",
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private String buildEmailHtml(String resetLink) {
        return "<!DOCTYPE html><html dir='rtl'><head><meta charset='UTF-8'>" +
                "<style>" +
                "body{font-family:Arial,sans-serif;background:#f4f4f4;margin:0;padding:20px}" +
                ".container{max-width:480px;margin:auto;background:#ffffff;border-radius:12px;overflow:hidden;box-shadow:0 4px 12px rgba(0,0,0,0.1)}" +
                ".header{background:linear-gradient(135deg,#1565C0,#00695C);padding:30px;text-align:center}" +
                ".header h1{color:#ffffff;margin:0;font-size:24px}" +
                ".body{padding:30px;text-align:right}" +
                ".body p{color:#333;font-size:15px;line-height:1.6}" +
                ".btn{display:block;width:fit-content;margin:24px auto;padding:14px 32px;" +
                "background:#1565C0;color:#ffffff;text-decoration:none;border-radius:8px;font-size:16px;font-weight:bold}" +
                ".footer{background:#f9f9f9;padding:16px;text-align:center;color:#999;font-size:12px}" +
                "</style></head><body>" +
                "<div class='container'>" +
                "<div class='header'><h1>\uD83D\uDD10 MogoMarket</h1></div>" +
                "<div class='body'>" +
                "<p>שלום,</p>" +
                "<p>קיבלנו בקשה לאיפוס הסיסמה לחשבון שלך ב-MogoMarket.</p>" +
                "<p>לחץ על הכפתור למטה כדי לאפס את הסיסמה:</p>" +
                "<a href='" + resetLink + "' class='btn'>איפוס סיסמה</a>" +
                "<p style='font-size:13px;color:#999'>הלינק תקף ל-24 שעות בלבד.<br>" +
                "אם לא ביקשת איפוס סיסמה, התעלם מהודעה זו.</p>" +
                "</div>" +
                "<div class='footer'>MogoMarket &copy; 2026 &bull; מערכת ניהול השקעות אישית</div>" +
                "</div></body></html>";
    }
}
