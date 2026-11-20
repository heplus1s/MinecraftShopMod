package dev.jdguo.shop.support;

import dev.jdguo.shop.catalog.ShopConfig;
import dev.jdguo.shop.price.PriceBook;
import dev.jdguo.shop.price.ShopSaved;
import dev.jdguo.shop.trade.TradeDesk;

public final class StockFixture {
    private StockFixture() {}

    public static void seed(ShopSaved saved, String itemId, long count) {
        saved.applyStockAdjustment(saved.beginStockAdjustment(itemId, count, null, "test provisioning"));
    }

    public static PriceBook stocked(String... ids) {
        ShopSaved saved = ShopSaved.empty();
        for (String id : ids) seed(saved, id, TradeDesk.targetStock(ShopConfig.defaults().entry(id)));
        return new PriceBook(saved);
    }
}
