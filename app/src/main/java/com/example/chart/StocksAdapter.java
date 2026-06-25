package com.example.chart;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import java.util.Locale;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Call;
import okhttp3.Callback;
import org.json.JSONObject;
import java.io.IOException;

public class StocksAdapter extends RecyclerView.Adapter<StocksAdapter.StockViewHolder> {

    public interface OnStockClickListener {
        void onStockClick(String symbol);
        void onStockDelete(String symbol, double sellPrice);
    }

    private List<StockData> stocks;
    private final OnStockClickListener listener;
    private final OkHttpClient client = new OkHttpClient();

    public StocksAdapter(List<StockData> stocks, OnStockClickListener listener) {
        this.stocks = stocks;
        this.listener = listener;
    }

    @NonNull
    @Override
    public StockViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_portfolio, parent, false);
        return new StockViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull StockViewHolder holder, int position) {
        StockData stock = stocks.get(position);

        String sym = (stock.symbol != null) ? stock.symbol.trim() : "?";
        holder.symbolText.setText(sym);
        holder.buyPriceText.setText(String.format(Locale.US, "$%.2f", stock.buyPrice));

        if (stock.targetPrice > 0) {
            holder.targetPriceText.setText(String.format(Locale.US, "$%.2f", stock.targetPrice));
        } else {
            holder.targetPriceText.setText("-");
        }

        fetchCurrentPrice(stock.symbol, new PriceCallback() {
            @Override
            public void onPriceReceived(float price) {
                holder.currentPriceText.post(() ->
                        holder.currentPriceText.setText(String.format(Locale.US, "$%.2f", price)));

                float percentChange = (stock.buyPrice != 0f) ?
                        ((price - stock.buyPrice) / stock.buyPrice * 100f) : 0f;
                double pnlDollar = (stock.tradeAmount > 0) ?
                        stock.tradeAmount * (percentChange / 100.0) : 0;

                holder.changePercentText.post(() -> {
                    String arrow = percentChange >= 0 ? "▲" : "▼";
                    int color = percentChange >= 0 ? 0xFF16A34A : 0xFFEF4444;
                    holder.changePercentText.setText(
                            String.format(Locale.US, "%s %.2f%%", arrow, Math.abs(percentChange)));
                    holder.changePercentText.setTextColor(color);
                });

                holder.changePercentDetailText.post(() -> {
                    String arrow = percentChange >= 0 ? "▲" : "▼";
                    int color = percentChange >= 0 ? 0xFF16A34A : 0xFFEF4444;
                    holder.changePercentDetailText.setText(
                            String.format(Locale.US, "%s %.2f%%", arrow, Math.abs(percentChange)));
                    holder.changePercentDetailText.setTextColor(color);
                });

                if (stock.tradeAmount > 0) {
                    holder.pnlDollarText.post(() -> {
                        int color = pnlDollar >= 0 ? 0xFF16A34A : 0xFFEF4444;
                        String sign = pnlDollar >= 0 ? "+" : "";
                        holder.pnlDollarText.setText(
                                String.format(Locale.US, "%s$%.2f", sign, pnlDollar));
                        holder.pnlDollarText.setTextColor(color);
                    });
                } else {
                    holder.pnlDollarText.post(() -> holder.pnlDollarText.setText("-"));
                }
            }

            @Override
            public void onError(Exception e) {
                holder.currentPriceText.post(() -> holder.currentPriceText.setText("?"));
                holder.changePercentText.post(() -> holder.changePercentText.setText("?"));
            }
        });

        holder.btnEdit.setOnClickListener(view -> listener.onStockClick(stock.symbol));

        holder.btnDelete.setOnClickListener(view ->
                showSellPriceDialog(view.getContext(), stock.symbol));

        holder.itemView.setOnClickListener(view -> listener.onStockClick(stock.symbol));
    }

    @Override
    public int getItemCount() {
        return stocks != null ? stocks.size() : 0;
    }

    private void fetchCurrentPrice(String symbol, PriceCallback callback) {
        String apiKey = "0518811f0d394fa39842a8024a25c049";
        String url = "https://api.twelvedata.com/price?symbol=" + symbol + "&apikey=" + apiKey;
        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    String body = response.body().string();
                    JSONObject obj = new JSONObject(body);
                    float price = Float.parseFloat(obj.getString("price"));
                    callback.onPriceReceived(price);
                } catch (Exception e) {
                    callback.onError(e);
                }
            }
        });
    }

    public interface PriceCallback {
        void onPriceReceived(float price);
        void onError(Exception e);
    }

    static class StockViewHolder extends RecyclerView.ViewHolder {
        TextView symbolText;
        TextView currentPriceText;
        TextView changePercentText;
        TextView changePercentDetailText;
        TextView buyPriceText;
        TextView targetPriceText;
        TextView pnlDollarText;
        TextView stockNameText;
        ImageButton btnEdit;
        ImageButton btnDelete;

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

    public void refreshPrices() {
        notifyDataSetChanged();
    }

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
}
