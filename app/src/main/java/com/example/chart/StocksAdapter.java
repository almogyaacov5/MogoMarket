package com.example.chart;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class StocksAdapter extends RecyclerView.Adapter<StocksAdapter.StockViewHolder> {

    public interface OnStockClickListener {
        void onStockClick(String symbol);
        void onStockDelete(String symbol, double sellPrice);
        void onStockEdit(StockData updatedStock, String oldSymbol);
    }

    private final List<StockData> stocks;
    private final OnStockClickListener listener;
    private final OkHttpClient client = new OkHttpClient();
    private static final String FINNHUB_KEY = "d918pn9r01qr1uqui560d918pn9r01qr1uqui56g";

    public StocksAdapter(List<StockData> stocks, OnStockClickListener listener) {
        this.stocks = stocks;
        this.listener = listener;
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

        int colorGain     = ctx.getColor(R.color.gain);
        int colorLoss     = ctx.getColor(R.color.loss);
        int textPrimary   = ctx.getColor(R.color.text_primary);
        int textSecondary = ctx.getColor(R.color.text_secondary);
        int bgCard        = ctx.getColor(R.color.bg_card);
        int colorNeutral  = textSecondary;

        holder.itemView.setBackgroundColor(bgCard);

        String sym = (stock.symbol != null) ? stock.symbol.trim() : "?";

        // תצוגה: קריפטו -> קצר שם (למשל "BTC"), מניה -> כרגיל
        String displaySym = CryptoHelper.isCryptoSymbol(sym)
                ? CryptoHelper.getShortName(sym)
                : sym;
        holder.symbolText.setText(displaySym);
        holder.symbolText.setTextColor(textPrimary);
        holder.buyPriceText.setText(String.format(Locale.US, "$%.2f", stock.buyPrice));
        holder.buyPriceText.setTextColor(textSecondary);

        if (stock.targetPrice > 0) {
            holder.targetPriceText.setText(String.format(Locale.US, "$%.2f", stock.targetPrice));
        } else {
            holder.targetPriceText.setText("-");
        }
        holder.targetPriceText.setTextColor(textSecondary);

        if (stock.notes != null && !stock.notes.trim().isEmpty()) {
            holder.notesText.setText("\uD83D\uDCDD " + stock.notes.trim());
            holder.notesText.setVisibility(View.VISIBLE);
        } else {
            holder.notesText.setVisibility(View.GONE);
        }

        holder.currentPriceText.setText("...");
        holder.changePercentText.setText("...");
        holder.changePercentDetailText.setText("...");
        holder.pnlDollarText.setText("-");

        // לוגו: לקריפטו אין לוגו מפינהב, למניות טוען
        if (!CryptoHelper.isCryptoSymbol(sym)) {
            holder.stockLogoBackground.setImageBitmap(null);
            String logoUrl = "https://static2.finnhub.io/file/publicdatany/finnhubimage/stock_logo/" + sym + ".png";
            new Thread(() -> {
                try {
                    Bitmap bitmap = BitmapFactory.decodeStream(new URL(logoUrl).openStream());
                    if (bitmap != null) {
                        holder.itemView.post(() -> holder.stockLogoBackground.setImageBitmap(bitmap));
                    }
                } catch (Exception ignored) {}
            }).start();
        } else {
            holder.stockLogoBackground.setImageBitmap(null);
        }

        // שליפת מחיר: קריפטו -> Binance, מניה -> Finnhub
        if (CryptoHelper.isCryptoSymbol(sym)) {
            fetchCryptoQuote(sym, stock, holder, colorGain, colorLoss, textPrimary, textSecondary, colorNeutral);
        } else {
            fetchStockQuote(sym, stock, holder, colorGain, colorLoss, textPrimary, textSecondary, colorNeutral);
        }

        holder.btnEdit.setOnClickListener(view -> showEditDialog(ctx, stock));
        holder.btnDelete.setOnClickListener(view -> showSellPriceDialog(ctx, stock.symbol));
        holder.itemView.setOnClickListener(view -> listener.onStockClick(stock.symbol));
    }

    @Override
    public int getItemCount() { return stocks != null ? stocks.size() : 0; }

    // ========================= FETCH: קריפטו (Binance) =========================

    private void fetchCryptoQuote(String symbol, StockData stock, StockViewHolder holder,
                                  int colorGain, int colorLoss, int textPrimary,
                                  int textSecondary, int colorNeutral) {
        String pair = CryptoHelper.getPair(symbol);
        // מחיר נוכחי + שינוי 24ש מ-Binance
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
                    float currentPrice      = (float) obj.getDouble("lastPrice");
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
        String url = "https://finnhub.io/api/v1/quote?symbol=" + symbol + "&token=" + FINNHUB_KEY;
        client.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                holder.currentPriceText.post(() -> { holder.currentPriceText.setText("?"); holder.currentPriceText.setTextColor(colorNeutral); });
                holder.changePercentText.post(() -> holder.changePercentText.setText("N/A"));
                holder.changePercentDetailText.post(() -> holder.changePercentDetailText.setText("N/A"));
            }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    JSONObject obj = new JSONObject(response.body().string());
                    float currentPrice       = (float) obj.getDouble("c");
                    float dailyChangePercent = (float) obj.getDouble("dp");
                    if (currentPrice == 0f) return;
                    updateHolderWithPrices(holder, stock, currentPrice, dailyChangePercent,
                            colorGain, colorLoss, textPrimary, textSecondary, colorNeutral);
                } catch (Exception ignored) {}
            }
        });
    }

    // ========================= עדכון ה-UI בויזואל =========================

    private void updateHolderWithPrices(StockViewHolder holder, StockData stock,
                                        float currentPrice, float dailyChangePercent,
                                        int colorGain, int colorLoss,
                                        int textPrimary, int textSecondary, int colorNeutral) {
        float totalChangePercent = (stock.buyPrice != 0f)
                ? ((currentPrice - stock.buyPrice) / stock.buyPrice * 100f) : 0f;
        double pnlDollar = (stock.tradeAmount > 0)
                ? stock.tradeAmount * (totalChangePercent / 100.0) : 0;

        int gainLossColor      = totalChangePercent >= 0 ? colorGain : colorLoss;
        int dailyGainLossColor = dailyChangePercent >= 0 ? colorGain : colorLoss;

        String dailySign = dailyChangePercent >= 0 ? "+" : "-";
        String totalSign = totalChangePercent  >= 0 ? "+" : "-";

        holder.currentPriceText.post(() -> {
            holder.currentPriceText.setText(String.format(Locale.US, "$%.2f", currentPrice));
            holder.currentPriceText.setTextColor(textPrimary);
        });
        holder.changePercentText.post(() -> {
            holder.changePercentText.setText(
                    String.format(Locale.US, "%s%.2f%% Today", dailySign, Math.abs(dailyChangePercent)));
            holder.changePercentText.setTextColor(dailyGainLossColor);
        });
        holder.changePercentDetailText.post(() -> {
            holder.changePercentDetailText.setText(
                    String.format(Locale.US, "%s%.2f%% vs Entry", totalSign, Math.abs(totalChangePercent)));
            holder.changePercentDetailText.setTextColor(gainLossColor);
        });
        if (stock.tradeAmount > 0) {
            holder.pnlDollarText.post(() -> {
                String sign = pnlDollar >= 0 ? "+" : "-";
                holder.pnlDollarText.setText(
                        String.format(Locale.US, "%s$%.2f", sign, Math.abs(pnlDollar)));
                holder.pnlDollarText.setTextColor(pnlDollar >= 0 ? colorGain : colorLoss);
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

        layout.addView(makeLabel(context, "Notes / Trade Reason - optional"));
        EditText etNotes = makeEditText(context, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        etNotes.setLines(2);
        etNotes.setMaxLines(3);
        etNotes.setText(stock.notes != null ? stock.notes : "");
        layout.addView(etNotes);

        builder.setView(layout);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String rawSym = etSymbol.getText().toString().trim();
            // אם קריפטו שומר פורמט BINANCE:XXX, אחרת uppercase
            String newSymbol = CryptoHelper.isCryptoSymbol(rawSym)
                    ? rawSym.trim()
                    : rawSym.toUpperCase(Locale.US);
            if (newSymbol.isEmpty()) {
                Toast.makeText(context, "Ticker cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }
            String oldSymbol  = stock.symbol;
            stock.symbol      = newSymbol;
            stock.name        = etName.getText().toString().trim();
            String buyStr     = etBuyPrice.getText().toString().trim();
            stock.buyPrice    = buyStr.isEmpty() ? 0f : Float.parseFloat(buyStr);
            String amountStr  = etAmount.getText().toString().trim();
            stock.tradeAmount = amountStr.isEmpty() ? 0 : Double.parseDouble(amountStr);
            String targetStr  = etTarget.getText().toString().trim();
            stock.targetPrice = targetStr.isEmpty() ? 0f : Float.parseFloat(targetStr);
            stock.notes       = etNotes.getText().toString().trim();

            listener.onStockEdit(stock, oldSymbol);
            notifyDataSetChanged();
            Toast.makeText(context, "\u2705 " + newSymbol + " updated successfully", Toast.LENGTH_SHORT).show();
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
        TextView changePercentDetailText, buyPriceText, targetPriceText, pnlDollarText, stockNameText, notesText;
        ImageButton btnEdit, btnDelete;
        ImageView stockLogoBackground;

        public StockViewHolder(@NonNull View itemView) {
            super(itemView);
            symbolText              = itemView.findViewById(R.id.stockSymbol);
            currentPriceText        = itemView.findViewById(R.id.stockCurrentPrice);
            changePercentText       = itemView.findViewById(R.id.stockChangePercent);
            changePercentDetailText = itemView.findViewById(R.id.stockChangePercentDetail);
            buyPriceText            = itemView.findViewById(R.id.stockBuyPrice);
            targetPriceText         = itemView.findViewById(R.id.stockTargetPrice);
            pnlDollarText           = itemView.findViewById(R.id.stockPnlDollar);
            stockNameText           = itemView.findViewById(R.id.stockName);
            notesText               = itemView.findViewById(R.id.stockNotes);
            btnEdit                 = itemView.findViewById(R.id.btnEditStock);
            btnDelete               = itemView.findViewById(R.id.btnDeleteStock);
            stockLogoBackground     = itemView.findViewById(R.id.stockLogoBackground);
        }
    }

    public void refreshPrices() { notifyDataSetChanged(); }

    private void showSellPriceDialog(Context context, String symbol) {
        boolean isCrypto = CryptoHelper.isCryptoSymbol(symbol);
        String displaySym = isCrypto ? CryptoHelper.getShortName(symbol) : symbol;
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Close Position - " + displaySym);
        final EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setHint("Exit price ($)");
        builder.setView(input);
        builder.setPositiveButton("Close Trade", (dialog, which) -> {
            String priceStr = input.getText().toString();
            double sellPrice = priceStr.isEmpty() ? 0 : Double.parseDouble(priceStr);
            listener.onStockDelete(symbol, sellPrice);
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private int dpToPx(Context context, int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }
}
