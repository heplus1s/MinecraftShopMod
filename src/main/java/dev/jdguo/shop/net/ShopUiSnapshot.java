package dev.jdguo.shop.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.jdguo.shop.ShopRuntime;
import dev.jdguo.shop.price.PlainNumbers;
import dev.jdguo.shop.text.ShopText;
import dev.jdguo.shop.ui.ShopChestUiHooks;
import dev.jdguo.shop.ui.ShopListingView;

import java.util.List;

/**
 * Server snapshot for the optional client pack.
 *
 * <p>Fields: {@code protocol}, {@code bankLoaded}, {@code settlementReady}, {@code currency},
 * {@code step}, {@code page}, {@code pages}, {@code message}, and {@code listings}. Each listing has
 * {@code item}, {@code name}, {@code diamond}, {@code anchor}, and {@code notes}. Decimal fields are
 * strings. {@code notes} is JSON null when the exchange rate is unavailable. {@code message} is
 * Chinese and already includes the shop prefix. Item ids and field names are ASCII.
 */
public final class ShopUiSnapshot {
    private ShopUiSnapshot() {}

    public static String json(int page, String message) {
        int pages = ShopChestUiHooks.pages();
        int shown = page < 1 ? 1 : Math.min(page, pages);
        List<ShopListingView> rows = ShopChestUiHooks.listings(shown);
        JsonObject root = new JsonObject();
        root.addProperty("protocol", ShopUiNetwork.PROTOCOL);
        root.addProperty("bankLoaded", ShopRuntime.bank.modLoaded());
        root.addProperty("settlementReady", ShopRuntime.bank.operatingAccountReady()
                && ShopRuntime.bank.unitsPerDiamond() != null);
        root.addProperty("currency", ShopText.currency(ShopRuntime.bank));
        root.addProperty("step", PlainNumbers.amount(ShopRuntime.config.stepFraction()));
        root.addProperty("page", shown);
        root.addProperty("pages", pages);
        root.addProperty("message", message == null || message.isBlank() ? "" : "[商店] " + message);
        JsonArray listings = new JsonArray();
        for (ShopListingView row : rows) {
            JsonObject item = new JsonObject();
            item.addProperty("item", row.itemId());
            item.addProperty("name", row.itemName());
            if (row.diamondEach() == null) {
                item.add("diamond", com.google.gson.JsonNull.INSTANCE);
            } else {
                item.addProperty("diamond", PlainNumbers.amount(row.diamondEach()));
            }
            item.addProperty("anchor", PlainNumbers.amount(row.anchor()));
            if (row.notesEach() == null) {
                item.add("notes", com.google.gson.JsonNull.INSTANCE);
            } else {
                item.addProperty("notes", PlainNumbers.amount(row.notesEach()));
            }
            listings.add(item);
        }
        root.add("listings", listings);
        return root.toString();
    }
}
