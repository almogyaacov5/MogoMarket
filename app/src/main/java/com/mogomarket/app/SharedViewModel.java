package com.mogomarket.app;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

/**
 * SharedViewModel — מקור אמת אחד לנתונים משותפים בין כל הפרגמנטים.
 * נגיש דרך: new ViewModelProvider(requireActivity()).get(SharedViewModel.class)
 */
public class SharedViewModel extends ViewModel {

    // ✅ הסמל הנבחר (לגרף)
    private final MutableLiveData<String> selectedSymbol = new MutableLiveData<>();

    // ✅ מחירים עדכניים מה-Repository
    private final MutableLiveData<java.util.Map<String, Double>> prices =
            new MutableLiveData<>();

    // ---- Selected Symbol ----

    public LiveData<String> getSelectedSymbol() {
        return selectedSymbol;
    }

    public void setSelectedSymbol(String symbol) {
        selectedSymbol.setValue(symbol);
    }

    // ---- Market Prices ----

    public LiveData<java.util.Map<String, Double>> getPrices() {
        return prices;
    }

    public void updatePrice(String symbol, double price) {
        java.util.Map<String, Double> current = prices.getValue();
        if (current == null) current = new java.util.HashMap<>();
        current.put(symbol, price);
        prices.setValue(current);
    }
}