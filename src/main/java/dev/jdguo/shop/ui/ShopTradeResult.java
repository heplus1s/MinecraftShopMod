package dev.jdguo.shop.ui;

import java.math.BigDecimal;

/**
 * Result of one purchase or sale. {@code code} is an ASCII token.
 * {@code message} is the Chinese sentence shown to the player.
 * On failure, item and money balances are left as they were, except for the
 * explicit {@code settle_inconsistent} case described in the message.
 * {@code charge} is the whole order at four decimal places. A short balance refuses the order.
 */
public record ShopTradeResult(
        boolean success,
        String code,
        String message,
        String itemId,
        String itemName,
        int count,
        BigDecimal diamondEach,
        BigDecimal diamondTotal,
        BigDecimal diamondEachAfter,
        BigDecimal charge
) {
    public static ShopTradeResult fail(String code, String message) {
        return new ShopTradeResult(false, code, message, "", "", 0, null, null, null, null);
    }
}
