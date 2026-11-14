package dev.jdguo.shop.net;

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
            try {
                action = buf.readString(32);
                itemId = buf.readString(128);
                count = buf.readVarInt();
                page = buf.readVarInt();
            } catch (RuntimeException error) {
                return;
            }
            server.execute(() -> handle(player, action, itemId, count, page));
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> sessions.remove(handler.player.getUuid()));
    }

    private static void handle(ServerPlayerEntity player, String action, String itemId, int count, int page) {
        if (!sessions.contains(player.getUuid())) {
            return;
        }
        int shownPage = page < 1 ? 1 : page;
        switch (action) {
            case "list" -> sync(player, shownPage, "");
            case "quote" -> {
                ShopQuoteView view = ShopChestUiHooks.quote(itemId, Math.max(count, 1), true);
                sync(player, shownPage, view.message());
            }
            case "buy" -> {
                ShopTradeResult result = ShopChestUiHooks.buy(player, itemId, count);
                sync(player, shownPage, result.message());
            }
            case "sell" -> {
                ShopTradeResult result = ShopChestUiHooks.sell(player, itemId, count);
                sync(player, shownPage, result.message());
            }
            default -> sync(player, shownPage, "不认识这个操作。");
        }
    }

    private static void sync(ServerPlayerEntity player, int page, String message) {
        if (!ServerPlayNetworking.canSend(player, SYNC)) {
            return;
        }
        PacketByteBuf buf = PacketByteBufs.create();
        String json = ShopUiSnapshot.json(page, message);
        if (json.length() > 32000) {
            json = "{\"protocol\":1,\"message\":\"[商店] 目录太长，请用指令查询。\",\"listings\":[]}";
        }
        buf.writeString(json, 32767);
        ServerPlayNetworking.send(player, SYNC, buf);
    }
}
