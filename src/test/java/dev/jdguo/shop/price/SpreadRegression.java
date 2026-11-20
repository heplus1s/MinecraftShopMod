package dev.jdguo.shop.price;

import dev.jdguo.shop.catalog.*;
import dev.jdguo.shop.bank.PayChoice;
import dev.jdguo.shop.support.*;
import dev.jdguo.shop.trade.*;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.UnaryOperator;

/** Executable without JUnit; SpreadRegressionTest also runs this under Gradle. */
public final class SpreadRegression {
    private static final BigDecimal STEP = new BigDecimal("0.002");
    private static final BigDecimal FLOOR = new BigDecimal("0.000000001");
    private static int checks;
    private static void yes(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static void equal(BigDecimal a, BigDecimal b, String message) {
        yes(a.compareTo(b) == 0, message + ": " + a + " vs " + b);
    }
    private static PriceMath.Execution quote(BigDecimal p, int n, boolean buy, BigDecimal spread,
                                              BigDecimal rate, UnaryOperator<BigDecimal> reference) {
        return PriceMath.execution(p, STEP, FLOOR, n, buy, spread, reference, rate);
    }
    public static void main(String[] args) {
        checks = 0;
        for (String start : new String[]{"0.0001", "0.06", "1", "10", "999.9999"}) {
            for (String spread : new String[]{"0.0001", "0.04", "0.5"}) {
                for (String units : new String[]{"0.000123", "1", "2.7182818", "1000"}) {
                    for (int n : new int[]{1, 2, 64, 2304}) {
                        BigDecimal p = new BigDecimal(start), s = new BigDecimal(spread), r = new BigDecimal(units);
                        for (UnaryOperator<BigDecimal> ref : java.util.List.<UnaryOperator<BigDecimal>>of(
                                UnaryOperator.identity(), v -> MarketInfluence.blend(v, BigDecimal.TEN))) {
                            PriceMath.Execution bought = quote(p, n, true, s, r, ref);
                            PriceMath.Execution soldBack = quote(bought.path().end(), n, false, s, r, ref);
                            yes(soldBack.charge().compareTo(bought.charge()) <= 0, "buy/sell profit");
                            yes(soldBack.path().end().subtract(p).abs().compareTo(new BigDecimal("1e-25")) < 0,
                                    "reverse curve drift");
                            PriceMath.Execution sold = quote(p, n, false, s, r, ref);
                            PriceMath.Execution boughtBack = quote(sold.path().end(), n, true, s, r, ref);
                            yes(sold.charge().compareTo(boughtBack.charge()) <= 0, "sell/buy profit");
                            // The same sequence and rounding must be independent of order packaging.
                            for (boolean buy : new boolean[]{true, false}) {
                                BigDecimal state = p, sum = BigDecimal.ZERO;
                                for (int i = 0; i < n; i++) {
                                    PriceMath.Execution one = quote(state, 1, buy, s, r, ref);
                                    sum = sum.add(one.charge()); state = one.path().end();
                                }
                                PriceMath.Execution batch = quote(p, n, buy, s, r, ref);
                                equal(sum, batch.charge(), "split charge");
                                equal(state, batch.path().end(), "split state");
                            }
                        }
                    }
                }
            }
        }
        ShopConfig config = ShopConfig.defaults().withSpread(new BigDecimal("0.06"));
        equal(ConfigJson.parse(ConfigJson.write(config)).spreadFraction(), new BigDecimal("0.06"), "config persist");
        equal(config.withStep(new BigDecimal("0.01")).withAnchor("minecraft:diamond", BigDecimal.TEN)
                .spreadFraction(), new BigDecimal("0.06"), "config updates retain spread");
        equal(ConfigJson.parse("{\"catalog\":[{\"item\":\"minecraft:diamond\",\"name\":\"钻石\",\"anchorDiamonds\":\"1\"}]}")
                .spreadFraction(), ShopConfig.DEFAULT_SPREAD, "legacy config migration");
        for (String bad : new String[]{"0", "-0.01", "1.01"}) {
            boolean rejected = false;
            try { config.withSpread(new BigDecimal(bad)); } catch (IllegalArgumentException expected) { rejected = true; }
            yes(rejected, "invalid spread accepted");
        }
        // Cross-currency valuation is conservative at unchanged, coherent rates, not a promise
        // against external rate manipulation or independently moving markets.
        BigDecimal buyA = quote(BigDecimal.TEN, 64, true, new BigDecimal("0.04"), new BigDecimal("2"), UnaryOperator.identity()).charge();
        BigDecimal end = PriceMath.buy(BigDecimal.TEN, STEP, FLOOR, 64).end();
        BigDecimal sellB = quote(end, 64, false, new BigDecimal("0.04"), new BigDecimal("17"), UnaryOperator.identity()).charge();
        yes(sellB.divide(new BigDecimal("17"), PriceMath.MC).compareTo(buyA.divide(new BigDecimal("2"), PriceMath.MC)) < 0,
                "cross-currency constant-rate profit");
        MemoryBank bank = new MemoryBank();
        bank.player.put("note", new BigDecimal("10000"));
        PriceBook book = StockFixture.stocked("minecraft:diamond");
        ShopSession session = new ShopSession(ShopConfig.defaults(), book, bank, () -> {});
        MapPurse purse = new MapPurse(); UUID player = UUID.randomUUID();
        var buy = TradeDesk.buy(session, purse, player, PayChoice.note(), "minecraft:diamond", 64);
        yes(buy.success(), "buy integration");
        ShopSaved restored = ShopSaved.parse(book.saved().toJson());
        yes(restored.stock("minecraft:diamond") == book.saved().stock("minecraft:diamond"), "exact stock reload");
        var sell = TradeDesk.sell(session, purse, player, PayChoice.note(), "minecraft:diamond", 64);
        yes(sell.success(), "sell integration");
        yes(bank.player("note").compareTo(new BigDecimal("10000")) < 0, "integration roundtrip profit");
        yes(bank.shop("note").signum() > 0, "spread not accrued");
        System.out.println("PASS: " + checks + " spread/roundtrip/split/rounding/config/reload/TradeDesk checks");
    }
}
