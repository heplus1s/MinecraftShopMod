package dev.jdguo.shop.catalog;

import java.math.BigDecimal;

/** Starting reference price is not permission for the infinite system account to trade. */
public record CatalogEntry(String itemId, String name, BigDecimal anchorDiamonds, boolean systemTradeEnabled) {
    public CatalogEntry(String itemId, String name, BigDecimal anchorDiamonds) {
        this(itemId, name, anchorDiamonds, true);
    }
}
