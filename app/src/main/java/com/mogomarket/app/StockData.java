package com.mogomarket.app;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

public class StockData {
    public String symbol;
    public String name;
    public double dailyProfitLoss;
    public double dailyProfitLossPercent;
    public float buyPrice;
    public float targetPrice;
    public float stopLossPrice;
    public float currentPrice;
    public float changePercent;
    public long openedAt;
    public double sellPrice;
    public double tradeAmount;
    public double shares;
    public double entryFees;
    public double exitFees;
    public double taxes;
    public String notes;
    public String strategyTag;
    public String sector;
    public String exitReason;
    public java.util.List<String> screenshotPaths;
    public java.util.List<String> screenshotUrls;
    public Double missedProfitLoss;
    public String missedPnlStatus;
    public long closedAt;

    // New calculated fields for portfolio/email summary
    public double currentValue;
    public double profitLoss;
    public double profitLossPercent;
    public String closedTradeId;
    public String tradeNumber;
    public transient String firebaseKey;

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

    static StockData combine(StockData first, StockData second, boolean includeSellPrice) {
        StockData combined = new StockData(
                second.symbol != null ? second.symbol : first.symbol,
                weightedAveragePrice(first, second, first.buyPrice, second.buyPrice),
                first.currentPrice > 0 ? first.currentPrice : second.currentPrice);
        combined.tradeAmount = first.tradeAmount + second.tradeAmount;
        combined.shares = getShareCount(first) + getShareCount(second);
        combined.entryFees = first.entryFees + second.entryFees;
        combined.exitFees = first.exitFees + second.exitFees;
        combined.taxes = first.taxes + second.taxes;
        combined.targetPrice = second.targetPrice > 0 ? second.targetPrice : first.targetPrice;
        combined.stopLossPrice = second.stopLossPrice > 0
                ? second.stopLossPrice : first.stopLossPrice;
        combined.openedAt = first.openedAt > 0 && second.openedAt > 0
                ? Math.min(first.openedAt, second.openedAt)
                : first.openedAt > 0 ? first.openedAt : second.openedAt;
        combined.name = second.name != null && !second.name.isEmpty() ? second.name : first.name;
        combined.notes = combineNotes(first.notes, second.notes);
        combined.strategyTag = second.strategyTag != null && !second.strategyTag.trim().isEmpty()
                ? second.strategyTag : first.strategyTag;
        combined.sector = second.sector != null && !second.sector.trim().isEmpty()
                ? second.sector : first.sector;
        combined.screenshotPaths = combineList(first.screenshotPaths, second.screenshotPaths);
        combined.screenshotUrls = combineList(first.screenshotUrls, second.screenshotUrls);
        combined.sellPrice = includeSellPrice
                ? weightedAveragePrice(first, second, first.sellPrice, second.sellPrice)
                : 0;
        combined.changePercent = first.changePercent;
        combined.dailyProfitLoss = first.dailyProfitLoss;
        combined.dailyProfitLossPercent = first.dailyProfitLossPercent;
        return combined;
    }

    static double calculateRiskRewardRatio(StockData trade) {
        if (trade == null || trade.buyPrice <= 0 || trade.targetPrice <= trade.buyPrice
                || trade.stopLossPrice <= 0 || trade.stopLossPrice >= trade.buyPrice) {
            return Double.NaN;
        }
        return (trade.targetPrice - trade.buyPrice) / (trade.buyPrice - trade.stopLossPrice);
    }

    static double calculateProfitLoss(StockData trade) {
        double grossProfitLoss;
        if (trade.tradeAmount > 0 && trade.buyPrice > 0) {
            grossProfitLoss = (trade.sellPrice - trade.buyPrice)
                    * trade.tradeAmount / trade.buyPrice;
        } else {
            grossProfitLoss = trade.sellPrice - trade.buyPrice;
        }
        return grossProfitLoss - trade.entryFees - trade.exitFees - trade.taxes;
    }

    static double calculateProfitLossPercent(StockData trade) {
        if (trade == null) return 0;
        double investedAmount = trade.tradeAmount > 0 ? trade.tradeAmount
                : trade.buyPrice * (trade.shares > 0 ? trade.shares : 1);
        return investedAmount > 0
                ? calculateProfitLoss(trade) / investedAmount * 100.0 : 0;
    }

    static long calculateHoldingDays(long openedAt, long now) {
        if (openedAt <= 0) return -1;
        ZoneId zone = ZoneId.systemDefault();
        LocalDate openedDate = Instant.ofEpochMilli(openedAt).atZone(zone).toLocalDate();
        LocalDate currentDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        return Math.max(0, ChronoUnit.DAYS.between(openedDate, currentDate));
    }

    static String toRomanNumeral(int number) {
        if (number <= 0) return String.valueOf(number);

        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] numerals = {"M", "CM", "D", "CD", "C", "XC", "L", "XL",
                "X", "IX", "V", "IV", "I"};
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            while (number >= values[i]) {
                result.append(numerals[i]);
                number -= values[i];
            }
        }
        return result.toString();
    }

    private static float weightedAveragePrice(StockData first, StockData second,
                                              double firstPrice, double secondPrice) {
        if (first.tradeAmount > 0 && second.tradeAmount > 0
                && first.buyPrice > 0 && second.buyPrice > 0) {
            double firstShares = first.tradeAmount / first.buyPrice;
            double secondShares = second.tradeAmount / second.buyPrice;
            double shares = firstShares + secondShares;
            if (shares > 0) {
                return (float) ((firstPrice * firstShares + secondPrice * secondShares) / shares);
            }
        }
        return (float) ((firstPrice + secondPrice) / 2.0);
    }

    private static double getShareCount(StockData trade) {
        if (trade.shares > 0) return trade.shares;
        return trade.tradeAmount > 0 && trade.buyPrice > 0
                ? trade.tradeAmount / trade.buyPrice : 0;
    }

    private static String combineNotes(String first, String second) {
        String firstNote = first == null ? "" : first.trim();
        String secondNote = second == null ? "" : second.trim();
        if (firstNote.isEmpty()) return secondNote;
        if (secondNote.isEmpty()) return firstNote;
        return firstNote + "\n" + secondNote;
    }

    private static java.util.List<String> combineList(java.util.List<String> first,
                                                       java.util.List<String> second) {
        java.util.List<String> combined = new java.util.ArrayList<>();
        if (first != null) combined.addAll(first);
        if (second != null) {
            for (String value : second) {
                if (!combined.contains(value)) combined.add(value);
            }
        }
        return combined;
    }
}