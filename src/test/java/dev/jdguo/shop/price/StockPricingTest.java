package dev.jdguo.shop.price;

import dev.jdguo.shop.catalog.ShopConfig;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class StockPricingTest {
    private final ShopConfig config = ShopConfig.defaults();
    private static final BigDecimal BASE = BigDecimal.ONE;

    @Test void quantityTargetAddsOnlyQualifiedVolumeAndCapsIt() {
        int normal = StockPricing.target("minecraft:sponge", BASE, 0);
        assertTrue(normal < StockPricing.target("minecraft:dirt", BASE, 0));
        assertEquals(normal + 8, StockPricing.target("minecraft:sponge", BASE, 32));
        assertEquals(normal, StockPricing.target("minecraft:sponge", BASE, -1));
        assertEquals(normal * 2, StockPricing.target("minecraft:sponge", BASE, Long.MAX_VALUE));
    }
    @Test void moreStockMeansLowerPriceAndNeverCrossesZero() {
        assertTrue(StockPricing.midpoint(BASE, 32, 1, config).compareTo(BASE) > 0);
        assertEquals(0, StockPricing.midpoint(BASE, 32, 32, config).compareTo(BASE));
        assertTrue(StockPricing.midpoint(BASE, 32, 512, config).compareTo(BASE) < 0);
        assertTrue(StockPricing.midpoint(BASE, 32, Long.MAX_VALUE, config).signum() > 0);
    }
    @Test void batchAndSplitMatchAndImmediateReverseLosesSpread() {
        for (BigDecimal market : new BigDecimal[]{null, new BigDecimal("3")}) {
            var bought = StockPricing.execution(BASE, 128, 128, 32, true, config, market, BASE);
            var soldBack = StockPricing.execution(BASE, 128, 96, 32, false, config, market, BASE, new BigDecimal("-32"));
            assertTrue(soldBack.charge().compareTo(bought.charge()) < 0);
            BigDecimal split = BigDecimal.ZERO;
            for (long stock=128;stock>96;stock--) {
                split=split.add(StockPricing.execution(BASE,128,stock,1,true,config,market,BASE,BigDecimal.valueOf(stock-128)).charge());
            }
            assertEquals(bought.charge(),split);
        }
        assertThrows(IllegalArgumentException.class, () -> StockPricing.execution(BASE,1,1,2,true,config,null,BASE));
        assertThrows(ArithmeticException.class, () -> StockPricing.execution(BASE,1,Long.MAX_VALUE,1,false,config,null,BASE));
    }
    @Test void normalStockAndFlowAreBoundedButLastUnitsHaveExtraPremium() {
        for (long level = 10; level <= 1000; level++) {
            var upper = StockPricing.midpoint(BASE,100,level,config,new BigDecimal("-1000000"));
            var lower = StockPricing.midpoint(BASE,100,level,config,new BigDecimal("1000000"));
            assertTrue(upper.compareTo(new BigDecimal("1.09")) <= 0);
            assertTrue(lower.compareTo(new BigDecimal("0.93")) >= 0);
        }
        assertTrue(StockPricing.midpoint(BASE,100,1,config).compareTo(new BigDecimal("1.10")) > 0);
    }

    @Test void sameStockConcentratedTradingMovesPriceMoreThanSlowTradingInBothDirections() {
        BigDecimal burst = new BigDecimal("80");
        BigDecimal spread = new BigDecimal("20");
        for (int i=0; i<3; i++) spread = StockPricing.decay(spread, 0, 2).add(new BigDecimal("20"));
        var sellRapid = StockPricing.midpoint(BASE,100,100,config,burst);
        var sellSlow = StockPricing.midpoint(BASE,100,100,config,spread);
        var buyRapid = StockPricing.midpoint(BASE,100,100,config,burst.negate());
        var buySlow = StockPricing.midpoint(BASE,100,100,config,spread.negate());
        assertTrue(sellRapid.compareTo(sellSlow) < 0);
        assertTrue(buyRapid.compareTo(buySlow) > 0);
        assertEquals(0, StockPricing.decay(burst,50,52).compareTo(new BigDecimal("40")));
        assertEquals(0, StockPricing.decay(burst,50,49).compareTo(burst));
    }

    @Test void journalRestartsAndRetriesDoNotCountTradeFlowTwice() {
        String sku = "minecraft:dirt";
        var p = new java.util.UUID(1,1);
        var saves = ShopSaved.empty();
        long at = 600_000L;
        saves.beginTrade("first",p,sku,20,false,"note",BASE,BASE,BASE,at);
        assertEquals(0,saves.recentFlowAt(sku,2).signum());
        saves = ShopSaved.parse(saves.toJson());
        saves.applyTradeStock("first");
        saves.applyTradeStock("first");
        saves.tradeState("first","completed");
        assertEquals(0,saves.recentFlowAt(sku,2).compareTo(new BigDecimal("20")));
        assertEquals(0,saves.recentFlowAt(sku,4).compareTo(new BigDecimal("10")));
        saves.beginTrade("second",p,sku,10,false,"note",BASE,BASE,BASE,1_200_000L);
        saves.tradeState("second","cancelled");
        assertEquals(0,saves.recentFlowAt(sku,4).compareTo(new BigDecimal("10")));
        String adjustment = saves.beginStockAdjustment(sku,1000,p,"期初配置");
        saves.applyStockAdjustment(adjustment);
        assertEquals(0,saves.recentFlowAt(sku,4).compareTo(new BigDecimal("10")));
    }
}
