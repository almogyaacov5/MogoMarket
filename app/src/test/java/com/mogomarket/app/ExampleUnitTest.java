package com.mogomarket.app;

import org.junit.Test;

import java.util.List;
import java.util.Calendar;

import static org.junit.Assert.*;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {
    @Test
    public void addition_isCorrect() {
        assertEquals(4, 2 + 2);
    }

    @Test
    public void combineUsesShareWeightedPricesAndAddsInvestedAmount() {
        StockData first = new StockData("AAPL", 10f, 12f);
        first.tradeAmount = 100;
        first.sellPrice = 12;
        StockData second = new StockData("AAPL", 20f, 22f);
        second.tradeAmount = 200;
        second.sellPrice = 22;

        StockData combined = StockData.combine(first, second, true);

        assertEquals(15f, combined.buyPrice, 0.001f);
        assertEquals(17, combined.sellPrice, 0.001);
        assertEquals(300, combined.tradeAmount, 0.001);
        assertEquals(40, StockData.calculateProfitLoss(combined), 0.001);
        assertEquals(20, combined.shares, 0.001);
    }

    @Test
    public void dailySummaryIsScheduledOnlyTuesdayThroughSaturday() {
        assertFalse(DailySummaryEmailService.isSummaryDay(Calendar.SUNDAY));
        assertFalse(DailySummaryEmailService.isSummaryDay(Calendar.MONDAY));
        assertTrue(DailySummaryEmailService.isSummaryDay(Calendar.TUESDAY));
        assertTrue(DailySummaryEmailService.isSummaryDay(Calendar.WEDNESDAY));
        assertTrue(DailySummaryEmailService.isSummaryDay(Calendar.THURSDAY));
        assertTrue(DailySummaryEmailService.isSummaryDay(Calendar.FRIDAY));
        assertTrue(DailySummaryEmailService.isSummaryDay(Calendar.SATURDAY));
    }

    @Test
    public void feesAndTaxesAreIncludedInNetProfitLoss() {
        StockData trade = new StockData("AAPL", 10f, 12f);
        trade.tradeAmount = 100;
        trade.sellPrice = 12;
        trade.entryFees = 1;
        trade.exitFees = 2;
        trade.taxes = 3;

        assertEquals(14, StockData.calculateProfitLoss(trade), 0.001);
    }

    @Test
    public void missedProfitLossUsesFiveDayHighAndCannotBeNegative() {
        StockData trade = new StockData("AAPL", 10f, 10f);
        trade.tradeAmount = 100;
        trade.sellPrice = 12;

        assertEquals(30, MissedProfitLossFetcher.calculate(trade, 15), 0.001);
        assertEquals(0, MissedProfitLossFetcher.calculate(trade, 11), 0.001);
    }

    @Test
    public void tradeCsvRoundTripsFeesNotesAndIdentifiers() throws Exception {
        StockData trade = new StockData("AAPL", 10f, 12f);
        trade.closedTradeId = "trade-1";
        trade.sellPrice = 12;
        trade.tradeAmount = 100;
        trade.entryFees = 1.25;
        trade.exitFees = 2.5;
        trade.taxes = 3;
        trade.notes = "breakout, \"strong\"\nsecond line";
        trade.sector = "Technology";
        trade.missedProfitLoss = 15.5;
        trade.missedPnlStatus = "AVAILABLE";

        List<StockData> imported = TradeCsv.importTrades(
                TradeCsv.exportTrades(java.util.Collections.singletonList(trade)));

        assertEquals(1, imported.size());
        assertEquals("trade-1", imported.get(0).closedTradeId);
        assertEquals(trade.notes, imported.get(0).notes);
        assertEquals("Technology", imported.get(0).sector);
        assertEquals(1.25, imported.get(0).entryFees, 0.001);
        assertEquals(15.5, imported.get(0).missedProfitLoss, 0.001);
    }

    @Test
    public void tradeCsvStillImportsPreviousVersionWithoutSectorColumn() throws Exception {
        String legacyCsv = "\"id\",\"symbol\",\"buy_price\",\"sell_price\",\"amount\","
                + "\"entry_fees\",\"exit_fees\",\"taxes\",\"exit_reason\",\"strategy\","
                + "\"notes\",\"opened_at\",\"closed_at\",\"missed_pnl\"\n"
                + "\"old-id\",\"AAPL\",\"10\",\"12\",\"100\",\"0\",\"0\",\"0\","
                + "\"MANUAL_EXIT\",\"Breakout\",\"legacy note\",\"1000\",\"2000\",\"\"";

        List<StockData> imported = TradeCsv.importTrades(legacyCsv);

        assertEquals(1, imported.size());
        assertEquals("legacy note", imported.get(0).notes);
        assertEquals("Other", imported.get(0).sector);
    }

    @Test
    public void tradeExcelExportsAndImportsARealXlsxWorkbook() throws Exception {
        StockData trade = new StockData("AAPL", 10f, 12f);
        trade.closedTradeId = "trade-xlsx";
        trade.sellPrice = 12;
        trade.tradeAmount = 100;
        trade.sector = "Technology";
        trade.notes = "breakout & pullback";
        java.io.ByteArrayOutputStream workbook = new java.io.ByteArrayOutputStream();

        TradeExcel.exportTrades(java.util.Collections.singletonList(trade), workbook);
        List<StockData> imported = TradeExcel.importTrades(
                new java.io.ByteArrayInputStream(workbook.toByteArray()));

        assertEquals(1, imported.size());
        assertEquals("trade-xlsx", imported.get(0).closedTradeId);
        assertEquals("Technology", imported.get(0).sector);
        assertEquals(trade.notes, imported.get(0).notes);
    }

    @Test
    public void portfolioBetaMatchesBenchmarkForIdenticalReturns() {
        java.util.Map<Long, Double> benchmark = new java.util.HashMap<>();
        java.util.Map<Long, Double> holding = new java.util.HashMap<>();
        for (long day = 0; day < 25; day++) {
            double price = day % 2 == 0 ? 100 + day : 110 + day;
            benchmark.put(day, price);
            holding.put(day, price * 2);
        }
        java.util.Map<String, java.util.Map<Long, Double>> histories =
                new java.util.HashMap<>();
        histories.put("SPY", benchmark);
        histories.put("AAPL", holding);
        java.util.Map<String, Double> weights = new java.util.HashMap<>();
        weights.put("AAPL", 1.0);

        assertEquals(1.0, PortfolioBetaFetcher.calculateBeta(histories, weights), 0.001);
    }

    @Test
    public void tradeNumbersUseRomanNumerals() {
        assertEquals("I", StockData.toRomanNumeral(1));
        assertEquals("II", StockData.toRomanNumeral(2));
        assertEquals("IV", StockData.toRomanNumeral(4));
        assertEquals("XII", StockData.toRomanNumeral(12));
    }

    @Test
    public void riskRewardRatioUsesEntryStopAndTargetPrices() {
        StockData trade = new StockData("AAPL", 100f, 100f);
        trade.stopLossPrice = 90f;
        trade.targetPrice = 120f;

        assertEquals(2.0, StockData.calculateRiskRewardRatio(trade), 0.001);
    }

    @Test
    public void riskRewardRatioIsUnavailableWithoutValidStopAndTarget() {
        StockData trade = new StockData("AAPL", 100f, 100f);
        trade.targetPrice = 120f;

        assertTrue(Double.isNaN(StockData.calculateRiskRewardRatio(trade)));
    }

    @Test
    public void closedTradeAnalyticsCalculatesProfitFactorHoldingAndDrawdown() {
        StockData win = closedTrade(100f, 110f, 1, 10);
        StockData loss = closedTrade(100f, 90f, 2, 11);
        StockData smallerLoss = closedTrade(100f, 95f, 3, 12);
        ClosedTradeAnalytics analytics = ClosedTradeAnalytics.calculate(
                java.util.Arrays.asList(win, loss, smallerLoss));

        assertEquals(2.0 / 3.0, analytics.profitFactor, 0.001);
        assertEquals(10, analytics.averageWin, 0.001);
        assertEquals(7.5, analytics.averageLoss, 0.001);
        assertEquals(2, analytics.averageHoldingDays, 0.001);
        assertEquals(15, analytics.maxDrawdown, 0.001);
    }

    @Test
    public void dailyPortfolioProfitLossUsesPriceChangeAndPositionSize() {
        assertEquals(20, PortfolioDailyPerformance.calculatePnl(100, 50, 50, 60), 0.001);
        assertEquals(-10, PortfolioDailyPerformance.calculatePnl(100, 50, 60, 55), 0.001);
    }

    @Test
    public void closedTradeReturnPercentIncludesFeesAndPositionSize() {
        StockData trade = new StockData("TEST", 100f, 110f);
        trade.sellPrice = 110;
        trade.tradeAmount = 200;
        trade.entryFees = 2;
        trade.exitFees = 2;

        assertEquals(8, StockData.calculateProfitLossPercent(trade), 0.001);
    }

    @Test
    public void dailyQuotePercentageUsesCurrentPriceAndPreviousClose() {
        assertEquals(2.0, DailyQuoteMath.percentChange(102, 100), 0.001);
        assertEquals(-2.0, DailyQuoteMath.percentChange(98, 100), 0.001);
        assertTrue(Double.isNaN(DailyQuoteMath.percentChange(100, 0)));
        assertTrue(Double.isNaN(DailyQuoteMath.percentChange(Double.NaN, 100)));
    }

    @Test
    public void yahooQuoteUsesPreviousSessionCloseBeforeHistoricalFallback() {
        double previousClose = YahooQuoteMath.selectPreviousClose(200, 190, 150);
        assertEquals(2, DailyQuoteMath.percentChange(204, previousClose), 0.001);
        assertEquals(190, YahooQuoteMath.selectPreviousClose(Double.NaN, 190, 150), 0.001);
        assertEquals(150, YahooQuoteMath.selectPreviousClose(Double.NaN, 0, 150), 0.001);
    }

    @Test
    public void blinkScreenshotParserExtractsTradeFromPositionSummary() {
        TradeScreenshotParser.ParsedTrade trade = TradeScreenshotParser.parse(
                "SPY\n7.7155\n$5,862.31\n$739.33\n92.09%\n"
                        + "$0.00 (0%)\n$158.04 (2.77%)");

        assertNotNull(trade);
        assertEquals("SPY", trade.symbol);
        assertEquals(7.7155, trade.shares, 0.00001);
        assertEquals(739.33, trade.buyPrice, 0.01);
        assertEquals(5704.27, trade.tradeAmount, 0.02);
    }

    private StockData closedTrade(float buyPrice, float sellPrice, long openedDaysBeforeClose,
                                  long closedDay) {
        StockData trade = new StockData("TEST", buyPrice, sellPrice);
        trade.sellPrice = sellPrice;
        trade.tradeAmount = 100;
        trade.closedAt = closedDay * 86_400_000L;
        trade.openedAt = trade.closedAt - openedDaysBeforeClose * 86_400_000L;
        return trade;
    }
}