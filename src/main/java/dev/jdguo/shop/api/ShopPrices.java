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
