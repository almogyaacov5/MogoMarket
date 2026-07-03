package com.mogomarket.app;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.HashMap;
import java.util.Map;

/**
 * SharedViewModel — מקור אמת אחד לנתונים משותפים בין כל ה-Fragments באותה Activity.
 * משמש להעברת symbol לגרף, וגם למפת מחירים אם תרצה.
 */
public class SharedViewModel extends ViewModel {

    // הסמל הנבחר (לגרף)
    private final MutableLiveData<String> selectedSymbol = new MutableLiveData<>();

    // מפה של מחירים (symbol -> price)
    private final MutableLiveData<Map<String, Double>> prices =
            new MutableLiveData<>();

    // --- Selected Symbol ---

    public LiveData<String> getSelectedSymbol() {
        return selectedSymbol;
    }

    public void setSelectedSymbol(String symbol) {
        selectedSymbol.setValue(symbol);
    }

    // --- Prices Map ---

    public LiveData<Map<String, Double>> getPrices() {
        return prices;
    }

    public void updatePrice(String symbol, double price) {
        Map<String, Double> current = prices.getValue();
        if (current == null) {
            current = new HashMap<>();
        }
        current.put(symbol, price);
        prices.setValue(current);
    }
}