package com.mogomarket.mogomarket.util;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageButton;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;

/**
 * ChartZoomHelper - הגדלת גרף למסך מלא (אורך + רוחב)
 *
 * שימוש:
 *   ChartZoomHelper helper = new ChartZoomHelper(activity, rootLayout, myLineChart);
 *   btnExpand.setOnClickListener(v -> helper.toggleFullscreen());
 *
 * ה-helper מוסיף כפתור X לסגירה אוטומטית כשמוגדל.
 */
public class ChartZoomHelper {

    public interface OnFullscreenChangeListener {
        void onFullscreenChanged(boolean isFullscreen);
    }

    private final Context context;
    private final ViewGroup rootLayout;      // ה-root של ה-Activity (FrameLayout מומלץ)
    private final LineChart chart;
    private final ViewGroup originalParent;
    private final ViewGroup.LayoutParams originalParams;
    private final int originalIndex;

    private boolean isFullscreen = false;
    private FrameLayout fullscreenContainer;
    private ImageButton btnClose;

    private OnFullscreenChangeListener listener;

    public ChartZoomHelper(Context context, ViewGroup rootLayout, LineChart chart) {
        this.context = context;
        this.rootLayout = rootLayout;
        this.chart = chart;
        this.originalParent = (ViewGroup) chart.getParent();
        this.originalParams = chart.getLayoutParams();
        this.originalIndex = originalParent.indexOfChild(chart);
    }

    public void setOnFullscreenChangeListener(OnFullscreenChangeListener listener) {
        this.listener = listener;
    }

    /**
     * Toggle fullscreen / normal
     */
    public void toggleFullscreen() {
        if (isFullscreen) {
            exitFullscreen();
        } else {
            enterFullscreen();
        }
    }

    public boolean isFullscreen() {
        return isFullscreen;
    }

    // ─── Enter ───────────────────────────────────────────────────────────────

    private void enterFullscreen() {
        // הסר גרף מה-parent המקורי
        originalParent.removeView(chart);

        // צור container שחור/כהה למסך מלא
        fullscreenContainer = new FrameLayout(context);
        fullscreenContainer.setBackgroundColor(Color.parseColor("#0B0F14"));
        fullscreenContainer.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // הגדרות גרף בפולסקרין
        FrameLayout.LayoutParams chartParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        int margin = dpToPx(8);
        chartParams.setMargins(margin, dpToPx(48), margin, margin);
        chart.setLayoutParams(chartParams);

        // הגדר X Axis למצב אופקי (Landscape feel)
        chart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        chart.setScaleXEnabled(true);
        chart.setScaleYEnabled(true);
        chart.setPinchZoom(true);
        chart.setDoubleTapToZoomEnabled(true);

        fullscreenContainer.addView(chart);

        // כפתור סגירה (X)
        btnClose = createCloseButton();
        fullscreenContainer.addView(btnClose);

        // הוסף לרוט
        rootLayout.addView(fullscreenContainer);

        // אנימציה
        animateEntry(fullscreenContainer);

        isFullscreen = true;
        if (listener != null) listener.onFullscreenChanged(true);
    }

    // ─── Exit ────────────────────────────────────────────────────────────────

    private void exitFullscreen() {
        // הסר מ-fullscreen container
        fullscreenContainer.removeView(chart);
        rootLayout.removeView(fullscreenContainer);
        fullscreenContainer = null;
        btnClose = null;

        // החזר params מקוריים
        chart.setLayoutParams(originalParams);

        // כבה pinch zoom (אופציונלי - השאר דלוק אם רוצים גם במצב רגיל)
        chart.setScaleXEnabled(true);
        chart.setScaleYEnabled(false);
        chart.setPinchZoom(false);
        chart.setDoubleTapToZoomEnabled(false);
        chart.fitScreen(); // איפוס zoom

        // החזר לפוזיציה המקורית
        originalParent.addView(chart, originalIndex, originalParams);

        isFullscreen = false;
        if (listener != null) listener.onFullscreenChanged(false);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private ImageButton createCloseButton() {
        ImageButton btn = new ImageButton(context);
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(
                dpToPx(44), dpToPx(44)
        );
        p.topMargin = dpToPx(4);
        p.rightMargin = dpToPx(8);
        p.gravity = android.view.Gravity.TOP | android.view.Gravity.END;
        btn.setLayoutParams(p);
        btn.setBackgroundColor(Color.TRANSPARENT);
        btn.setImageDrawable(
                androidx.core.content.ContextCompat.getDrawable(context,
                        android.R.drawable.ic_menu_close_clear_cancel)
        );
        btn.setColorFilter(Color.parseColor("#E6EDF3"));
        btn.setContentDescription("Close fullscreen");
        btn.setOnClickListener(v -> exitFullscreen());
        return btn;
    }

    private void animateEntry(View view) {
        view.setAlpha(0f);
        view.setScaleX(0.92f);
        view.setScaleY(0.92f);
        view.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(250)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private int dpToPx(int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
