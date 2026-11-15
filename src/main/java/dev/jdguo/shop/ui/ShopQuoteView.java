package dev.jdguo.shop.ui;

import java.math.BigDecimal;

/**
 * A price quote that does not move items or money.
 * {@code charge} is the four-decimal total in the selected currency, or null when that
 * currency cannot be quoted. A missing diamond price is not replaced with a made-up number.
 */
public record ShopQuoteView(
        boolean listed,
        String code,
        String message,
        String itemId,
        String itemName,
        int count,
        boolean buying,
        BigDecimal diamondEach,
        BigDecimal anchor,
        BigDecimal diamondTotal,
        BigDecimal charge
) {
}
