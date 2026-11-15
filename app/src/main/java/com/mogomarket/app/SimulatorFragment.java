package com.mogomarket.app;

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
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class SimulatorFragment extends Fragment {

    private DatabaseReference closedTradesRef;
    private DatabaseReference paperTradingRef;
    private TextInputEditText etAmount;
    private TextInputEditText etPaperSymbol;
    private TextInputEditText etPaperShares;
    private RadioButton       rbSequential, rbParallel;
    private Button            btnRun;
    private Button btnPaperBuy;
    private Button btnPaperSell;
    private ProgressBar       progress;
    private TextView          tvResult;
    private TextView tvPaperCash;
    private TextView tvPaperPositions;
    private TextView tvPaperHistory;
    private TextView tvPaperMessage;
    private ValueEventListener paperAccountListener;

    private static final double DEFAULT_PAPER_CASH = 100_000.0;
    private static final OkHttpClient QUOTE_CLIENT = new OkHttpClient.Builder()
            .callTimeout(15, TimeUnit.SECONDS)
            .build();

    private enum Mode { SEQUENTIAL, PARALLEL }

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
        etPaperSymbol = v.findViewById(R.id.etPaperSymbol);
        etPaperShares = v.findViewById(R.id.etPaperShares);
        btnPaperBuy = v.findViewById(R.id.btnPaperBuy);
        btnPaperSell = v.findViewById(R.id.btnPaperSell);
        tvPaperCash = v.findViewById(R.id.tvPaperCash);
        tvPaperPositions = v.findViewById(R.id.tvPaperPositions);
        tvPaperHistory = v.findViewById(R.id.tvPaperHistory);
        tvPaperMessage = v.findViewById(R.id.tvPaperMessage);
        progress     = v.findViewById(R.id.progressSim);
        tvResult     = v.findViewById(R.id.tvSimResult);

        if (progress != null) progress.setVisibility(View.GONE);
        if (tvResult != null) tvResult.setText("");
        if (rbSequential != null) rbSequential.setChecked(true);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            if (tvResult != null) tvResult.setText("Please log in to use the simulator.");
            if (btnRun   != null) btnRun.setEnabled(false);
            if (btnPaperBuy != null) btnPaperBuy.setEnabled(false);
            if (btnPaperSell != null) btnPaperSell.setEnabled(false);
            return v;
        }

        paperTradingRef = FirebaseDatabase.getInstance().getReference("users")
                .child(user.getUid()).child("paper-trading");
        initializePaperAccount();
        listenToPaperAccount();

        closedTradesRef = FirebaseDatabase.getInstance()
                .getReference("users/" + user.getUid() + "/closed-trades");

        if (btnRun != null) {
            btnRun.setOnClickListener(view -> {
                hideKeyboard(view);
                runSimulation();
            });
        }
        btnPaperBuy.setOnClickListener(view -> placePaperOrder(true));
        btnPaperSell.setOnClickListener(view -> placePaperOrder(false));
        return v;
    }

    private void initializePaperAccount() {
        paperTradingRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                if (currentData.child("cash").getValue() == null) {
                    currentData.child("cash").setValue(DEFAULT_PAPER_CASH);
                }
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError error, boolean committed,
                                   @Nullable DataSnapshot snapshot) {
                if (error != null && isAdded()) {
                    Toast.makeText(requireContext(), "Could not initialize paper account: "
                                    + error.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void listenToPaperAccount() {
        paperAccountListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Double cash = snapshot.child("cash").getValue(Double.class);
                tvPaperCash.setText(String.format(Locale.US, "$%,.2f",
                        cash == null ? DEFAULT_PAPER_CASH : cash));
                StringBuilder positionsText = new StringBuilder();
                for (DataSnapshot item : snapshot.child("positions").getChildren()) {
                    PaperPosition position = item.getValue(PaperPosition.class);
                    if (position == null || position.shares <= 0) continue;
                    if (positionsText.length() > 0) positionsText.append("\n\n");
                    positionsText.append(position.symbol)
                            .append("  ·  ")
                            .append(String.format(Locale.US, "%.6f shares", position.shares))
                            .append("\nAvg. cost ")
                            .append(String.format(Locale.US, "$%.2f", position.averagePrice))
                            .append("  ·  Last quote ")
                            .append(String.format(Locale.US, "$%.2f", position.lastPrice));
                }
                tvPaperPositions.setText(positionsText.length() == 0
                        ? "No open positions" : positionsText.toString());
                List<PaperTradeRecord> trades = new ArrayList<>();
                for (DataSnapshot item : snapshot.child("trades").getChildren()) {
                    PaperTradeRecord trade = item.getValue(PaperTradeRecord.class);
                    if (trade != null) trades.add(trade);
                }
                StringBuilder historyText = new StringBuilder();
                for (int i = trades.size() - 1; i >= Math.max(0, trades.size() - 10); i--) {
                    PaperTradeRecord trade = trades.get(i);
                    if (historyText.length() > 0) historyText.append("\n");
                    historyText.append(trade.side).append(" ")
                            .append(String.format(Locale.US, "%.6f", trade.shares))
                            .append(" ").append(trade.symbol)
                            .append(" @ ").append(String.format(Locale.US, "$%.2f", trade.price));
                    if ("SELL".equals(trade.side)) {
                        historyText.append("  ·  P/L ")
                                .append("$")
                                .append(String.format(Locale.US, "%+,.2f", trade.realizedPnl));
                    }
                }
                tvPaperHistory.setText(historyText.length() == 0
                        ? "No paper trades yet" : historyText.toString());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (isAdded()) tvPaperMessage.setText(
                        "Could not load paper account: " + error.getMessage());
            }
        };
        paperTradingRef.addValueEventListener(paperAccountListener);
    }

    private void placePaperOrder(boolean buy) {
        String symbol = etPaperSymbol.getText() == null ? ""
                : etPaperSymbol.getText().toString().trim().toUpperCase(Locale.US);
        String sharesText = etPaperShares.getText() == null ? ""
                : etPaperShares.getText().toString().trim();
        if (!symbol.matches("[A-Z0-9.^=_-]{1,20}")) {
            etPaperSymbol.setError("Enter a valid ticker");
            return;
        }
        double shares;
        try {
            shares = Double.parseDouble(sharesText);
        } catch (NumberFormatException error) {
            etPaperShares.setError("Enter a valid share quantity");
            return;
        }
        if (!Double.isFinite(shares) || shares <= 0 || shares > 1_000_000_000) {
            etPaperShares.setError("Share quantity must be greater than zero");
            return;
        }

        hideKeyboard(btnPaperBuy);
        setPaperLoading(true);
        tvPaperMessage.setText("Getting the latest quote for " + symbol + "…");
        fetchPaperQuote(symbol, (price, error) -> {
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) return;
                if (error != null) {
                    setPaperLoading(false);
                    tvPaperMessage.setText("Could not get a quote: " + error.getMessage());
                    return;
                }
                executePaperOrder(symbol, shares, price, buy);
            });
        });
    }

    private interface QuoteCallback {
        void onResult(double price, @Nullable Exception error);
    }

    private void fetchPaperQuote(String symbol, QuoteCallback callback) {
        boolean spx = symbol.equals("SPX") || symbol.equals("^SPX")
                || symbol.equals("GSPC") || symbol.equals("^GSPC");
        String url;
        if (spx) {
            url = "https://query1.finance.yahoo.com/v8/finance/chart/%5EGSPC?range=1d&interval=1m";
        } else {
            String token = BuildConfig.FINNHUB_KEY;
            if (token == null || token.trim().isEmpty()) {
                callback.onResult(0, new IOException("Stock quote service is not configured"));
                return;
            }
            try {
                url = "https://finnhub.io/api/v1/quote?symbol="
                        + URLEncoder.encode(symbol, StandardCharsets.UTF_8.name())
                        + "&token=" + URLEncoder.encode(token, StandardCharsets.UTF_8.name());
            } catch (Exception error) {
                callback.onResult(0, new IOException("Could not prepare quote request", error));
                return;
            }
        }
        Request.Builder requestBuilder = new Request.Builder().url(url);
        if (spx) requestBuilder.header("User-Agent", "Mozilla/5.0");
        QUOTE_CLIENT.newCall(requestBuilder.build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException error) {
                callback.onResult(0, error);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response)
                    throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    callback.onResult(0, new IOException("Quote service returned HTTP "
                            + response.code()));
                    return;
                }
                try {
                    String body = response.body().string();
                    double price;
                    if (spx) {
                        JSONObject chart = new JSONObject(body).getJSONObject("chart");
                        JSONArray results = chart.optJSONArray("result");
                        if (results == null || results.length() == 0) {
                            throw new IOException("No quote is available for " + symbol);
                        }
                        JSONObject meta = results.getJSONObject(0).optJSONObject("meta");
                        price = meta == null ? 0
                                : meta.optDouble("regularMarketPrice", 0);
                    } else {
                        price = new JSONObject(body).optDouble("c", 0);
                    }
                    if (!Double.isFinite(price) || price <= 0) {
                        throw new IOException("Quote service returned an invalid price");
                    }
                    callback.onResult(price, null);
                } catch (Exception error) {
                    callback.onResult(0, error instanceof IOException
                            ? (IOException) error : new IOException("Could not read the quote", error));
                }
            }
        });
    }

    private void executePaperOrder(String symbol, double shares, double price, boolean buy) {
        String positionKey = symbol.replaceAll("[^A-Z0-9_-]", "_");
        String tradeKey = paperTradingRef.child("trades").push().getKey();
        if (tradeKey == null) {
            setPaperLoading(false);
            tvPaperMessage.setText("Could not create trade record");
            return;
        }
        long tradedAt = System.currentTimeMillis();
        paperTradingRef.runTransaction(new Transaction.Handler() {
            private String failure = "Trade could not be completed";

            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData account) {
                Double storedCash = account.child("cash").getValue(Double.class);
                double cash = storedCash == null ? DEFAULT_PAPER_CASH : storedCash;
                MutableData positionData = account.child("positions").child(positionKey);
                PaperPosition position = positionData.getValue(PaperPosition.class);
                double heldShares = position == null ? 0 : position.shares;
                double averagePrice = position == null ? 0 : position.averagePrice;
                double notional = shares * price;
                if (!Double.isFinite(notional) || notional <= 0) {
                    failure = "Trade amount is outside the supported range";
                    return Transaction.abort();
                }

                double realizedPnl = 0;
                if (buy) {
                    if (cash + 0.000001 < notional) {
                        failure = "Not enough virtual cash for this purchase";
                        return Transaction.abort();
                    }
                    double totalShares = heldShares + shares;
                    double newAverage = (heldShares * averagePrice + notional) / totalShares;
                    PaperPosition updated = new PaperPosition(symbol, totalShares,
                            newAverage, price);
                    positionData.setValue(updated);
                    account.child("cash").setValue(cash - notional);
                } else {
                    if (position == null || heldShares + 0.000000001 < shares) {
                        failure = "You do not own enough shares to sell";
                        return Transaction.abort();
                    }
                    realizedPnl = (price - averagePrice) * shares;
                    if (heldShares - shares <= 0.000000001) {
                        positionData.setValue(null);
                    } else {
                        positionData.setValue(new PaperPosition(symbol,
                                heldShares - shares, averagePrice, price));
                    }
                    account.child("cash").setValue(cash + notional);
                }

                PaperTradeRecord trade = new PaperTradeRecord(symbol, buy ? "BUY" : "SELL",
                        shares, price, notional, realizedPnl, tradedAt);
                MutableData trades = account.child("trades");
                trades.child(tradeKey).setValue(trade);
                while (trades.getChildrenCount() > 100) {
                    MutableData oldest = null;
                    long oldestTime = Long.MAX_VALUE;
                    for (MutableData item : trades.getChildren()) {
                        Long time = item.child("executedAt").getValue(Long.class);
                        if (time != null && time < oldestTime) {
                            oldest = item;
                            oldestTime = time;
                        }
                    }
                    if (oldest == null) break;
                    oldest.setValue(null);
                }
                return Transaction.success(account);
            }

            @Override
            public void onComplete(@Nullable DatabaseError error, boolean committed,
                                   @Nullable DataSnapshot snapshot) {
                if (!isAdded()) return;
                setPaperLoading(false);
                if (error != null) {
                    tvPaperMessage.setText("Trade failed: " + error.getMessage());
                } else if (committed) {
                    etPaperSymbol.setText(symbol);
                    tvPaperMessage.setText(String.format(Locale.US,
                            "%s %.6f %s at $%.2f (virtual order)",
                            buy ? "Bought" : "Sold", shares, symbol, price));
                } else {
                    tvPaperMessage.setText(failure);
                }
            }
        });
    }

    private void setPaperLoading(boolean loading) {
        if (btnPaperBuy != null) btnPaperBuy.setEnabled(!loading);
        if (btnPaperSell != null) btnPaperSell.setEnabled(!loading);
    }

    @Override
    public void onDestroyView() {
        if (paperTradingRef != null && paperAccountListener != null) {
            paperTradingRef.removeEventListener(paperAccountListener);
        }
        paperAccountListener = null;
        super.onDestroyView();
    }

    private void runSimulation() {
        String raw = (etAmount.getText() != null) ? etAmount.getText().toString().trim() : "";
        if (TextUtils.isEmpty(raw)) {
            etAmount.setError("Enter investment amount");
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            etAmount.setError("Invalid number");
            return;
        }
        if (!Double.isFinite(amount) || amount <= 0) {
            etAmount.setError("Amount must be greater than 0");
            return;
        }

        Mode mode = (rbSequential != null && rbSequential.isChecked())
                ? Mode.SEQUENTIAL : Mode.PARALLEL;

        setLoading(true);
        if (tvResult != null) tvResult.setText("");

        closedTradesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<StockData> trades = new ArrayList<>();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    StockData data = ds.getValue(StockData.class);
                    if (data != null && data.buyPrice > 0 && data.sellPrice > 0) {
                        trades.add(data);
                    }
                }
                trades.sort((first, second) ->
                        Long.compare(tradeDate(first), tradeDate(second)));
                setLoading(false);

                if (trades.isEmpty()) {
                    if (tvResult != null) {
                        tvResult.setTextColor(Color.GRAY);
                        tvResult.setText(
                            "\u26a0\ufe0f No closed trades with price data found.\n" +
                            "Close a trade to use the simulator."
                        );
                    }
                    return;
                }

                SimulationResult result = simulate(trades, amount, mode);
                showResult(result, mode);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                setLoading(false);
                if (isAdded()) {
                    Toast.makeText(requireContext(), "Error loading trades: " + error.getMessage(),
                            Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private long tradeDate(StockData trade) {
        if (trade.closedAt > 0) return trade.closedAt;
        return trade.openedAt;
    }

    private SimulationResult simulate(List<StockData> trades, double startAmount, Mode mode) {
        int n = trades.size();
        if (n == 0) return new SimulationResult(startAmount, startAmount, 0);

        if (mode == Mode.SEQUENTIAL) {
            double pot = startAmount;
            for (StockData t : trades) {
                pot = pot * (1.0 + safeReturn(returnFraction(t)));
            }
            return new SimulationResult(startAmount, pot, n);
        } else {
            double perTrade = startAmount / n;
            double total    = 0.0;
            for (StockData t : trades) {
                total += perTrade * (1.0 + safeReturn(returnFraction(t)));
            }
            return new SimulationResult(startAmount, total, n);
        }
    }

    private double returnFraction(StockData t) {
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

    private void showResult(SimulationResult r, Mode mode) {
        if (tvResult == null) return;

        boolean isProfit = r.finalAmount >= r.startAmount;
        double  diff     = r.finalAmount - r.startAmount;
        String  sign     = diff >= 0 ? "+" : "-";

        String modeStr   = (mode == Mode.SEQUENTIAL)
                ? "\uD83D\uDD01 Sequential (trade after trade)"
                : "\u23F8 Parallel (equal split)";
        String header    = isProfit ? "\u2705 You would have profited!" : "\u274C You would have lost!";
        String label     = isProfit ? "\uD83D\uDCC8 Profit" : "\uD83D\uDCC9 Loss";

        String text = String.format(Locale.US,
                "%s\n" +
                "\uD83D\uDCCA Mode:             %s\n" +
                "\uD83D\uDCB9 Trades:           %d\n" +
                "\uD83D\uDCB5 Starting amount: $%.2f\n" +
                "\uD83D\uDCB0 Final amount:    $%.2f\n" +
                "%s:          %s$%.2f\n" +
                "\uD83D\uDCCA Return:           %s%.2f%%",
                header, modeStr, r.tradeCount,
                r.startAmount, r.finalAmount,
                label, sign, Math.abs(diff),
                sign, Math.abs(r.percent)
        );

        tvResult.setText(text);
        tvResult.setTextColor(isProfit
                ? Color.parseColor("#00C896")
                : Color.parseColor("#FF4D4D"));
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

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager)
                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }
}
