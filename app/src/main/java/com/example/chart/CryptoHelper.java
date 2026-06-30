package com.example.chart;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class CryptoHelper {

    // מיפוי: כינויים -> BINANCE:XXXUSDT
    public static final Map<String, String> CRYPTO_MAP = new HashMap<>();
    static {
        CRYPTO_MAP.put("BTC",       "BINANCE:BTCUSDT");
        CRYPTO_MAP.put("BTCUSD",    "BINANCE:BTCUSDT");
        CRYPTO_MAP.put("BTCUSDT",   "BINANCE:BTCUSDT");
        CRYPTO_MAP.put("BITCOIN",   "BINANCE:BTCUSDT");
        CRYPTO_MAP.put("ETH",       "BINANCE:ETHUSDT");
        CRYPTO_MAP.put("ETHUSD",    "BINANCE:ETHUSDT");
        CRYPTO_MAP.put("ETHUSDT",   "BINANCE:ETHUSDT");
        CRYPTO_MAP.put("ETHEREUM",  "BINANCE:ETHUSDT");
        CRYPTO_MAP.put("XRP",       "BINANCE:XRPUSDT");
        CRYPTO_MAP.put("XRPUSD",    "BINANCE:XRPUSDT");
        CRYPTO_MAP.put("SOL",       "BINANCE:SOLUSDT");
        CRYPTO_MAP.put("SOLUSD",    "BINANCE:SOLUSDT");
        CRYPTO_MAP.put("SOLANA",    "BINANCE:SOLUSDT");
        CRYPTO_MAP.put("BNB",       "BINANCE:BNBUSDT");
        CRYPTO_MAP.put("BNBUSD",    "BINANCE:BNBUSDT");
        CRYPTO_MAP.put("DOGE",      "BINANCE:DOGEUSDT");
        CRYPTO_MAP.put("DOGEUSD",   "BINANCE:DOGEUSDT");
        CRYPTO_MAP.put("DOGEUSDT",  "BINANCE:DOGEUSDT");
        CRYPTO_MAP.put("DOGECOIN",  "BINANCE:DOGEUSDT");
        CRYPTO_MAP.put("ADA",       "BINANCE:ADAUSDT");
        CRYPTO_MAP.put("ADAUSD",    "BINANCE:ADAUSDT");
        CRYPTO_MAP.put("CARDANO",   "BINANCE:ADAUSDT");
        CRYPTO_MAP.put("AVAX",      "BINANCE:AVAXUSDT");
        CRYPTO_MAP.put("AVAXUSD",   "BINANCE:AVAXUSDT");
        CRYPTO_MAP.put("AVALANCHE", "BINANCE:AVAXUSDT");
        CRYPTO_MAP.put("DOT",       "BINANCE:DOTUSDT");
        CRYPTO_MAP.put("DOTUSD",    "BINANCE:DOTUSDT");
        CRYPTO_MAP.put("POLKADOT",  "BINANCE:DOTUSDT");
        CRYPTO_MAP.put("LINK",      "BINANCE:LINKUSDT");
        CRYPTO_MAP.put("LINKUSD",   "BINANCE:LINKUSDT");
        CRYPTO_MAP.put("CHAINLINK", "BINANCE:LINKUSDT");
        CRYPTO_MAP.put("LTC",       "BINANCE:LTCUSDT");
        CRYPTO_MAP.put("LTCUSD",    "BINANCE:LTCUSDT");
        CRYPTO_MAP.put("LITECOIN",  "BINANCE:LTCUSDT");
        CRYPTO_MAP.put("MATIC",     "BINANCE:MATICUSDT");
        CRYPTO_MAP.put("MATICUSD",  "BINANCE:MATICUSDT");
        CRYPTO_MAP.put("POLYGON",   "BINANCE:MATICUSDT");
        CRYPTO_MAP.put("UNI",       "BINANCE:UNIUSDT");
        CRYPTO_MAP.put("UNIUSD",    "BINANCE:UNIUSDT");
        CRYPTO_MAP.put("UNISWAP",   "BINANCE:UNIUSDT");
        CRYPTO_MAP.put("SHIB",      "BINANCE:SHIBUSDT");
        CRYPTO_MAP.put("SHIBUSD",   "BINANCE:SHIBUSDT");
        CRYPTO_MAP.put("SHIBAUSDT", "BINANCE:SHIBUSDT");
        CRYPTO_MAP.put("TRX",       "BINANCE:TRXUSDT");
        CRYPTO_MAP.put("TRXUSD",    "BINANCE:TRXUSDT");
        CRYPTO_MAP.put("TRON",      "BINANCE:TRXUSDT");
        CRYPTO_MAP.put("TON",       "BINANCE:TONUSDT");
        CRYPTO_MAP.put("TONUSD",    "BINANCE:TONUSDT");
        CRYPTO_MAP.put("SUI",       "BINANCE:SUIUSDT");
        CRYPTO_MAP.put("SUIUSD",    "BINANCE:SUIUSDT");
        CRYPTO_MAP.put("PEPE",      "BINANCE:PEPEUSDT");
        CRYPTO_MAP.put("PEPEUSD",   "BINANCE:PEPEUSDT");
        CRYPTO_MAP.put("WLD",       "BINANCE:WLDUSDT");
        CRYPTO_MAP.put("WLDUSD",    "BINANCE:WLDUSDT");
        CRYPTO_MAP.put("WORLDCOIN", "BINANCE:WLDUSDT");
        CRYPTO_MAP.put("INJ",       "BINANCE:INJUSDT");
        CRYPTO_MAP.put("INJUSD",    "BINANCE:INJUSDT");
        CRYPTO_MAP.put("ARB",       "BINANCE:ARBUSDT");
        CRYPTO_MAP.put("ARBUSD",    "BINANCE:ARBUSDT");
        CRYPTO_MAP.put("ARBITRUM",  "BINANCE:ARBUSDT");
        CRYPTO_MAP.put("OP",        "BINANCE:OPUSDT");
        CRYPTO_MAP.put("OPUSD",     "BINANCE:OPUSDT");
        CRYPTO_MAP.put("OPTIMISM",  "BINANCE:OPUSDT");
        CRYPTO_MAP.put("NEAR",      "BINANCE:NEARUSDT");
        CRYPTO_MAP.put("NEARUSD",   "BINANCE:NEARUSDT");
        CRYPTO_MAP.put("ATOM",      "BINANCE:ATOMUSDT");
        CRYPTO_MAP.put("ATOMUSD",   "BINANCE:ATOMUSDT");
        CRYPTO_MAP.put("COSMOS",    "BINANCE:ATOMUSDT");
        CRYPTO_MAP.put("FIL",       "BINANCE:FILUSDT");
        CRYPTO_MAP.put("FILUSD",    "BINANCE:FILUSDT");
        CRYPTO_MAP.put("APT",       "BINANCE:APTUSDT");
        CRYPTO_MAP.put("APTUSD",    "BINANCE:APTUSDT");
        CRYPTO_MAP.put("APTOS",     "BINANCE:APTUSDT");
    }

    // שמות תצוגה
    private static final Map<String, String> CRYPTO_NAMES = new HashMap<>();
    static {
        CRYPTO_NAMES.put("BTCUSDT",   "Bitcoin");
        CRYPTO_NAMES.put("ETHUSDT",   "Ethereum");
        CRYPTO_NAMES.put("XRPUSDT",   "XRP");
        CRYPTO_NAMES.put("SOLUSDT",   "Solana");
        CRYPTO_NAMES.put("BNBUSDT",   "BNB");
        CRYPTO_NAMES.put("DOGEUSDT",  "Dogecoin");
        CRYPTO_NAMES.put("ADAUSDT",   "Cardano");
        CRYPTO_NAMES.put("AVAXUSDT",  "Avalanche");
        CRYPTO_NAMES.put("DOTUSDT",   "Polkadot");
        CRYPTO_NAMES.put("LINKUSDT",  "Chainlink");
        CRYPTO_NAMES.put("LTCUSDT",   "Litecoin");
        CRYPTO_NAMES.put("MATICUSDT", "Polygon");
        CRYPTO_NAMES.put("UNIUSDT",   "Uniswap");
        CRYPTO_NAMES.put("SHIBUSDT",  "Shiba Inu");
        CRYPTO_NAMES.put("TRXUSDT",   "TRON");
        CRYPTO_NAMES.put("TONUSDT",   "TON");
        CRYPTO_NAMES.put("SUIUSDT",   "Sui");
        CRYPTO_NAMES.put("PEPEUSDT",  "Pepe");
        CRYPTO_NAMES.put("WLDUSDT",   "Worldcoin");
        CRYPTO_NAMES.put("INJUSDT",   "Injective");
        CRYPTO_NAMES.put("ARBUSDT",   "Arbitrum");
        CRYPTO_NAMES.put("OPUSDT",    "Optimism");
        CRYPTO_NAMES.put("NEARUSDT",  "NEAR Protocol");
        CRYPTO_NAMES.put("ATOMUSDT",  "Cosmos");
        CRYPTO_NAMES.put("FILUSDT",   "Filecoin");
        CRYPTO_NAMES.put("APTUSDT",   "Aptos");
    }

    public static boolean isCryptoSymbol(String sym) {
        return sym != null && sym.contains(":");
    }

    /** BINANCE:BTCUSDT -> "BTCUSDT" */
    public static String getPair(String symbol) {
        return symbol.contains(":") ? symbol.substring(symbol.indexOf(':') + 1) : symbol;
    }

    /** BINANCE:BTCUSDT -> "BTC" */
    public static String getShortName(String symbol) {
        String pair = getPair(symbol);
        if (pair.endsWith("USDT")) return pair.substring(0, pair.length() - 4);
        return pair;
    }

    /** חיפוש מקומי לפי prefix/שם מלא */
    public static List<CryptoSuggestion> searchLocal(String query) {
        List<CryptoSuggestion> results = new ArrayList<>();
        String q = query.toUpperCase(Locale.US);
        for (Map.Entry<String, String> entry : CRYPTO_MAP.entrySet()) {
            String key          = entry.getKey();
            String binanceSym   = entry.getValue();
            String pair         = getPair(binanceSym);
            String shortName    = getShortName(binanceSym);
            String fullName     = CRYPTO_NAMES.containsKey(pair) ? CRYPTO_NAMES.get(pair) : shortName;

            boolean match = key.startsWith(q)
                    || shortName.startsWith(q)
                    || fullName.toUpperCase(Locale.US).contains(q);

            if (match) {
                boolean dup = false;
                for (CryptoSuggestion s : results) {
                    if (s.binanceSymbol.equals(binanceSym)) { dup = true; break; }
                }
                if (!dup) results.add(new CryptoSuggestion(binanceSym, shortName, fullName));
            }
        }
        return results;
    }

    // ─── מחיר נוכחי מ-Binance ───
    public interface PriceCallback {
        void onPrice(double price);
        void onError(String error);
    }

    public static void fetchCurrentPrice(OkHttpClient client, String symbol, PriceCallback cb) {
        String pair = getPair(symbol);
        String url  = "https://api.binance.com/api/v3/ticker/price?symbol=" + pair;
        client.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                new Handler(Looper.getMainLooper()).post(() -> cb.onError(e.getMessage()));
            }
            @Override public void onResponse(Call call, Response response) throws IOException {
                try {
                    JSONObject json = new JSONObject(response.body().string());
                    double price = json.getDouble("price");
                    new Handler(Looper.getMainLooper()).post(() -> cb.onPrice(price));
                } catch (Exception e) {
                    new Handler(Looper.getMainLooper()).post(() -> cb.onError(e.getMessage()));
                }
            }
        });
    }

    // ─── Data class ───
    public static class CryptoSuggestion {
        public final String binanceSymbol; // BINANCE:BTCUSDT
        public final String shortName;     // BTC
        public final String fullName;      // Bitcoin

        public CryptoSuggestion(String binanceSymbol, String shortName, String fullName) {
            this.binanceSymbol = binanceSymbol;
            this.shortName     = shortName;
            this.fullName      = fullName;
        }
    }
}
