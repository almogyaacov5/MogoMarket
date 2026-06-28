package com.example.chart;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.io.IOException;
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
        holder.symbolText.setText(sym);
        holder.symbolText.setTextColor(textPrimary);
        holder.buyPriceText.setText(String.format(Locale.US, "$%.2f", stock.buyPrice));
        holder.buyPriceText.setTextColor(textSecondary);

        if (stock.targetPrice > 0) {
            holder.targetPriceText.setText(String.format(Locale.US, "$%.2f", stock.targetPrice));
        } else {
            holder.targetPriceText.setText("-");
        }
        holder.targetPriceText.setTextColor(textSecondary);

        fetchCurrentPrice(stock.symbol, new PriceCallback() {
            @Override
            public void onPriceReceived(float price) {
                holder.currentPriceText.post(() -> {
                    holder.currentPriceText.setText(String.format(Locale.US, "$%.2f", price));
                    holder.currentPriceText.setTextColor(textPrimary);
                });

                float percentChange = (stock.buyPrice != 0f)
                        ? ((price - stock.buyPrice) / stock.buyPrice * 100f) : 0f;
                double pnlDollar = (stock.tradeAmount > 0)
                        ? stock.tradeAmount * (percentChange / 100.0) : 0;

                int gainLossColor = percentChange >= 0 ? colorGain : colorLoss;
                String arrow      = percentChange >= 0 ? "▲" : "▼";

                holder.changePercentText.post(() -> {
                    holder.changePercentText.setText(
                            String.format(Locale.US, "%s %.2f%%", arrow, Math.abs(percentChange)));
                    holder.changePercentText.setTextColor(gainLossColor);
                });

                holder.changePercentDetailText.post(() -> {
                    holder.changePercentDetailText.setText(
                            String.format(Locale.US, "%s %.2f%%", arrow, Math.abs(percentChange)));
                    holder.changePercentDetailText.setTextColor(gainLossColor);
                });

                if (stock.tradeAmount > 0) {
                    holder.pnlDollarText.post(() -> {
                        String sign = pnlDollar >= 0 ? "+" : "";
                        holder.pnlDollarText.setText(
                                String.format(Locale.US, "%s$%.2f", sign, pnlDollar));
                        holder.pnlDollarText.setTextColor(pnlDollar >= 0 ? colorGain : colorLoss);
                    });
                } else {
                    holder.pnlDollarText.post(() -> {
                        holder.pnlDollarText.setText("-");
                        holder.pnlDollarText.setTextColor(colorNeutral);
                    });
                }
            }

            @Override
            public void onError(Exception e) {
                holder.currentPriceText.post(() -> {
                    holder.currentPriceText.setText("?");
                    holder.currentPriceText.setTextColor(colorNeutral);
                });
                holder.changePercentText.post(() -> holder.changePercentText.setText("?"));
            }
        });

        holder.btnEdit.setOnClickListener(view -> showEditDialog(ctx, stock));
        holder.btnDelete.setOnClickListener(view -> showSellPriceDialog(ctx, stock.symbol));
        holder.itemView.setOnClickListener(view -> listener.onStockClick(stock.symbol));
    }

    @Override
    public int getItemCount() { return stocks != null ? stocks.size() : 0; }

    // ========================= EDIT DIALOG =========================

    private void showEditDialog(Context context, StockData stock) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("✏️ עריכת " + stock.symbol);

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dpToPx(context, 16);
        layout.setPadding(pad, pad, pad, pad);
        // אין setSpacing ב-LinearLayout - משתמשים ב-LayoutParams margins

        layout.addView(makeLabel(context, "טיקר (סימבול)"));
        EditText etSymbol = makeEditText(context, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        etSymbol.setText(stock.symbol != null ? stock.symbol : "");
        layout.addView(etSymbol);

        layout.addView(makeLabel(context, "שם החברה (אופציונלי)"));
        EditText etName = makeEditText(context, InputType.TYPE_CLASS_TEXT);
        etName.setText(stock.name != null ? stock.name : "");
        layout.addView(etName);

        layout.addView(makeLabel(context, "מחיר קנייה ($)"));
        EditText etBuyPrice = makeEditText(context, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etBuyPrice.setText(stock.buyPrice > 0 ? String.format(Locale.US, "%.2f", stock.buyPrice) : "");
        layout.addView(etBuyPrice);

        layout.addView(makeLabel(context, "סכום השקעה ($)"));
        EditText etAmount = makeEditText(context, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etAmount.setText(stock.tradeAmount > 0 ? String.format(Locale.US, "%.2f", stock.tradeAmount) : "");
        layout.addView(etAmount);

        layout.addView(makeLabel(context, "מחיר יעד ($) - אופציונלי"));
        EditText etTarget = makeEditText(context, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etTarget.setText(stock.targetPrice > 0 ? String.format(Locale.US, "%.2f", stock.targetPrice) : "");
        layout.addView(etTarget);

        layout.addView(makeLabel(context, "הערות / סיבת קנייה - אופציונלי"));
        EditText etNotes = makeEditText(context, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        etNotes.setLines(2);
        etNotes.setMaxLines(3);
        etNotes.setText(stock.notes != null ? stock.notes : "");
        layout.addView(etNotes);

        builder.setView(layout);

        builder.setPositiveButton("שמור", (dialog, which) -> {
            String newSymbol = etSymbol.getText().toString().trim().toUpperCase(Locale.US);
            if (newSymbol.isEmpty()) {
                Toast.makeText(context, "טיקר לא יכול להיות ריק", Toast.LENGTH_SHORT).show();
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
            Toast.makeText(context, "✅ " + newSymbol + " עודכן בהצלחה", Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton("ביטול", (dialog, which) -> dialog.cancel());
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

    // ========================= FETCH PRICE =========================

    private void fetchCurrentPrice(String symbol, PriceCallback callback) {
        String apiKey = "0518811f0d394fa39842a8024a25c049";
        String url = "https://api.twelvedata.com/price?symbol=" + symbol + "&apikey=" + apiKey;
        client.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) { callback.onError(e); }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    String body = response.body().string();
                    JSONObject obj = new JSONObject(body);
                    float price = Float.parseFloat(obj.getString("price"));
                    callback.onPriceReceived(price);
                } catch (Exception e) { callback.onError(e); }
            }
        });
    }

    public interface PriceCallback {
        void onPriceReceived(float price);
        void onError(Exception e);
    }

    static class StockViewHolder extends RecyclerView.ViewHolder {
        TextView symbolText, currentPriceText, changePercentText;
        TextView changePercentDetailText, buyPriceText, targetPriceText, pnlDollarText, stockNameText;
        ImageButton btnEdit, btnDelete;

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
            btnEdit                 = itemView.findViewById(R.id.btnEditStock);
            btnDelete               = itemView.findViewById(R.id.btnDeleteStock);
        }
    }

    public void refreshPrices() { notifyDataSetChanged(); }

    private void showSellPriceDialog(Context context, String symbol) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("הזן מחיר סגירה ל-" + symbol);
        final EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        builder.setView(input);
        builder.setPositiveButton("סגור טרייד", (dialog, which) -> {
            String priceStr = input.getText().toString();
            double sellPrice = priceStr.isEmpty() ? 0 : Double.parseDouble(priceStr);
            listener.onStockDelete(symbol, sellPrice);
        });
        builder.setNegativeButton("ביטול", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private int dpToPx(Context context, int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }
}
