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
        tvResult.setVisibility(View.GONE);

        tvInfo.setText("לחץ 'הפעל סימולטור', הכנס סכום השקעה ובחר מצב חישוב (רציף / במקביל).");

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

    // ─── שלב 1: טעינת טריידים סגורים מ-Firebase ───
    private void runFlow() {
        setLoading(true);
        tvResult.setVisibility(View.GONE);
        tvResult.setText("");
        tvInfo.setText("טוען טריידים סגורים...");

        closedTradesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<StockData> trades = new ArrayList<>();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    StockData data = ds.getValue(StockData.class);
                    // כולל רק טריידים עם מחיר קנייה ומכירה תקפים
                    if (data != null && data.buyPrice > 0 && data.sellPrice > 0) {
                        trades.add(data);
                    }
                }
                setLoading(false);

                if (trades.isEmpty()) {
                    tvInfo.setText("לא נמצאו טריידים סגורים עם נתונים תקינים.\nסגור טרייד עם מחיר קנייה ומכירה כדי להשתמש בסימולטור.");
                    Toast.makeText(getContext(), "אין טריידים סגורים לסימולציה", Toast.LENGTH_SHORT).show();
                    return;
                }

                tvInfo.setText("נמצאו " + trades.size() + " טריידים סגורים.");
                askAmount(trades);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                setLoading(false);
                tvInfo.setText("שגיאה בטעינת הטריידים הסגורים.");
                Toast.makeText(getContext(), "שגיאה: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ─── שלב 2: בקשת סכום השקעה ───
    private void askAmount(List<StockData> trades) {
        if (getContext() == null) return;

        EditText input = new EditText(requireContext());
        input.setHint("לדוגמא: 10000");
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("\uD83D\uDCB0 סכום השקעה")
                .setMessage("כמה כסף תרצה להשקיע? (בדולרים)")
                .setView(input)
                .setPositiveButton("המשך \u2192", (dialog, which) -> {
                    String raw = input.getText().toString().trim();
                    double amount = parseAmount(raw);
                    if (amount <= 0) {
                        Toast.makeText(getContext(), "אנא הזן סכום גדול מ-0", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    askMode(trades, amount);
                })
                .setNegativeButton("ביטול", null)
                .show();
    }

    // ─── שלב 3: בחירת מצב חישוב ───
    private void askMode(List<StockData> trades, double amount) {
        if (getContext() == null) return;

        String[] options = {
                "\uD83D\uDD01 רציף – כל טרייד מקבל את כל הסכום שנצבר",
                "\u2194\uFE0F במקביל – הסכום מתחלק שווה בין כל הטריידים"
        };
        final int[] chosen = {0};

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("\uD83E\uDDE0 אופן חישוב")
                .setMessage(String.format(Locale.US,
                        "סכום: $%,.2f  |  טריידים: %d\n\n"
                        + "רציף: כל רווח/הפסד מצטבר לסכום הבא.\n"
                        + "במקביל: כל הטריידים מתבצעים בו-זמנית עם חלק שווה.",
                        amount, trades.size()))
                .setSingleChoiceItems(options, 0, (d, which) -> chosen[0] = which)
                .setPositiveButton("\u2795 חשב", (dialog, which) -> {
                    Mode mode = (chosen[0] == 0) ? Mode.SEQUENTIAL : Mode.SPLIT_EQUAL;
                    SimulationResult result = simulate(trades, amount, mode);
                    displayResult(result, mode, amount, trades);
                })
                .setNegativeButton("חזור", null)
                .show();
    }

    // ─── שלב 4: הצגת תוצאות ───
    private void displayResult(SimulationResult r, Mode mode, double originalAmount, List<StockData> trades) {
        if (tvResult == null || tvInfo == null) return;

        String modeLabel = (mode == Mode.SEQUENTIAL)
                ? "\uD83D\uDD01 רציף (טרייד אחרי טרייד)"
                : "\u2194\uFE0F במקביל (חלוקה שווה)";

        double diff    = r.finalAmount - r.startAmount;
        boolean profit = diff >= 0;
        String sign    = profit ? "+" : "";
        String emoji   = profit ? "\uD83D\uDCC8 רווח" : "\uD83D\uDCC9 הפסד";

        // פירוט הטריידים
        StringBuilder tradesDetail = new StringBuilder();
        for (StockData t : trades) {
            double pct = (t.buyPrice > 0) ? ((t.sellPrice - t.buyPrice) / t.buyPrice) * 100.0 : 0.0;
            String pctSign = pct >= 0 ? "+" : "";
            tradesDetail.append(String.format(Locale.US,
                    "  • %s: $%.2f \u2192 $%.2f (%s%.1f%%)\n",
                    t.symbol != null ? t.symbol : "?",
                    t.buyPrice, t.sellPrice, pctSign, pct));
        }

        String text = String.format(Locale.US,
                "%s\n"
                + "\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\n"
                + "\uD83D\uDCB5 סכום התחלתי:  $%,.2f\n"
                + "\uD83D\uDCB0 סכום סופי:      $%,.2f\n"
                + "%s:  %s$%,.2f\n"
                + "\uD83D\uDCC9 תשואה כוללת:  %s%.2f%%\n"
                + "\uD83D\uDCCA מס' טריידים:  %d\n"
                + "\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\n"
                + "פירוט טריידים:\n%s",
                modeLabel,
                r.startAmount,
                r.finalAmount,
                emoji, sign, Math.abs(diff),
                sign, r.percent,
                r.tradeCount,
                tradesDetail.toString());

        tvResult.setText(text);
        tvResult.setTextColor(profit
                ? android.graphics.Color.parseColor("#00C896")
                : android.graphics.Color.parseColor("#FF4D4D"));
        tvResult.setVisibility(View.VISIBLE);
        tvInfo.setText("\u2705 הסימולציה הושלמה על בסיס " + r.tradeCount + " טריידים סגורים.");
    }

    // ─── חישוב סימולציה ───
    private SimulationResult simulate(List<StockData> trades, double startAmount, Mode mode) {
        int n = trades.size();
        if (n == 0) return new SimulationResult(startAmount, startAmount, 0);

        if (mode == Mode.SEQUENTIAL) {
            // רציף: הרווח/הפסד של כל טרייד מצטבר לסכום הכולל
            double pot = startAmount;
            for (StockData t : trades) {
                double ret = safeReturn(returnFraction(t));
                pot = pot * (1.0 + ret);
            }
            return new SimulationResult(startAmount, pot, n);
        } else {
            // במקביל: הסכום מתחלק שווה, כל טרייד עובד על חלקו
            double perTrade = startAmount / n;
            double total    = 0.0;
            for (StockData t : trades) {
                double ret = safeReturn(returnFraction(t));
                total += perTrade * (1.0 + ret);
            }
            return new SimulationResult(startAmount, total, n);
        }
    }

    /** חישוב שיעור התשואה של טרייד: (מכירה - קנייה) / קנייה */
    private double returnFraction(StockData t) {
        if (t == null || t.buyPrice <= 0 || t.sellPrice <= 0) return 0.0;
        return (t.sellPrice - t.buyPrice) / t.buyPrice;
    }

    private static double safeReturn(double r) {
        if (Double.isNaN(r) || Double.isInfinite(r)) return 0.0;
        if (r < -1.0) return -1.0;
        return r;
    }

    private static class SimulationResult {
        final double startAmount;
        final double finalAmount;
        final double percent;
        final int    tradeCount;

        SimulationResult(double startAmount, double finalAmount, int tradeCount) {
            this.startAmount = startAmount;
            this.finalAmount = finalAmount;
            this.tradeCount  = tradeCount;
            this.percent     = (startAmount == 0) ? 0
                    : ((finalAmount - startAmount) / startAmount) * 100.0;
        }
    }

    private void setLoading(boolean loading) {
        if (progress != null) progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (btnRun   != null) btnRun.setEnabled(!loading);
    }

    private static double parseAmount(String s) {
        try {
            if (s == null || s.isEmpty()) return 0.0;
            // תמיכה בפסיק אנגלי כמפריד אלפים
            s = s.replace(",", "");
            return Double.parseDouble(s);
        } catch (Exception e) {
            return 0.0;
        }
    }
}
