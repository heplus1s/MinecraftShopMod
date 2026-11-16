package dev.jdguo.shop.trade;

import dev.jdguo.shop.bank.PayChoice;
import dev.jdguo.shop.bank.ShopBank;
import dev.jdguo.shop.catalog.ShopConfig;
import dev.jdguo.shop.price.PriceBook;
import dev.jdguo.shop.price.PriceMath;
import dev.jdguo.shop.price.ShopSaved;
import dev.jdguo.shop.support.MapPurse;
import dev.jdguo.shop.support.MemoryBank;
import dev.jdguo.shop.text.ShopText;
import dev.jdguo.shop.ui.ShopQuoteView;
import dev.jdguo.shop.ui.ShopTradeResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeDeskTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String DIAMOND = "minecraft:diamond";
    private static final String IRON = "minecraft:iron_ingot";

    @Test
    void buyingMovesTheSelectedCurrencyAtFourDecimals() {
        MemoryBank bank = new MemoryBank();
        PriceBook book = new PriceBook(ShopSaved.empty());
        AtomicInteger saves = new AtomicInteger();
        ShopSession session = new ShopSession(ShopConfig.defaults(), book, bank, saves::incrementAndGet);
        MapPurse purse = new MapPurse();

        ShopTradeResult result = TradeDesk.buy(session, purse, PLAYER, PayChoice.note(), DIAMOND, 1);

        assertTrue(result.success());
        assertEquals(0, result.charge().compareTo(new BigDecimal("1.0200")));
        assertEquals(0, bank.player("note").compareTo(new BigDecimal("98.9800")));
        assertEquals(0, bank.shop("note").compareTo(new BigDecimal("1.0200")));
        assertEquals(1, purse.plainCount(DIAMOND));
        assertTrue(saves.get() >= 2, "intent and completion must both persist");
        assertTrue(book.lookup(session.config(), DIAMOND).diamondEach().compareTo(BigDecimal.ONE) > 0);
        assertTrue(result.message().contains("钻石等值"));
        assertTrue(result.message().contains("银行券"));
        assertFalse(result.message().contains("FX"));
    }

    @Test
    void anOrderBelowOneNoteIsAccepted() {
        MemoryBank bank = new MemoryBank();
        ShopSession session = new ShopSession(ShopConfig.defaults(), new PriceBook(ShopSaved.empty()), bank, () -> { });
        ShopTradeResult result = TradeDesk.buy(session, new MapPurse(), PLAYER, PayChoice.note(), IRON, 1);
        assertTrue(result.success(), result.message());
        assertEquals(0, result.charge().compareTo(new BigDecimal("0.0612")));
        assertTrue(result.charge().compareTo(BigDecimal.ONE) < 0);
        assertEquals(0, bank.player("note").compareTo(new BigDecimal("99.9388")));
        assertEquals(0, bank.shop("note").compareTo(new BigDecimal("0.0612")));
    }

    @Test
    void aShortBalanceRefusesTheWholeOrder() {
        MemoryBank bank = new MemoryBank();
        bank.player.put("note", new BigDecimal("0.0500"));
        ShopSession session = new ShopSession(ShopConfig.defaults(), new PriceBook(ShopSaved.empty()), bank, () -> { });
        MapPurse purse = new MapPurse();
        ShopTradeResult result = TradeDesk.buy(session, purse, PLAYER, PayChoice.note(), IRON, 1);
        assertEquals("insufficient_funds", result.code());
        assertEquals(0, purse.plainCount(IRON));
        assertEquals(0, bank.player("note").compareTo(new BigDecimal("0.0500")));
        assertEquals(0, bank.shop("note").compareTo(BigDecimal.ZERO));
        assertTrue(result.message().contains("没有少买一部分"));

        bank.player.put("note", new BigDecimal("1.0000"));
        ShopTradeResult stack = TradeDesk.buy(session, purse, PLAYER, PayChoice.note(), IRON, 64);
        assertEquals("insufficient_funds", stack.code());
        assertEquals(0, purse.plainCount(IRON));
        assertEquals(0, bank.player("note").compareTo(new BigDecimal("1.0000")));
    }

    @Test
    void aPersonalNoteDoesNotTouchTheCentralNote() {
        MemoryBank bank = new MemoryBank();
        UUID noteId = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
        PayChoice personal = PayChoice.personal(noteId, "甲");
        bank.player.put(personal.key(), new BigDecimal("5.0000"));
        bank.shop.put(personal.key(), BigDecimal.ZERO);
        bank.rates.put(personal.key(), new BigDecimal("2"));
        bank.notes.put(personal.key(), new ShopBank.PersonalNoteRef(noteId, "甲", true));
        ShopSession session = new ShopSession(ShopConfig.defaults(), new PriceBook(ShopSaved.empty()), bank, () -> { });

        ShopTradeResult result = TradeDesk.buy(session, new MapPurse(), PLAYER, personal, DIAMOND, 1);

        assertTrue(result.success(), result.message());
        assertEquals(0, result.charge().compareTo(new BigDecimal("2.0400")));
        assertEquals(0, bank.player("note").compareTo(new BigDecimal("100.0000")));
        assertEquals(0, bank.shop("note").compareTo(BigDecimal.ZERO));
        assertEquals(0, bank.player(personal.key()).compareTo(new BigDecimal("2.9600")));
        assertEquals(0, bank.shop(personal.key()).compareTo(new BigDecimal("2.0400")));
        assertTrue(result.message().contains("甲"));
        assertFalse(result.message().contains("铜锭"));
    }

    @Test
    void hardCurrencyIsRefused() {
        MemoryBank bank = new MemoryBank();
        ShopSession session = new ShopSession(ShopConfig.defaults(), new PriceBook(ShopSaved.empty()), bank, () -> { });
        PayChoice copper = new PayChoice("copper", "铜锭", false, null);
        ShopTradeResult result = TradeDesk.buy(session, new MapPurse(), PLAYER, copper, DIAMOND, 1);
        assertEquals("hard_currency", result.code());
        assertEquals(0, bank.player("note").compareTo(new BigDecimal("100")));
        assertTrue(result.message().contains("铜锭"));
    }

    @Test
    void sellingPaysFromTheSameCurrencyAndLowersThePrice() {
        MemoryBank bank = new MemoryBank();
        bank.shop.put("note", new BigDecimal("10"));
        PriceBook book = new PriceBook(ShopSaved.empty());
        book.commit(DIAMOND, new BigDecimal("8"));
        ShopSession session = new ShopSession(ShopConfig.defaults(), book, bank, () -> { });
        MapPurse purse = new MapPurse();
        purse.items.put(DIAMOND, 1);

        ShopTradeResult result = TradeDesk.sell(session, purse, PLAYER, PayChoice.note(), DIAMOND, 1);

        assertTrue(result.success());
        assertEquals(0, purse.plainCount(DIAMOND));
        assertEquals(0, bank.player("note").compareTo(new BigDecimal("107.8243")));
        assertEquals(0, bank.shop("note").compareTo(new BigDecimal("2.1757")));
        assertEquals(0, book.lookup(session.config(), DIAMOND).diamondEach().compareTo(new BigDecimal("8").divide(new BigDecimal("1.002"), PriceMath.MC)));
    }

    @Test
    void aMissingBankOrHookDoesNotMoveGoodsOrPrices() {
        for (boolean loaded : new boolean[] {true, false}) {
            MemoryBank bank = new MemoryBank();
            bank.loaded = loaded;
            bank.hook = false;
            PriceBook book = new PriceBook(ShopSaved.empty());
            ShopSession session = new ShopSession(ShopConfig.defaults(), book, bank, () -> { });
            MapPurse purse = new MapPurse();
            ShopTradeResult result = TradeDesk.buy(session, purse, PLAYER, PayChoice.note(), DIAMOND, 1);
            assertFalse(result.success());
            assertEquals(loaded ? "bank_hook_missing" : "bank_absent", result.code());
            assertEquals(0, bank.player("note").compareTo(new BigDecimal("100")));
            assertEquals(0, bank.shop("note").compareTo(BigDecimal.ZERO));
            assertEquals(0, purse.plainCount(DIAMOND));
            assertEquals(0, book.lookup(session.config(), DIAMOND).diamondEach().compareTo(BigDecimal.ONE));
        }
        String missing = ShopText.bankHookMissing();
        assertTrue(missing.contains("储蓄总池"));
        assertTrue(missing.contains("活期存款"));
        assertTrue(missing.contains("发行准备金"));
        assertTrue(missing.contains("个人发行金库"));
        assertTrue(missing.contains("土地出让账户"));
    }

    @Test
    void quotesStillShowTheDiamondPriceWhenTheBankIsAbsent() {
        MemoryBank bank = new MemoryBank();
        bank.loaded = false;
        ShopSession session = new ShopSession(ShopConfig.defaults(), new PriceBook(ShopSaved.empty()), bank, () -> { });
        ShopQuoteView view = TradeDesk.quote(session, DIAMOND, 1, true, PayChoice.note());
        assertTrue(view.listed());
        assertEquals(0, view.diamondEach().compareTo(new BigDecimal("1.02")));
        assertNull(view.charge());
        assertTrue(view.message().contains("钻石等值"));
        assertTrue(view.message().contains("中央银行没有加载"));
    }

    @Test
    void aMissingPriceSaysThereIsNoQuote() {
        MemoryBank bank = new MemoryBank();
        ShopSaved saved = ShopSaved.empty();
        saved.setPrice(DIAMOND, BigDecimal.ZERO);
        ShopSession session = new ShopSession(ShopConfig.defaults(), new PriceBook(saved), bank, () -> { });
        ShopQuoteView view = TradeDesk.quote(session, DIAMOND, 1, true, PayChoice.note());
        assertTrue(view.message().contains("暂无牌价"));
        assertNull(view.diamondEach());
        ShopTradeResult trade = TradeDesk.buy(session, new MapPurse(), PLAYER, PayChoice.note(), "minecraft:not_real", 1);
        assertTrue(trade.message().contains("暂无牌价"));
        assertEquals(0, bank.player("note").compareTo(new BigDecimal("100")));
    }

    @Test
    void theShopCannotBuyWhatItCannotPayFor() {
        MemoryBank bank = new MemoryBank();
        ShopSession session = new ShopSession(ShopConfig.defaults(), new PriceBook(ShopSaved.empty()), bank, () -> { });
        MapPurse purse = new MapPurse();
        purse.items.put(DIAMOND, 4);
        ShopTradeResult result = TradeDesk.sell(session, purse, PLAYER, PayChoice.note(), DIAMOND, 1);
        assertEquals("insufficient_shop", result.code());
        assertEquals(4, purse.plainCount(DIAMOND));
        assertEquals(0, bank.shop("note").compareTo(BigDecimal.ZERO));
        assertTrue(result.message().contains("没有少收一部分"));
    }

    @Test
    void aFailedGiveReturnsThePaymentAndDoesNotSaveANewPrice() {
        MemoryBank bank = new MemoryBank();
        PriceBook book = new PriceBook(ShopSaved.empty());
        AtomicInteger saves = new AtomicInteger();
        ShopSession session = new ShopSession(ShopConfig.defaults(), book, bank, saves::incrementAndGet);
        MapPurse purse = new MapPurse();
        purse.failGive = true;

        ShopTradeResult result = TradeDesk.buy(session, purse, PLAYER, PayChoice.note(), DIAMOND, 1);

        assertEquals("refunded", result.code());
        assertEquals(0, bank.player("note").compareTo(new BigDecimal("100")));
        assertEquals(0, bank.shop("note").compareTo(BigDecimal.ZERO));
        assertEquals(0, purse.plainCount(DIAMOND));
        assertTrue(saves.get() >= 2, "failed delivery must retain a cancelled audit record");
        assertEquals(0, book.lookup(session.config(), DIAMOND).diamondEach().compareTo(BigDecimal.ONE));
    }

    @Test
    void manyPurchasesCanLeaveTheAnchor() {
        MemoryBank bank = new MemoryBank();
        bank.player.put("note", new BigDecimal("100000"));
        PriceBook book = new PriceBook(ShopSaved.empty());
        ShopSession session = new ShopSession(ShopConfig.defaults(), book, bank, () -> { });
        MapPurse purse = new MapPurse();
        ShopTradeResult result = TradeDesk.buy(session, purse, PLAYER, PayChoice.note(), DIAMOND, 64);
        assertTrue(result.success(), result.message());
        BigDecimal end = book.lookup(session.config(), DIAMOND).diamondEach();
        BigDecimal expected = PriceMath.buy(BigDecimal.ONE, session.config().stepFraction(),
                session.config().minDiamondPrice(), 64).end();
        assertEquals(0, end.compareTo(expected));
        assertTrue(end.compareTo(new BigDecimal("1.1")) > 0);
    }
}
