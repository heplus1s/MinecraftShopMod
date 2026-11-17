package dev.jdguo.shop.api;

import com.google.gson.*;
import dev.jdguo.shop.ShopRuntime;
import dev.jdguo.shop.bank.PayChoice;
import dev.jdguo.shop.inventory.PlayerPurse;
import dev.jdguo.shop.ui.ShopChestUiHooks;
import java.math.BigDecimal;
import java.util.UUID;

/** Optional server-thread market bridge. Reuses the shop's real quote and durable settlement. */
public final class ShopMarket {
    private ShopMarket() {}
    private static net.minecraft.server.network.ServerPlayerEntity player(UUID id) {
        if (ShopRuntime.server == null || !ShopRuntime.server.isOnThread() || ShopRuntime.state == null)
            throw new IllegalStateException("shop unavailable");
        var player = ShopRuntime.server.getPlayerManager().getPlayer(id);
        if (player == null || !player.isAlive()) throw new IllegalArgumentException("player unavailable");
        return player;
    }
    public static String quote(UUID id, String item, int count, boolean buying) {
        var player = player(id);
        var quote = ShopChestUiHooks.quote(item, count, buying, PayChoice.note());
        JsonObject out = new JsonObject();
        boolean ready = "ready".equals(quote.code()) && quote.listed() && quote.charge() != null && quote.charge().signum() > 0;
        String message = quote.message();
        var purse = new PlayerPurse(player);
        if (ready && (buying ? purse.roomFor(item) : purse.plainCount(item)) < count) {
            ready = false; message = buying ? "背包空间不足" : "仅收购全新、无附魔、无自定义数据的物品；数量不足";
        }
        BigDecimal balance = buying ? ShopRuntime.bank.playerBalance(id, PayChoice.note())
                : ShopRuntime.bank.operatingBalance(PayChoice.note());
        if (ready && (balance == null || balance.compareTo(quote.charge()) < 0)) {
            ready = false; message = buying ? "央行券余额不足" : "系统商店经营账户余额不足";
        }
        out.addProperty("ok", ready); out.addProperty("message", message);
        out.addProperty("itemName", quote.itemName()); out.addProperty("currencyName", ShopRuntime.bank.currencyName());
        out.addProperty("total", quote.charge() == null ? "" : quote.charge().toPlainString());
        out.addProperty("itemId", item); out.addProperty("count", count); out.addProperty("buying", buying);
        out.addProperty("maxCount", ShopRuntime.config.maxCount());
        return out.toString();
    }
    /** Exact-total confirmation, never multiply displayed one-item indicative price. */
    public static String execute(UUID id, String item, int count, boolean buying, BigDecimal expected) {
        var player = player(id);
        JsonObject current = JsonParser.parseString(quote(id, item, count, buying)).getAsJsonObject();
        if (!current.get("ok").getAsBoolean()) return current.toString();
        if (expected == null || new BigDecimal(current.get("total").getAsString()).compareTo(expected) != 0) {
            current.addProperty("ok", false); current.addProperty("message", "商店报价已变化，请重新预览确认"); return current.toString();
        }
        var result = buying ? ShopChestUiHooks.buy(player, item, count, PayChoice.note())
                : ShopChestUiHooks.sell(player, item, count, PayChoice.note());
        current.addProperty("ok", result.success()); current.addProperty("message", result.message());
        return current.toString();
    }
    /** Journal is the source of truth. No second trade insertion, feedback loop, or replay duplicates. */
    public static String recentTrades() {
        if (ShopRuntime.server == null || !ShopRuntime.server.isOnThread() || ShopRuntime.state == null) return "[]";
        return ShopRuntime.state.book().saved().marketTrades().toString();
    }
}
