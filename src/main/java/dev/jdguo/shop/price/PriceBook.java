package dev.jdguo.shop.price;

import dev.jdguo.shop.catalog.CatalogEntry;
import dev.jdguo.shop.catalog.ShopConfig;

import java.math.BigDecimal;

/**
 * Live diamond-equivalent prices. A saved price always beats the config anchor.
 * Items that have never traded keep using the anchor, so an anchor edit still
 * applies to them. Reset is the only way to drop a saved price.
 */
public final class PriceBook {
    private final ShopSaved saved;

    public PriceBook(ShopSaved saved) {
        this.saved = saved;
    }

    public ShopSaved saved() {
        return saved;
    }

    public int storedCount() {
        return saved.size();
    }

    public Lookup lookup(ShopConfig config, String itemId) {
        CatalogEntry entry = config.entry(itemId);
        ShopSaved.Read read = saved.read(itemId);
        if (read.corrupt()) {
            return new Lookup(entry, null, true);
        }
        if (read.present() && read.value() != null && read.value().signum() > 0) {
            return new Lookup(entry, read.value(), false);
        }
        BigDecimal anchor = entry == null ? null : entry.anchorDiamonds();
        return new Lookup(entry, anchor, false);
    }

    public void commit(String itemId, BigDecimal price) {
        saved.setPrice(itemId, price);
    }

    public void reset(String itemId) {
        saved.clearPrice(itemId);
    }

    public record Lookup(CatalogEntry entry, BigDecimal diamondEach, boolean corrupt) {
    }
}
