package dev.jdguo.shop.ui;

import dev.jdguo.shop.ShopRuntime;
import dev.jdguo.shop.inventory.PlayerPurse;
import dev.jdguo.shop.trade.TradeDesk;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

/**
 * Stable calls for a later chest screen.
 *
 * <p>Call these on the server thread. {@code itemId} is an ASCII registry id such as
 * {@code minecraft:diamond}. The screen should display {@link ShopQuoteView} and send the
 * player's click back through {@link #buy} or {@link #sell}. It should not price items itself
 * and should not talk to the bank.
 */
public final class ShopChestUiHooks {
    private ShopChestUiHooks() {}

    public static ShopQuoteView quote(String itemId, int count, boolean buying) {
        requireServerThread();
        return TradeDesk.quote(ShopRuntime.session(), itemId, count, buying);
    }

    public static ShopTradeResult buy(ServerPlayerEntity player, String itemId, int count) {
        requirePlayer(player);
        if (!PlayerPurse.knownItem(itemId)) {
            return ShopTradeResult.fail("bad_item", dev.jdguo.shop.text.ShopText.badItem());
        }
        return TradeDesk.buy(ShopRuntime.session(), new PlayerPurse(player), player.getUuid(), itemId, count);
    }

    public static ShopTradeResult sell(ServerPlayerEntity player, String itemId, int count) {
        requirePlayer(player);
        if (!PlayerPurse.knownItem(itemId)) {
            return ShopTradeResult.fail("bad_item", dev.jdguo.shop.text.ShopText.badItem());
        }
        return TradeDesk.sell(ShopRuntime.session(), new PlayerPurse(player), player.getUuid(), itemId, count);
    }

    public static List<ShopListingView> listings(int page) {
        requireServerThread();
        return TradeDesk.listings(ShopRuntime.session(), page);
    }

    public static int pages() {
        requireServerThread();
        return Math.max(1, ShopRuntime.config.pages());
    }

    private static void requirePlayer(ServerPlayerEntity player) {
        if (player == null) {
            throw new IllegalArgumentException("player");
        }
        requireServerThread();
        if (player.getWorld().isClient()) {
            throw new IllegalStateException("ShopChestUiHooks must run on the server");
        }
    }

    private static void requireServerThread() {
        if (ShopRuntime.server != null && !ShopRuntime.server.isOnThread()) {
            throw new IllegalStateException("ShopChestUiHooks must be called on the server thread");
        }
    }
}
