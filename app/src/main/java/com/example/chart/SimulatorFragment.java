package com.example.chart;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SimulatorFragment extends Fragment {

    private DatabaseReference closedTradesRef;
    private TextInputEditText etAmount;
    private RadioButton rbSequential, rbParallel;
    private Button      btnRun;
    private ProgressBar progress;
    private TextView    tvResult;

    private enum Mode { SEQUENTIAL, SPLIT_EQUAL }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_simulator, container, false);

        etAmount     = v.findViewById(R.id.etSimAmount);
        rbSequential = v.findViewById(R.id.rbSequential);
        rbParallel   = v.findViewById(R.id.rbParallel);
        btnRun       = v.findViewById(R.id.btnRunSimulation);
        progress     = v.findViewById(R.id.progressSim);
        tvResult     = v.findViewById(R.id.tvSimResult);

        progress.setVisibility(View.GONE);
        tvResult.setText("");

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            tvResult.setText("Please log in to use the simulator.");
            btnRun.setEnabled(false);
            return v;
        }

        closedTradesRef = FirebaseDatabase.getInstance()
                .getReference("users/" + user.getUid() + "/closed-trades");

        btnRun.setOnClickListener(view -> {
            hideKeyboard(view);
            runSimulation();
        });
        return v;
    }

    // סגירת מקלדת
    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager)
                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private void runSimulation() {
        String raw = etAmount.getText() != null ? etAmount.getText().toString().trim() : "";
        if (TextUtils.isEmpty(raw)) {
            etAmount.setError("Enter an investment amount");
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            etAmount.setError("Invalid number");
            return;
        }
        if (amount <= 0) {
            etAmount.setError("Amount must be greater than 0");
            return;
        }

        Mode mode = rbSequential.isChecked() ? Mode.SEQUENTIAL : Mode.SPLIT_EQUAL;

        setLoading(true);
        tvResult.setText("");

        closedTradesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<StockData> trades = new ArrayList<>();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    StockData data = ds.getValue(StockData.class);
                    if (data != null) trades.add(data);
                }
                setLoading(false);

                if (trades.isEmpty()) {
                    tvResult.setTextColor(Color.GRAY);
                    tvResult.setText("No closed trades found.\nClose a trade to use the simulator.");
                    return;
                }

                SimulationResult result = simulate(trades, amount, mode);
                showResult(result, mode);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                setLoading(false);
                Toast.makeText(getContext(), "Error loading trades", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showResult(SimulationResult r, Mode mode) {
        boolean isProfit = r.finalAmount >= r.startAmount;
        double  diff     = r.finalAmount - r.startAmount;
        String  sign     = diff >= 0 ? "+" : "";
        String  modeText = (mode == Mode.SEQUENTIAL)
                ? "Sequential (trade after trade)"
                : "Parallel (equal split)";

        String text = String.format(Locale.US,
                "\uD83D\uDCCA Mode: %s\n" +
                "\uD83D\uDCC8 Trades: %d\n" +
                "\uD83D\uDCB5 Starting amount: $%.2f\n" +
                "\uD83D\uDCB0 Final amount:    $%.2f\n" +
                "%s: %s$%.2f\n" +
                "\uD83D\uDCC9 Return: %s%.2f%%",
                modeText,
                r.tradeCount,
                r.startAmount,
                r.finalAmount,
                isProfit ? "\uD83D\uDCC8 Profit" : "\uD83D\uDCC9 Loss",
                sign, Math.abs(diff),
                sign, r.percent);

        tvResult.setText(text);
        tvResult.setTextColor(isProfit
                ? Color.parseColor("#00C896")
                : Color.parseColor("#FF4D4D"));
    }

    private SimulationResult simulate(List<StockData> trades, double startAmount, Mode mode) {
        int n = trades.size();
        if (n == 0) return new SimulationResult(startAmount, startAmount, 0);

        if (mode == Mode.SEQUENTIAL) {
            double pot = startAmount;
            for (StockData t : trades) {
                pot = pot * (1.0 + safeReturn(returnFractionFromTrade(t)));
            }
            return new SimulationResult(startAmount, pot, n);
        } else {
            double per = startAmount / n;
            double sum = 0.0;
            for (StockData t : trades) {
                sum += per * (1.0 + safeReturn(returnFractionFromTrade(t)));
            }
            return new SimulationResult(startAmount, sum, n);
        }
    }

    private double returnFractionFromTrade(StockData t) {
        if (t == null) return 0.0;
        double buy  = t.buyPrice;
        double sell = t.sellPrice;
        if (buy <= 0 || sell <= 0) return 0.0;
        return (sell - buy) / buy;
    }

    private static double safeReturn(double r) {
        if (Double.isNaN(r) || Double.isInfinite(r)) return 0.0;
        return Math.max(r, -1.0);
    }

    private static class SimulationResult {
        final double startAmount, finalAmount, percent;
        final int    tradeCount;
        SimulationResult(double start, double fin, int count) {
            this.startAmount = start;
            this.finalAmount = fin;
            this.tradeCount  = count;
            this.percent     = (start == 0) ? 0 : ((fin - start) / start) * 100.0;
        }
    }

    private void setLoading(boolean loading) {
        if (progress != null) progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (btnRun   != null) btnRun.setEnabled(!loading);
    }
}
