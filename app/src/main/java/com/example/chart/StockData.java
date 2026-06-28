package com.example.chart;

public class StockData {
    public String symbol;
    public String name;
    public float buyPrice;
    public float targetPrice;
    public float currentPrice;
    public float changePercent;
    public double sellPrice;
    public double tradeAmount;
    public String notes;  // הערות / סיבת קנייה

    // קונסטרוקטור ריק בשביל Firebase
    public StockData() {}

    // קונסטרוקטור מלא - 5 פרמטרים (עם סכום השקעה)
    public StockData(String symbol, float buyPrice, float currentPrice, float changePercent, double tradeAmount) {
        this.symbol = symbol;
        this.buyPrice = buyPrice;
        this.currentPrice = currentPrice;
        this.changePercent = changePercent;
        this.tradeAmount = tradeAmount;
        this.targetPrice = 0;
        this.name = "";
        this.notes = "";
    }

    // קונסטרוקטור 4 פרמטרים
    public StockData(String symbol, float buyPrice, float currentPrice, float changePercent) {
        this.symbol = symbol;
        this.buyPrice = buyPrice;
        this.currentPrice = currentPrice;
        this.changePercent = changePercent;
        this.tradeAmount = 0;
        this.targetPrice = 0;
        this.name = "";
        this.notes = "";
    }

    // קונסטרוקטור חלקי - 3 פרמטרים
    public StockData(String symbol, float buyPrice, float currentPrice) {
        this.symbol = symbol;
        this.buyPrice = buyPrice;
        this.currentPrice = currentPrice;
        this.changePercent = 0;
        this.tradeAmount = 0;
        this.targetPrice = 0;
        this.name = "";
        this.notes = "";
    }
}
