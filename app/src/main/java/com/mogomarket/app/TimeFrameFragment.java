package com.mogomarket.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import com.google.android.material.button.MaterialButton;
import java.util.Arrays;
import java.util.List;

public class TimeFrameFragment extends DialogFragment {

    public interface TimeFrameListener {
        void onTimeFrameSelected(String interval);
    }

    private TimeFrameListener listener;
    private MaterialButton selectedButton = null;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        Fragment parent = getParentFragment();
        if (parent instanceof TimeFrameListener) {
            listener = (TimeFrameListener) parent;
        } else if (context instanceof TimeFrameListener) {
            listener = (TimeFrameListener) context;
        } else {
            listener = null;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_timeframe, container, false);

        // סדר הלחצנים תואם בדיוק לסדר ב-XML: שורה 1, שורה 2, שורה 3
        List<MaterialButton> buttons = Arrays.asList(
            view.findViewById(R.id.btn1min),    // 1m
            view.findViewById(R.id.btn5min),    // 5m
            view.findViewById(R.id.btn15min),   // 15m
            view.findViewById(R.id.btn30min),   // 30m
            view.findViewById(R.id.btn1hour),   // 1H
            view.findViewById(R.id.btn4hour),   // 4H
            view.findViewById(R.id.btn1day),    // 1D
            view.findViewById(R.id.btn1week),   // 1W
            view.findViewById(R.id.btn1month)   // 1M
        );

        String[] intervals = {"1min", "5min", "15min", "30min", "60min", "4h", "1day", "1week", "1month"};

        for (int i = 0; i < buttons.size(); i++) {
            final MaterialButton btn = buttons.get(i);
            if (btn == null) continue;
            final String interval = intervals[i];

            btn.setOnClickListener(v -> {
                // Reset all buttons
                for (MaterialButton b : buttons) {
                    if (b == null) continue;
                    b.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FFFFFF")));
                    b.setTextColor(Color.parseColor("#0D1117"));
                    b.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#CBD5E0")));
                    b.setStrokeWidth(dpToPx(1));
                }

                // Highlight selected
                btn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#4DA3FF")));
                btn.setTextColor(Color.WHITE);
                btn.setStrokeWidth(0);
                selectedButton = btn;

                if (listener != null) {
                    listener.onTimeFrameSelected(interval);
                    dismiss();
                }
            });
        }

        // RadioGroup hidden for backward compat
        RadioGroup radioGroup = view.findViewById(R.id.radioGroupTimeframes);
        if (radioGroup != null) radioGroup.setVisibility(View.GONE);

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
    }

    private int dpToPx(int dp) {
        float density = requireContext().getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
