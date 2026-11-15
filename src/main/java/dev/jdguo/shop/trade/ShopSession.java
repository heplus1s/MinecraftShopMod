package dev.jdguo.shop.trade;

import dev.jdguo.shop.bank.ShopBank;
import dev.jdguo.shop.catalog.ShopConfig;
import dev.jdguo.shop.price.PriceBook;

/** One world's catalog, prices, and bank view. {@link #persist()} writes the price document. */
public final class ShopSession {
    private final ShopConfig config;
    private final PriceBook book;
    private final ShopBank bank;
    private final Runnable persist;

    public ShopSession(ShopConfig config, PriceBook book, ShopBank bank, Runnable persist) {
        this.config = config;
        this.book = book;
        this.bank = bank;
        this.persist = persist == null ? () -> { } : persist;
    }

    public ShopConfig config() { return config; }
    public PriceBook book() { return book; }
    public ShopBank bank() { return bank; }

    public void persist() {
        persist.run();
    }
}
