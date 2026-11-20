package dev.jdguo.shop.price;

import com.google.gson.JsonParser;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShopStockTest {
    final String item = "minecraft:diamond";
    final UUID player = UUID.randomUUID();
    void provision(ShopSaved saved, long n) { saved.applyStockAdjustment(saved.beginStockAdjustment(item, n, player, "test provision")); }
    void trade(ShopSaved saved, String ref, int n, boolean buy) {
        saved.beginTrade(ref, player, item, n, buy, "note", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE);
    }
    @Test void missingStockIsZeroAndSaleThenBuyConserveGoods() {
        var s = ShopSaved.empty(); assertEquals(0, s.stock(item));
        assertThrows(IllegalArgumentException.class, () -> trade(s, "buy_empty", 1, true));
        trade(s, "sell", 4, false); assertEquals(0, s.stock(item));
        s.applyTradeStock("sell"); s.applyTradeStock("sell"); s.tradeState("sell", "completed");
        assertEquals(4, s.stock(item)); assertEquals(1, s.stockRevision(item));
        trade(s, "buy", 3, true); s.applyTradeStock("buy"); s.applyTradeStock("buy");
        assertEquals(1, s.stock(item)); assertEquals(2, s.stockRevision(item));
        var restored = ShopSaved.parse(s.toJson()); restored.applyTradeStock("buy"); assertEquals(1, restored.stock(item));
    }
    @Test void negativeOverflowAndConflictingIntentsFailClosed() {
        var s = ShopSaved.empty(); provision(s, Long.MAX_VALUE);
        assertThrows(ArithmeticException.class, () -> trade(s, "overflow", 1, false));
        trade(s, "a", 1, true);
        assertThrows(IllegalStateException.class, () -> trade(s, "b", 1, true));
        assertThrows(IllegalStateException.class, () -> s.beginStockAdjustment(item, 1, player, "test"));
        assertThrows(IllegalArgumentException.class, () -> s.beginStockAdjustment("x", -1, player, "test"));
    }
    @Test void adjustmentAuditSurvivesFailureAndRetriesAbsoluteValue() {
        var s = ShopSaved.empty(); String ref = s.beginStockAdjustment(item, 20, null, "console seed");
        assertEquals(ref, s.pendingItem(item)); assertEquals(0, s.stock(item));
        assertEquals(ref, s.beginStockAdjustment(item, 20, null, "console seed"));
        s.applyStockAdjustment(ref); s.stockAdjustmentPending(ref);
        assertEquals(20, s.stock(item)); assertEquals(ref, s.pendingItem(item));
        var restored = ShopSaved.parse(s.toJson());
        assertEquals(ref, restored.beginStockAdjustment(item, 20, null, "console seed"));
        restored.applyStockAdjustment(ref); assertEquals(20, restored.stock(item)); assertEquals(1, restored.stockRevision(item));
        assertNull(restored.pendingItem(item)); assertTrue(restored.toJson().contains("console seed"));
    }
    @Test void legacyPendingAndCompletedRowsNeverRetroactivelyCreateStock() {
        var s = ShopSaved.empty(); trade(s, "old", 2, false);
        var doc = JsonParser.parseString(s.toJson()).getAsJsonObject(); doc.addProperty("version", 3);
        var row = doc.getAsJsonObject("trades").getAsJsonObject("old");
        for (String key : new String[]{"stockBefore", "stockAfter", "stockRevisionBefore", "stockRevisionAfter"}) row.remove(key);
        s = ShopSaved.parse(doc.toString()); s.applyTradeStock("old"); s.tradeState("old", "completed");
        assertEquals(0, ShopSaved.parse(s.toJson()).stock(item));
    }
    @Test void malformedOrTamperedStockFailsRatherThanClamping() {
        var s = ShopSaved.empty(); provision(s, 2); trade(s, "a", 1, true);
        var doc = JsonParser.parseString(s.toJson()).getAsJsonObject();
        doc.getAsJsonObject("trades").getAsJsonObject("a").addProperty("stockAfter", 7);
        var corrupt = ShopSaved.parse(doc.toString()); assertThrows(IllegalStateException.class, () -> corrupt.verifyTradeStock("a"));
        doc.getAsJsonObject("stock").getAsJsonObject(item).addProperty("count", "0.5");
        var fractional = ShopSaved.parse(doc.toString()); assertThrows(ArithmeticException.class, () -> fractional.stock(item));
    }
}
