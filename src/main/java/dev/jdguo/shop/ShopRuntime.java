package dev.jdguo.shop;

import dev.jdguo.shop.bank.ShopBank;
import dev.jdguo.shop.catalog.ShopConfig;
import dev.jdguo.shop.persistence.ShopWorldState;
import dev.jdguo.shop.trade.ShopSession;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ShopRuntime {
    public static final Logger LOGGER = LoggerFactory.getLogger("shopmod");

    public static ShopConfig config = ShopConfig.defaults();
    public static ShopWorldState state;
    public static MinecraftServer server;
    public static ShopBank bank = ShopBank.absent();

    private ShopRuntime() {}

    public static ShopSession session() {
        return new ShopSession(config, state == null ? null : state.book(), bank, ShopRuntime::save);
    }

    public static void save() {
        if (state != null) {
            state.markDirty();
            state.writeBackup();
        }
    }
}
