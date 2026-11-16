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
                () -> new ShopWorldState(newestSaved(ShopSaved.empty())), ID);
    }

    public static ShopWorldState fromNbt(NbtCompound nbt) {
        String json = nbt.contains("json") ? nbt.getString("json") : "";
        if (!json.isBlank()) {
            try {
                return new ShopWorldState(newestSaved(ShopSaved.parse(json)));
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

    private static ShopSaved newestSaved(ShopSaved nbt) {
        Path path = backupPath(ShopRuntime.server);
        if (path == null || !Files.exists(path)) return nbt;
        try {
            ShopSaved json = ShopSaved.parse(Files.readString(path));
            return json.journalRevision() >= nbt.journalRevision() ? json : nbt;
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("商店交易日志损坏，禁止静默回滚到旧 NBT", error);
        }
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
        if (path == null) throw new IllegalStateException("商店存档路径不可用");
        try {
            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            String document = book.saved().toJson();
            Files.writeString(tmp, document);
            try (var channel = java.nio.channels.FileChannel.open(tmp, java.nio.file.StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                throw new IOException("Filesystem does not support atomic journal replacement", ignored);
            }
            if (!document.equals(Files.readString(path))) throw new IOException("Shop journal readback mismatch");
            try (var directory = java.nio.channels.FileChannel.open(path.getParent(), java.nio.file.StandardOpenOption.READ)) {
                directory.force(true);
            } catch (UnsupportedOperationException unsupported) {
                warnDirectorySync();
            } catch (java.nio.file.AccessDeniedException denied) {
                if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) throw denied;
                warnDirectorySync();
            }
        } catch (IOException error) {
            throw new IllegalStateException("商店交易日志未可靠保存", error);
        }
    }

    private static boolean directorySyncWarning;
    private static void warnDirectorySync() {
        if (!directorySyncWarning) {
            directorySyncWarning = true;
            ShopRuntime.LOGGER.warn("平台不支持目录 fsync；已完成文件 force、原子替换和读回。断电时目录项持久性仍依赖操作系统保证。");
        }
    }

    public static Path backupPath(MinecraftServer server) {
        if (server == null) {
            return null;
        }
        return server.getSavePath(WorldSavePath.ROOT).resolve("data").resolve("shopmod-prices.json");
    }
}
