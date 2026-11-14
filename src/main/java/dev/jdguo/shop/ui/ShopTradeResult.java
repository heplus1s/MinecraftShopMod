package dev.jdguo.shop.ui;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Result of one purchase or sale. {@code code} is an ASCII token.
 * {@code message} is the Chinese sentence shown to the player.
 * On failure, item and money balances are left as they were, except for the
 * explicit {@code settle_inconsistent} case described in the message.
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
        BigInteger notesCharged,
        int minimumCount
) {
    public static ShopTradeResult fail(String code, String message) {
        return new ShopTradeResult(false, code, message, "", "", 0, null, null, null, null, -1);
    }
}
