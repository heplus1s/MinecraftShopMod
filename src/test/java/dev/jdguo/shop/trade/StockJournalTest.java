package dev.jdguo.shop.trade;

import com.google.gson.JsonParser;
import dev.jdguo.shop.bank.PayChoice;
import dev.jdguo.shop.catalog.ShopConfig;
import dev.jdguo.shop.price.*;
import dev.jdguo.shop.support.*;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StockJournalTest {
    final UUID player = UUID.randomUUID();
    final String item = "minecraft:diamond";
    final MemoryBank bank = new MemoryBank();
    final MapPurse purse = new MapPurse();
    final ShopSaved saved = ShopSaved.empty();
    ShopSession session(Runnable persist) { return new ShopSession(ShopConfig.defaults(), new PriceBook(saved), bank, persist); }
    void seed(long n) { saved.applyStockAdjustment(saved.beginStockAdjustment(item,n,player,"fixture")); }
    TradeJournal.Result begin(ShopSession session, boolean buy) {
        return TradeJournal.begin(session,purse,player,item,1,buy,PayChoice.note(),BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE);
    }
    @Test void journalRechecksInventoryWithoutRelyingOnQuote() {
        var r=begin(session(()->{}),true);assertEquals("stock_insufficient",r.code());assertEquals(0,bank.referencedCalls);
    }
    @Test void successfulPurchaseDecrementsExactlyOnceAndDoesNotFreezeAnchor() {
        seed(2);var session=session(()->{});var result=begin(session,true);assertTrue(result.success());
        assertEquals(1,saved.stock(item));assertFalse(saved.read(item).present());
        assertTrue(TradeJournal.resume(session,purse,player,result.reference()).success());assertEquals(1,saved.stock(item));
    }
    @Test void successfulSaleAddsButRejectedSaleDoesNot() {
        purse.items.put(item,2);bank.shop.put("note",BigDecimal.TEN);
        assertTrue(begin(session(()->{}),false).success());assertEquals(1,saved.stock(item));
        bank.rejectReferenced=true;assertFalse(begin(session(()->{}),false).success());assertEquals(1,saved.stock(item));
    }
    @Test void failedFinalSaveReplaysAbsoluteStockWithoutRedelivery() {
        seed(3);var session=session(()->{
            var pending=saved.pendingTrade(player);
            if(pending==null && saved.stock(item)==2)throw new IllegalStateException("final persistence");
        });
        var result=begin(session,true);assertEquals("settlement_pending",result.code());assertEquals(2,saved.stock(item));
        var restored=new ShopSession(ShopConfig.defaults(),new PriceBook(ShopSaved.parse(saved.toJson())),bank,()->{});
        assertTrue(TradeJournal.resume(restored,purse,player,result.reference()).success());
        assertEquals(2,restored.book().saved().stock(item));assertEquals(1,purse.plainCount(item));assertEquals(1,bank.referencedCalls);
    }
    @Test void failedPreparedSaveAndBankFailureLeaveStockUnchanged() {
        seed(2);assertEquals("settlement_pending",begin(session(()->{throw new IllegalStateException();}),true).code());
        assertEquals(2,saved.stock(item));assertEquals(0,bank.referencedCalls);
    }
    @Test void rejectedDeliveryRefundLeavesStockUnchanged() {
        seed(2);purse.failGive=true;var result=begin(session(()->{}),true);
        assertEquals("refunded",result.code());assertEquals(2,saved.stock(item));assertEquals(1,bank.rollbackCalls);
    }
    @Test void lostBankResponseLocksStockUntilSameTradeResumes() {
        seed(2);bank.unknownAfterPostOnce=true;var session=session(()->{});var result=begin(session,true);
        assertEquals("settlement_pending",result.code());assertEquals(2,saved.stock(item));
        assertEquals(result.reference(),saved.pendingItem(item));
        assertTrue(TradeJournal.resume(session,purse,player,result.reference()).success());
        assertEquals(1,saved.stock(item));assertEquals(1,bank.referencedCalls);
    }
    @Test void legacyPendingPurchaseCanFinishAtZeroStock() {
        seed(1);saved.beginTrade("old",player,item,1,true,"note",BigDecimal.ONE,BigDecimal.ONE,new BigDecimal("1.002"));
        var doc=JsonParser.parseString(saved.toJson()).getAsJsonObject();doc.remove("stock");doc.remove("stockAdjustments");
        var row=doc.getAsJsonObject("trades").getAsJsonObject("old");
        for(String k:new String[]{"stockBefore","stockAfter","stockRevisionBefore","stockRevisionAfter"})row.remove(k);
        var legacy=new ShopSession(ShopConfig.defaults(),new PriceBook(ShopSaved.parse(doc.toString())),bank,()->{});
        assertTrue(TradeJournal.resume(legacy,purse,player,"old").success());assertEquals(0,legacy.book().saved().stock(item));
        assertEquals(0,new BigDecimal("1.002").compareTo(legacy.book().saved().read(item).value()));
    }
}
