package com.mogomarket.app;

public class StockWatchData {
    public String  symbol;
    public float   alertTargetPrice;
    public boolean alertEnabled;
    public boolean alertTriggered;

    // שדות זמניים - לא נשמרים ב-Firebase, מחושבים בזמן ריצה
    // הוסרה מילת transient כדי ש-ClassMapper לא יזרוק אזהרות
    public float currentPrice;
    public float dayChangePercent; // שם תואם ל-ClassMapper (dayChangePercent)
    public float dayChange;        // תאימות לאחור עם קוד ישן

    // בנאי ריק לצורך Firebase
    public StockWatchData() {}

    public StockWatchData(String symbol, float currentPrice, float dayChangePercent) {
        this.symbol            = symbol;
        this.currentPrice      = currentPrice;
        this.dayChangePercent  = dayChangePercent;
        this.dayChange         = dayChangePercent; // סנכרון
    }

    // Getters & Setters — נדרשים ל-ClassMapper של Firebase
    public String getSymbol()             { return symbol; }
    public void   setSymbol(String v)     { symbol = v; }

    public float getAlertTargetPrice()          { return alertTargetPrice; }
    public void  setAlertTargetPrice(float v)   { alertTargetPrice = v; }

    public boolean isAlertEnabled()             { return alertEnabled; }
    public void    setAlertEnabled(boolean v)   { alertEnabled = v; }

    public boolean isAlertTriggered()           { return alertTriggered; }
    public void    setAlertTriggered(boolean v) { alertTriggered = v; }

    public float getCurrentPrice()          { return currentPrice; }
    public void  setCurrentPrice(float v)   { currentPrice = v; }

    public float getDayChangePercent()          { return dayChangePercent; }
    public void  setDayChangePercent(float v)   { dayChangePercent = v; dayChange = v; }

    public float getDayChange()             { return dayChange; }
    public void  setDayChange(float v)      { dayChange = v; dayChangePercent = v; }
}
