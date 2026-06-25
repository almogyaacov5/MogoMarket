package com.example.chart;

// מודל נתונים למניה ב-Watchlist - תומך גם בהתראות מחיר
public class StockWatchData {
    public String symbol;          // הטיקר (לדוגמה: "TSLA")
    public float currentPrice;     // מחיר נוכחי
    public float dayChangePercent; // אחוז שינוי יומי
    public float alertTargetPrice; // מחיר יעד להתראה (0 אם לא הוגדר)
    public boolean alertEnabled;   // האם ההתראה פעילה
    public boolean alertTriggered; // האם ההתראה כבר נשלחה (למניעת ספאם)

    public StockWatchData() {} // קונסטרוקטור ריק - חובה ל-Firebase

    public StockWatchData(String symbol, float currentPrice, float dayChangePercent) {
        this.symbol = symbol;
        this.currentPrice = currentPrice;
        this.dayChangePercent = dayChangePercent;
        this.alertTargetPrice = 0f;  // ברירת מחדל: ללא יעד
        this.alertEnabled = false;   // ברירת מחדל: כבוי
        this.alertTriggered = false;
    }

    // Getters וו Setters - נדרשים ל-Firebase
    public String getSymbol() { return symbol; }
    public float getCurrentPrice() { return currentPrice; }
    public float getDayChangePercent() { return dayChangePercent; }
    public float getAlertTargetPrice() { return alertTargetPrice; }
    public boolean isAlertEnabled() { return alertEnabled; }
    public boolean isAlertTriggered() { return alertTriggered; }

    public void setSymbol(String symbol) { this.symbol = symbol; }
    public void setCurrentPrice(float currentPrice) { this.currentPrice = currentPrice; }
    public void setDayChangePercent(float dayChangePercent) { this.dayChangePercent = dayChangePercent; }
    public void setAlertTargetPrice(float alertTargetPrice) { this.alertTargetPrice = alertTargetPrice; }
    public void setAlertEnabled(boolean alertEnabled) { this.alertEnabled = alertEnabled; }
    public void setAlertTriggered(boolean alertTriggered) { this.alertTriggered = alertTriggered; }
}