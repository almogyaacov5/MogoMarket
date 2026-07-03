package com.mogomarket.app;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * SplashActivity — מסך ראשון שנפתח.
 * בודק אם המשתמש מחובר ומנווט בהתאם:
 *  - מחובר  → MainActivity
 *  - לא מחובר → AuthLogin
 * כך נמנע המסך הריק הנגרם מהעיכוב של onStart ב-AuthLogin.
 */
public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // אין setContentView — המסך נשאר עם theme הרקע (windowBackground)

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        Intent intent;
        if (user != null) {
            // משתמש מחובר → עבור ישירות לאפליקציה
            intent = new Intent(this, MainActivity.class);
        } else {
            // לא מחובר → מסך התחברות
            intent = new Intent(this, AuthLogin.class);
        }

        startActivity(intent);
        finish(); // סגור את SplashActivity כדי שלא יהיה אפשר לחזור אליו
    }
}
