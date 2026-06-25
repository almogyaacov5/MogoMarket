package com.example.chart;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
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
    private Button btnRun;
    private ProgressBar progress;
    private TextView tvInfo;
    private TextView tvResult;

    private enum Mode { SEQUENTIAL, SPLIT_EQUAL }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_simulator, container, false);

        btnRun   = v.findViewById(R.id.btnRunSimulation);
        progress = v.findViewById(R.id.progressSim);
        tvInfo   = v.findViewById(R.id.tvSimInfo);
        tvResult = v.findViewById(R.id.tvSimResult);

        progress.setVisibility(View.GONE);
        tvResult.setText("");

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            tvInfo.setText("יש להתחבר כדי להשתמש בסימולטור");
            btnRun.setEnabled(false);
            return v;
        }

        closedTradesRef = FirebaseDatabase.getInstance()
                .getReference("users/" + user.getUid() + "/closed-trades");

        btnRun.setOnClickListener(view -> runFlow());
        return v;
    }

    private void runFlow() {
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
                    Toast.makeText(getContext(), "אין טריידים סגורים לסימולציה", Toast.LENGTH_SHORT).show();
                    return;
                }

                askAmountThenMode(trades);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                setLoading(false);
                Toast.makeText(getContext(), "שגיאה בטעינת הטריידים הסגורים", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void askAmountThenMode(List<StockData> trades) {
        if (getContext() == null) return;
        EditText input = new EditText(requireContext());
        input.setHint("לדוגמא: 1000");
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("סימולטור")
                .setMessage("כמה כסף להשקיע? (אותו סכום בסיס לסימולציה)")
                .setView(input)
                .setPositiveButton("המשך", (dialog, which) -> {
                    double amount = parseDouble(input.getText().toString().trim());
                    if (amount <= 0) {
                        Toast.makeText(getContext(), "אנא הזן סכום גדול מ-0", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    askModeAndSimulate(trades, amount);
                })
                .setNegativeButton("ביטול", null)
                .show();
    }

    private void askModeAndSimulate(List<StockData> trades, double amount) {
        if (getContext() == null) return;
        String[] options = new String[]{
                "רציף (טרייד אחרי טרייד, הסכום מצטבר)",
                "חלוקה שווה (הסכום מתחלק בין כל הטריידים)"
        };

        final int[] chosen = {0};

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("איך לחשב?")
                .setSingleChoiceItems(options, 0, (dialog, which) -> chosen[0] = which)
                .setPositiveButton("חשב", (dialog, which) -> {
                    Mode mode = (chosen[0] == 0) ? Mode.SEQUENTIAL : Mode.SPLIT_EQUAL;
                    SimulationResult result = simulate(trades, amount, mode);
                    showResult(result, mode);
                })
                .setNegativeButton("ביטול", null)
                .show();
    }

    private void showResult(SimulationResult r, Mode mode) {
        String modeText = (mode == Mode.SEQUENTIAL) ? "רציף" : "חלוקה שווה";
        String text = String.format(Locale.US,
                "מצב: %s\nטריידים: %d\nסכום התחלה: %.2f\nסכום סופי: %.2f\nתשואה: %.2f%%",
                modeText, r.tradeCount, r.startAmount, r.finalAmount, r.percent);
        tvResult.setText(text);
        tvInfo.setText("הסימולציה חושבה על בסיס הטריידים הסגורים הקיימים.");
    }

    private static class SimulationResult {
        final double startAmount;
        final double finalAmount;
        final double percent;
        final int tradeCount;

        SimulationResult(double startAmount, double finalAmount, int tradeCount) {
            this.startAmount = startAmount;
            this.finalAmount = finalAmount;
            this.tradeCount  = tradeCount;
            this.percent     = (startAmount == 0) ? 0 : ((finalAmount - startAmount) / startAmount) * 100.0;
        }
    }

    private SimulationResult simulate(List<StockData> trades, double startAmount, Mode mode) {
        int n = trades.size();
        if (n == 0) return new SimulationResult(startAmount, startAmount, 0);

        if (mode == Mode.SEQUENTIAL) {
            double pot = startAmount;
            for (StockData t : trades) {
                double r = safeReturn(returnFractionFromTrade(t));
                pot = pot * (1.0 + r);
            }
            return new SimulationResult(startAmount, pot, n);
        } else {
            double per = startAmount / n;
            double sum = 0.0;
            for (StockData t : trades) {
                double r = safeReturn(returnFractionFromTrade(t));
                sum += per * (1.0 + r);
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
        if (r < -1.0) return -1.0;
        return r;
    }

    private void setLoading(boolean loading) {
        if (progress != null) progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (btnRun   != null) btnRun.setEnabled(!loading);
    }

    private static double parseDouble(String s) {
        try {
            if (s == null || s.isEmpty()) return 0.0;
            return Double.parseDouble(s);
        } catch (Exception e) {
            return 0.0;
        }
    }
}