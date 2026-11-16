package dev.jdguo.shop.net;

import dev.jdguo.shop.ShopRuntime;
import dev.jdguo.shop.bank.PayChoice;
import dev.jdguo.shop.ui.ShopChestUiHooks;
import dev.jdguo.shop.ui.ShopQuoteView;
import dev.jdguo.shop.ui.ShopTradeResult;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Optional client channel. The server answers only after a client says hello on
 * {@code shopmod:hello}, and only if that client can receive {@code shopmod:sync}.
 * A vanilla client never registers these payloads, so joining does not depend on them.
 *
 * <p>Actions are ASCII: {@code quote}, {@code buy}, {@code sell}, {@code list}.
 * The packet is a string action, a string item id, a count, and a page.
 */
public final class ShopUiNetwork {
    public static final int PROTOCOL = 1;
    public static final Identifier HELLO = new Identifier("shopmod", "hello");
    public static final Identifier ACTION = new Identifier("shopmod", "action");
    public static final Identifier SYNC = new Identifier("shopmod", "sync");

    private static final Set<UUID> sessions = ConcurrentHashMap.newKeySet();

    private ShopUiNetwork() {}

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(HELLO, (server, player, handler, buf, response) -> {
            int version;
            try {
                version = buf.readVarInt();
            } catch (RuntimeException error) {
                return;
            }
            server.execute(() -> {
                if (version != PROTOCOL) {
                    sessions.remove(player.getUuid());
                    return;
                }
                sessions.add(player.getUuid());
                sync(player, 1, "商店目录由服务器发出。没有安装界面时，仍可使用指令。");
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(ACTION, (server, player, handler, buf, response) -> {
            final String action;
            final String itemId;
            final int count;
            final int page;
            final String currency;
            try {
                action = buf.readString(32);
                itemId = buf.readString(128);
                count = buf.readVarInt();
                page = buf.readVarInt();
                currency = buf.isReadable() ? buf.readString(64) : null;
            } catch (RuntimeException error) {
                return;
            }
            server.execute(() -> handle(player, action, itemId, count, page, currency));
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            sessions.remove(handler.player.getUuid());
            dev.jdguo.shop.ui.ShopPayoutPreviews.cancel(handler.player.getUuid());
        });
    }

    private static void handle(ServerPlayerEntity player, String action, String itemId, int count, int page, String currency) {
        if (!sessions.contains(player.getUuid())) {
            return;
        }
        int shownPage = page < 1 ? 1 : page;
        if("payout_confirm".equals(action)) {
            var reply=ShopChestUiHooks.confirmPayout(player,itemId);
            sync(player,shownPage,reply.result().message(),reply.replacement());return;
        }
        if("payout_cancel".equals(action)) {
            dev.jdguo.shop.ui.ShopPayoutPreviews.cancel(player.getUuid());
            sync(player,shownPage,"已取消组合付款预览，未执行交易。");return;
        }
        PayChoice pay = currency == null ? ShopChestUiHooks.payer(player) : currency(currency);
        if (pay == null) {
            sync(player, shownPage, "所选币种不可用，本次没有结算。");
            return;
        }
        switch (action) {
            case "list" -> sync(player, shownPage, "");
            case "quote" -> {
                ShopQuoteView view = ShopChestUiHooks.quote(itemId, Math.max(count, 1), true, pay);
                sync(player, shownPage, view.message());
            }
            case "buy" -> {
                ShopTradeResult result = ShopChestUiHooks.buy(player, itemId, count, pay);
                sync(player, shownPage, result.message());
            }
            case "sell" -> {
                if(pay.personal()) {
                    var preview=ShopChestUiHooks.previewPayout(player,itemId,count,pay);
                    sync(player,shownPage,preview==null?"暂无可执行组合报价，请检查央行券资金或发行人挂单。":"请核对组合收款，确认后才收取商品。",preview);
                    break;
                }
                ShopTradeResult result = ShopChestUiHooks.sell(player, itemId, count, pay);
                sync(player, shownPage, result.message());
            }
            default -> sync(player, shownPage, "不认识这个操作。");
        }
    }

    private static PayChoice currency(String key) {
        if ("note".equals(key)) {
            return PayChoice.note(ShopRuntime.bank.currencyName());
        }
        if (!key.startsWith("personal:")) {
            return null;
        }
        for (String name : ShopRuntime.bank.personalNames()) {
            var note = ShopRuntime.bank.findPersonal(name);
            if (note != null && ("personal:" + note.id()).equals(key)) {
                return PayChoice.personal(note.id(), note.name());
            }
        }
        return null;
    }

    private static void sync(ServerPlayerEntity player, int page, String message) {
        sync(player,page,message,null);
    }
    private static void sync(ServerPlayerEntity player, int page, String message, dev.jdguo.shop.ui.ShopPayoutPreviews.Preview preview) {
        if (!ServerPlayNetworking.canSend(player, SYNC)) {
            return;
        }
        PacketByteBuf buf = PacketByteBufs.create();
        com.google.gson.JsonObject snapshot=com.google.gson.JsonParser.parseString(ShopUiSnapshot.json(player,page,message)).getAsJsonObject();
        snapshot.addProperty("issuerPayoutPreview",true);
        if(preview!=null)snapshot.add("payoutPreview",preview.json());
        String json = snapshot.toString();
        if (json.length() > 32000) {
            json = "{\"protocol\":1,\"message\":\"[商店] 目录太长，请用指令查询。\",\"listings\":[]}";
        }
        buf.writeString(json, 32767);
        ServerPlayNetworking.send(player, SYNC, buf);
    }
}
