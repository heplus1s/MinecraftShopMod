package dev.jdguo.shop.trade;

import dev.jdguo.shop.bank.PayChoice;
import dev.jdguo.shop.catalog.ShopConfig;
import dev.jdguo.shop.price.*;
import dev.jdguo.shop.support.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class TradeJournalTest {
    final UUID player = UUID.randomUUID();
    final String item = "minecraft:diamond";
    final MemoryBank bank = new MemoryBank();
    final MapPurse purse = new MapPurse();
    final PriceBook book = new PriceBook(ShopSaved.empty());
    ShopSession session(Runnable persist) { return new ShopSession(ShopConfig.defaults(), book, bank, persist); }
    void same(String n, BigDecimal amount) { assertEquals(0, new BigDecimal(n).compareTo(amount)); }

    @Test void successfulBankWithLostResponseDoesNotRepeatDebit() {
        bank.unknownAfterPostOnce = true;
        var session = session(() -> {});
        assertEquals("settlement_pending", TradeDesk.buy(session, purse, player, PayChoice.note(), item, 1).code());
        String ref = book.saved().pendingTrade(player);
        same("98.98", bank.player("note"));
        assertEquals(0, purse.plainCount(item));
        assertTrue(TradeJournal.resume(session, purse, player, ref).success());
        assertTrue(TradeJournal.resume(session, purse, player, ref).success());
        assertEquals(1, bank.referencedCalls);
        assertEquals(1, purse.plainCount(item));
        same("98.98", bank.player("note"));
    }
    @Test void inventoryUncertaintyNeverRefundsOrRedelivers() {
        purse.uncertainOnTransfer = true;
        var session = session(() -> {});
        assertEquals("settlement_pending", TradeDesk.buy(session, purse, player, PayChoice.note(), item, 1).code());
        String ref = book.saved().pendingTrade(player);
        assertEquals(1, purse.plainCount(item)); assertEquals(0, bank.rollbackCalls);
        assertFalse(TradeJournal.resume(session, purse, player, ref).success());
        purse.uncertainAfterTransfer = false;
        assertTrue(TradeJournal.resume(session, purse, player, ref).success());
        assertEquals(1, purse.plainCount(item)); assertEquals(0, bank.rollbackCalls);
    }
    @Test void failedDeliveryUsesExactReferenceRollback() {
        purse.failGive = true;
        var result = TradeDesk.buy(session(() -> {}), purse, player, PayChoice.note(), item, 1);
        assertEquals("refunded", result.code()); assertEquals(1, bank.rollbackCalls);
        same("100", bank.player("note")); same("0", bank.shop("note"));
        assertEquals(0, purse.plainCount(item)); assertNull(book.saved().pendingTrade(player));
    }
    @Test void journalFailureBeforeEffectsLeavesMoneyAndGoodsAlone() {
        var result = TradeDesk.buy(session(() -> { throw new IllegalStateException("disk full"); }), purse,
                player, PayChoice.note(), item, 1);
        assertEquals("settlement_pending", result.code()); assertEquals(0, bank.referencedCalls);
        same("100", bank.player("note")); assertEquals(0, purse.plainCount(item));
    }
    @Test void finalJournalFailureRecoversFromDurableInventoryReceipt() {
        AtomicInteger writes = new AtomicInteger();
        var session = session(() -> { if (writes.incrementAndGet() == 6) throw new IllegalStateException("final fsync"); });
        assertEquals("settlement_pending", TradeDesk.buy(session, purse, player, PayChoice.note(), item, 1).code());
        String ref = book.saved().pendingTrade(player); assertNotNull(ref);
        assertEquals(1, purse.plainCount(item));
        // JSON re-read simulates restored shop journal; bank and inventory receipts persist independently.
        var restored = new ShopSession(ShopConfig.defaults(), new PriceBook(ShopSaved.parse(book.saved().toJson())), bank, () -> {});
        assertTrue(TradeJournal.resume(restored, purse, player, ref).success());
        assertEquals(1, purse.plainCount(item)); assertEquals(1, bank.referencedCalls);
    }
    @Test void saleHonorsChosenCurrencyAndRequiresReportedCapacity() {
        var personal = PayChoice.personal(UUID.randomUUID(), "私币");
        bank.rates.put(personal.key(), new BigDecimal("9"));
        bank.shop.put("note", new BigDecimal("1000"));
        purse.items.put(item, 1);
        var session = session(() -> {});
        assertEquals("insufficient_shop", TradeDesk.sell(session, purse, player, personal, item, 1).code());
        assertEquals(1, purse.plainCount(item));
        bank.shop.put(personal.key(), BigDecimal.TEN);
        assertTrue(TradeDesk.sell(session, purse, player, personal, item, 1).success());
        same("1000", bank.shop("note")); same("100", bank.player("note"));
        assertTrue(bank.player(personal.key()).signum() > 0);
    }
    @Test void rejectedSaleRestoresItemsOnce() {
        bank.shop.put("note", BigDecimal.TEN); bank.rejectReferenced = true;
        purse.items.put(item, 2);
        var result = TradeDesk.sell(session(() -> {}), purse, player, PayChoice.note(), item, 1);
        assertEquals("bank_rejected", result.code()); assertEquals(2, purse.plainCount(item));
        same("100", bank.player("note")); assertNull(book.saved().pendingTrade(player));
    }
    @Test void uncertainOrderLocksItemAgainstOtherPlayers() {
        purse.uncertainOnTransfer = true;
        var session = session(() -> {});
        TradeDesk.buy(session, purse, player, PayChoice.note(), item, 1);
        assertEquals("settlement_pending", TradeDesk.buy(session, new MapPurse(), UUID.randomUUID(), PayChoice.note(), item, 1).code());
        assertEquals(1, bank.referencedCalls);
    }
    @Test void failedBankCompletionDoesNotRedeliverAndRetriesAcknowledgement() {
        bank.failComplete = true;
        var session = session(() -> {});
        assertEquals("settlement_pending", TradeDesk.buy(session, purse, player, PayChoice.note(), item, 1).code());
        String ref = book.saved().pendingTrade(player);
        assertEquals(1, purse.plainCount(item)); assertEquals(1, bank.referencedCalls);
        bank.failComplete = false;
        assertTrue(TradeJournal.resume(session, purse, player, ref).success());
        assertEquals(1, purse.plainCount(item)); assertEquals(1, bank.referencedCalls);
        assertEquals(2, bank.completionCalls); same("98.98", bank.player("note"));
    }

}
