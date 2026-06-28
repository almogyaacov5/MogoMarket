package com.example.chart;

public class StockWatchData {
    public String  symbol;
    public float   alertTargetPrice;
    public boolean alertEnabled;
    public boolean alertTriggered;

    // שדות זמניים - לא נשמרים ב-Firebase, מחושבים בזמן ריצה
    public transient float currentPrice;
    public transient float dayChange;

    // בנאי ריק לצורך Firebase
    public StockWatchData() {}

    public StockWatchData(String symbol, float currentPrice, float dayChange) {
        this.symbol       = symbol;
        this.currentPrice = currentPrice;
        this.dayChange    = dayChange;
    }
}
