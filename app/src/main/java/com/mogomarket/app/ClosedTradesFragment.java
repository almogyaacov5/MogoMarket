package com.mogomarket.app;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClosedTradesFragment extends Fragment {

    private List<StockData> closedTrades;
    private ClosedTradesAdapter adapter;
    private DatabaseReference closedTradesRef;

    // UI
    private MaterialButton btnAnalyze;
    private ProgressBar progressBar;
    private TextView totalPnlText;
    private TextView winRateText;
    private LLMService llmService;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_closed_trades, container, false);

        // אתחול רכיבי
        btnAnalyze   = v.findViewById(R.id.btnAnalyzeTrades);
        progressBar  = v.findViewById(R.id.aiProgressBar);
        totalPnlText = v.findViewById(R.id.totalPnlText);
        winRateText  = v.findViewById(R.id.winRateText);

        llmService = new LLMService();

        // Firebase
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            closedTradesRef = FirebaseDatabase.getInstance()
                    .getReference("users")
                    .child(currentUser.getUid())
                    .child("closed-trades");
        }

        // Adapter
        closedTrades = new ArrayList<>();
        adapter = new ClosedTradesAdapter(closedTrades, this::showEditDialog);

        // חיבור summaryListener לעדכון Header
        adapter.setSummaryListener((totalPnl, wins, total) -> {
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                // סהכ P&L
                String pnlStr = String.format("%s$%.2f", totalPnl >= 0 ? "+" : "", totalPnl);
                totalPnlText.setText(pnlStr);
                totalPnlText.setTextColor(
                        totalPnl > 0 ? 0xFF00E676 :
                        totalPnl < 0 ? 0xFFFF5252 :
                                       0xFF78909C
                );

                // Win Rate
                int winRate = (total > 0) ? (int) ((wins * 100.0) / total) : 0;
                winRateText.setText(winRate + "%");
                winRateText.setTextColor(
                        winRate >= 60 ? 0xFF00E676 :
                        winRate >= 40 ? 0xFFFFD740 :
                                        0xFFFF5252
                );
            });
        });

        RecyclerView recyclerView = v.findViewById(R.id.closedTradesRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        // Firebase listener
        if (closedTradesRef != null) {
            closedTradesRef.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    closedTrades.clear();
                    for (DataSnapshot ds : snapshot.getChildren()) {
                        StockData data = ds.getValue(StockData.class);
                        if (data != null) closedTrades.add(data);
                    }
                    adapter.notifyDataSetChanged();
                    // עדכון ה-Header אחרי רענון
                    adapter.triggerSummaryUpdate();
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Toast.makeText(getContext(), "שגיאה בטעינת נתונים", Toast.LENGTH_SHORT).show();
                }
            });
        }

        btnAnalyze.setOnClickListener(view -> analyzeTradesWithAI());

        return v;
    }

    // --- AI ---
    private void analyzeTradesWithAI() {
        if (closedTrades.isEmpty()) {
            Toast.makeText(getContext(), "אין מספיק נתונים לניתוח. בצע לפחות טרייד אחד.", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("אתה יועץ השקעות מומחה. אנא נתח את היסטוריית המסחר שלי ותן לי תובנות לשיפור.\n");
        promptBuilder.append("הנה רשימת העסקאות הסגורות שלי:\n\n");

        int wins = 0, losses = 0;
        for (StockData trade : closedTrades) {
            double pct = (trade.buyPrice != 0)
                    ? ((trade.sellPrice - trade.buyPrice) / trade.buyPrice) * 100 : 0;
            if (pct > 0) wins++; else losses++;
            promptBuilder.append(String.format(
                    "- מניה: %s | קנייה: $%.2f | מכירה: $%.2f | תשואה: %.2f%%\n",
                    trade.symbol, trade.buyPrice, trade.sellPrice, pct));
        }
        promptBuilder.append(String.format("\nסה\"\u05db עסקאות: %d (רווחיות: %d, מפסידות: %d)\n",
                closedTrades.size(), wins, losses));
        promptBuilder.append("\nאנא ספק:\n");
        promptBuilder.append("1. 📊 **ניתוח דפוסים:** איפה אני מצליח ואיפה אני נכשל?\n");
        promptBuilder.append("2. 💡 **המלצה אסטרטגית:** מה לשנות בניהול הסיכונים?\n");
        promptBuilder.append("3. 🎯 **טיפ לטרייד הבא:** על מה להסתכל לפני כניסה לעסקה?\n");
        promptBuilder.append("תשובה קצרה, ממוקדת ובעברית בלבד.");

        progressBar.setVisibility(View.VISIBLE);
        btnAnalyze.setEnabled(false);
        btnAnalyze.setText("מנתח...");

        llmService.generateContent(promptBuilder.toString(), new LLMService.LLMCallback() {
            @Override
            public void onSuccess(String result) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        btnAnalyze.setEnabled(true);
                        btnAnalyze.setText("✨  AI Trade Analysis");
                        showAnalysisDialog(result);
                    });
                }
            }

            @Override
            public void onFailure(Throwable t) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        btnAnalyze.setEnabled(true);
                        btnAnalyze.setText("✨  AI Trade Analysis");
                        Toast.makeText(getContext(), "שגיאה בניתוח: " + t.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            }
        });
    }

    private void showAnalysisDialog(String analysis) {
        new AlertDialog.Builder(getContext())
                .setTitle("🤖 ניתוח ביצועים אישי")
                .setMessage(analysis)
                .setPositiveButton("תודה, הבנתי", (d, w) -> d.dismiss())
                .show();
    }

    // --- עריכה ---
    private void showEditDialog(StockData trade) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("עריכת עסקה: " + trade.symbol);

        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 40, 60, 20);

        final EditText inputBuy = new EditText(getContext());
        inputBuy.setHint("מחיר קנייה ($)");
        inputBuy.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        inputBuy.setText(String.valueOf(trade.buyPrice));
        layout.addView(inputBuy);

        final EditText inputSell = new EditText(getContext());
        inputSell.setHint("מחיר מכירה ($)");
        inputSell.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        inputSell.setText(String.valueOf(trade.sellPrice));
        layout.addView(inputSell);

        builder.setView(layout);
        builder.setPositiveButton("שמור שינויים", (dialog, which) -> {
            try {
                String buyStr  = inputBuy.getText().toString();
                String sellStr = inputSell.getText().toString();
                if (buyStr.isEmpty() || sellStr.isEmpty()) {
                    Toast.makeText(getContext(), "נא למלא את כל השדות", Toast.LENGTH_SHORT).show();
                    return;
                }
                updateTradeInFirebase(trade.symbol,
                        Double.parseDouble(buyStr), Double.parseDouble(sellStr));
            } catch (NumberFormatException e) {
                Toast.makeText(getContext(), "נא להזין מספרים תקינים בלבד", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("ביטול", (d, w) -> d.cancel());
        builder.show();
    }

    private void updateTradeInFirebase(String symbol, double buyPrice, double sellPrice) {
        if (closedTradesRef == null) return;
        Map<String, Object> updates = new HashMap<>();
        updates.put("buyPrice",  buyPrice);
        updates.put("sellPrice", sellPrice);
        closedTradesRef.child(symbol).updateChildren(updates)
                .addOnSuccessListener(a ->
                        Toast.makeText(getContext(), "העסקה עודכנה בהצלחה", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "שגיאה בעדכון: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
}
