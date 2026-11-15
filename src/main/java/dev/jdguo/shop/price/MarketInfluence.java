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
        BigDecimal center = adjusted(itemId, base, notesPerDiamond);
        if (center == base) {
            return base;
        }
        return MoneyScale.quantize(center.multiply(new BigDecimal(playerBuys ? "1.02" : "0.98")));
    }

    static BigDecimal blend(BigDecimal base, BigDecimal market) {
        BigDecimal limit = base.multiply(new BigDecimal("0.05"));
        BigDecimal movement = market.subtract(base).multiply(new BigDecimal("0.20"));
        return MoneyScale.quantize(base.add(movement.max(limit.negate()).min(limit)));
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
