package com.mogomarket.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public class ClosedTradesFragment extends Fragment {

    private List<StockData> closedTrades;
    private ClosedTradesAdapter adapter;
    private DatabaseReference userRef;
    private DatabaseReference closedTradesRef;

    // UI
    private MaterialButton btnAnalyze;
    private TextView totalPnlText;
    private TextView winRateText;
    private TextView tvClosedTotalPnl;
    private TextView tvClosedTradeCount;
    private TextView tvClosedTotalInvested;
    private TextView tvProfitFactor;
    private TextView tvAverageWinLoss;
    private TextView tvAverageHolding;
    private TextView tvMaxDrawdown;
    private TextView tvBestStrategy;
    private ValueEventListener closedTradesListener;
    private final Set<String> missedPnlRequests = ConcurrentHashMap.newKeySet();
    private String pendingExportCsv;
    private List<StockData> pendingExcelExport;
    private String pendingScreenshotPath;
    private final ActivityResultLauncher<String> screenshotPicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(),
                    this::uploadScreenshot);
    private final ActivityResultLauncher<String> exportExcelLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
                    this::writeExcel);
    private final ActivityResultLauncher<String> exportCsvLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("text/csv"),
                    this::writeCsv);
    private final ActivityResultLauncher<String[]> importCsvLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                    this::readCsv);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_closed_trades, container, false);

        btnAnalyze              = v.findViewById(R.id.btnAnalyzeTrades);
        totalPnlText            = v.findViewById(R.id.totalPnlText);
        winRateText             = v.findViewById(R.id.winRateText);
        tvClosedTradeCount      = v.findViewById(R.id.tvClosedTradeCount);
        tvProfitFactor          = v.findViewById(R.id.tvProfitFactor);
        tvAverageWinLoss        = v.findViewById(R.id.tvAverageWinLoss);
        tvAverageHolding        = v.findViewById(R.id.tvAverageHolding);
        tvMaxDrawdown           = v.findViewById(R.id.tvMaxDrawdown);
        tvBestStrategy          = v.findViewById(R.id.tvBestStrategy);


        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            userRef = FirebaseDatabase.getInstance()
                    .getReference("users")
                    .child(currentUser.getUid());
            closedTradesRef = userRef.child("closed-trades");
        }

        closedTrades = new ArrayList<>();
        adapter = new ClosedTradesAdapter(closedTrades, this::showEditDialog);

        adapter.setDeleteListener(trade -> showDeleteConfirmDialog(trade));
        adapter.setScreenshotsListener(this::showScreenshotMenu);

        adapter.setSummaryListener((totalPnl, wins, total, totalInvested, analytics) -> {
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if (getView() == null) return;
                // סה"כ P&L
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

                // סכום כולל סגורים + invested
                updateClosedSummary(totalPnl, total, totalInvested);
                tvProfitFactor.setText(Double.isInfinite(analytics.profitFactor)
                        ? "∞" : formatMetric(analytics.profitFactor, "%.2f"));
                tvAverageWinLoss.setText(formatMetric(analytics.averageWin, "+$%.2f")
                        + " / " + formatMetric(analytics.averageLoss, "-$%.2f"));
                tvAverageHolding.setText(formatMetric(analytics.averageHoldingDays, "%.1f days"));
                tvMaxDrawdown.setText(formatMetric(analytics.maxDrawdown, "$%.2f"));
                tvBestStrategy.setText(analytics.bestStrategy == null
                        ? "Best setup: —"
                        : String.format(java.util.Locale.US, "Best setup: %s (%.1f%%, %d trades)",
                                analytics.bestStrategy, analytics.bestStrategyWinRate,
                                analytics.bestStrategyTrades));
            });
        });

        RecyclerView recyclerView = v.findViewById(R.id.closedTradesRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        if (closedTradesRef != null) {
            closedTradesListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    closedTrades.clear();
                    Map<String, Integer> tradeNumbers = new HashMap<>();
                    for (DataSnapshot ds : snapshot.getChildren()) {
                        StockData data = ds.getValue(StockData.class);
                        if (data != null) {
                            data.closedTradeId = ds.getKey();
                            String symbolKey = data.symbol != null
                                    ? data.symbol.toUpperCase(java.util.Locale.US) : "";
                            int number = tradeNumbers.getOrDefault(symbolKey, 0) + 1;
                            tradeNumbers.put(symbolKey, number);
                            if (data.tradeNumber == null || data.tradeNumber.trim().isEmpty()) {
                                data.tradeNumber = StockData.toRomanNumeral(number);
                            }
                            closedTrades.add(data);
                            requestMissedProfitLoss(data);
                        }
                    }
                    adapter.notifyDataSetChanged();
                    adapter.triggerSummaryUpdate();
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Toast.makeText(getContext(), "שגיאה בטעינת נתונים", Toast.LENGTH_SHORT).show();
                }
            };
            closedTradesRef.addValueEventListener(closedTradesListener);
        }

        btnAnalyze.setOnClickListener(view -> showAiTradeAnalysis());
        v.findViewById(R.id.btnExportTrades).setOnClickListener(view -> {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Export closed trades")
                    .setItems(new String[]{"CSV", "Excel (.xlsx)"}, (dialog, which) -> {
                        if (which == 0) {
                            pendingExportCsv = TradeCsv.exportTrades(new ArrayList<>(closedTrades));
                            exportCsvLauncher.launch("mogo-closed-trades.csv");
                        } else {
                            pendingExcelExport = new ArrayList<>(closedTrades);
                            exportExcelLauncher.launch("mogo-closed-trades.xlsx");
                        }
                    })
                    .show();
        });
        v.findViewById(R.id.btnImportTrades).setOnClickListener(view -> {
            if (closedTradesRef == null) {
                Toast.makeText(requireContext(), "Sign in to import trades", Toast.LENGTH_SHORT).show();
                return;
            }
            new AlertDialog.Builder(requireContext())
                    .setTitle("Import closed trades")
                    .setMessage("This imports trade history only and does not change your cash balance.")
                    .setPositiveButton("Continue", (dialog, which) ->
                            importCsvLauncher.launch(new String[]{"text/*", "application/csv",
                                    "application/vnd.ms-excel",
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"}))
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        return v;
    }

    @Override
    public void onDestroyView() {
        if (closedTradesRef != null && closedTradesListener != null) {
            closedTradesRef.removeEventListener(closedTradesListener);
        }
        closedTradesListener = null;
        super.onDestroyView();
    }

    private void showAiTradeAnalysis() {
        if (closedTrades == null || closedTrades.isEmpty()) {
            Toast.makeText(requireContext(), "אין עסקאות סגורות לניתוח", Toast.LENGTH_SHORT).show();
            return;
        }

        ClosedTradeAnalytics analytics = ClosedTradeAnalytics.calculate(closedTrades);
        StringBuilder context = new StringBuilder()
                .append("מספר עסקאות: ").append(closedTrades.size())
                .append("\nProfit factor: ").append(analytics.profitFactor)
                .append("\nרווח ממוצע: ").append(analytics.averageWin)
                .append("\nהפסד ממוצע: ").append(analytics.averageLoss)
                .append("\nמשך החזקה ממוצע בימים: ").append(analytics.averageHoldingDays)
                .append("\nירידה מרבית: ").append(analytics.maxDrawdown);
        if (analytics.bestStrategy != null) {
            context.append("\nאסטרטגיה מובילה: ").append(analytics.bestStrategy)
                    .append(" (").append(analytics.bestStrategyWinRate)
                    .append("% הצלחה, ").append(analytics.bestStrategyTrades).append(" עסקאות)");
        }
        context.append("\n\nעסקאות אחרונות:");
        int start = Math.max(0, closedTrades.size() - 30);
        for (int i = start; i < closedTrades.size(); i++) {
            StockData trade = closedTrades.get(i);
            if (trade == null) continue;
            context.append("\n")
                    .append(trade.symbol == null ? "?" : trade.symbol)
                    .append(" | כניסה: ").append(trade.buyPrice)
                    .append(" | יציאה: ").append(trade.sellPrice)
                    .append(" | רווח/הפסד: ").append(StockData.calculateProfitLoss(trade))
                    .append(" | אסטרטגיה: ")
                    .append(trade.strategyTag == null ? "לא צוין" : trade.strategyTag);
        }
        AiChatDialog.show(requireContext(),
                "נתח את היסטוריית העסקאות שלי, הצבע על דפוסים והצע שאלות לשיפור.",
                context.toString());
    }

    private String formatMetric(double value, String format) {
        return Double.isFinite(value)
                ? String.format(java.util.Locale.US, format, value) : "—";
    }

    private void requestMissedProfitLoss(StockData trade) {
        if (closedTradesRef == null || trade.closedTradeId == null || trade.closedAt <= 0
                || "AVAILABLE".equals(trade.missedPnlStatus)
                || "UNAVAILABLE".equals(trade.missedPnlStatus)
                || !missedPnlRequests.add(trade.closedTradeId)) {
            return;
        }
        MissedProfitLossFetcher.fetch(trade, (maxHigh, windowComplete) -> {
            missedPnlRequests.remove(trade.closedTradeId);
            if (!windowComplete) return;
            closedTradesRef.child(trade.closedTradeId).runTransaction(new com.google.firebase.database.Transaction.Handler() {
                @NonNull
                @Override
                public com.google.firebase.database.Transaction.Result doTransaction(
                        @NonNull com.google.firebase.database.MutableData currentData) {
                    StockData latest = currentData.getValue(StockData.class);
                    if (latest == null || latest.closedAt != trade.closedAt) {
                        return com.google.firebase.database.Transaction.abort();
                    }
                    if (maxHigh != null && latest.buyPrice > 0 && latest.tradeAmount > 0) {
                        latest.missedProfitLoss = MissedProfitLossFetcher.calculate(latest, maxHigh);
                        if (!Double.isFinite(latest.missedProfitLoss)) return
                                com.google.firebase.database.Transaction.abort();
                        latest.missedPnlStatus = "AVAILABLE";
                    } else {
                        latest.missedProfitLoss = null;
                        latest.missedPnlStatus = "UNAVAILABLE";
                    }
                    currentData.setValue(latest);
                    return com.google.firebase.database.Transaction.success(currentData);
                }

                @Override
                public void onComplete(@Nullable DatabaseError error, boolean committed,
                                       @Nullable DataSnapshot currentData) {
                    if (error != null) {
                        android.util.Log.w("ClosedTrades", "Failed to save missed P&L",
                                error.toException());
                    }
                }
            });
        });
    }

    private void writeCsv(@Nullable Uri uri) {
        if (uri == null || pendingExportCsv == null || getContext() == null) return;
        String content = pendingExportCsv;
        pendingExportCsv = null;
        try (OutputStream output = requireContext().getContentResolver().openOutputStream(uri)) {
            if (output == null) throw new IOException("Could not open selected file");
            output.write(content.getBytes(StandardCharsets.UTF_8));
            Toast.makeText(getContext(), "Closed trades exported", Toast.LENGTH_SHORT).show();
        } catch (IOException | SecurityException e) {
            Toast.makeText(getContext(), "CSV export failed: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void writeExcel(@Nullable Uri uri) {
        if (uri == null || pendingExcelExport == null || getContext() == null) return;
        List<StockData> trades = pendingExcelExport;
        pendingExcelExport = null;
        try (OutputStream output = requireContext().getContentResolver().openOutputStream(uri)) {
            if (output == null) throw new IOException("Could not open selected file");
            TradeExcel.exportTrades(trades, output);
            Toast.makeText(getContext(), "Excel workbook exported", Toast.LENGTH_SHORT).show();
        } catch (IOException | SecurityException e) {
            Toast.makeText(getContext(), "Excel export failed: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void readCsv(@Nullable Uri uri) {
        if (uri == null || closedTradesRef == null || getContext() == null) return;
        try (InputStream input = requireContext().getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("Could not read selected file");
            ByteArrayOutputStream content = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                content.write(buffer, 0, count);
                if (content.size() > 25 * 1024 * 1024) {
                    throw new IOException("Import file is too large");
                }
            }
            byte[] fileContents = content.toByteArray();
            List<StockData> imported;
            if (fileContents.length >= 2 && fileContents[0] == 'P' && fileContents[1] == 'K') {
                imported = TradeExcel.importTrades(new ByteArrayInputStream(fileContents));
            } else {
                imported = TradeCsv.importTrades(
                        new String(fileContents, StandardCharsets.UTF_8));
            }
            if (imported.isEmpty()) throw new IOException("CSV contains no trades");
            Map<String, Object> updates = new HashMap<>();
            for (StockData trade : imported) {
                String id = trade.closedTradeId;
                if (id == null || !id.matches("[A-Za-z0-9_-]+")) {
                    id = closedTradesRef.push().getKey();
                }
                if (id == null) throw new IOException("Could not create a trade record");
                trade.closedTradeId = id;
                trade.tradeNumber = null;
                updates.put(id, trade);
            }
            closedTradesRef.updateChildren(updates)
                    .addOnSuccessListener(unused -> {
                        if (getContext() != null) {
                            Toast.makeText(getContext(), imported.size() + " trades imported",
                                    Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnFailureListener(error -> {
                        if (getContext() != null) {
                            Toast.makeText(getContext(), "Import failed: " + error.getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    });
        } catch (IOException | SecurityException e) {
            Toast.makeText(getContext(), "Import failed: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void showScreenshotMenu(StockData trade) {
        if (trade == null || trade.closedTradeId == null) {
            Toast.makeText(getContext(), "This trade has no screenshot record key",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        List<String> urls = trade.screenshotUrls == null
                ? new ArrayList<>() : new ArrayList<>(trade.screenshotUrls);
        List<String> choices = new ArrayList<>();
        choices.add("Add screenshot");
        for (int i = 0; i < urls.size(); i++) choices.add("View screenshot " + (i + 1));
        new AlertDialog.Builder(requireContext())
                .setTitle("Trade screenshots")
                .setItems(choices.toArray(new String[0]), (dialog, index) -> {
                    if (index == 0) {
                        pendingScreenshotPath = "closed-trades/" + trade.closedTradeId;
                        screenshotPicker.launch("image/*");
                    } else {
                        openScreenshot(urls.get(index - 1));
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void uploadScreenshot(Uri uri) {
        if (uri == null || pendingScreenshotPath == null || userRef == null) return;
        String tradePath = pendingScreenshotPath;
        pendingScreenshotPath = null;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        TradeScreenshotStorage.uploadAndAttach(requireContext(), uri, user.getUid(),
                userRef, tradePath, (success, message) -> {
                    if (!isAdded() || getContext() == null) return;
                    requireActivity().runOnUiThread(() -> Toast.makeText(getContext(), message,
                            success ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show());
                });
    }

    private void openScreenshot(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(getContext(), "No app can open this screenshot",
                    Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== סכום כולל עסקאות סגורות ====================

    private void updateClosedSummary(double totalPnl, int total, double totalInvested) {
        if (tvClosedTotalPnl != null) {
            String sign = totalPnl >= 0 ? "+" : "";
            tvClosedTotalPnl.setText(String.format("%s$%.2f", sign, totalPnl));
            tvClosedTotalPnl.setTextColor(
                    totalPnl > 0 ? 0xFF00E676 :
                    totalPnl < 0 ? 0xFFFF5252 :
                                   0xFF78909C);
        }
        if (tvClosedTradeCount != null) {
            tvClosedTradeCount.setText(String.valueOf(total));
        }
        if (tvClosedTotalInvested != null) {
            if (totalInvested > 0) {
                tvClosedTotalInvested.setText(String.format("$%,.2f", totalInvested));
            } else {
                tvClosedTotalInvested.setText("$0.00");
            }
        }
    }

    // ==================== מחיקת עסקה ====================

    private void showDeleteConfirmDialog(StockData trade) {
        new AlertDialog.Builder(getContext())
                .setTitle("מחיקת עסקה")
                .setMessage("האם אתה בטוח שברצונך למחוק את עסקת " + trade.symbol
                        + " " + trade.tradeNumber + "?\nהפעולה אינה ניתנת לביטול.")
                .setPositiveButton("מחק", (dialog, which) -> deleteTradeFromFirebase(trade))
                .setNegativeButton("ביטול", (d, w) -> d.cancel())
                .show();
    }

    private void deleteTradeFromFirebase(StockData trade) {
        if (closedTradesRef == null || trade.symbol == null) return;
        String key = trade.closedTradeId != null
                ? trade.closedTradeId : trade.symbol.replace(":", "_");
        closedTradesRef.child(key).removeValue()
                .addOnSuccessListener(a -> {
                    TradeScreenshotStorage.deleteAll(trade.screenshotPaths);
                    if (getContext() != null) {
                        Toast.makeText(getContext(), "\u2705 העסקה נמחקה בהצלחה",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "שגיאה במחיקה: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    // ==================== עריכה ====================

    private void showEditDialog(StockData trade) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("עריכת עסקה: " + trade.symbol + " " + trade.tradeNumber);

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

        final EditText inputAmount = new EditText(getContext());
        inputAmount.setHint("סכום הושקע ($)");
        inputAmount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        inputAmount.setText(trade.tradeAmount > 0 ? String.valueOf(trade.tradeAmount) : "");
        layout.addView(inputAmount);

        final EditText inputEntryFees = createMoneyInput(trade.entryFees, "Entry fees ($)");
        layout.addView(inputEntryFees);

        final EditText inputExitFees = createMoneyInput(trade.exitFees, "Exit fees ($)");
        layout.addView(inputExitFees);

        final EditText inputTaxes = createMoneyInput(trade.taxes, "Taxes paid ($)");
        layout.addView(inputTaxes);

        final Spinner exitReason = new Spinner(getContext());
        String[] exitReasons = {"Manual Exit", "Hit TP", "Hit SL", "Trailing Stop"};
        ArrayAdapter<String> reasonAdapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_item, exitReasons);
        reasonAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        exitReason.setAdapter(reasonAdapter);
        if ("HIT_TP".equals(trade.exitReason)) exitReason.setSelection(1);
        else if ("HIT_SL".equals(trade.exitReason)) exitReason.setSelection(2);
        else if ("TRAILING_STOP".equals(trade.exitReason)) exitReason.setSelection(3);
        layout.addView(exitReason);

        final Spinner strategy = new Spinner(getContext());
        String[] strategies = {"None", "Breakout", "Pullback", "EMA Bounce",
                "Earnings Play", "Other"};
        ArrayAdapter<String> strategyAdapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_item, strategies);
        strategyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        strategy.setAdapter(strategyAdapter);
        int strategyPosition = java.util.Arrays.asList(strategies)
                .indexOf(trade.strategyTag == null ? "" : trade.strategyTag);
        strategy.setSelection(Math.max(0, strategyPosition));
        layout.addView(strategy);

        final Spinner sector = new Spinner(getContext());
        String[] sectors = {"Other", "Technology", "Healthcare", "Financials",
                "Consumer Discretionary", "Consumer Staples", "Industrials", "Energy",
                "Materials", "Utilities", "Real Estate", "Communication Services",
                "Cryptocurrency"};
        ArrayAdapter<String> sectorAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, sectors);
        sectorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sector.setAdapter(sectorAdapter);
        int sectorPosition = java.util.Arrays.asList(sectors)
                .indexOf(trade.sector == null ? "Other" : trade.sector);
        sector.setSelection(Math.max(0, sectorPosition));
        layout.addView(sector);

        builder.setView(layout);
        builder.setPositiveButton("שמור שינויים", (dialog, which) -> {
            try {
                String buyStr    = inputBuy.getText().toString();
                String sellStr   = inputSell.getText().toString();
                String amountStr = inputAmount.getText().toString();
                String entryFeesStr = inputEntryFees.getText().toString().trim();
                String exitFeesStr = inputExitFees.getText().toString().trim();
                String taxesStr = inputTaxes.getText().toString().trim();
                if (buyStr.isEmpty() || sellStr.isEmpty()) {
                    Toast.makeText(getContext(), "נא למלא את כל השדות", Toast.LENGTH_SHORT).show();
                    return;
                }
                double entryFees = entryFeesStr.isEmpty() ? 0 : Double.parseDouble(entryFeesStr);
                double exitFees = exitFeesStr.isEmpty() ? 0 : Double.parseDouble(exitFeesStr);
                double taxes = taxesStr.isEmpty() ? 0 : Double.parseDouble(taxesStr);
                if (entryFees < 0 || exitFees < 0 || taxes < 0
                        || !Double.isFinite(entryFees) || !Double.isFinite(exitFees)
                        || !Double.isFinite(taxes)) {
                    Toast.makeText(getContext(), "עמלות ומסים חייבים להיות מספרים חיוביים",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                updateTradeInFirebase(trade,
                        Double.parseDouble(buyStr),
                        Double.parseDouble(sellStr),
                        amountStr.isEmpty() ? 0 : Double.parseDouble(amountStr),
                        new String[]{"MANUAL_EXIT", "HIT_TP", "HIT_SL", "TRAILING_STOP"}
                                [exitReason.getSelectedItemPosition()],
                        "None".equals(strategy.getSelectedItem().toString())
                                ? "" : strategy.getSelectedItem().toString(),
                        entryFees, exitFees, taxes, sector.getSelectedItem().toString());
            } catch (NumberFormatException e) {
                Toast.makeText(getContext(), "נא להזין מספרים תקינים בלבד", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("ביטול", (d, w) -> d.cancel());
        builder.show();
    }

    private EditText createMoneyInput(double value, String hint) {
        EditText input = new EditText(requireContext());
        input.setHint(hint);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (value > 0) input.setText(String.valueOf(value));
        return input;
    }

    private void updateTradeInFirebase(StockData trade, double buyPrice,
                                       double sellPrice, double tradeAmount, String exitReason,
                                       String strategyTag, double entryFees, double exitFees,
                                       double taxes, String sector) {
        if (userRef == null || trade.symbol == null) return;
        String key = trade.closedTradeId != null
                ? trade.closedTradeId : trade.symbol.replace(":", "_");
        userRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                MutableData tradeData = currentData.child("closed-trades").child(key);
                StockData previous = tradeData.getValue(StockData.class);
                if (previous == null || buyPrice <= 0 || sellPrice <= 0 || tradeAmount < 0
                        || !Double.isFinite(buyPrice) || !Double.isFinite(sellPrice)
                        || !Double.isFinite(tradeAmount)) {
                    return Transaction.abort();
                }

                double oldProceeds = previous.tradeAmount > 0
                        ? previous.tradeAmount * previous.sellPrice / previous.buyPrice : 0;
                double newProceeds = tradeAmount > 0 ? tradeAmount * sellPrice / buyPrice : 0;
                double cashAdjustment = previous.tradeAmount + previous.entryFees
                        - tradeAmount - entryFees
                        + newProceeds - exitFees - taxes
                        - oldProceeds + previous.exitFees + previous.taxes;
                if (!Double.isFinite(cashAdjustment)) return Transaction.abort();

                previous.buyPrice = (float) buyPrice;
                previous.sellPrice = sellPrice;
                previous.tradeAmount = tradeAmount;
                previous.exitReason = exitReason;
                previous.strategyTag = strategyTag;
                previous.sector = sector;
                previous.entryFees = entryFees;
                previous.exitFees = exitFees;
                previous.taxes = taxes;
                tradeData.setValue(previous);

                MutableData cashData = currentData.child("portfolio-cash");
                Double storedCash = cashData.getValue(Double.class);
                double adjustedCash = (storedCash != null ? storedCash : 0) + cashAdjustment;
                if (!Double.isFinite(adjustedCash) || adjustedCash < -0.0000001) {
                    return Transaction.abort();
                }
                cashData.setValue(Math.max(0, adjustedCash));

                MutableData events = currentData.child("daily-trade-events");
                for (MutableData eventData : events.getChildren()) {
                    DailyTradeEvent event = eventData.getValue(DailyTradeEvent.class);
                    if (event == null || !trade.symbol.equalsIgnoreCase(event.symbol)) continue;
                    boolean sameTrade = trade.closedTradeId != null
                            ? trade.closedTradeId.equals(event.closedTradeId)
                                    || (event.closedTradeId == null
                                    && event.closedAt == trade.closedAt)
                            : event.closedAt == trade.closedAt;
                    if (sameTrade) {
                        event.buyPrice = buyPrice;
                        event.sellPrice = sellPrice;
                        event.tradeAmount = tradeAmount;
                        event.profitLoss = StockData.calculateProfitLoss(previous);
                        eventData.setValue(event);
                    }
                }
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError error, boolean committed,
                                   @Nullable DataSnapshot currentData) {
                if (!isAdded() || getContext() == null) return;
                if (error != null) {
                    Toast.makeText(getContext(), "שגיאה בעדכון: " + error.getMessage(),
                            Toast.LENGTH_SHORT).show();
                } else if (committed) {
                    Toast.makeText(getContext(), "העסקה עודכנה בהצלחה", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getContext(), "הנתונים שהוזנו אינם תקינים",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
}
