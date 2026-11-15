package com.mogomarket.app;

import android.content.Intent;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.os.Bundle;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

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

    private static final long LOGO_BUILD_DURATION_MS = 1_500L;
    private static final long TRANSITION_DURATION_MS = 800L;
    private android.animation.AnimatorSet splashAnimation;
    private boolean navigated;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdgeHelper.enable(this);
        Window window = getWindow();
        window.setStatusBarColor(android.graphics.Color.BLACK);
        window.setNavigationBarColor(android.graphics.Color.BLACK);
        WindowInsetsControllerCompat bars =
                new WindowInsetsControllerCompat(window, window.getDecorView());
        bars.setAppearanceLightStatusBars(false);
        bars.setAppearanceLightNavigationBars(false);
        setContentView(R.layout.activity_splash);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.splashRoot), (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(systemBars.left, systemBars.top,
                    systemBars.right, systemBars.bottom);
            return insets;
        });

        View logo = findViewById(R.id.splashLogo);
        View title = findViewById(R.id.splashTitle);
        SplashGraphView graph = findViewById(R.id.splashGraph);

        ObjectAnimator ignite = ObjectAnimator.ofFloat(
                logo, View.ALPHA, 0f, 0.12f, 0.03f, 0.52f, 0.18f, 1f);
        ignite.setDuration(600L);
        ignite.setInterpolator(new DecelerateInterpolator(0.7f));
        ignite.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                logo.setAlpha(1f);
            }
        });

        ObjectAnimator logoScaleX = ObjectAnimator.ofFloat(
                logo, View.SCALE_X, 0.88f, 1.04f, 1f);
        ObjectAnimator logoScaleY = ObjectAnimator.ofFloat(
                logo, View.SCALE_Y, 0.88f, 1.04f, 1f);
        logoScaleX.setDuration(800L);
        logoScaleY.setDuration(800L);
        logoScaleX.setInterpolator(new DecelerateInterpolator(1.4f));
        logoScaleY.setInterpolator(new DecelerateInterpolator(1.4f));

        ValueAnimator graphBuild = ValueAnimator.ofFloat(0f, 1f);
        graphBuild.setStartDelay(500L);
        graphBuild.setDuration(LOGO_BUILD_DURATION_MS - 500L);
        graphBuild.addUpdateListener(value ->
                graph.setProgress((float) value.getAnimatedValue()));

        ObjectAnimator titleFade = ObjectAnimator.ofFloat(title, View.ALPHA, 0f, 1f);
        ObjectAnimator titleRise = ObjectAnimator.ofFloat(title, View.TRANSLATION_Y, 16f, 0f);
        titleFade.setStartDelay(850L);
        titleRise.setStartDelay(850L);
        titleFade.setDuration(500L);
        titleRise.setDuration(500L);
        titleFade.setInterpolator(new DecelerateInterpolator(1.4f));
        titleRise.setInterpolator(new DecelerateInterpolator(1.4f));

        ObjectAnimator logoZoomX = ObjectAnimator.ofFloat(logo, View.SCALE_X, 1f, 1.38f);
        ObjectAnimator logoZoomY = ObjectAnimator.ofFloat(logo, View.SCALE_Y, 1f, 1.38f);
        logoZoomX.setStartDelay(LOGO_BUILD_DURATION_MS);
        logoZoomY.setStartDelay(LOGO_BUILD_DURATION_MS);
        logoZoomX.setDuration(TRANSITION_DURATION_MS);
        logoZoomY.setDuration(TRANSITION_DURATION_MS);
        logoZoomX.setInterpolator(new DecelerateInterpolator(1.5f));
        logoZoomY.setInterpolator(new DecelerateInterpolator(1.5f));

        android.animation.AnimatorSet sequence = new android.animation.AnimatorSet();
        sequence.playTogether(ignite, logoScaleX, logoScaleY, graphBuild,
                titleFade, titleRise, logoZoomX, logoZoomY);
        sequence.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                navigate();
            }
        });
        sequence.start();
        splashAnimation = sequence;
    }

    private void navigate() {
        if (navigated || isFinishing()) return;
        navigated = true;
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
        overridePendingTransition(R.anim.splash_enter, R.anim.splash_exit);
        finish(); // סגור את SplashActivity כדי שלא יהיה אפשר לחזור אליו
    }

    @Override
    protected void onDestroy() {
        if (splashAnimation != null) splashAnimation.cancel();
        super.onDestroy();
    }
}
