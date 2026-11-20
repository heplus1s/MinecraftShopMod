package dev.jdguo.shop.catalog;

import dev.jdguo.shop.ShopRuntime;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** {@code config/shopmod.json}. Only explicit/versioned catalog upgrades rewrite an existing file, after backup. */
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
            ShopRuntime.config = readAndUpgrade(path);
        } catch (Exception error) {
            ShopRuntime.LOGGER.error("config/shopmod.json 读不了，改用内置默认。原文件没有被覆盖。", error);
            ShopRuntime.config = ShopConfig.defaults();
        }
    }

    public static ShopConfig read() throws IOException {
        return readAndUpgrade(path());
    }

    /** Used by startup and explicit /shop admin reload; never touches world price/stock state. */
    static ShopConfig readAndUpgrade(Path path) throws IOException {
        String original = Files.readString(path);
        ShopConfig config = ConfigJson.parse(original);
        var json = com.google.gson.JsonParser.parseString(original).getAsJsonObject();
        boolean catalogUpgrade = !json.has("catalogVersion") || json.get("catalogVersion").getAsInt() < 3;
        int oldPricing = json.has("pricingVersion") ? json.get("pricingVersion").getAsInt() : 0;
        boolean pricingUpgrade = config.pricingVersion() > oldPricing;
        if (catalogUpgrade || pricingUpgrade) {
            try {
            // Backup failure aborts reload rather than applying an unrecorded repricing.
            String backupName = pricingUpgrade ? "shopmod.pre-pricing-v" + CatalogPricing.VERSION + ".json" : "shopmod.pre-catalog-v3.json";
            Path backup = path.resolveSibling(backupName);
            if (Files.exists(backup) && !Files.readString(backup).equals(original))
                backup = Files.createTempFile(path.getParent(), "shopmod.pre-upgrade-", ".json");
            if (Files.notExists(backup) || Files.size(backup) == 0) Files.writeString(backup, original);
            var upgraded = com.google.gson.JsonParser.parseString(ConfigJson.write(config)).getAsJsonObject();
            json.add("catalog", upgraded.get("catalog"));
            json.addProperty("catalogVersion", 3);
            json.addProperty("pricingVersion", config.pricingVersion());
            if (pricingUpgrade) json.addProperty("refreshDefaultAnchors", false);
            atomicWrite(path, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(json));
            } catch (IOException failure) {
                // No silent fallback to new anchors if the repricing backup cannot be written.
                ShopRuntime.LOGGER.warn("目录升级未保存，保留现有价格；请检查备份目录权限后重试。", failure);
                var untouched = com.google.gson.JsonParser.parseString(original).getAsJsonObject();
                untouched.addProperty("refreshDefaultAnchors", false);
                return ConfigJson.parse(untouched.toString());
            }
        }
        return config;
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
