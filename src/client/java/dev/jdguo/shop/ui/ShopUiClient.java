package dev.jdguo.shop.ui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/** Client half of the optional shop screen. It only speaks if this jar is installed. */
public final class ShopUiClient implements ClientModInitializer {
    static final int PROTOCOL = 1;
    static final Identifier HELLO = new Identifier("shopmod", "hello");
    static final Identifier ACTION = new Identifier("shopmod", "action");
    static final Identifier SYNC = new Identifier("shopmod", "sync");

    private static final ShopCatalogBatch catalogBatch = new ShopCatalogBatch();
    private static boolean negotiated;
    private static int helloRetry;
    private static JsonObject pending = new JsonObject();
    private static KeyBinding openKey;
    private static boolean openRequested;
    private static int awaitingOpen;

    @Override
    public void onInitializeClient() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            pending = new JsonObject();
            catalogBatch.clear(); negotiated = false; helloRetry = 0;
            openRequested = false;
            awaitingOpen = 0;
        });
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.shopmod-ui.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "category.shopmod-ui"));
        ClientPlayNetworking.registerGlobalReceiver(SYNC, (client, handler, buf, response) -> {
            String json = buf.readString(32767);
            client.execute(() -> receive(client, json));
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (!ClientPlayNetworking.canSend(HELLO)) return;
            var buf = PacketByteBufs.create();
            buf.writeVarInt(PROTOCOL);
            ClientPlayNetworking.send(HELLO, buf);
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!negotiated && client.player != null && ++helloRetry >= 20) {
                helloRetry = 0;
                if (ClientPlayNetworking.canSend(HELLO)) {
                    var hello = PacketByteBufs.create(); hello.writeVarInt(PROTOCOL);
                    ClientPlayNetworking.send(HELLO, hello);
                }
            }
            while (openKey.wasPressed()) openRequested = true;
            // A command executes inside ChatScreen.keyPressed. Open only after it has closed.
            if (openRequested) {
                openRequested = false;
                present(client);
            } else if (awaitingOpen > 0 && --awaitingOpen == 0) {
                inform(client, "商店界面未收到服务器响应，请检查两端模组版本；可尝试 /shop ui。");
            }
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(literal("shopui").executes(context -> {
                    openRequested = true;
                    return 1;
                })));
    }

    /** Buy and sell use the server's four-field action packet. */
    static void sendTrade(boolean buy, String itemId, int count, String currency) {
        sendAction(buy ? "buy" : "sell", itemId, count, 1, currency);
    }

    static void confirmPayout(String quoteId) { sendAction("payout_confirm",quoteId,0,1,null); }
    static void cancelPayout() { sendAction("payout_cancel","",0,1,null); }

    static void sendList() {
        sendAction("list", "", 1, 1, null);
    }

    private static void sendAction(String action, String itemId, int count, int page, String currency) {
        if (!ClientPlayNetworking.canSend(ACTION)) return;
        var buf = PacketByteBufs.create();
        buf.writeString(action, 32);
        buf.writeString(itemId == null ? "" : itemId, 128);
        buf.writeVarInt(Math.max(0, count));
        buf.writeVarInt(Math.max(1, page));
        if (currency != null) {
            buf.writeString(currency, 64);
        }
        ClientPlayNetworking.send(ACTION, buf);
    }

    private static void receive(MinecraftClient client, String json) {
        JsonObject data;
        try {
            data = JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException malformed) {
            return;
        }
        negotiated = true;
        data = catalogBatch.accept(data);
        if (data == null) return;
        pending = data;
        if(data.has("payoutPreview") && data.get("payoutPreview").isJsonObject()) {
            awaitingOpen=0; client.setScreen(new ShopPayoutConfirmScreen(data)); return;
        }
        if(client.currentScreen instanceof ShopPayoutConfirmScreen) {
            client.setScreen(new ShopScreen(data)); return;
        }
        if (client.currentScreen instanceof ShopScreen screen) {
            screen.show(data);
            awaitingOpen = 0;
            return;
        }
        if (awaitingOpen > 0 || ShopQuotes.shouldPresent(data)) {
            awaitingOpen = 0;
            client.setScreen(new ShopScreen(data));
        }
    }

    private static void present(MinecraftClient client) {
        if (client.player == null) return;
        if (!ClientPlayNetworking.canSend(HELLO) || !ClientPlayNetworking.canSend(ACTION)) {
            inform(client, "服务器未提供商店界面接口，请检查服务端 shopmod；原版菜单使用 /shop ui。");
            return;
        }
        // Re-negotiate every open: JOIN may have run before channel registration arrived.
        awaitingOpen = 100;
        var buf = PacketByteBufs.create();
        buf.writeVarInt(PROTOCOL);
        ClientPlayNetworking.send(HELLO, buf);
    }

    private static void inform(MinecraftClient client, String message) {
        if (client.player != null) client.player.sendMessage(Text.literal(message), false);
    }
}
