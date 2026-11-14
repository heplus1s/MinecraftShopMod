package dev.jdguo.shop.ui;

import java.math.BigDecimal;

/** One catalog row at its current diamond-equivalent price. {@code notesEach} is null without a rate. */
public record ShopListingView(
        String itemId,
        String itemName,
        BigDecimal diamondEach,
        BigDecimal anchor,
        BigDecimal notesEach
) {
}
