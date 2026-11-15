package dev.jdguo.shop;

import dev.jdguo.shop.bank.ShopBank;
import dev.jdguo.shop.bank.UnreachableBank;
import dev.jdguo.shop.catalog.ConfigStore;
import dev.jdguo.shop.commands.ShopCommands;
import dev.jdguo.shop.net.ShopUiNetwork;
import dev.jdguo.shop.persistence.ShopWorldState;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Shop for a dedicated server and for the integrated server in a Fabric client.
 * The {@code main} entrypoint is used because a {@code server} entrypoint does not run in singleplayer.
 * A vanilla client can join a dedicated server without this mod and without a resource pack.
 */
public final class ShopMod implements ModInitializer {
    @Override
    public void onInitialize() {
        ConfigStore.load();
        ShopCommands.register();
        ShopUiNetwork.register();
        ShopRuntime.bank = connectBank();

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ShopRuntime.server = server;
            ShopRuntime.state = ShopWorldState.get(server);
            if (FabricLoader.getInstance().isModLoaded("bankmod")) {
                try {
                    Class.forName("dev.jdguo.shop.bank.FabricBankBridge").getMethod("installQuote").invoke(null);
                } catch (Throwable error) {
                    ShopRuntime.LOGGER.error("无法向中央银行登记商店报价", error);
                }
            }
            ShopRuntime.LOGGER.info("商店已装入 {} 种目录物品，已保存成交价 {} 条",
                    ShopRuntime.config.catalog().size(),
                    ShopRuntime.state.book().storedCount());
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> ShopRuntime.save());
    }

    private static ShopBank connectBank() {
        if (!FabricLoader.getInstance().isModLoaded("bankmod")) {
            ShopRuntime.LOGGER.info("未加载中央银行。商店可以查询钻石等值，买卖需要储蓄总池中的商店经营账户。");
            return ShopBank.absent();
        }
        try {
            Object bank = Class.forName("dev.jdguo.shop.bank.FabricBankBridge").getMethod("open").invoke(null);
            if (bank instanceof ShopBank shopBank) {
                return shopBank;
            }
        } catch (Throwable error) {
            ShopRuntime.LOGGER.error("中央银行已加载，但商店无法连接它的公开接口", error);
        }
        return new UnreachableBank();
    }
}
