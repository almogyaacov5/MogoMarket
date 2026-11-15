package com.mogomarket.app;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ArrayAdapter;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Date;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class StocksAdapter extends RecyclerView.Adapter<StocksAdapter.StockViewHolder> {

    public interface OnStockClickListener {
        void onStockClick(String symbol);
        void onStockDelete(String symbol, double sellPrice, String exitReason,
                           double exitFees, double taxes);
        void onStockEdit(StockData updatedStock, String oldSymbol);
        void onManageScreenshots(StockData stock);
        default boolean supportsTradeDetails() { return false; }
    }

    // Callback לעדכון סה"כ הושקע בפורטפוליו
    public interface OnTotalInvestedListener {
        void onTotalInvestedChanged(double totalInvested);
    }

    public interface OnPortfolioSummaryListener {
        void onPortfolioSummaryChanged(double invested, double marketValue, double totalPnl,
                                       double dailyPnl, double cashBalance, int positionCount);
    }

    private final List<StockData> stocks;
    private final OnStockClickListener listener;
    private OnTotalInvestedListener totalInvestedListener;
    private OnPortfolioSummaryListener portfolioSummaryListener;
    private final Map<String, StockViewHolder> visibleHolders = new HashMap<>();
    private double cashBalance;
    private final OkHttpClient client = new OkHttpClient();
    private static final String FINNHUB_KEY = "d918pn9r01qr1uqui560d918pn9r01qr1uqui56g";

    public StocksAdapter(List<StockData> stocks, OnStockClickListener listener) {
        this.stocks = stocks;
        this.listener = listener;
    }

    public void setTotalInvestedListener(OnTotalInvestedListener listener) {
        this.totalInvestedListener = listener;
    }

    public void setPortfolioSummaryListener(OnPortfolioSummaryListener listener) {
        this.portfolioSummaryListener = listener;
        updatePortfolioSummary();
    }

    public void setCashBalance(double cashBalance) {
        this.cashBalance = cashBalance;
        updatePortfolioSummary();
        updatePortfolioAllocations();
    }

    public void updatePortfolioSummary() {
        if (portfolioSummaryListener == null || stocks == null) return;

        double invested = 0;
        double marketValue = 0;
        double totalPnl = 0;
        double dailyPnl = 0;
        for (StockData stock : stocks) {
            if (stock == null) continue;
            if (stock.buyPrice <= 0 || stock.tradeAmount <= 0) continue;

            double currentPrice = stock.currentPrice > 0 ? stock.currentPrice : stock.buyPrice;
            double currentValue = stock.tradeAmount * currentPrice / stock.buyPrice;
            invested += stock.tradeAmount;
            marketValue += currentValue;
            totalPnl += currentValue - stock.tradeAmount;
            dailyPnl += currentValue * (stock.dailyProfitLossPercent / 100.0);
        }
        portfolioSummaryListener.onPortfolioSummaryChanged(
                invested, marketValue, totalPnl, dailyPnl, cashBalance, stocks.size());
    }

    private void updatePortfolioAllocations() {
        if (stocks == null) return;
        double portfolioValue = cashBalance;
        for (StockData stock : stocks) {
            if (stock == null || stock.buyPrice <= 0 || stock.tradeAmount <= 0) continue;
            double currentPrice = stock.currentPrice > 0 ? stock.currentPrice : stock.buyPrice;
            portfolioValue += stock.tradeAmount * currentPrice / stock.buyPrice;
        }

        for (StockData stock : stocks) {
            if (stock == null || stock.symbol == null) continue;
            StockViewHolder holder = visibleHolders.get(stock.symbol);
            if (holder == null) continue;
            if (portfolioValue <= 0 || stock.buyPrice <= 0 || stock.tradeAmount <= 0) {
                holder.allocationRing.post(() -> {
                    holder.allocationRing.setPercent(0, holder.accentColor);
                    holder.allocationRing.setContentDescription("Portfolio allocation unavailable");
                });
                continue;
            }
            double currentPrice = stock.currentPrice > 0 ? stock.currentPrice : stock.buyPrice;
            double currentValue = stock.tradeAmount * currentPrice / stock.buyPrice;
            double percent = currentValue / portfolioValue * 100.0;
            holder.allocationRing.post(() -> {
                holder.allocationRing.setPercent(percent, holder.accentColor);
                holder.allocationRing.setContentDescription(String.format(Locale.US,
                        "Portfolio allocation %.1f percent", percent));
            });
        }
    }

    /** מחשב סה"כ הושקע מכל המניות ומודיע ל-listener */
    public void notifyTotalInvested() {
        if (totalInvestedListener == null || stocks == null) return;
        double total = 0;
        for (StockData s : stocks) {
            total += s.tradeAmount;
        }
        totalInvestedListener.onTotalInvestedChanged(total);
    }

    @NonNull
    @Override
    public StockViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_portfolio, parent, false);
        return new StockViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull StockViewHolder holder, int position) {
        StockData stock = stocks.get(position);
        Context ctx = holder.itemView.getContext();
        if (holder.boundSymbol != null && visibleHolders.get(holder.boundSymbol) == holder) {
            visibleHolders.remove(holder.boundSymbol);
        }
        holder.boundSymbol = stock.symbol;
        if (stock.symbol != null) visibleHolders.put(stock.symbol, holder);

        int colorGain     = ctx.getColor(R.color.gain);
        int colorLoss     = ctx.getColor(R.color.loss);
        int textPrimary   = ctx.getColor(R.color.text_primary);
        int textSecondary = ctx.getColor(R.color.text_secondary);
        int colorNeutral  = textSecondary;

        int accentColor = position % 2 == 1
                ? Color.rgb(255, 184, 77) : Color.rgb(34, 211, 238);
        holder.accentColor = accentColor;
        holder.cardView.setCardBackgroundColor(Color.rgb(13, 17, 25));
        holder.cardView.setStrokeColor(Color.argb(75, Color.red(accentColor),
                Color.green(accentColor), Color.blue(accentColor)));
        holder.trendView.setChangePercent(0, accentColor);
        holder.allocationRing.setPercent(0, accentColor);

        String sym = (stock.symbol != null) ? stock.symbol.trim() : "?";

        String displaySym = CryptoHelper.isCryptoSymbol(sym)
                ? CryptoHelper.getShortName(sym)
                : sym;
        holder.symbolText.setText(displaySym);
        holder.symbolText.setTextColor(accentColor);
        holder.pnlLabel.setTextColor(accentColor);
        if (stock.name != null && !stock.name.trim().isEmpty()) {
            holder.stockNameText.setText(stock.name.trim());
            holder.stockNameText.setVisibility(View.VISIBLE);
        } else {
            holder.stockNameText.setText("");
            holder.stockNameText.setVisibility(View.GONE);
        }
        holder.buyPriceText.setText(String.format(Locale.US, "$%.2f", stock.buyPrice));
        holder.buyPriceText.setTextColor(textSecondary);

        if (stock.targetPrice > 0) {
            holder.targetPriceText.setText(String.format(Locale.US, "$%.2f", stock.targetPrice));
        } else {
            holder.targetPriceText.setText("-");
        }
        holder.targetPriceText.setTextColor(textSecondary);

        holder.stopLossPriceText.setText(stock.stopLossPrice > 0
                ? String.format(Locale.US, "$%.2f", stock.stopLossPrice) : "-");
        holder.stopLossPriceText.setTextColor(textSecondary);

        double riskRewardRatio = StockData.calculateRiskRewardRatio(stock);
        holder.riskRewardText.setText(Double.isFinite(riskRewardRatio)
                ? String.format(Locale.US, "1:%.2f", riskRewardRatio) : "-");
        holder.riskRewardText.setTextColor(textSecondary);

        if (stock.openedAt > 0) {
            long holdingDays = StockData.calculateHoldingDays(
                    stock.openedAt, System.currentTimeMillis());
            String duration = String.format(Locale.US, "%d %s", holdingDays,
                    holdingDays == 1 ? "day" : "days");
            holder.holdingDaysText.setText(duration);
            holder.holdingDaysText.setTextColor(textSecondary);
            holder.holdingDaysText.setContentDescription(
                    duration + " in trade. Tap to edit the open date");
        } else {
            holder.holdingDaysText.setText("Add date");
            holder.holdingDaysText.setTextColor(accentColor);
            holder.holdingDaysText.setContentDescription(
                    "Open date missing. Tap to add it and calculate days in trade");
        }
        holder.holdingDaysText.setOnClickListener(view -> showEditDialog(ctx, stock));

        if (stock.notes != null && !stock.notes.trim().isEmpty()) {
            holder.notesText.setText("\uD83D\uDCDD " + stock.notes.trim());
            holder.notesText.setVisibility(View.VISIBLE);
        } else {
            holder.notesText.setVisibility(View.GONE);
        }
        if (stock.strategyTag != null && !stock.strategyTag.trim().isEmpty()) {
            holder.strategyTagText.setText("Setup: " + stock.strategyTag.trim());
            holder.strategyTagText.setVisibility(View.VISIBLE);
        } else {
            holder.strategyTagText.setVisibility(View.GONE);
        }
        boolean showTradeDetails = listener.supportsTradeDetails();
        holder.sectorText.setVisibility(showTradeDetails ? View.VISIBLE : View.GONE);
        if (showTradeDetails) {
            holder.sectorText.setText(stock.sector == null || stock.sector.trim().isEmpty()
                    ? "Sector: Other" : "Sector: " + stock.sector.trim());
        }

        // הצגת סכום הושקע
        if (stock.tradeAmount > 0) {
            holder.layoutInvested.setVisibility(View.VISIBLE);
        } else {
            holder.layoutInvested.setVisibility(View.GONE);
        }

        holder.currentPriceText.setText("...");
        holder.changePercentText.setText("...");
        holder.changePercentDetailText.setText("...");
        holder.pnlDollarText.setText("-");

        if (!CryptoHelper.isCryptoSymbol(sym)) {
            holder.stockLogoBackground.setImageBitmap(null);
            String logoUrl = "https://static2.finnhub.io/file/publicdatany/finnhubimage/stock_logo/" + sym + ".png";
            Request logoRequest = new Request.Builder().url(logoUrl).build();
            client.newCall(logoRequest).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException error) {
                    Log.w("StocksAdapter", "Unable to load logo for " + sym, error);
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response)
                        throws IOException {
                    if (!response.isSuccessful() || response.body() == null) {
                        response.close();
                        return;
                    }
                    byte[] imageBytes;
                    try (Response closeableResponse = response) {
                        imageBytes = closeableResponse.body().bytes();
                    }
                    BitmapFactory.Options bounds = new BitmapFactory.Options();
                    bounds.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, bounds);
                    int targetSize = dpToPx(ctx, 200);
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = 1;
                    while (bounds.outWidth / (options.inSampleSize * 2) >= targetSize
                            && bounds.outHeight / (options.inSampleSize * 2) >= targetSize) {
                        options.inSampleSize *= 2;
                    }
                    Bitmap bitmap = BitmapFactory.decodeByteArray(
                            imageBytes, 0, imageBytes.length, options);
                    if (bitmap != null) {
                        holder.itemView.post(() -> {
                            if (sym.equals(holder.boundSymbol)) {
                                holder.stockLogoBackground.setImageBitmap(bitmap);
                            } else {
                                bitmap.recycle();
                            }
                        });
                    }
                }
            });
        } else {
            holder.stockLogoBackground.setImageBitmap(null);
        }

        if (CryptoHelper.isCryptoSymbol(sym)) {
            fetchCryptoQuote(sym, stock, holder, colorGain, colorLoss, textPrimary, textSecondary, colorNeutral);
        } else {
            fetchStockQuote(sym, stock, holder, colorGain, colorLoss, textPrimary, textSecondary, colorNeutral);
        }

        holder.btnEdit.setOnClickListener(view -> showEditDialog(ctx, stock));
        holder.btnDelete.setOnClickListener(view -> showSellPriceDialog(ctx, stock));
        holder.btnScreenshots.setVisibility(showTradeDetails ? View.VISIBLE : View.GONE);
        holder.btnScreenshots.setOnClickListener(view -> {
            if (listener.supportsTradeDetails()) listener.onManageScreenshots(stock);
        });
        holder.itemView.setOnClickListener(view -> showTradeDetailsDialog(ctx, stock));
        holder.itemView.setOnLongClickListener(view -> {
            PopupMenu popupMenu = new PopupMenu(ctx, view);
            popupMenu.getMenu().add("Edit position").setOnMenuItemClickListener(item -> {
                showEditDialog(ctx, stock);
                return true;
            });
            popupMenu.getMenu().add("Close position").setOnMenuItemClickListener(item -> {
                showSellPriceDialog(ctx, stock);
                return true;
            });
            if (listener.supportsTradeDetails()) {
                popupMenu.getMenu().add("Manage screenshots").setOnMenuItemClickListener(item -> {
                    listener.onManageScreenshots(stock);
                    return true;
                });
            }
            popupMenu.show();
            return true;
        });
        updatePortfolioAllocations();
    }

    @Override
    public int getItemCount() { return stocks != null ? stocks.size() : 0; }

    private void showTradeDetailsDialog(Context context, StockData stock) {
        String symbol = stock.symbol == null ? "Trade" : stock.symbol.trim();
        String displaySymbol = CryptoHelper.isCryptoSymbol(symbol)
                ? CryptoHelper.getShortName(symbol) : symbol;
        double currentPrice = stock.currentPrice > 0 ? stock.currentPrice : stock.buyPrice;
        double changePercent = stock.buyPrice > 0
                ? (currentPrice - stock.buyPrice) / stock.buyPrice * 100.0 : 0;
        double pnl = stock.tradeAmount > 0 && stock.buyPrice > 0
                ? stock.tradeAmount * changePercent / 100.0 : 0;
        double currentValue = stock.tradeAmount > 0 && stock.buyPrice > 0
                ? stock.tradeAmount * currentPrice / stock.buyPrice : 0;
        double shareCount = stock.shares > 0 ? stock.shares
                : stock.buyPrice > 0 ? stock.tradeAmount / stock.buyPrice : 0;
        double riskReward = StockData.calculateRiskRewardRatio(stock);
        String openedDate = stock.openedAt > 0
                ? android.text.format.DateFormat.getDateFormat(context)
                        .format(new Date(stock.openedAt)) : "Not set";
        String holdingDuration = stock.openedAt > 0
                ? StockData.calculateHoldingDays(stock.openedAt, System.currentTimeMillis())
                        + " days" : "Not available";

        LinearLayout details = new LinearLayout(context);
        details.setOrientation(LinearLayout.VERTICAL);
        int horizontalPadding = dpToPx(context, 20);
        int verticalPadding = dpToPx(context, 8);
        details.setPadding(horizontalPadding, verticalPadding,
                horizontalPadding, verticalPadding);

        if (stock.name != null && !stock.name.trim().isEmpty()) {
            TextView name = new TextView(context);
            name.setText(stock.name.trim());
            name.setTextColor(context.getColor(R.color.text_secondary));
            name.setTextSize(13);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            nameParams.bottomMargin = dpToPx(context, 8);
            details.addView(name, nameParams);
        }

        addTradeDetailRow(context, details, "Current price",
                String.format(Locale.US, "$%.2f", currentPrice));
        addTradeDetailRow(context, details, "Daily change",
                String.format(Locale.US, "%+.2f%%", stock.dailyProfitLossPercent));
        addTradeDetailRow(context, details, "Change vs. entry",
                String.format(Locale.US, "%+.2f%%", changePercent));
        addTradeDetailRow(context, details, "Entry price",
                String.format(Locale.US, "$%.2f", stock.buyPrice));
        addTradeDetailRow(context, details, "Target price",
                stock.targetPrice > 0 ? String.format(Locale.US, "$%.2f", stock.targetPrice) : "—");
        addTradeDetailRow(context, details, "Stop loss",
                stock.stopLossPrice > 0
                        ? String.format(Locale.US, "$%.2f", stock.stopLossPrice) : "—");
        addTradeDetailRow(context, details, "Shares",
                shareCount > 0 ? String.format(Locale.US, "%.4f", shareCount) : "—");
        addTradeDetailRow(context, details, "Invested",
                stock.tradeAmount > 0 ? String.format(Locale.US, "$%,.2f", stock.tradeAmount) : "—");
        addTradeDetailRow(context, details, "Current value",
                currentValue > 0 ? String.format(Locale.US, "$%,.2f", currentValue) : "—");
        addTradeDetailRow(context, details, "Unrealized P&L",
                stock.tradeAmount > 0
                        ? String.format(Locale.US, "%s$%,.2f (%+.2f%%)",
                                pnl >= 0 ? "+" : "-", Math.abs(pnl), changePercent)
                        : "—",
                pnl >= 0 ? context.getColor(R.color.gain) : context.getColor(R.color.loss));
        addTradeDetailRow(context, details, "Opened",
                openedDate + (stock.openedAt > 0 ? " · " + holdingDuration : ""));
        addTradeDetailRow(context, details, "Entry fees",
                String.format(Locale.US, "$%.2f", stock.entryFees));
        addTradeDetailRow(context, details, "Exit fees / taxes",
                String.format(Locale.US, "$%.2f / $%.2f", stock.exitFees, stock.taxes));
        addTradeDetailRow(context, details, "Risk / reward",
                Double.isFinite(riskReward)
                        ? String.format(Locale.US, "1:%.2f", riskReward) : "—");
        addTradeDetailRow(context, details, "Sector",
                stock.sector == null || stock.sector.trim().isEmpty()
                        ? "Other" : stock.sector.trim());
        addTradeDetailRow(context, details, "Strategy",
                stock.strategyTag == null || stock.strategyTag.trim().isEmpty()
                        ? "—" : stock.strategyTag.trim());

        if (stock.notes != null && !stock.notes.trim().isEmpty()) {
            TextView notes = new TextView(context);
            notes.setText("Notes\n" + stock.notes.trim());
            notes.setTextColor(context.getColor(R.color.text_primary));
            notes.setTextSize(14);
            notes.setPadding(0, dpToPx(context, 12), 0, dpToPx(context, 8));
            details.addView(notes);
        }
        int screenshotCount = stock.screenshotPaths == null ? 0 : stock.screenshotPaths.size();
        if (stock.screenshotUrls != null) {
            screenshotCount = Math.max(screenshotCount, stock.screenshotUrls.size());
        }
        addTradeDetailRow(context, details, "Screenshots", String.valueOf(screenshotCount));

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(false);
        scrollView.addView(details);

        new AlertDialog.Builder(context)
                .setTitle(displaySymbol + " · Trade details")
                .setView(scrollView)
                .setPositiveButton("View chart",
                        (dialog, which) -> listener.onStockClick(stock.symbol))
                .setNegativeButton("Close", null)
                .show();
    }

    private void addTradeDetailRow(Context context, LinearLayout parent,
                                   String label, String value) {
        addTradeDetailRow(context, parent, label, value,
                context.getColor(R.color.text_primary));
    }

    private void addTradeDetailRow(Context context, LinearLayout parent,
                                   String label, String value, int valueColor) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);

        TextView labelView = new TextView(context);
        labelView.setText(label);
        labelView.setTextColor(context.getColor(R.color.text_secondary));
        labelView.setTextSize(13);
        row.addView(labelView, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView valueView = new TextView(context);
        valueView.setText(value);
        valueView.setTextColor(valueColor);
        valueView.setTextSize(13);
        valueView.setTypeface(null, android.graphics.Typeface.BOLD);
        valueView.setGravity(android.view.Gravity.END);
        row.addView(valueView, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        parent.addView(row);
        View divider = new View(context);
        divider.setBackgroundColor(context.getColor(R.color.colorDivider));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(context, 1));
        dividerParams.topMargin = dpToPx(context, 7);
        dividerParams.bottomMargin = dpToPx(context, 7);
        parent.addView(divider, dividerParams);
    }

    @Override
    public void onViewRecycled(@NonNull StockViewHolder holder) {
        if (holder.boundSymbol != null && visibleHolders.get(holder.boundSymbol) == holder) {
            visibleHolders.remove(holder.boundSymbol);
        }
        super.onViewRecycled(holder);
    }

    // ========================= FETCH: קריפטו (Binance) =========================

    private void fetchCryptoQuote(String symbol, StockData stock, StockViewHolder holder,
                                  int colorGain, int colorLoss, int textPrimary,
                                  int textSecondary, int colorNeutral) {
        String pair = CryptoHelper.getPair(symbol);
        String urlTicker = "https://api.binance.com/api/v3/ticker/24hr?symbol=" + pair;
        client.newCall(new Request.Builder().url(urlTicker).build()).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                holder.currentPriceText.post(() -> { holder.currentPriceText.setText("?"); holder.currentPriceText.setTextColor(colorNeutral); });
                holder.changePercentText.post(() -> holder.changePercentText.setText("N/A"));
                holder.changePercentDetailText.post(() -> holder.changePercentDetailText.setText("N/A"));
            }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    JSONObject obj = new JSONObject(response.body().string());
                    float currentPrice       = (float) obj.getDouble("lastPrice");
                    float dailyChangePercent = (float) obj.getDouble("priceChangePercent");
                    if (currentPrice == 0f) return;
                    updateHolderWithPrices(holder, stock, currentPrice, dailyChangePercent,
                            colorGain, colorLoss, textPrimary, textSecondary, colorNeutral);
                } catch (Exception ignored) {}
            }
        });
    }

    // ========================= FETCH: מניה (Finnhub) =========================

    private void fetchStockQuote(String symbol, StockData stock, StockViewHolder holder,
                                 int colorGain, int colorLoss, int textPrimary,
                                 int textSecondary, int colorNeutral) {
        if ("SPX".equalsIgnoreCase(symbol) || "^SPX".equalsIgnoreCase(symbol)
                || "GSPC".equalsIgnoreCase(symbol) || "^GSPC".equalsIgnoreCase(symbol)) {
            fetchSpxQuote(stock, holder, colorGain, colorLoss,
                    textPrimary, textSecondary, colorNeutral);
            return;
        }
        String encodedSymbol = URLEncoder.encode(symbol, StandardCharsets.UTF_8);
        String url = "https://finnhub.io/api/v1/quote?symbol=" + encodedSymbol
                + "&token=" + BuildConfig.FINNHUB_KEY;
        client.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                holder.currentPriceText.post(() -> { holder.currentPriceText.setText("?"); holder.currentPriceText.setTextColor(colorNeutral); });
                holder.changePercentText.post(() -> holder.changePercentText.setText("N/A"));
                holder.changePercentDetailText.post(() -> holder.changePercentDetailText.setText("N/A"));
            }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    if (!response.isSuccessful() || response.body() == null) {
                        showUnavailableQuote(holder, symbol, colorNeutral);
                        return;
                    }
                    JSONObject obj = new JSONObject(response.body().string());
                    float currentPrice       = (float) obj.getDouble("c");
                    float previousClose = (float) obj.getDouble("pc");
                    float dailyChangePercent = (float) DailyQuoteMath.percentChange(
                            currentPrice, previousClose);
                    if (currentPrice <= 0f || !Float.isFinite(dailyChangePercent)) {
                        showUnavailableQuote(holder, symbol, colorNeutral);
                        return;
                    }
                    updateHolderWithPrices(holder, stock, currentPrice, dailyChangePercent,
                            colorGain, colorLoss, textPrimary, textSecondary, colorNeutral);
                } catch (Exception error) {
                    Log.w("StocksAdapter", "Could not parse quote for " + symbol, error);
                    showUnavailableQuote(holder, symbol, colorNeutral);
                } finally {
                    response.close();
                }
            }
        });
    }

    private void fetchSpxQuote(StockData stock, StockViewHolder holder,
                               int colorGain, int colorLoss, int textPrimary,
                               int textSecondary, int colorNeutral) {
        String encodedSymbol = URLEncoder.encode("^GSPC", StandardCharsets.UTF_8);
        String url = "https://query1.finance.yahoo.com/v8/finance/chart/" + encodedSymbol
                + "?range=5d&interval=1d&includePrePost=false";
        client.newCall(new Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build())
                .enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException error) {
                        showUnavailableQuote(holder, stock.symbol, colorNeutral);
                        Log.w("StocksAdapter", "Failed to fetch SPX quote", error);
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response)
                            throws IOException {
                        if (!response.isSuccessful() || response.body() == null) {
                            showUnavailableQuote(holder, stock.symbol, colorNeutral);
                            Log.w("StocksAdapter", "SPX quote request failed: HTTP "
                                    + response.code());
                            return;
                        }
                        try {
                            JSONObject chart = new JSONObject(response.body().string())
                                    .getJSONObject("chart");
                            org.json.JSONArray results = chart.optJSONArray("result");
                            if (results == null || results.length() == 0) {
                                showUnavailableQuote(holder, stock.symbol, colorNeutral);
                                return;
                            }
                            JSONObject item = results.getJSONObject(0);
                            org.json.JSONArray closes = item
                                    .getJSONObject("indicators").getJSONArray("quote")
                                    .getJSONObject(0).getJSONArray("close");
                            float last = Float.NaN;
                            YahooQuoteMath.Quote quote = YahooQuoteMath.fromChartItem(item);
                            last = (float) quote.currentPrice;
                            if (!Float.isFinite(last) || last <= 0f) {
                                showUnavailableQuote(holder, stock.symbol, colorNeutral);
                                return;
                            }
                            float dailyChange = (float) quote.percentChange();
                            if (!Float.isFinite(dailyChange)) {
                                showUnavailableQuote(holder, stock.symbol, colorNeutral);
                                return;
                            }
                            updateHolderWithPrices(holder, stock, last, dailyChange,
                                    colorGain, colorLoss, textPrimary, textSecondary, colorNeutral);
                        } catch (Exception error) {
                            showUnavailableQuote(holder, stock.symbol, colorNeutral);
                            Log.w("StocksAdapter", "Could not parse SPX quote", error);
                        }
                    }
                });
    }

    private void showUnavailableQuote(StockViewHolder holder, String targetSymbol,
                                      int colorNeutral) {
        holder.itemView.post(() -> {
            if (targetSymbol == null || !targetSymbol.equals(holder.boundSymbol)) return;
            holder.currentPriceText.setText("?");
            holder.currentPriceText.setTextColor(colorNeutral);
            holder.changePercentText.setText("N/A");
            holder.changePercentDetailText.setText("N/A");
        });
    }

    // ========================= עדכון ה-UI =========================

    private void updateHolderWithPrices(StockViewHolder holder, StockData stock,
                                        float currentPrice, float dailyChangePercent,
                                        int colorGain, int colorLoss,
                                        int textPrimary, int textSecondary, int colorNeutral) {
        stock.currentPrice = currentPrice;
        stock.dailyProfitLossPercent = dailyChangePercent;
        if (stock.symbol == null || !stock.symbol.equals(holder.boundSymbol)) return;
        float totalChangePercent = (stock.buyPrice != 0f)
                ? ((currentPrice - stock.buyPrice) / stock.buyPrice * 100f) : 0f;

        double pnlDollar = (stock.tradeAmount > 0)
                ? stock.tradeAmount * (totalChangePercent / 100.0) : 0;

        double currentValue    = (stock.tradeAmount > 0) ? stock.tradeAmount * (1 + totalChangePercent / 100.0) : 0;
        double dailyPnlDollar  = (stock.tradeAmount > 0) ? currentValue * (dailyChangePercent / 100.0) : 0;

        int gainLossColor      = totalChangePercent >= 0 ? colorGain : colorLoss;
        int dailyGainLossColor = dailyChangePercent >= 0 ? colorGain : colorLoss;

        String dailySign = dailyChangePercent >= 0 ? "+" : "-";
        String totalSign = totalChangePercent  >= 0 ? "+" : "-";

        holder.currentPriceText.post(() -> {
            holder.currentPriceText.setText(String.format(Locale.US, "$%.2f", currentPrice));
            holder.currentPriceText.setTextColor(textPrimary);
        });

        holder.changePercentText.post(() -> {
            String dailyDollarSign = dailyPnlDollar >= 0 ? "+" : "-";
            String dailyText;
            if (stock.tradeAmount > 0) {
                dailyText = String.format(Locale.US, "%s%.2f%% (%s$%.2f) Today",
                        dailySign, Math.abs(dailyChangePercent),
                        dailyDollarSign, Math.abs(dailyPnlDollar));
            } else {
                dailyText = String.format(Locale.US, "%s%.2f%% Today",
                        dailySign, Math.abs(dailyChangePercent));
            }
            holder.itemView.post(() -> {
                updatePortfolioSummary();
                updatePortfolioAllocations();
            });
            holder.changePercentText.setText(dailyText);
            holder.changePercentText.setTextColor(dailyGainLossColor);
        });

        holder.changePercentDetailText.post(() -> {
            holder.changePercentDetailText.setText(
                    String.format(Locale.US, "%s%.2f%% vs Entry", totalSign, Math.abs(totalChangePercent)));
            holder.changePercentDetailText.setTextColor(gainLossColor);
        });
        holder.trendView.post(() -> holder.trendView.setChangePercent(
                totalChangePercent, holder.accentColor));

        if (stock.tradeAmount > 0) {
            holder.pnlDollarText.post(() -> {
                String sign = pnlDollar >= 0 ? "+" : "-";
                holder.pnlDollarText.setText(
                        String.format(Locale.US, "%s$%.2f", sign, Math.abs(pnlDollar)));
                holder.pnlDollarText.setTextColor(pnlDollar >= 0
                        ? holder.accentColor : colorLoss);
            });
        } else {
            holder.pnlDollarText.post(() -> {
                holder.pnlDollarText.setText("-");
                holder.pnlDollarText.setTextColor(colorNeutral);
            });
        }
    }

    // ========================= EDIT DIALOG =========================

    private void showEditDialog(Context context, StockData stock) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        boolean isCrypto = CryptoHelper.isCryptoSymbol(stock.symbol);
        builder.setTitle("\u270f\ufe0f Edit " + (isCrypto ? CryptoHelper.getShortName(stock.symbol) : stock.symbol));

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dpToPx(context, 16);
        layout.setPadding(pad, pad, pad, pad);

        layout.addView(makeLabel(context, "Ticker (Symbol)"));
        EditText etSymbol = makeEditText(context, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        etSymbol.setText(stock.symbol != null ? stock.symbol : "");
        layout.addView(etSymbol);

        layout.addView(makeLabel(context, "Company Name (optional)"));
        EditText etName = makeEditText(context, InputType.TYPE_CLASS_TEXT);
        etName.setText(stock.name != null ? stock.name : "");
        layout.addView(etName);

        layout.addView(makeLabel(context, "Entry Price ($)"));
        EditText etBuyPrice = makeEditText(context, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etBuyPrice.setText(stock.buyPrice > 0 ? String.format(Locale.US, "%.2f", stock.buyPrice) : "");
        layout.addView(etBuyPrice);

        layout.addView(makeLabel(context, "Investment Amount ($)"));
        EditText etAmount = makeEditText(context, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etAmount.setText(stock.tradeAmount > 0 ? String.format(Locale.US, "%.2f", stock.tradeAmount) : "");
        layout.addView(etAmount);

        layout.addView(makeLabel(context, "Target Price ($) - optional"));
        EditText etTarget = makeEditText(context, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etTarget.setText(stock.targetPrice > 0 ? String.format(Locale.US, "%.2f", stock.targetPrice) : "");
        layout.addView(etTarget);

        layout.addView(makeLabel(context, "Stop Loss ($) - optional"));
        EditText etStopLoss = makeEditText(context, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etStopLoss.setText(stock.stopLossPrice > 0 ? String.format(Locale.US, "%.2f", stock.stopLossPrice) : "");
        layout.addView(etStopLoss);

        layout.addView(makeLabel(context, "Entry Fees ($)"));
        EditText etEntryFees = makeEditText(context,
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etEntryFees.setText(stock.entryFees > 0 ? String.format(Locale.US, "%.2f", stock.entryFees) : "");
        layout.addView(etEntryFees);

        layout.addView(makeLabel(context, "Open Date"));
        Calendar selectedOpenDate = Calendar.getInstance();
        if (stock.openedAt > 0) selectedOpenDate.setTimeInMillis(stock.openedAt);
        selectedOpenDate.set(Calendar.HOUR_OF_DAY, 0);
        selectedOpenDate.set(Calendar.MINUTE, 0);
        selectedOpenDate.set(Calendar.SECOND, 0);
        selectedOpenDate.set(Calendar.MILLISECOND, 0);
        TextView openDateButton = new TextView(context);
        openDateButton.setTextSize(16);
        openDateButton.setPadding(dpToPx(context, 12), dpToPx(context, 12),
                dpToPx(context, 12), dpToPx(context, 12));
        openDateButton.setBackgroundColor(context.getColor(R.color.bg_card));
        updateOpenDateText(context, openDateButton, selectedOpenDate.getTimeInMillis());
        openDateButton.setOnClickListener(view -> {
            DatePickerDialog picker = new DatePickerDialog(context,
                    (datePicker, year, month, dayOfMonth) -> {
                        selectedOpenDate.set(year, month, dayOfMonth, 0, 0, 0);
                        selectedOpenDate.set(Calendar.MILLISECOND, 0);
                        updateOpenDateText(context, openDateButton,
                                selectedOpenDate.getTimeInMillis());
                    }, selectedOpenDate.get(Calendar.YEAR), selectedOpenDate.get(Calendar.MONTH),
                    selectedOpenDate.get(Calendar.DAY_OF_MONTH));
            picker.getDatePicker().setMaxDate(System.currentTimeMillis());
            picker.show();
        });
        layout.addView(openDateButton);

        layout.addView(makeLabel(context, "Strategy / Setup"));
        Spinner strategySpinner = new Spinner(context);
        String[] strategies = {"None", "Breakout", "Pullback", "EMA Bounce",
                "Earnings Play", "Other"};
        ArrayAdapter<String> strategyAdapter = new ArrayAdapter<>(
                context, android.R.layout.simple_spinner_item, strategies);
        strategyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        strategySpinner.setAdapter(strategyAdapter);
        int strategyPosition = java.util.Arrays.asList(strategies)
                .indexOf(stock.strategyTag == null ? "" : stock.strategyTag);
        strategySpinner.setSelection(Math.max(0, strategyPosition));
        layout.addView(strategySpinner);

        layout.addView(makeLabel(context, "Notes / Trade Reason - optional"));
        EditText etNotes = makeEditText(context, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        etNotes.setLines(2);
        etNotes.setMaxLines(3);
        etNotes.setText(stock.notes != null ? stock.notes : "");
        layout.addView(etNotes);

        layout.addView(makeLabel(context, "Sector"));
        Spinner sectorSpinner = new Spinner(context);
        String[] sectors = {"Other", "Technology", "Healthcare", "Financials",
                "Consumer Discretionary", "Consumer Staples", "Industrials", "Energy",
                "Materials", "Utilities", "Real Estate", "Communication Services"};
        ArrayAdapter<String> sectorAdapter = new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_item, sectors);
        sectorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sectorSpinner.setAdapter(sectorAdapter);
        int sectorPosition = java.util.Arrays.asList(sectors)
                .indexOf(stock.sector == null ? "Other" : stock.sector);
        sectorSpinner.setSelection(Math.max(0, sectorPosition));
        layout.addView(sectorSpinner);

        builder.setView(layout);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String rawSym = etSymbol.getText().toString().trim();
            String newSymbol = CryptoHelper.isCryptoSymbol(rawSym)
                    ? rawSym.trim()
                    : rawSym.toUpperCase(Locale.US);
            if (newSymbol.isEmpty()) {
                Toast.makeText(context, "Ticker cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }

            float buyPrice;
            double tradeAmount;
            float targetPrice;
            float stopLossPrice;
            double entryFees;
            try {
                String buyStr = etBuyPrice.getText().toString().trim();
                String amountStr = etAmount.getText().toString().trim();
                String targetStr = etTarget.getText().toString().trim();
                String stopLossStr = etStopLoss.getText().toString().trim();
                String entryFeesStr = etEntryFees.getText().toString().trim();
                buyPrice = buyStr.isEmpty() ? 0f : Float.parseFloat(buyStr);
                tradeAmount = amountStr.isEmpty() ? 0 : Double.parseDouble(amountStr);
                targetPrice = targetStr.isEmpty() ? 0f : Float.parseFloat(targetStr);
                stopLossPrice = stopLossStr.isEmpty() ? 0f : Float.parseFloat(stopLossStr);
                entryFees = entryFeesStr.isEmpty() ? 0 : Double.parseDouble(entryFeesStr);
            } catch (NumberFormatException e) {
                Toast.makeText(context, "Enter valid numeric trade values", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!Float.isFinite(buyPrice) || buyPrice <= 0
                    || !Double.isFinite(tradeAmount) || tradeAmount < 0
                    || !Float.isFinite(targetPrice) || targetPrice < 0
                    || !Float.isFinite(stopLossPrice) || stopLossPrice < 0
                    || !Double.isFinite(entryFees) || entryFees < 0) {
                Toast.makeText(context, "Enter valid non-negative trade values", Toast.LENGTH_SHORT).show();
                return;
            }
            if (targetPrice > 0 && targetPrice <= buyPrice) {
                Toast.makeText(context, "Target price must be above entry price", Toast.LENGTH_SHORT).show();
                return;
            }
            if (stopLossPrice > 0 && stopLossPrice >= buyPrice) {
                Toast.makeText(context, "Stop loss must be below entry price", Toast.LENGTH_SHORT).show();
                return;
            }
            if (selectedOpenDate.getTimeInMillis() > System.currentTimeMillis()) {
                Toast.makeText(context, "Open date cannot be in the future", Toast.LENGTH_SHORT).show();
                return;
            }

            String oldSymbol  = stock.symbol;
            stock.symbol      = newSymbol;
            stock.name        = etName.getText().toString().trim();
            stock.buyPrice    = buyPrice;
            stock.tradeAmount = tradeAmount;
            stock.targetPrice = targetPrice;
            stock.stopLossPrice = stopLossPrice;
            stock.entryFees = entryFees;
            stock.openedAt = selectedOpenDate.getTimeInMillis();
            String selectedStrategy = strategySpinner.getSelectedItem().toString();
            stock.strategyTag = "None".equals(selectedStrategy) ? "" : selectedStrategy;
            stock.sector = CryptoHelper.isCryptoSymbol(stock.symbol) ? "Cryptocurrency"
                    : sectorSpinner.getSelectedItem().toString();
            stock.notes       = etNotes.getText().toString().trim();

            listener.onStockEdit(stock, oldSymbol);
            notifyDataSetChanged();
            notifyTotalInvested();
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    // ========================= HELPERS =========================

    private TextView makeLabel(Context context, String text) {
        TextView tv = new TextView(context);
        tv.setText(text);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dpToPx(context, 10);
        tv.setLayoutParams(lp);
        return tv;
    }

    private void updateOpenDateText(Context context, TextView view, long openedAt) {
        String date = openedAt > 0
                ? android.text.format.DateFormat.getDateFormat(context).format(new Date(openedAt))
                : "Select date";
        view.setText("Open date: " + date);
        view.setContentDescription("Open date: " + date + ". Tap to change");
        view.setTextColor(context.getColor(R.color.text_primary));
    }

    private EditText makeEditText(Context context, int inputType) {
        EditText et = new EditText(context);
        et.setInputType(inputType);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dpToPx(context, 4);
        et.setLayoutParams(lp);
        return et;
    }

    public interface QuoteCallback {
        void onQuoteReceived(float currentPrice, float dailyChangePercent);
        void onError(Exception e);
    }

    public interface PriceCallback {
        void onPriceReceived(float price);
        void onError(Exception e);
    }

    static class StockViewHolder extends RecyclerView.ViewHolder {
        TextView symbolText, currentPriceText, changePercentText;
        TextView changePercentDetailText, buyPriceText, targetPriceText, stopLossPriceText;
        TextView riskRewardText, holdingDaysText, pnlDollarText, stockNameText, notesText;
        TextView strategyTagText;
        TextView sectorText;
        TextView pnlLabel;
        MaterialCardView cardView;
        PortfolioTrendView trendView;
        PortfolioAllocationRingView allocationRing;
        LinearLayout layoutInvested;
        ImageButton btnEdit, btnDelete;
        ImageButton btnScreenshots;
        ImageView stockLogoBackground;
        String boundSymbol;
        int accentColor;

        public StockViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView                = (MaterialCardView) itemView;
            trendView               = itemView.findViewById(R.id.stockTrend);
            allocationRing          = itemView.findViewById(R.id.portfolioAllocationRing);
            pnlLabel                = itemView.findViewById(R.id.stockPnlLabel);
            symbolText              = itemView.findViewById(R.id.stockSymbol);
            currentPriceText        = itemView.findViewById(R.id.stockCurrentPrice);
            changePercentText       = itemView.findViewById(R.id.stockChangePercent);
            changePercentDetailText = itemView.findViewById(R.id.stockChangePercentDetail);
            buyPriceText            = itemView.findViewById(R.id.stockBuyPrice);
            targetPriceText         = itemView.findViewById(R.id.stockTargetPrice);
            stopLossPriceText       = itemView.findViewById(R.id.stockStopLossPrice);
            riskRewardText          = itemView.findViewById(R.id.stockRiskReward);
            holdingDaysText         = itemView.findViewById(R.id.stockHoldingDays);
            pnlDollarText           = itemView.findViewById(R.id.stockPnlDollar);
            stockNameText           = itemView.findViewById(R.id.stockName);
            notesText               = itemView.findViewById(R.id.stockNotes);
            strategyTagText         = itemView.findViewById(R.id.stockStrategyTag);
            sectorText = itemView.findViewById(R.id.stockSector);
            layoutInvested          = itemView.findViewById(R.id.layoutInvested);
            btnEdit                 = itemView.findViewById(R.id.btnEditStock);
            btnDelete               = itemView.findViewById(R.id.btnDeleteStock);
            btnScreenshots = itemView.findViewById(R.id.btnStockScreenshots);
            stockLogoBackground     = itemView.findViewById(R.id.stockLogoBackground);
        }
    }

    public void refreshPrices() { notifyDataSetChanged(); }

    private void showSellPriceDialog(Context context, StockData stock) {
        String symbol = stock.symbol;
        boolean isCrypto = CryptoHelper.isCryptoSymbol(symbol);
        String displaySym = isCrypto ? CryptoHelper.getShortName(symbol) : symbol;
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Close Position - " + displaySym);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = dpToPx(context, 20);
        content.setPadding(padding, dpToPx(context, 8), padding, 0);
        final EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setHint("Exit price ($)");
        content.addView(input);
        EditText exitFeesInput = new EditText(context);
        exitFeesInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        exitFeesInput.setHint("Exit fees ($) - optional");
        content.addView(exitFeesInput);
        EditText taxesInput = new EditText(context);
        taxesInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        taxesInput.setHint("Taxes paid ($) - optional");
        content.addView(taxesInput);
        Spinner exitReasonSpinner = new Spinner(context);
        String[] exitReasons = {"Manual Exit", "Hit TP", "Hit SL", "Trailing Stop"};
        ArrayAdapter<String> reasonAdapter = new ArrayAdapter<>(
                context, android.R.layout.simple_spinner_item, exitReasons);
        reasonAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        exitReasonSpinner.setAdapter(reasonAdapter);
        if ("HIT_TP".equals(stock.exitReason)) exitReasonSpinner.setSelection(1);
        else if ("HIT_SL".equals(stock.exitReason)) exitReasonSpinner.setSelection(2);
        else if ("TRAILING_STOP".equals(stock.exitReason)) exitReasonSpinner.setSelection(3);
        content.addView(exitReasonSpinner);
        builder.setView(content);
        builder.setPositiveButton("Close Trade", null);
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String priceStr = input.getText().toString().trim();
                    double sellPrice;
                    try {
                        sellPrice = Double.parseDouble(priceStr);
                    } catch (NumberFormatException e) {
                        input.setError("Enter a valid exit price");
                        return;
                    }
                    if (!Double.isFinite(sellPrice) || sellPrice <= 0) {
                        input.setError("Exit price must be greater than zero");
                        return;
                    }
                    double exitFees;
                    double taxes;
                    try {
                        exitFees = exitFeesInput.getText().toString().trim().isEmpty()
                                ? 0 : Double.parseDouble(exitFeesInput.getText().toString().trim());
                        taxes = taxesInput.getText().toString().trim().isEmpty()
                                ? 0 : Double.parseDouble(taxesInput.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        taxesInput.setError("Enter valid fees and taxes");
                        return;
                    }
                    if (!Double.isFinite(exitFees) || exitFees < 0
                            || !Double.isFinite(taxes) || taxes < 0) {
                        taxesInput.setError("Fees and taxes cannot be negative");
                        return;
                    }
                    String[] reasonCodes = {"MANUAL_EXIT", "HIT_TP", "HIT_SL", "TRAILING_STOP"};
                    listener.onStockDelete(symbol, sellPrice,
                            reasonCodes[exitReasonSpinner.getSelectedItemPosition()],
                            exitFees, taxes);
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private int dpToPx(Context context, int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }
}
