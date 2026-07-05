package com.mogomarket.app;

public class StockData {
    public String symbol;
    public String name;
    public double dailyProfitLoss;
    public double dailyProfitLossPercent;
    public float buyPrice;
    public float targetPrice;
    public float currentPrice;
    public float changePercent;
    public double sellPrice;
    public double tradeAmount;
    public String notes;

    // New calculated fields for portfolio/email summary
    public double currentValue;
    public double profitLoss;
    public double profitLossPercent;

    public StockData() {}

    public StockData(String symbol, float buyPrice, float currentPrice, float changePercent, double tradeAmount) {
        this.symbol = symbol;
        this.buyPrice = buyPrice;
        this.currentPrice = currentPrice;
        this.changePercent = changePercent;
        this.tradeAmount = tradeAmount;
        this.targetPrice = 0;
        this.name = "";
        this.notes = "";
        this.sellPrice = 0;
        this.currentValue = 0;
        this.profitLoss = 0;
        this.profitLossPercent = 0;
    }

    public StockData(String symbol, float buyPrice, float currentPrice, float changePercent) {
        this.symbol = symbol;
        this.buyPrice = buyPrice;
        this.currentPrice = currentPrice;
        this.changePercent = changePercent;
        this.tradeAmount = 0;
        this.targetPrice = 0;
        this.name = "";
        this.notes = "";
        this.sellPrice = 0;
        this.currentValue = 0;
        this.profitLoss = 0;
        this.profitLossPercent = 0;
    }

    public StockData(String symbol, float buyPrice, float currentPrice) {
        this.symbol = symbol;
        this.buyPrice = buyPrice;
        this.currentPrice = currentPrice;
        this.changePercent = 0;
        this.tradeAmount = 0;
        this.targetPrice = 0;
        this.name = "";
        this.notes = "";
        this.sellPrice = 0;
        this.currentValue = 0;
        this.profitLoss = 0;
        this.profitLossPercent = 0;
    }
}