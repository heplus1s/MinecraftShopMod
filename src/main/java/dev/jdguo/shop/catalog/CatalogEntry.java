package dev.jdguo.shop.catalog;

import java.math.BigDecimal;

/** One catalog row. {@code anchorDiamonds} is only the starting price. */
public record CatalogEntry(String itemId, String name, BigDecimal anchorDiamonds) {
}
