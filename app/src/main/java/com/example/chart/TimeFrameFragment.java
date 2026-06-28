package com.example.chart;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

public class TimeFrameFragment extends DialogFragment {

    public interface TimeFrameListener {
        void onTimeFrameSelected(String interval);
    }

    private TimeFrameListener listener;

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

        RadioGroup radioGroup = view.findViewById(R.id.radioGroupTimeframes);
        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            String interval = "1day";
            if      (checkedId == R.id.radio1min)   interval = "1min";
            else if (checkedId == R.id.radio5min)   interval = "5min";
            else if (checkedId == R.id.radio15min)  interval = "15min";
            else if (checkedId == R.id.radio65min)  interval = "65min";
            else if (checkedId == R.id.radio1day)   interval = "1day";
            else if (checkedId == R.id.radio1week)  interval = "1week";
            else if (checkedId == R.id.radio1month) interval = "1month";

            if (listener != null) {
                listener.onTimeFrameSelected(interval);
                dismiss();
            }
        });
        return view;
    }
}
