package dev.jdguo.shop.trade;

import com.google.gson.JsonObject;
import dev.jdguo.shop.bank.PayChoice;
import dev.jdguo.shop.catalog.ShopConfig;
import dev.jdguo.shop.price.PriceBook;
import dev.jdguo.shop.price.ShopSaved;
import dev.jdguo.shop.support.MapPurse;
import dev.jdguo.shop.support.MemoryBank;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BulkSettlementTest {
    static final String ITEM = "minecraft:cobblestone";
    final ShopConfig config = ShopConfig.defaults();
    final UUID player = UUID.randomUUID();
    final MemoryBank bank = new MemoryBank();
    final MapPurse inner = new MapPurse();
    final JsonObject plan = new JsonObject();
    final ItemPurse boxes = new ItemPurse() {
        @Override public ItemPurse forBulk(JsonObject saved) { return plan.equals(saved) ? this : null; }
        @Override public int plainCount(String item) { return inner.plainCount(item); }
        @Override public int roomFor(String item) { return 0; }
        @Override public boolean durableDelivery() { return true; }
        @Override public Delivery transferReferenced(String ref, String item, int count, boolean give) {
            return inner.transferReferenced(ref, item, count, give);
        }
        @Override public String receiptStatus(String ref) { return inner.receiptStatus(ref); }
        @Override public void forgetReceipt(String ref) { inner.forgetReceipt(ref); }
        @Override public void give(String item, int count) { throw new AssertionError("not loose"); }
        @Override public void takePlain(String item, int count) { throw new AssertionError("not loose"); }
    };

    BulkSettlementTest() {
        bank.shop.put("note", new BigDecimal("1000"));
        plan.addProperty("test-box-plan", "saved exactly");
        inner.items.put(ITEM, 1728);
    }

    ShopSession session(ShopSaved saved) { return new ShopSession(config, new PriceBook(saved), bank, () -> {}); }

    @Test void bulkAndOrdinaryLimitsAreSeparateAndBothUseStock() {
        var saved = ShopSaved.empty();
        var session = session(saved);
        assertEquals("bad_count", TradeDesk.quote(session, ITEM, 1728, false, PayChoice.note()).code());
        var quote = TradeDesk.quoteBulkSale(session, ITEM, 1728);
        assertEquals("ready", quote.code());
        assertTrue(TradeDesk.sellBulk(session, boxes, player, ITEM, 1728, quote.charge(), plan).success());
        assertEquals(1728, saved.stock(ITEM));
        assertEquals(0, inner.plainCount(ITEM));
        assertEquals(1, saved.marketTrades().size());
        assertEquals("system_shop", saved.marketTrades().get(0).getAsJsonObject().get("source").getAsString());
        assertEquals(1, bank.referencedCalls);
        assertEquals("ready", TradeDesk.quote(session, ITEM, 20, true, PayChoice.note()).code());
    }

    @Test void changedQuoteAndConfirmedBankRejectionDoNotConsumeBoxes() {
        var saved = ShopSaved.empty();
        var session = session(saved);
        var quote = TradeDesk.quoteBulkSale(session, ITEM, 1728);
        assertEquals("bulk_quote_changed",
                TradeDesk.sellBulk(session, boxes, player, ITEM, 1728, quote.charge().add(BigDecimal.ONE), plan).code());
        assertEquals(0, bank.referencedCalls);
        bank.rejectReferenced = true;
        assertFalse(TradeDesk.sellBulk(session, boxes, player, ITEM, 1728, quote.charge(), plan).success());
        assertEquals(1728, inner.plainCount(ITEM));
        assertEquals(0, saved.stock(ITEM));
        assertEquals(0, saved.marketTrades().size());
    }

    @Test void recoveredBulkCannotFallThroughToLooseInventory() {
        var saved = ShopSaved.empty();
        var session = session(saved);
        var quote = TradeDesk.quoteBulkSale(session, ITEM, 1728);
        bank.unknownAfterPostOnce = true;
        assertEquals("settlement_pending", TradeDesk.sellBulk(session, boxes, player, ITEM, 1728, quote.charge(), plan).code());
        String ref = saved.pendingTrade(player);
        assertNotNull(ref);
        assertEquals(plan, saved.trade(ref).getAsJsonObject("boxPlan"));
        var restart = session(ShopSaved.parse(saved.toJson()));
        var loose = new MapPurse();
        loose.items.put(ITEM, 1728);
        assertFalse(TradeJournal.resume(restart, loose, player, ref).success());
        assertEquals(1728, loose.plainCount(ITEM));
        assertTrue(TradeJournal.resume(restart, boxes, player, ref).success());
        assertTrue(TradeJournal.resume(restart, boxes, player, ref).success());
        assertEquals(0, inner.plainCount(ITEM));
        assertEquals(1728, restart.book().saved().stock(ITEM));
        assertFalse(restart.book().saved().trade(ref).has("boxPlan"));
        assertEquals(1, bank.referencedCalls);
        assertEquals(1, restart.book().saved().marketTrades().size());
    }
}
