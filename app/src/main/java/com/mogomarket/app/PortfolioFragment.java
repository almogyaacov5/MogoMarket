package com.mogomarket.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.functions.FirebaseFunctions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;

public class PortfolioFragment extends Fragment implements StocksAdapter.OnStockClickListener {

    private RecyclerView recyclerView;
    private StocksAdapter adapter;
    private final List<StockData> stocks = new ArrayList<>();
    private DatabaseReference userRef;
    private DatabaseReference portfolioStocksRef;
    private ValueEventListener stocksListener;
    private ValueEventListener cashListener;
    private TextView tvOpenCount, tvTotalPnl, tvTotalPct, tvDailyPnl, tvDailyPct;
    private TextView tvTotalInvested, tvCash, tvPortfolioValue;
    private TextView tvConcentrationAlert;
    private View concentrationRiskCard;
    private TextView tvPortfolioBeta;
    private AssetAllocationChart assetAllocationChart;
    private View assetAllocationToggle;
    private ImageView assetAllocationArrow;
    private boolean assetAllocationExpanded;
    private double cashBalance;
    private String betaRequestKey;
    private String pendingScreenshotPath;
    private final ActivityResultLauncher<String> screenshotPicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(),
                    this::uploadScreenshot);
    private static final double CONCENTRATION_ALERT_PERCENT = 25.0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_portfolio, container, false);

        tvOpenCount = view.findViewById(R.id.tvOpenCount);
        tvTotalPnl = view.findViewById(R.id.tvTotalPnl);
        tvTotalPct = view.findViewById(R.id.tvTotalPct);
        tvDailyPnl = view.findViewById(R.id.tvDailyPnl);
        tvDailyPct = view.findViewById(R.id.tvDailyPct);
        tvTotalInvested = view.findViewById(R.id.tvTotalInvested);
        tvCash = view.findViewById(R.id.tvCash);
        tvPortfolioValue = view.findViewById(R.id.tvPortfolioValue);
        tvConcentrationAlert = view.findViewById(R.id.tvConcentrationAlert);
        concentrationRiskCard = view.findViewById(R.id.concentrationRiskCard);
        tvPortfolioBeta = view.findViewById(R.id.tvPortfolioBeta);
        assetAllocationChart = view.findViewById(R.id.assetAllocationChart);
        assetAllocationToggle = view.findViewById(R.id.assetAllocationToggle);
        assetAllocationArrow = view.findViewById(R.id.assetAllocationArrow);
        assetAllocationExpanded = false;
        assetAllocationToggle.setOnClickListener(v -> setAssetAllocationExpanded(
                !assetAllocationExpanded));

        recyclerView = view.findViewById(R.id.tradesRecyclerView);
        recyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        adapter = new StocksAdapter(stocks, this);
        adapter.setCashBalance(cashBalance);
        adapter.setPortfolioSummaryListener(this::showPortfolioSummary);
        recyclerView.setAdapter(adapter);

        view.findViewById(R.id.btnAddStockToPortfolio).setOnClickListener(
                v -> Navigation.findNavController(v).navigate(R.id.portfolioAddStockFragment));
        view.findViewById(R.id.btnRefreshPortfolio).setOnClickListener(v -> {
            v.animate().rotationBy(360f).setDuration(450L).start();
            betaRequestKey = null;
            requestPortfolioBeta(cashBalance);
            adapter.refreshPrices();
        });
        view.findViewById(R.id.btnSharePortfolio).setOnClickListener(
                v -> showPortfolioShareOptions());
        view.findViewById(R.id.btnIncomingShares).setOnClickListener(
                v -> showIncomingTradeShares());
        tvCash.setOnLongClickListener(v -> {
            showCashDialog();
            return true;
        });

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            userRef = FirebaseDatabase.getInstance().getReference("users").child(currentUser.getUid());
            portfolioStocksRef = userRef.child("portfolio-stocks");
            listenToPortfolio();
        } else {
            Toast.makeText(getContext(), "Sign in to view your portfolio", Toast.LENGTH_SHORT).show();
        }

        return view;
    }

    private void listenToPortfolio() {
        stocksListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                stocks.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    StockData stock = child.getValue(StockData.class);
                    if (stock != null) {
                        stock.firebaseKey = child.getKey();
                        stocks.add(stock);
                    }
                }
                if (adapter != null) adapter.notifyDataSetChanged();
                updateSummary();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Failed to load portfolio: " + error.getMessage(),
                            Toast.LENGTH_LONG).show();
                }
            }
        };
        portfolioStocksRef.addValueEventListener(stocksListener);

        cashListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Double value = snapshot.getValue(Double.class);
                cashBalance = value != null ? value : 0.0;
                if (adapter != null) adapter.setCashBalance(cashBalance);
                updateSummary();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Failed to load cash balance: " + error.getMessage(),
                            Toast.LENGTH_LONG).show();
                }
            }
        };
        userRef.child("portfolio-cash").addValueEventListener(cashListener);
    }

    private void updateSummary() {
        if (adapter != null) adapter.updatePortfolioSummary();
    }

    private void showPortfolioSummary(double invested, double marketValue, double totalPnl,
                                      double dailyPnl, double cash, int positionCount) {
        if (getActivity() == null || getView() == null) return;
        requestPortfolioBeta(cash);
        double totalPct = invested > 0 ? totalPnl / invested * 100.0 : 0.0;
        double dailyPct = marketValue > 0 ? dailyPnl / marketValue * 100.0 : 0.0;
        double portfolioValue = marketValue + cash;
        double largestPositionValue = 0;
        String largestSymbol = "";
        Map<String, Double> allocation = new LinkedHashMap<>();
        for (StockData stock : stocks) {
            if (stock == null || stock.buyPrice <= 0 || stock.tradeAmount <= 0) continue;
            double price = stock.currentPrice > 0 ? stock.currentPrice : stock.buyPrice;
            double value = stock.tradeAmount * price / stock.buyPrice;
            String positionName = stock.symbol == null || stock.symbol.trim().isEmpty()
                    ? "Position" : stock.symbol.trim();
            if (CryptoHelper.isCryptoSymbol(positionName)) {
                positionName = CryptoHelper.getShortName(positionName);
            }
            allocation.merge(positionName, value, Double::sum);
            if (value > largestPositionValue) {
                largestPositionValue = value;
                largestSymbol = stock.symbol;
            }
        }
        if (cash > 0) allocation.put("Cash", cash);
        double largestPercent = portfolioValue > 0
                ? largestPositionValue / portfolioValue * 100.0 : 0;
        String concentratedSymbol = largestSymbol;
        double concentratedPercent = largestPercent;

        getActivity().runOnUiThread(() -> {
            if (getView() == null) return;
            tvOpenCount.setText(String.format(Locale.US, "%d Open", positionCount));
            tvTotalInvested.setText(String.format(Locale.US, "$%,.2f", invested));
            tvCash.setText(String.format(Locale.US, "$%,.2f", cash));
            tvPortfolioValue.setText(String.format(Locale.US, "$%,.2f", portfolioValue));
            updateAssetAllocationChart(allocation);
            if (largestPercent >= CONCENTRATION_ALERT_PERCENT) {
                tvConcentrationAlert.setText(String.format(Locale.US,
                        "%s is %.1f%% of your portfolio",
                        concentratedSymbol, concentratedPercent));
                concentrationRiskCard.setVisibility(View.VISIBLE);
            } else {
                concentrationRiskCard.setVisibility(View.GONE);
            }
            setSignedAmount(tvTotalPnl, totalPnl);
            tvTotalPnl.setTextColor(0xFFFFB84D);
            setSignedPercent(tvTotalPct, totalPct);
            setSignedAmount(tvDailyPnl, dailyPnl);
            setSignedPercent(tvDailyPct, dailyPct);
        });
    }

    private void updateAssetAllocationChart(Map<String, Double> allocation) {
        if (assetAllocationChart == null) return;
        assetAllocationChart.setAllocation(allocation);
        float density = getResources().getDisplayMetrics().density;
        int chartHeight = Math.round(Math.max(120f, allocation.size() * 20f + 16f) * density);
        ViewGroup.LayoutParams layoutParams = assetAllocationChart.getLayoutParams();
        if (layoutParams.height != chartHeight) {
            layoutParams.height = chartHeight;
            assetAllocationChart.setLayoutParams(layoutParams);
        }
    }

    private void setAssetAllocationExpanded(boolean expanded) {
        assetAllocationExpanded = expanded;
        assetAllocationChart.setVisibility(expanded ? View.VISIBLE : View.GONE);
        assetAllocationArrow.animate().rotation(expanded ? 180f : 0f)
                .setDuration(180L).start();
        assetAllocationToggle.setContentDescription(expanded
                ? "Collapse asset allocation chart"
                : "Expand asset allocation chart");
    }

    private void requestPortfolioBeta(double cash) {
        if (tvPortfolioBeta == null || getContext() == null) return;
        List<StockData> positions = new ArrayList<>(stocks);
        positions.sort(Comparator.comparing(stock -> stock.symbol == null
                ? "" : stock.symbol.toUpperCase(Locale.US)));
        StringBuilder keyBuilder = new StringBuilder(String.format(Locale.US, "%.2f", cash));
        for (StockData stock : positions) {
            if (stock != null) {
                keyBuilder.append('|').append(stock.symbol).append(':')
                        .append(stock.tradeAmount).append(':').append(stock.buyPrice)
                        .append(':').append(stock.currentPrice);
            }
        }
        String requestKey = keyBuilder.toString();
        if (requestKey.equals(betaRequestKey)) return;
        betaRequestKey = requestKey;
        tvPortfolioBeta.setText("Portfolio beta vs. S&P 500: calculating");
        PortfolioBetaFetcher.fetch(positions, cash, (beta, error) -> {
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (tvPortfolioBeta == null || !requestKey.equals(betaRequestKey)) return;
                tvPortfolioBeta.setText(beta == null
                        ? "Portfolio beta vs. S&P 500: unavailable"
                        : String.format(Locale.US, "Portfolio beta vs. S&P 500: %.2f", beta));
            });
        });
    }

    private void setSignedAmount(TextView textView, double value) {
        textView.setText(String.format(Locale.US, "%s$%,.2f", value >= 0 ? "+" : "-", Math.abs(value)));
        textView.setTextColor(getResources().getColor(value >= 0 ? R.color.gain : R.color.loss,
                requireContext().getTheme()));
    }

    private void setSignedPercent(TextView textView, double value) {
        textView.setText(String.format(Locale.US, "%s%.2f%%", value >= 0 ? "+" : "-", Math.abs(value)));
        textView.setTextColor(getResources().getColor(value >= 0 ? R.color.gain : R.color.loss,
                requireContext().getTheme()));
    }

    private void showCashDialog() {
        if (userRef == null) return;
        EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED);
        input.setHint("Cash balance ($)");
        input.setText(String.format(Locale.US, "%.2f", cashBalance));
        input.setSelectAllOnFocus(true);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Set portfolio cash")
                .setView(input)
                .setPositiveButton("Save", (ignoredDialog, which) -> {
                    String value = input.getText().toString().trim();
                    double newCash;
                    try {
                        newCash = Double.parseDouble(value);
                    } catch (NumberFormatException e) {
                        Toast.makeText(getContext(), "Enter a valid cash amount", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!Double.isFinite(newCash) || newCash < 0) {
                        Toast.makeText(getContext(), "Enter a valid non-negative cash amount",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    userRef.child("portfolio-cash").setValue(newCash)
                            .addOnFailureListener(error -> {
                                if (getContext() != null) {
                                    Toast.makeText(getContext(), "Failed to save cash: " + error.getMessage(),
                                            Toast.LENGTH_LONG).show();
                                }
                            });
                })
                .setNegativeButton("Cancel", null)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                            | android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dialog.show();
        input.requestFocus();
        input.selectAll();
        input.postDelayed(() -> {
            android.view.inputmethod.InputMethodManager keyboard =
                    (android.view.inputmethod.InputMethodManager) requireContext()
                            .getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (keyboard != null) {
                keyboard.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            }
        }, 150);
    }

    private void showPortfolioShareOptions() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Share trades")
                .setItems(new String[]{"Share full portfolio", "Share one trade"}, (dialog, which) -> {
                    if (which == 0) {
                        chooseShareDestination("My Mogo Market portfolio",
                                buildPortfolioShareText(), new ArrayList<>(stocks));
                    } else if (stocks.isEmpty()) {
                        Toast.makeText(getContext(), "There are no open trades to share",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        String[] tradeNames = new String[stocks.size()];
                        for (int i = 0; i < stocks.size(); i++) {
                            StockData stock = stocks.get(i);
                            tradeNames[i] = stock.symbol
                                    + (stock.name == null || stock.name.trim().isEmpty()
                                    ? "" : " — " + stock.name.trim());
                        }
                        new AlertDialog.Builder(requireContext())
                                .setTitle("Choose a trade")
                                .setItems(tradeNames, (chooseDialog, index) ->
                                        chooseShareDestination(
                                                "Mogo Market trade: " + stocks.get(index).symbol,
                                                buildTradeShareText(stocks.get(index)),
                                                java.util.Collections.singletonList(stocks.get(index))))
                                .setNegativeButton("Cancel", null)
                                .show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void chooseShareDestination(String title, String text, List<StockData> trades) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Share")
                .setItems(new String[]{"Inside Mogo Market", "Outside the app", "Both"}, (dialog, choice) -> {
                    if (choice == 0) {
                        promptRecipientEmail(title, trades);
                    } else if (choice == 1) {
                        shareText(text);
                    } else if (choice == 2) {
                        promptRecipientEmail(title, trades, () -> shareText(text));
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void promptRecipientEmail(String title, List<StockData> trades) {
        promptRecipientEmail(title, trades, null);
    }

    private void promptRecipientEmail(String title, List<StockData> trades,
                                      @Nullable Runnable afterSend) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null || currentUser.isAnonymous()) {
            Toast.makeText(getContext(), "Sign in to share inside Mogo Market",
                    Toast.LENGTH_LONG).show();
            if (afterSend != null) afterSend.run();
            return;
        }
        if (trades.isEmpty()) {
            Toast.makeText(getContext(), "There are no open trades to share",
                    Toast.LENGTH_SHORT).show();
            if (afterSend != null) afterSend.run();
            return;
        }

        EditText emailInput = new EditText(requireContext());
        emailInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        emailInput.setSingleLine(true);
        emailInput.setHint("Recipient's Mogo Market email");
        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        emailInput.setPadding(padding, padding / 2, padding, padding / 2);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Send inside Mogo Market")
                .setMessage("Enter the email address used by the recipient's Mogo Market account.")
                .setView(emailInput)
                .setPositiveButton("Send", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String email = emailInput.getText().toString().trim();
                    if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                        emailInput.setError("Enter a valid email address");
                        return;
                    }
                    dialog.dismiss();
                    sendInAppTradeShare(email, title, trades, afterSend);
                }));
        dialog.show();
    }

    private void sendInAppTradeShare(String recipientEmail, String title, List<StockData> trades,
                                     @Nullable Runnable afterSend) {
        List<Map<String, Object>> sharedTrades = new ArrayList<>();
        for (StockData stock : trades) {
            if (stock == null || stock.symbol == null || stock.buyPrice <= 0) continue;
            double current = stock.currentPrice > 0 ? stock.currentPrice : stock.buyPrice;
            double shares = stock.shares > 0 ? stock.shares : stock.tradeAmount / stock.buyPrice;
            double pnl = stock.tradeAmount > 0
                    ? (current - stock.buyPrice) * shares : 0;
            Map<String, Object> trade = new LinkedHashMap<>();
            trade.put("symbol", stock.symbol);
            trade.put("name", stock.name == null ? "" : stock.name);
            trade.put("shares", shares);
            trade.put("entryPrice", stock.buyPrice);
            trade.put("currentPrice", current);
            trade.put("pnlAmount", pnl);
            trade.put("pnlPercent", (current - stock.buyPrice) / stock.buyPrice * 100.0);
            sharedTrades.add(trade);
        }
        if (sharedTrades.isEmpty()) {
            Toast.makeText(getContext(), "No valid trades to share", Toast.LENGTH_SHORT).show();
            if (afterSend != null) afterSend.run();
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("recipientEmail", recipientEmail);
        payload.put("title", title);
        payload.put("trades", sharedTrades);
        Toast.makeText(getContext(), "Sending trade...", Toast.LENGTH_SHORT).show();
        FirebaseFunctions.getInstance().getHttpsCallable("sendTradeShare")
                .call(payload)
                .addOnSuccessListener(result -> {
                    if (!isAdded() || getContext() == null) return;
                    Toast.makeText(getContext(), "Trade shared inside Mogo Market",
                            Toast.LENGTH_LONG).show();
                })
                .addOnFailureListener(error -> {
                    if (!isAdded() || getContext() == null) return;
                    String message = error.getLocalizedMessage();
                    Toast.makeText(getContext(), "Couldn't share trade: "
                                    + (message == null ? "unknown error" : message),
                            Toast.LENGTH_LONG).show();
                });
        if (afterSend != null) afterSend.run();
    }

    private void showIncomingTradeShares() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null || currentUser.isAnonymous()) {
            Toast.makeText(getContext(), "Sign in to view shared trades", Toast.LENGTH_SHORT).show();
            return;
        }
        FirebaseFunctions.getInstance().getHttpsCallable("getIncomingTradeShares")
                .call()
                .addOnSuccessListener(result -> {
                    if (!isAdded() || getContext() == null) return;
                    Object rawData = result.getData();
                    if (!(rawData instanceof Map)) {
                        Toast.makeText(getContext(), "Couldn't read shared trades",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    Object rawShares = ((Map<?, ?>) rawData).get("shares");
                    List<Map<?, ?>> shares = new ArrayList<>();
                    if (rawShares instanceof List) {
                        for (Object item : (List<?>) rawShares) {
                            if (item instanceof Map) shares.add((Map<?, ?>) item);
                        }
                    }
                    if (shares.isEmpty()) {
                        new AlertDialog.Builder(requireContext())
                                .setTitle("Shared with me")
                                .setMessage("No trades have been shared with you yet.")
                                .setPositiveButton("OK", null)
                                .show();
                        return;
                    }
                    String[] labels = new String[shares.size()];
                    for (int i = 0; i < shares.size(); i++) {
                        Map<?, ?> share = shares.get(i);
                        String sender = valueAsString(share.get("senderEmail"));
                        String shareTitle = valueAsString(share.get("title"));
                        labels[i] = (sender.isEmpty() ? "Mogo Market user" : sender) + " — "
                                + (shareTitle.isEmpty() ? "Shared trade" : shareTitle);
                    }
                    new AlertDialog.Builder(requireContext())
                            .setTitle("Shared with me")
                            .setItems(labels, (dialog, index) ->
                                    showIncomingTradeDetails(shares.get(index)))
                            .setPositiveButton("Close", null)
                            .show();
                })
                .addOnFailureListener(error -> {
                    if (isAdded() && getContext() != null) {
                        String message = error.getLocalizedMessage();
                        Toast.makeText(getContext(), "Couldn't load shared trades: "
                                        + (message == null ? "unknown error" : message),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void showIncomingTradeDetails(Map<?, ?> share) {
        String sender = valueAsString(share.get("senderEmail"));
        String title = valueAsString(share.get("title"));
        StringBuilder details = new StringBuilder("From ")
                .append(sender.isEmpty() ? "Mogo Market user" : sender)
                .append("\n\n");
        if (!title.isEmpty()) details.append(title).append('\n');
        Object rawTrades = share.get("trades");
        if (rawTrades instanceof List) {
            for (Object item : (List<?>) rawTrades) {
                if (!(item instanceof Map)) continue;
                Map<?, ?> trade = (Map<?, ?>) item;
                String symbol = valueAsString(trade.get("symbol"));
                String name = valueAsString(trade.get("name"));
                double shares = valueAsDouble(trade.get("shares"));
                double entry = valueAsDouble(trade.get("entryPrice"));
                double current = valueAsDouble(trade.get("currentPrice"));
                double pnl = valueAsDouble(trade.get("pnlAmount"));
                double pnlPercent = valueAsDouble(trade.get("pnlPercent"));
                details.append('\n').append(name.isEmpty()
                                ? (symbol.isEmpty() ? "Trade" : symbol) : name + " (" + symbol + ")")
                        .append(String.format(Locale.US,
                                "\nShares: %.6f | Entry: $%.2f | Last: $%.2f\nP/L: %s$%.2f (%.2f%%)\n",
                                shares, entry, current, pnl >= 0 ? "+" : "-", Math.abs(pnl), pnlPercent));
            }
        }
        new AlertDialog.Builder(requireContext())
                .setTitle("Shared trade")
                .setMessage(details.toString())
                .setPositiveButton("OK", null)
                .show();
    }

    private String valueAsString(Object value) {
        return value instanceof String ? ((String) value).trim() : "";
    }

    private double valueAsDouble(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0;
    }

    private String buildPortfolioShareText() {
        StringBuilder text = new StringBuilder("My Mogo Market portfolio\n");
        text.append(String.format(Locale.US, "Cash: $%,.2f\n", cashBalance));
        if (stocks.isEmpty()) {
            text.append("No open trades.");
            return text.toString();
        }
        for (StockData stock : stocks) {
            text.append("\n").append(buildTradeShareText(stock));
        }
        return text.toString();
    }

    private String buildTradeShareText(StockData stock) {
        double shares = stock.shares > 0 ? stock.shares
                : stock.buyPrice > 0 ? stock.tradeAmount / stock.buyPrice : 0;
        double current = stock.currentPrice > 0 ? stock.currentPrice : stock.buyPrice;
        double changePercent = stock.buyPrice > 0
                ? (current - stock.buyPrice) / stock.buyPrice * 100.0 : 0;
        return String.format(Locale.US,
                "%s%s\nShares: %.6f | Entry: $%.2f | Last: $%.2f | P/L: %+.2f%%",
                stock.name == null || stock.name.trim().isEmpty()
                        ? stock.symbol : stock.name.trim() + " (" + stock.symbol + ")",
                "", shares, stock.buyPrice, current, changePercent);
    }

    private void shareText(String text) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, text);
        try {
            startActivity(Intent.createChooser(shareIntent, "Share trades"));
        } catch (android.content.ActivityNotFoundException error) {
            Toast.makeText(getContext(), "No app is available to share this trade",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onStockClick(String symbol) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).showChartWithSymbol(symbol);
        }
    }

    @Override
    public boolean supportsTradeDetails() {
        return true;
    }

    @Override
    public void onStockDelete(String symbol, double sellPrice, String exitReason,
                              double exitFees, double taxes) {
        if (userRef == null || symbol == null) return;
        String key = symbol.replace(":", "_");
        DatabaseReference dailyEventsRef = userRef.child("daily-trade-events");
        String eventKey = dailyEventsRef.push().getKey();
        if (eventKey == null) {
            Toast.makeText(getContext(), "Failed to record the closed trade", Toast.LENGTH_LONG).show();
            return;
        }
        DatabaseReference closedTradesRef = userRef.child("closed-trades");
        String closedTradeId = closedTradesRef.push().getKey();
        if (closedTradeId == null) {
            Toast.makeText(getContext(), "Failed to record the closed trade", Toast.LENGTH_LONG).show();
            return;
        }
        String closingSymbol = symbol;
        long closedAt = System.currentTimeMillis();
        userRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                MutableData openTradeData = currentData.child("portfolio-stocks").child(key);
                StockData openTrade = openTradeData.getValue(StockData.class);
                if (openTrade == null || openTrade.buyPrice <= 0 || sellPrice <= 0
                        || !Double.isFinite(sellPrice)) return Transaction.abort();
                if (openTrade.symbol == null || openTrade.symbol.trim().isEmpty()) {
                    openTrade.symbol = closingSymbol;
                }

                double saleProceeds = openTrade.tradeAmount > 0
                        ? openTrade.tradeAmount * sellPrice / openTrade.buyPrice
                        : 0;
                saleProceeds -= exitFees + taxes;
                if (!Double.isFinite(saleProceeds)) return Transaction.abort();

                openTrade.sellPrice = sellPrice;
                openTrade.exitReason = exitReason;
                openTrade.closedAt = closedAt;
                openTrade.exitFees = exitFees;
                openTrade.taxes = taxes;
                openTrade.closedTradeId = closedTradeId;
                DailyTradeEvent event = new DailyTradeEvent();
                event.closedTradeId = closedTradeId;
                event.symbol = openTrade.symbol;
                event.buyPrice = openTrade.buyPrice;
                event.sellPrice = openTrade.sellPrice;
                event.tradeAmount = openTrade.tradeAmount;
                event.profitLoss = StockData.calculateProfitLoss(openTrade);
                event.closedAt = closedAt;
                currentData.child("daily-trade-events").child(eventKey).setValue(event);

                MutableData closedTradesData = currentData.child("closed-trades");
                int symbolTradeCount = 0;
                for (MutableData existingTradeData : closedTradesData.getChildren()) {
                    StockData existingTrade = existingTradeData.getValue(StockData.class);
                    if (existingTrade != null && existingTrade.symbol != null
                            && existingTrade.symbol.equalsIgnoreCase(openTrade.symbol)) {
                        symbolTradeCount++;
                    }
                }
                openTrade.closedTradeId = closedTradeId;
                openTrade.tradeNumber = StockData.toRomanNumeral(symbolTradeCount + 1);
                closedTradesData.child(closedTradeId).setValue(openTrade);

                MutableData cashData = currentData.child("portfolio-cash");
                Double currentCash = cashData.getValue(Double.class);
                cashData.setValue((currentCash != null ? currentCash : 0.0) + saleProceeds);
                openTradeData.setValue(null);
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError error, boolean committed,
                                   @Nullable DataSnapshot currentData) {
                if (!isAdded() || getContext() == null) return;
                if (error != null) {
                    Toast.makeText(getContext(), "Failed to close trade: " + error.getMessage(),
                            Toast.LENGTH_LONG).show();
                } else if (!committed) {
                    Toast.makeText(getContext(), "Open trade was not found", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    public void onStockEdit(StockData updatedStock, String oldSymbol) {
        if (userRef == null || updatedStock.symbol == null || updatedStock.symbol.trim().isEmpty()) return;
        if (oldSymbol == null) return;
        String oldKey = updatedStock.firebaseKey != null
                && !updatedStock.firebaseKey.trim().isEmpty()
                ? updatedStock.firebaseKey : oldSymbol.replace(":", "_");
        String newKey = updatedStock.symbol.replace(":", "_");
        userRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                MutableData portfolio = currentData.child("portfolio-stocks");
                MutableData oldData = portfolio.child(oldKey);
                StockData previousStock = oldData.getValue(StockData.class);
                if (previousStock == null) return Transaction.abort();
                if (!oldKey.equals(newKey) && portfolio.child(newKey).getValue() != null) {
                    return Transaction.abort();
                }
                MutableData cashData = currentData.child("portfolio-cash");
                Double storedCash = cashData.getValue(Double.class);
                double cash = storedCash != null ? storedCash : 0.0;
                boolean investmentChanged =
                        Double.compare(previousStock.tradeAmount, updatedStock.tradeAmount) != 0
                        || Double.compare(previousStock.entryFees, updatedStock.entryFees) != 0;
                double availableCash = cash;
                if (investmentChanged) {
                    availableCash += previousStock.tradeAmount + previousStock.entryFees
                            - updatedStock.tradeAmount - updatedStock.entryFees;
                    if (!Double.isFinite(availableCash) || availableCash < -0.0000001) {
                        return Transaction.abort();
                    }
                }
                if (!oldKey.equals(newKey)) oldData.setValue(null);
                portfolio.child(newKey).setValue(updatedStock);
                if (investmentChanged) cashData.setValue(Math.max(0, availableCash));
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError error, boolean committed,
                                   @Nullable DataSnapshot currentData) {
                if (!isAdded() || getContext() == null) return;
                if (error != null) {
                    Toast.makeText(getContext(), "Failed to update trade: " + error.getMessage(),
                            Toast.LENGTH_LONG).show();
                } else if (!committed) {
                    Toast.makeText(getContext(),
                            "Trade update failed. Check the cash balance and ticker.",
                            Toast.LENGTH_LONG).show();
                } else {
                    updatedStock.firebaseKey = newKey;
                    Toast.makeText(getContext(), "Trade updated successfully",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    public void onManageScreenshots(StockData stock) {
        if (stock == null || stock.symbol == null) return;
        String key = stock.firebaseKey != null && !stock.firebaseKey.trim().isEmpty()
                ? stock.firebaseKey : stock.symbol.replace(":", "_");
        showScreenshotMenu(stock, "portfolio-stocks/" + key);
    }

    private void showScreenshotMenu(StockData trade, String tradePath) {
        List<String> urls = trade.screenshotUrls == null
                ? new ArrayList<>() : new ArrayList<>(trade.screenshotUrls);
        List<String> choices = new ArrayList<>();
        choices.add("Add screenshot");
        for (int i = 0; i < urls.size(); i++) choices.add("View screenshot " + (i + 1));
        new AlertDialog.Builder(requireContext())
                .setTitle("Trade screenshots")
                .setItems(choices.toArray(new String[0]), (dialog, index) -> {
                    if (index == 0) {
                        pendingScreenshotPath = tradePath;
                        screenshotPicker.launch("image/*");
                    } else {
                        openScreenshot(urls.get(index - 1));
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void uploadScreenshot(Uri uri) {
        Context context = getContext();
        if (uri == null || pendingScreenshotPath == null || userRef == null
                || context == null || !isAdded()) return;
        String tradePath = pendingScreenshotPath;
        pendingScreenshotPath = null;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        Context appContext = context.getApplicationContext();
        TradeScreenshotStorage.uploadAndAttach(appContext, uri, user.getUid(),
                userRef, tradePath, (success, message) -> {
                    Toast.makeText(appContext, message,
                            success ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
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

    @Override
    public void onDestroyView() {
        if (portfolioStocksRef != null && stocksListener != null) {
            portfolioStocksRef.removeEventListener(stocksListener);
        }
        if (userRef != null && cashListener != null) {
            userRef.child("portfolio-cash").removeEventListener(cashListener);
        }
        recyclerView = null;
        adapter = null;
        super.onDestroyView();
    }
}
