package dev.jdguo.shop.trade;

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
import java.math.BigInteger;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeDeskTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String DIAMOND = "minecraft:diamond";
    private static final String IRON = "minecraft:iron_ingot";

    @Test
    void buyingMovesNotesInsideThePoolAndRaisesThePrice() {
        MemoryBank bank = new MemoryBank();
        PriceBook book = new PriceBook(ShopSaved.empty());
        AtomicInteger saves = new AtomicInteger();
        ShopSession session = new ShopSession(ShopConfig.defaults(), book, bank, saves::incrementAndGet);
        MapPurse purse = new MapPurse();

        ShopTradeResult result = TradeDesk.buy(session, purse, PLAYER, DIAMOND, 1);

        assertTrue(result.success());
        assertEquals(new BigInteger("1"), result.notesCharged());
        assertEquals(new BigInteger("99"), bank.player);
        assertEquals(new BigInteger("1"), bank.shop);
        assertEquals(new BigInteger("100"), bank.savings);
        assertEquals(1, purse.plainCount(DIAMOND));
        assertEquals(1, saves.get());
        assertTrue(book.lookup(session.config(), DIAMOND).diamondEach().compareTo(BigDecimal.ONE) > 0);
        assertTrue(result.message().contains("钻石等值"));
        assertTrue(result.message().contains("银行券"));
        assertFalse(result.message().contains("FX"));
        assertFalse(result.message().contains("NBT"));
    }

    @Test
    void sellingPaysFromTheOperatingAccountAndLowersThePrice() {
        MemoryBank bank = new MemoryBank();
        bank.shop = new BigInteger("10");
        bank.savings = bank.player.add(bank.shop);
        PriceBook book = new PriceBook(ShopSaved.empty());
        book.commit(DIAMOND, new BigDecimal("8"));
        ShopSession session = new ShopSession(ShopConfig.defaults(), book, bank, () -> { });
        MapPurse purse = new MapPurse();
        purse.items.put(DIAMOND, 1);

        ShopTradeResult result = TradeDesk.sell(session, purse, PLAYER, DIAMOND, 1);

        assertTrue(result.success());
        assertEquals(0, purse.plainCount(DIAMOND));
        assertEquals(new BigInteger("108"), bank.player);
        assertEquals(new BigInteger("2"), bank.shop);
        assertTrue(book.lookup(session.config(), DIAMOND).diamondEach().compareTo(new BigDecimal("8")) < 0);
        assertTrue(book.lookup(session.config(), DIAMOND).diamondEach().compareTo(new BigDecimal("2")) > 0);
        assertTrue(result.message().contains("商店") || result.message().contains("出售"));
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
            ShopTradeResult result = TradeDesk.buy(session, purse, PLAYER, DIAMOND, 1);
            assertFalse(result.success());
            assertEquals(loaded ? "bank_hook_missing" : "bank_absent", result.code());
            assertEquals(new BigInteger("100"), bank.player);
            assertEquals(BigInteger.ZERO, bank.shop);
            assertEquals(0, purse.plainCount(DIAMOND));
            assertFalse(book.lookup(session.config(), DIAMOND).diamondEach().compareTo(BigDecimal.ONE) != 0);
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
        ShopQuoteView view = TradeDesk.quote(session, DIAMOND, 1, true);
        assertTrue(view.listed());
        assertEquals(BigDecimal.ONE, view.diamondEach());
        assertEquals(null, view.notesExact());
        assertTrue(view.message().contains("钻石等值"));
        assertTrue(view.message().contains("中央银行没有加载"));
    }

    @Test
    void aCheapOrderWaitsUntilItIsWorthOneNote() {
        MemoryBank bank = new MemoryBank();
        ShopSession session = new ShopSession(ShopConfig.defaults(), new PriceBook(ShopSaved.empty()), bank, () -> { });
        ShopTradeResult small = TradeDesk.buy(session, new MapPurse(), PLAYER, IRON, 1);
        assertEquals("below_unit", small.code());
        assertTrue(small.minimumCount() > 1);
        assertEquals(new BigInteger("100"), bank.player);

        ShopTradeResult enough = TradeDesk.buy(session, new MapPurse(), PLAYER, IRON, small.minimumCount());
        assertTrue(enough.success(), enough.message());
        assertTrue(enough.notesCharged().compareTo(BigInteger.ONE) >= 0);
        assertEquals(bank.player.add(bank.shop), new BigInteger("100"));
    }

    @Test
    void theShopCannotBuyWhatItCannotPayFor() {
        MemoryBank bank = new MemoryBank();
        ShopSession session = new ShopSession(ShopConfig.defaults(), new PriceBook(ShopSaved.empty()), bank, () -> { });
        MapPurse purse = new MapPurse();
        purse.items.put(DIAMOND, 4);
        ShopTradeResult result = TradeDesk.sell(session, purse, PLAYER, DIAMOND, 1);
        assertEquals("insufficient_shop", result.code());
        assertEquals(4, purse.plainCount(DIAMOND));
        assertEquals(BigInteger.ZERO, bank.shop);
        assertTrue(result.message().contains("商店经营账户"));
    }

    @Test
    void aFailedGiveReturnsThePaymentAndDoesNotSaveANewPrice() {
        MemoryBank bank = new MemoryBank();
        PriceBook book = new PriceBook(ShopSaved.empty());
        AtomicInteger saves = new AtomicInteger();
        ShopSession session = new ShopSession(ShopConfig.defaults(), book, bank, saves::incrementAndGet);
        MapPurse purse = new MapPurse();
        purse.failGive = true;

        ShopTradeResult result = TradeDesk.buy(session, purse, PLAYER, DIAMOND, 1);

        assertEquals("refunded", result.code());
        assertEquals(new BigInteger("100"), bank.player);
        assertEquals(BigInteger.ZERO, bank.shop);
        assertEquals(0, purse.plainCount(DIAMOND));
        assertEquals(0, saves.get());
        assertEquals(0, book.lookup(session.config(), DIAMOND).diamondEach().compareTo(BigDecimal.ONE));
    }

    @Test
    void manyPurchasesCanLeaveTheAnchor() {
        MemoryBank bank = new MemoryBank();
        bank.player = new BigInteger("100000");
        PriceBook book = new PriceBook(ShopSaved.empty());
        ShopSession session = new ShopSession(ShopConfig.defaults(), book, bank, () -> { });
        MapPurse purse = new MapPurse();
        ShopTradeResult result = TradeDesk.buy(session, purse, PLAYER, DIAMOND, 64);
        assertTrue(result.success(), result.message());
        BigDecimal end = book.lookup(session.config(), DIAMOND).diamondEach();
        BigDecimal expected = PriceMath.buy(BigDecimal.ONE, session.config().stepFraction(),
                session.config().minDiamondPrice(), 64).end();
        assertEquals(expected, end);
        assertTrue(end.compareTo(new BigDecimal("1.1")) > 0);
    }
}
