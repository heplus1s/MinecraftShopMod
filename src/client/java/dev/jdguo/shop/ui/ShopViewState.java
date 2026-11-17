package dev.jdguo.shop.ui;

import com.google.gson.JsonObject;
import java.util.Map;

/** Immutable local navigation state; never contains an executable server quote. */
record ShopViewState(String currencyId, boolean currencyOpen, String category, int scroll,
                     Map<String, String> quantities) {
    ShopViewState { quantities = Map.copyOf(quantities); }
    static ShopViewState fromSnapshot(JsonObject data) {
        String currency = ShopQuotes.text(data, "currencyKey");
        return new ShopViewState(currency == null ? ShopQuotes.NOTE : currency, false, "", 0, Map.of());
    }

}
