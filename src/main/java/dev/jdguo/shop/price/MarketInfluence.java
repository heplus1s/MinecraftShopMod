package dev.jdguo.shop.price;

import java.lang.reflect.Method;
import java.math.BigDecimal;

public final class MarketInfluence {
    private static final Method REFERENCE = marketMethod("recentUnitPrice");
    private static final Method DEMAND = marketMethod("recentDemandUnits");

    private MarketInfluence() {
    }

    public static BigDecimal adjusted(String itemId, BigDecimal base, BigDecimal notesPerDiamond) {
        return adjusted(itemId, base, notesPerDiamond, dev.jdguo.shop.catalog.ShopConfig.DEFAULT_SPREAD);
    }

    public static BigDecimal adjusted(String itemId, BigDecimal base, BigDecimal notesPerDiamond, BigDecimal spread) {
        BigDecimal market = marketInDiamonds(itemId, notesPerDiamond);
        return base == null || market == null ? base : blend(base, market, spread);
    }

    /** One independently priced fill snapshot for an entire order, not one per unit. */
    public static BigDecimal marketInDiamonds(String itemId, BigDecimal notesPerDiamond) {
        if (REFERENCE == null || notesPerDiamond == null || notesPerDiamond.signum() <= 0) return null;
        try {
            BigDecimal notes = (BigDecimal) REFERENCE.invoke(null, itemId, "note");
            if (notes != null && notes.signum() > 0) return notes.divide(notesPerDiamond, PriceMath.MC);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
        return null;
    }

    /** Confirmed independent market volume, not net buying pressure. Older Market jars return zero. */
    public static long recentDemandUnits(String itemId) {
        if (DEMAND == null) return 0;
        try {
            Object value = DEMAND.invoke(null, itemId, "note");
            return value instanceof Long volume ? Math.max(0L, volume) : 0L;
        } catch (ReflectiveOperationException ignored) {
            return 0;
        }
    }

    public static BigDecimal tradeQuote(String itemId, BigDecimal base, BigDecimal notesPerDiamond, boolean playerBuys) {
        return tradeQuote(itemId, base, notesPerDiamond, playerBuys,
                dev.jdguo.shop.catalog.ShopConfig.DEFAULT_SPREAD);
    }

    public static BigDecimal tradeQuote(String itemId, BigDecimal base, BigDecimal notesPerDiamond,
                                        boolean playerBuys, BigDecimal spread) {
        BigDecimal center = adjusted(itemId, base, notesPerDiamond, spread);
        BigDecimal half = spread.divide(new BigDecimal("2"));
        return center.multiply(playerBuys ? BigDecimal.ONE.add(half) : BigDecimal.ONE.subtract(half), PriceMath.MC);
    }

    static BigDecimal blend(BigDecimal base, BigDecimal market) {
        return blend(base, market, dev.jdguo.shop.catalog.ShopConfig.DEFAULT_SPREAD);
    }

    static BigDecimal blend(BigDecimal base, BigDecimal market, BigDecimal spread) {
        if (spread == null || spread.signum() <= 0 || spread.compareTo(BigDecimal.ONE) > 0)
            throw new IllegalArgumentException("spread");
        // A change from -cap to +cap must not cross the bid/ask gap. With
        // cap <= spread/2, (1-cap)*(1+spread/2) >= (1+cap)*(1-spread/2).
        // Apply this to every quote, including non-default configured spreads.
        BigDecimal cap = new BigDecimal("0.05").min(spread.divide(new BigDecimal("2")));
        BigDecimal limit = base.multiply(cap);
        BigDecimal movement = market.subtract(base).multiply(new BigDecimal("0.20"));
        return base.add(movement.max(limit.negate()).min(limit));
    }

    private static Method marketMethod(String method) {
        try {
            return Class.forName("dev.jdguo.market.api.MarketItemPrices")
                    .getMethod(method, String.class, String.class);
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            return null;
        }
    }
}
