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

/**
 * סימולטור השקעה:
 *  1. משתמש מזין סכום
 *  2. בוחר רציף או מקביל
 *  3. הסימולטור טוען טריידים סגורים מ-Firebase ומחשב רווח/הפסד
 */
public class SimulatorFragment extends Fragment {

    private DatabaseReference closedTradesRef;
    private TextInputEditText etAmount;
    private RadioButton       rbSequential, rbParallel;
    private Button            btnRun;
    private ProgressBar       progress;
    private TextView          tvResult;

    private enum Mode { SEQUENTIAL, PARALLEL }

    // ─────────────────────────────────────────
    //  onCreateView
    // ─────────────────────────────────────────

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

        if (progress != null) progress.setVisibility(View.GONE);
        if (tvResult != null) tvResult.setText("");

        // ברירת מחדל: רציף
        if (rbSequential != null) rbSequential.setChecked(true);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            if (tvResult != null) tvResult.setText("יש להתחבר כדי להשתמש בסימולטור.");
            if (btnRun   != null) btnRun.setEnabled(false);
            return v;
        }

        closedTradesRef = FirebaseDatabase.getInstance()
                .getReference("users/" + user.getUid() + "/closed-trades");

        if (btnRun != null) {
            btnRun.setOnClickListener(view -> {
                hideKeyboard(view);
                runSimulation();
            });
        }
        return v;
    }

    // ─────────────────────────────────────────
    //  הפעלת הסימולציה
    // ─────────────────────────────────────────

    private void runSimulation() {

        // 1. וולידציה של הסכום
        String raw = (etAmount.getText() != null) ? etAmount.getText().toString().trim() : "";
        if (TextUtils.isEmpty(raw)) {
            etAmount.setError("הכנס סכום להשקעה");
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            etAmount.setError("מספר לא תקין");
            return;
        }
        if (amount <= 0) {
            etAmount.setError("הסכום חייב להיות גדול מ-0");
            return;
        }

        // 2. מצב הרצה
        Mode mode = (rbSequential != null && rbSequential.isChecked())
                ? Mode.SEQUENTIAL
                : Mode.PARALLEL;

        setLoading(true);
        if (tvResult != null) tvResult.setText("");

        // 3. טעינת טריידים סגורים מ-Firebase
        closedTradesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<StockData> trades = new ArrayList<>();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    StockData data = ds.getValue(StockData.class);
                    // קבל רק טריידים עם buyPrice ו-sellPrice תקינים
                    if (data != null && data.buyPrice > 0 && data.sellPrice > 0) {
                        trades.add(data);
                    }
                }
                setLoading(false);

                if (trades.isEmpty()) {
                    if (tvResult != null) {
                        tvResult.setTextColor(Color.GRAY);
                        tvResult.setText("⚠️ לא נמצאו טריידים סגורים עם נתוני מחיר.\nסגור טריייד כדי להשתמש בסימולטור.");
                    }
                    return;
                }

                // 4. חישוב והצגה
                SimulationResult result = simulate(trades, amount, mode);
                showResult(result, mode);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                setLoading(false);
                Toast.makeText(getContext(), "שגיאה בטעינת הטריידים", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ─────────────────────────────────────────
    //  לוגיקת הסימולציה
    // ─────────────────────────────────────────

    /**
     * SEQUENTIAL — כל הון גדל/קטן ברצף לפי כל טריייד.
     * PARALLEL   — הסכום מתחלק שווה בין כל הטריידים.
     */
    private SimulationResult simulate(List<StockData> trades, double startAmount, Mode mode) {
        int n = trades.size();
        if (n == 0) return new SimulationResult(startAmount, startAmount, 0);

        if (mode == Mode.SEQUENTIAL) {
            double pot = startAmount;
            for (StockData t : trades) {
                pot = pot * (1.0 + safeReturn(returnFraction(t)));
            }
            return new SimulationResult(startAmount, pot, n);

        } else { // PARALLEL
            double perTrade = startAmount / n;
            double total    = 0.0;
            for (StockData t : trades) {
                total += perTrade * (1.0 + safeReturn(returnFraction(t)));
            }
            return new SimulationResult(startAmount, total, n);
        }
    }

    /** (sell - buy) / buy */
    private double returnFraction(StockData t) {
        if (t == null) return 0.0;
        double buy  = t.buyPrice;
        double sell = t.sellPrice;
        if (buy <= 0 || sell <= 0) return 0.0;
        return (sell - buy) / buy;
    }

    private static double safeReturn(double r) {
        if (Double.isNaN(r) || Double.isInfinite(r)) return 0.0;
        return Math.max(r, -1.0); // לא יכול להפסיד יותר מ-100%
    }

    // ─────────────────────────────────────────
    //  הצגת תוצאה
    // ─────────────────────────────────────────

    private void showResult(SimulationResult r, Mode mode) {
        if (tvResult == null) return;

        boolean isProfit = r.finalAmount >= r.startAmount;
        double  diff     = r.finalAmount - r.startAmount;
        String  sign     = diff >= 0 ? "+" : "-";

        String modeHeb = (mode == Mode.SEQUENTIAL)
                ? "🔁 רציף (טריייד אחרי טריייד)"
                : "⏸ מקביל (חלוקה שווה בין הטריידים)";

        String header = isProfit ? "✅ הייתה מרוויח!" : "❌ הייתה מפסיד!";
        String label  = isProfit ? "📈 רווח" : "📉 הפסד";

        String text = String.format(Locale.US,
                "%s\n\n"
                + "📊 מצב:           %s\n"
                + "💹 טריידים:       %d\n"
                + "💵 סכום התחלתי:  $%.2f\n"
                + "💰 סכום סופי:    $%.2f\n"
                + "%s:    %s$%.2f\n"
                + "📊 תשואה:        %s%.2f%%",
                header,
                modeHeb,
                r.tradeCount,
                r.startAmount,
                r.finalAmount,
                label, sign, Math.abs(diff),
                sign, Math.abs(r.percent));

        tvResult.setText(text);
        tvResult.setTextColor(isProfit
                ? Color.parseColor("#00C896")
                : Color.parseColor("#FF4D4D"));
    }

    // ─────────────────────────────────────────
    //  מחלקת תוצאה
    // ─────────────────────────────────────────

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

    // ─────────────────────────────────────────
    //  עזר
    // ─────────────────────────────────────────

    private void setLoading(boolean loading) {
        if (progress != null) progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (btnRun   != null) btnRun.setEnabled(!loading);
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager)
                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }
}
