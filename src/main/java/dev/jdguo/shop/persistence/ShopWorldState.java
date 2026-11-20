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
                () -> recoverOrInitialize(server), ID);
    }

    public static ShopWorldState fromNbt(NbtCompound nbt) {
        boolean hasDocument = nbt.contains("json") || nbt.contains("jsonEncoding") || nbt.contains("jsonChunks")
                || nbt.contains("jsonLength") || nbt.contains("jsonSha256");
        if (hasDocument) {
            try {
                return new ShopWorldState(newestSaved(ShopSaved.parse(ShopNbtCodec.read(nbt))));
            } catch (RuntimeException error) {
                ShopRuntime.LOGGER.error("商店存档读不了，改读旁边的 JSON", error);
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
        throw new IllegalStateException("商店世界存档没有可用的记录，且没有可读的 JSON 副本。");
    }

    private static ShopWorldState recoverOrInitialize(MinecraftServer server) {
        Path backup = backupPath(server);
        if (!Files.notExists(backup)) {
            try {
                return new ShopWorldState(ShopSaved.parse(Files.readString(backup)));
            } catch (IOException | RuntimeException error) {
                throw new IllegalStateException("商店存档无法恢复：" + backup, error);
            }
        }
        // Vanilla PersistentStateManager can call the factory after a read error.
        // Do not mistake an unreadable existing ledger for a new world.
        Path expectedNbt = backup.resolveSibling(ID + ".dat");
        if (!Files.notExists(expectedNbt))
            throw new IllegalStateException("已有商店存档无法读取且没有备份，禁止初始化空库存：" + expectedNbt);
        return new ShopWorldState(ShopSaved.empty());
    }

    private static ShopSaved newestSaved(ShopSaved nbt) {
        Path path = backupPath(ShopRuntime.server);
        if (path == null || Files.notExists(path)) return nbt;
        try {
            ShopSaved json = ShopSaved.parse(Files.readString(path));
            return json.journalRevision() >= nbt.journalRevision() ? json : nbt;
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("商店交易日志损坏，禁止静默回滚到旧 NBT", error);
        }
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        ShopNbtCodec.write(nbt, book.saved().toJson());
        nbt.putInt("version", ShopSaved.VERSION);
        writeBackup();
        return nbt;
    }

    /**
     * Audited absolute provisioning, not item generation through normal trade.
     * Requires caller authorization and server thread. Null actor means console.
     * Invalid input throws IllegalArgumentException; a pending different operation throws IllegalStateException.
     * Persistence failures also throw: outcome may have reached disk. Repeat the exact request to resume;
     * do not compensate by incrementing stock or submitting a different target.
     */
    public String setStock(String item, long target, java.util.UUID actor, String reason) {
        if (ShopRuntime.server == null || !ShopRuntime.server.isOnThread())
            throw new IllegalStateException("Stock adjustment requires server thread");
        String reference = book.saved().beginStockAdjustment(item, target, actor, reason);
        markDirty();
        writeBackup(); // Persist intent before making stock available.
        book.saved().applyStockAdjustment(reference);
        markDirty();
        try { writeBackup(); }
        catch (RuntimeException uncertain) {
            book.saved().stockAdjustmentPending(reference);
            markDirty();
            throw uncertain;
        }
        return reference;
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
