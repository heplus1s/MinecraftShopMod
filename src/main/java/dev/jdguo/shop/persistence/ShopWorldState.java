package dev.jdguo.shop.persistence;

import dev.jdguo.shop.ShopRuntime;
import dev.jdguo.shop.price.PriceBook;
import dev.jdguo.shop.price.ShopSaved;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.PersistentState;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Prices live in the world data folder. The NBT record and the JSON copy carry the same document.
 * A damaged file is not replaced with an empty catalog of prices.
 */
public final class ShopWorldState extends PersistentState {
    public static final String ID = "shopmod";

    private final PriceBook book;

    public ShopWorldState(ShopSaved saved) {
        this.book = new PriceBook(saved);
    }

    public PriceBook book() {
        return book;
    }

    public static ShopWorldState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(
                ShopWorldState::fromNbt,
                () -> new ShopWorldState(ShopSaved.empty()),
                ID);
    }

    public static ShopWorldState fromNbt(NbtCompound nbt) {
        String json = nbt.contains("json") ? nbt.getString("json") : "";
        if (!json.isBlank()) {
            try {
                return new ShopWorldState(ShopSaved.parse(json));
            } catch (RuntimeException error) {
                ShopRuntime.LOGGER.error("商店价格存档读不了，改读旁边的 JSON", error);
            }
        }
        Path backup = backupPath(ShopRuntime.server);
        if (backup != null && Files.exists(backup)) {
            try {
                return new ShopWorldState(ShopSaved.parse(Files.readString(backup)));
            } catch (IOException | RuntimeException error) {
                throw new IllegalStateException("商店价格存档和旁边的 JSON 都无法读取: " + backup, error);
            }
        }
        if (!json.isBlank()) {
            throw new IllegalStateException("商店价格存档无法读取，且没有旁边的 JSON");
        }
        return new ShopWorldState(ShopSaved.empty());
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.putString("json", book.saved().toJson());
        nbt.putInt("version", ShopSaved.VERSION);
        writeBackup();
        return nbt;
    }

    public void writeBackup() {
        Path path = backupPath(ShopRuntime.server);
        if (path == null) {
            return;
        }
        try {
            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(tmp, book.saved().toJson());
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException error) {
            ShopRuntime.LOGGER.warn("写不了商店价格的旁边存档", error);
        }
    }

    public static Path backupPath(MinecraftServer server) {
        if (server == null) {
            return null;
        }
        return server.getSavePath(WorldSavePath.ROOT).resolve("data").resolve("shopmod-prices.json");
    }
}
