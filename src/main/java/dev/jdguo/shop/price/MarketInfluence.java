package dev.jdguo.shop.price;

import java.lang.reflect.Method;
import java.math.BigDecimal;

public final class MarketInfluence {
    private static final Method REFERENCE = marketReference();

    private MarketInfluence() {
    }

    public static BigDecimal adjusted(String itemId, BigDecimal base, BigDecimal notesPerDiamond) {
        if (REFERENCE == null || base == null || notesPerDiamond == null || notesPerDiamond.signum() <= 0) {
            return base;
        }
        try {
            BigDecimal notes = (BigDecimal) REFERENCE.invoke(null, itemId, "note");
            if (notes != null && notes.signum() > 0) {
                return blend(base, notes.divide(notesPerDiamond, PriceMath.MC));
            }
        } catch (ReflectiveOperationException ignored) {
            return base;
        }
        return base;
    }

    public static BigDecimal tradeQuote(String itemId, BigDecimal base, BigDecimal notesPerDiamond, boolean playerBuys) {
        return tradeQuote(itemId, base, notesPerDiamond, playerBuys,
                dev.jdguo.shop.catalog.ShopConfig.DEFAULT_SPREAD);
    }

    public static BigDecimal tradeQuote(String itemId, BigDecimal base, BigDecimal notesPerDiamond,
                                        boolean playerBuys, BigDecimal spread) {
        BigDecimal center = adjusted(itemId, base, notesPerDiamond);
        BigDecimal half = spread.divide(new BigDecimal("2"));
        return center.multiply(playerBuys ? BigDecimal.ONE.add(half) : BigDecimal.ONE.subtract(half), PriceMath.MC);
    }

    static BigDecimal blend(BigDecimal base, BigDecimal market) {
        BigDecimal limit = base.multiply(new BigDecimal("0.05"));
        BigDecimal movement = market.subtract(base).multiply(new BigDecimal("0.20"));
        return base.add(movement.max(limit.negate()).min(limit));
    }

    private static Method marketReference() {
        try {
            return Class.forName("dev.jdguo.market.api.MarketItemPrices")
                    .getMethod("recentUnitPrice", String.class, String.class);
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            return null;
        }
    }
}
