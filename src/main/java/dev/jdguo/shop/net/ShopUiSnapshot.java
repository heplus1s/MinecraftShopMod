package dev.jdguo.shop.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.jdguo.shop.ShopRuntime;
import dev.jdguo.shop.bank.PayChoice;
import dev.jdguo.shop.price.MoneyScale;
import dev.jdguo.shop.ui.ShopChestUiHooks;
import dev.jdguo.shop.ui.ShopListingView;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

/**
 * Server snapshot for the optional client pack.
 *
 * <p>Fields: {@code protocol}, {@code bankLoaded}, {@code settlementReady}, {@code currency},
 * {@code currencyKey}, {@code step}, {@code page}, {@code pages}, {@code message}, and {@code listings}.
 * Each listing has {@code item}, {@code name}, {@code diamond}, {@code anchor}, and {@code notes}.
 * Amounts are four-decimal strings, and a whole zero is {@code 0}. {@code notes} is JSON null when
 * the selected currency cannot be quoted. {@code message} is
 * Chinese and already includes the shop prefix. Item ids and field names are ASCII.
 */
public final class ShopUiSnapshot {
    private ShopUiSnapshot() {}

    public static String json(ServerPlayerEntity player, int page, String message) {
        PayChoice pay = ShopChestUiHooks.payer(player);
        int pages = ShopChestUiHooks.pages();
        int shown = page < 1 ? 1 : Math.min(page, pages);
        List<ShopListingView> rows = ShopChestUiHooks.listings(shown, pay);
        JsonObject root = new JsonObject();
        root.addProperty("protocol", ShopUiNetwork.PROTOCOL);
        root.addProperty("explicitCurrency", true);
        root.addProperty("bankLoaded", ShopRuntime.bank.modLoaded());
        root.addProperty("settlementReady", ShopRuntime.bank.operatingAccountReady()
                && ShopRuntime.bank.unitsPerDiamond(pay) != null);
        root.addProperty("currency", pay.name());
        root.addProperty("currencyKey", pay.key());
        root.addProperty("sellCurrency", pay.name());
        root.addProperty("sellCurrencyKey", pay.key());
        root.addProperty("step", MoneyScale.display(ShopRuntime.config.stepFraction()));
        root.addProperty("spread", MoneyScale.display(ShopRuntime.config.spreadFraction()));
        root.addProperty("maxCount", ShopRuntime.config.maxCount());
        root.addProperty("page", shown);
        root.addProperty("pages", pages);
        root.addProperty("message", message == null || message.isBlank() ? "" : "[商店] " + message);
        var bank = ShopRuntime.bank;
        var centralRate = bank.unitsPerDiamond(PayChoice.note());
        JsonArray currencies = new JsonArray();
        JsonArray notes = new JsonArray();
        if (centralRate != null && centralRate.signum() > 0) {
            JsonObject central = new JsonObject();
            central.addProperty("id", "note");
            central.addProperty("name", bank.currencyName());
            central.addProperty("unitsPerDiamond", centralRate.toPlainString());
            currencies.add(central);
        }
        for (String name : bank.personalNames()) {
            var note = bank.findPersonal(name);
            if (note == null) {
                continue;
            }
            var choice = PayChoice.personal(note.id(), note.name());
            var rate = bank.unitsPerDiamond(choice);
            JsonObject row = new JsonObject();
            row.addProperty("id", choice.key());
            row.addProperty("name", note.name());
            if (centralRate != null && rate != null && rate.signum() > 0) {
                row.addProperty("price", centralRate.divide(rate, java.math.MathContext.DECIMAL128).toPlainString());
            }
            notes.add(row);
        }
        root.add("currencies", currencies);
        root.add("notes", notes);
        JsonArray listings = new JsonArray();
        for (ShopListingView row : rows) {
            JsonObject item = new JsonObject();
            item.addProperty("item", row.itemId());
            item.addProperty("name", row.itemName());
            item.addProperty("systemTradeEnabled", ShopRuntime.config.entry(row.itemId()).systemTradeEnabled());
            String category = dev.jdguo.shop.catalog.CatalogCategories.id(row.itemId());
            item.addProperty("categoryId", category);
            item.addProperty("category", dev.jdguo.shop.catalog.CatalogCategories.label(category));
            if (row.diamondEach() == null) {
                item.add("diamond", com.google.gson.JsonNull.INSTANCE);
            } else {
                item.addProperty("diamond", MoneyScale.display(row.diamondEach()));
            }
            if (row.anchor() == null) {
                item.add("anchor", com.google.gson.JsonNull.INSTANCE);
            } else {
                item.addProperty("anchor", MoneyScale.display(row.anchor()));
            }
            if (row.notesEach() == null) {
                item.add("notes", com.google.gson.JsonNull.INSTANCE);
            } else {
                item.addProperty("notes", MoneyScale.display(row.notesEach()));
            }
            listings.add(item);
        }
        root.add("listings", listings);
        return root.toString();
    }
}
