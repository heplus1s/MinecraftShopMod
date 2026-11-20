package dev.jdguo.shop.api;

import dev.jdguo.shop.ShopRuntime;
import dev.jdguo.shop.bank.PayChoice;
import dev.jdguo.shop.trade.TradeDesk;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/** Public one-item quotes in bank notes. Legacy market listings settle in whole units. */
public final class ShopPrices {
    private ShopPrices() {
    }

    /** Empty means not in the configured shop catalog; no invented category or reference price. */
    public static String categoryId(String itemId) {
        if (ShopRuntime.config == null || ShopRuntime.config.entry(itemId) == null) return "";
        return dev.jdguo.shop.catalog.CatalogCategories.id(itemId);
    }

    public static boolean systemTradeEnabled(String itemId) {
        var entry = ShopRuntime.config == null ? null : ShopRuntime.config.entry(itemId);
        return entry != null && entry.systemTradeEnabled();
    }

    /** Informational reference only. Never use as an executable system order. */
    public static BigDecimal referenceEach(String itemId) {
        if (ShopRuntime.server == null || !ShopRuntime.server.isOnThread() || ShopRuntime.state == null) return null;
        var entry = ShopRuntime.config.entry(itemId);
        var rate = ShopRuntime.bank.unitsPerDiamond(PayChoice.note());
        if (entry == null || rate == null) return null;
        var price = ShopRuntime.state.book().lookup(ShopRuntime.config, itemId).diamondEach();
        return price == null ? null : dev.jdguo.shop.price.StockPricing.midpoint(price, TradeDesk.targetStock(entry),
                ShopRuntime.state.book().saved().stock(itemId), ShopRuntime.config, ShopRuntime.state.book().saved().recentFlow(itemId)).multiply(rate);
    }

    public static BigDecimal quoteEach(String itemId, String currency, boolean playerBuys) {
        if (!"note".equals(currency) || ShopRuntime.server == null || !ShopRuntime.server.isOnThread()
                || ShopRuntime.state == null) {
            return null;
        }
        return TradeDesk.quote(ShopRuntime.session(), itemId, 1, playerBuys, PayChoice.note()).charge();
    }

    public static BigInteger priceEach(String itemId, String currency) {
        BigDecimal quote = quoteEach(itemId, currency, true);
        return quote == null || quote.signum() <= 0 ? null : quote.setScale(0, RoundingMode.CEILING).toBigInteger();
    }
}
