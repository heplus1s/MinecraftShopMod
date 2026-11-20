package dev.jdguo.shop.inventory;

import com.google.gson.JsonObject;
import dev.jdguo.shop.ShopRuntime;
import dev.jdguo.shop.bank.PayChoice;
import dev.jdguo.shop.trade.TradeDesk;
import net.minecraft.server.network.ServerPlayerEntity;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** The Shop and Market entry points use one server-authoritative token and settlement. */
public final class BulkShulkerOrders {
    private static final long LIFETIME = TimeUnit.SECONDS.toNanos(45);
    private static final Map<UUID, Preview> PREVIEWS = new HashMap<>();
    private record Preview(String token, String item, BulkShulkerPurse.Plan plan, BigDecimal total, long created) {}

    private BulkShulkerOrders() {}

    public static JsonObject quote(ServerPlayerEntity player, String item) {
        requireServerThread(player);
        UUID owner = player.getUuid();
        PREVIEWS.remove(owner);
        if (ShopRuntime.config.entry(item) == null || !PlayerPurse.knownItem(item))
            return failure("这个商品不在系统商店目录中。");
        var saved = ShopRuntime.state.book().saved();
        if (saved.pendingTrade(owner) != null)
            return failure("你有一笔订单尚待核对，请重新连接后查看恢复结果，不要重复付款。");
        var plan = BulkShulkerPurse.plan(player, item, TradeDesk.BULK_MAX_BOXES, TradeDesk.BULK_MAX_COUNT);
        if (plan == null)
            return failure("主背包里没有合规的同类整盒。只接受未堆叠的装货盒，不能混装或含特殊数据。");
        var quote = TradeDesk.quoteBulkSale(ShopRuntime.session(), item, plan.count());
        if (!"ready".equals(quote.code()) || quote.charge() == null || quote.charge().signum() <= 0)
            return failure(quote.message());
        if (!ShopRuntime.bank.supportsReferencedSettlement())
            return failure("当前银行不支持可靠交易结算，未收取货物。");
        BigDecimal operating = ShopRuntime.bank.operatingBalance(PayChoice.note());
        if (operating == null || operating.compareTo(quote.charge()) < 0)
            return failure("商店经营账户余额不足，未收取货物。");
        String token = UUID.randomUUID().toString();
        PREVIEWS.put(owner, new Preview(token, item, plan, quote.charge(), System.nanoTime()));
        JsonObject out = response(true, "预览成功。45 秒内确认；将交付 " + plan.count() + " 件，"
                + plan.boxCount() + " 个空盒保留原槽位。");
        out.addProperty("confirmation", token);
        out.addProperty("itemId", item);
        out.addProperty("itemName", quote.itemName());
        out.addProperty("currencyName", ShopRuntime.bank.currencyName());
        out.addProperty("total", quote.charge().toPlainString());
        out.addProperty("count", plan.count());
        out.addProperty("boxes", plan.boxCount());
        return out;
    }

    public static JsonObject execute(ServerPlayerEntity player, String item, String token) {
        requireServerThread(player);
        UUID owner = player.getUuid();
        Preview preview = PREVIEWS.get(owner);
        if (preview == null || !preview.token.equals(token) || !preview.item.equals(item))
            return failure("确认码不匹配或已使用，请重新预览。");
        PREVIEWS.remove(owner); // Consume only a matched token, before any side effect.
        if (System.nanoTime() - preview.created > LIFETIME)
            return failure("报价已过期，请重新预览。");
        var current = BulkShulkerPurse.plan(player, item, TradeDesk.BULK_MAX_BOXES, TradeDesk.BULK_MAX_COUNT);
        if (current == null || current.count() != preview.plan.count()
                || !current.fingerprint().equals(preview.plan.fingerprint()))
            return failure("潜影盒内容或位置已变化，请重新预览。未收货。");
        var result = TradeDesk.sellBulk(ShopRuntime.session(), new BulkShulkerPurse(player, preview.plan), owner,
                item, preview.plan.count(), preview.total, preview.plan.toJson());
        JsonObject out = response(result.success(), result.message());
        out.addProperty("code", result.code());
        return out;
    }

    public static void disconnect(UUID owner) { PREVIEWS.remove(owner); }
    public static void clear() { PREVIEWS.clear(); }

    private static void requireServerThread(ServerPlayerEntity player) {
        var server = ShopRuntime.server;
        if (server == null || ShopRuntime.state == null || !server.isOnThread()
                || server.getPlayerManager().getPlayer(player.getUuid()) != player || !player.isAlive())
            throw new IllegalStateException("shop unavailable");
    }
    private static JsonObject failure(String message) { return response(false, message); }
    private static JsonObject response(boolean ok, String message) {
        JsonObject out = new JsonObject();
        out.addProperty("ok", ok);
        out.addProperty("message", message);
        return out;
    }
}
