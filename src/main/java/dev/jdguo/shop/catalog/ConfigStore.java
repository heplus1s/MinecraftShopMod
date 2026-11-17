package dev.jdguo.shop.catalog;

import dev.jdguo.shop.ShopRuntime;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** {@code config/shopmod.json}. An existing file is never rewritten on startup. */
public final class ConfigStore {
    private ConfigStore() {}

    public static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("shopmod.json");
    }

    public static void load() {
        Path path = path();
        try {
            if (Files.notExists(path)) {
                Files.createDirectories(path.getParent());
                atomicWrite(path, ConfigJson.write(ShopConfig.defaults()));
                ShopRuntime.config = ShopConfig.defaults();
                return;
            }
            String original = Files.readString(path);
            ShopRuntime.config = ConfigJson.parse(original);
            var json = com.google.gson.JsonParser.parseString(original).getAsJsonObject();
            if (!json.has("catalogVersion") || json.get("catalogVersion").getAsInt() < 2) {
                try {
                // Preserve unknown configuration fields and custom prices; back up before migration.
                if (Files.notExists(path.resolveSibling("shopmod.pre-catalog-v2.json")))
                    Files.copy(path, path.resolveSibling("shopmod.pre-catalog-v2.json"));
                var upgraded = com.google.gson.JsonParser.parseString(ConfigJson.write(ShopRuntime.config)).getAsJsonObject();
                json.add("catalog", upgraded.get("catalog")); json.addProperty("catalogVersion", 2);
                atomicWrite(path, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(json));
                } catch (IOException migrationFailure) {
                    ShopRuntime.LOGGER.warn("商店目录已在内存升级，配置备份/保存失败；保留原配置和自定义价格。", migrationFailure);
                }
            }
        } catch (Exception error) {
            ShopRuntime.LOGGER.error("config/shopmod.json 读不了，改用内置默认。原文件没有被覆盖。", error);
            ShopRuntime.config = ShopConfig.defaults();
        }
    }

    public static ShopConfig read() throws IOException {
        return ConfigJson.parse(Files.readString(path()));
    }

    public static void write(ShopConfig config) throws IOException {
        config.validate();
        Path path = path();
        Files.createDirectories(path.getParent());
        atomicWrite(path, ConfigJson.write(config));
    }

    private static void atomicWrite(Path path, String json) throws IOException {
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(tmp, json);
        try {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
