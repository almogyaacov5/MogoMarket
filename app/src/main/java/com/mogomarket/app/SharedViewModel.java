package com.mogomarket.app;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.HashMap;
import java.util.Map;

public class SharedViewModel extends ViewModel {

    private final MutableLiveData<String> selectedSymbol = new MutableLiveData<>();
    private final MutableLiveData<Map<String, Double>> prices = new MutableLiveData<>();

    // נשמר בזיכרון בלבד — הסמל האחרון שנפתח בגרף בתוך הסשן הנוכחי
    private String sessionSymbol = null;

    public LiveData<String> getSelectedSymbol() {
        return selectedSymbol;
    }

    public void setSelectedSymbol(String symbol) {
        if (symbol != null && symbol.equals(selectedSymbol.getValue())) {
            selectedSymbol.setValue(null);
        }
        selectedSymbol.setValue(symbol);
        // שמור כ-session symbol (רק בזיכרון)
        if (symbol != null && !symbol.trim().isEmpty()) {
            sessionSymbol = symbol.trim();
        }
    }

    /** מחזיר את הסמל האחרון שנפתח בגרף בסשן הנוכחי, או null אם לא היה */
    public String getSessionSymbol() {
        return sessionSymbol;
    }

    public LiveData<Map<String, Double>> getPrices() {
        return prices;
    }

    public void updatePrice(String symbol, double price) {
        Map<String, Double> current = prices.getValue();
        if (current == null) current = new HashMap<>();
        current.put(symbol, price);
        prices.setValue(current);
    }
}