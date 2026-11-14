package dev.jdguo.shop.ui;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * A price quote that does not move items or money.
 * {@code notesExact} and {@code notesDue} are null when the bank cannot convert diamonds yet.
 * {@code notesDue} is also null when the exact amount is still below one settlement unit.
 * {@code minimumCount} is the smallest count that reaches one unit, or {@code -1} if none does.
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
        BigDecimal notesExact,
        BigInteger notesDue,
        int minimumCount
) {
}
