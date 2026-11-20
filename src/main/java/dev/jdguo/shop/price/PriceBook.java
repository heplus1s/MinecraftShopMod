package dev.jdguo.shop.price;

import dev.jdguo.shop.bank.PayChoice;
import dev.jdguo.shop.catalog.CatalogEntry;
import dev.jdguo.shop.catalog.ShopConfig;
import dev.jdguo.shop.price.MoneyScale;

import java.math.BigDecimal;
import java.util.UUID;

/** Baseline prices. Existing legacy world prices are retained until reset; new trades
 * do not overwrite the baseline. Inventory and Market context are applied at quotation.
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
        if (read.corrupt() || (read.present() && !MoneyScale.positive(read.value()))) {
            return new Lookup(entry, null, true);
        }
        if (read.present()) {
            return new Lookup(entry, PriceMath.normalize(read.value()), false);
        }
        if (entry == null || !MoneyScale.positive(entry.anchorDiamonds())) {
            return new Lookup(entry, null, false);
        }
        return new Lookup(entry, PriceMath.normalize(entry.anchorDiamonds()), false);
    }

    public PayChoice payer(UUID player) {
        PayChoice choice = saved.payer(player);
        return choice == null || choice.hard() ? PayChoice.note() : choice;
    }

    public void rememberPayer(UUID player, PayChoice choice) {
        saved.setPayer(player, choice);
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
