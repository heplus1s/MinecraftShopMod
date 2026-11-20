package dev.jdguo.shop.catalog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Reads and writes {@code config/shopmod.json}. Missing optional keys keep their defaults. */
public final class ConfigJson {
    private ConfigJson() {}

    public static String write(ShopConfig config) {
        JsonObject root = new JsonObject();
        root.addProperty("pricingVersion", config.pricingVersion());
        root.addProperty("stepFraction", plain(config.stepFraction()));
        root.addProperty("spreadFraction", plain(config.spreadFraction()));
        root.addProperty("minDiamondPrice", plain(config.minDiamondPrice()));
        root.addProperty("maxCount", config.maxCount());
        root.addProperty("pageSize", config.pageSize());
        JsonArray catalog = new JsonArray();
        for (CatalogEntry entry : config.catalog()) {
            JsonObject row = new JsonObject();
            row.addProperty("item", entry.itemId());
            row.addProperty("name", entry.name());
            row.addProperty("anchorDiamonds", plain(entry.anchorDiamonds()));
            row.addProperty("systemTradeEnabled", entry.systemTradeEnabled());
            catalog.add(row);
        }
        root.add("catalog", catalog);
        return pretty(root);
    }

    public static ShopConfig parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        ShopConfig defaults = ShopConfig.defaults();
        BigDecimal step = root.has("stepFraction")
                ? new BigDecimal(text(root.get("stepFraction")))
                : defaults.stepFraction();
        BigDecimal spread = root.has("spreadFraction") ? new BigDecimal(text(root.get("spreadFraction"))) : defaults.spreadFraction();
        BigDecimal floor = root.has("minDiamondPrice")
                ? new BigDecimal(text(root.get("minDiamondPrice")))
                : defaults.minDiamondPrice();
        int maxCount = root.has("maxCount") ? root.get("maxCount").getAsInt() : defaults.maxCount();
        int pageSize = root.has("pageSize") ? root.get("pageSize").getAsInt() : defaults.pageSize();
        if (!root.has("catalog") || !root.get("catalog").isJsonArray()) {
            throw new IllegalArgumentException("catalog is required");
        }
        List<CatalogEntry> catalog = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("catalog")) {
            JsonObject row = element.getAsJsonObject();
            String item = text(row.get("item")).toLowerCase();
            String name = text(row.get("name"));
            BigDecimal anchor = new BigDecimal(text(row.get("anchorDiamonds")));
            catalog.add(new CatalogEntry(item, name, anchor, !row.has("systemTradeEnabled") || row.get("systemTradeEnabled").getAsBoolean()));
        }
        // One-time versioned addition: keep existing custom entries and all their prices.
        if (!root.has("catalogVersion") || root.get("catalogVersion").getAsInt() < 2) {
            for (CatalogEntry entry : ShopConfig.equipmentCatalog())
                if (catalog.stream().noneMatch(old -> old.itemId().equals(entry.itemId()))) catalog.add(entry);
        }
        if (!root.has("catalogVersion") || root.get("catalogVersion").getAsInt() < 3) {
            for (CatalogEntry entry : ShopConfig.catalogV3Additions())
                if (catalog.stream().noneMatch(old -> old.itemId().equals(entry.itemId()))) catalog.add(entry);
        }
        int pricingVersion = root.has("pricingVersion") ? root.get("pricingVersion").getAsInt() : 0;
        if (pricingVersion < 0 || pricingVersion > CatalogPricing.VERSION)
            throw new IllegalArgumentException("Unsupported pricingVersion: " + pricingVersion);
        if (pricingVersion < CatalogPricing.VERSION && root.has("refreshDefaultAnchors")
                && root.get("refreshDefaultAnchors").getAsBoolean()) {
            catalog = CatalogPricing.refreshLegacyDefaults(catalog, pricingVersion);
            pricingVersion = CatalogPricing.VERSION;
        }
        ShopConfig config = new ShopConfig(step, floor, maxCount, pageSize, catalog, spread, pricingVersion);
        config.validate();
        return config;
    }

    private static String text(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            throw new IllegalArgumentException("Missing value");
        }
        return element.getAsString().trim();
    }

    static String plain(BigDecimal value) {
        BigDecimal scaled = value.stripTrailingZeros();
        if (scaled.scale() < 0) {
            scaled = scaled.setScale(0);
        }
        return scaled.toPlainString();
    }

    private static String pretty(JsonObject root) {
        StringBuilder out = new StringBuilder();
        out.append("{\n  \"catalogVersion\": 3,\n");
        out.append("  \"pricingVersion\": ").append(root.get("pricingVersion").getAsInt()).append(",\n");
        out.append("  \"stepFraction\": \"").append(root.get("stepFraction").getAsString()).append("\",\n");
        out.append("  \"spreadFraction\": \"").append(root.get("spreadFraction").getAsString()).append("\",\n");
        out.append("  \"minDiamondPrice\": \"").append(root.get("minDiamondPrice").getAsString()).append("\",\n");
        out.append("  \"maxCount\": ").append(root.get("maxCount").getAsInt()).append(",\n");
        out.append("  \"pageSize\": ").append(root.get("pageSize").getAsInt()).append(",\n");
        out.append("  \"catalog\": [\n");
        JsonArray catalog = root.getAsJsonArray("catalog");
        for (int i = 0; i < catalog.size(); i++) {
            JsonObject row = catalog.get(i).getAsJsonObject();
            out.append("    {\"item\": \"").append(row.get("item").getAsString())
                    .append("\", \"name\": \"").append(escape(row.get("name").getAsString()))
                    .append("\", \"anchorDiamonds\": \"").append(row.get("anchorDiamonds").getAsString())
                    .append("\", \"systemTradeEnabled\": ").append(row.get("systemTradeEnabled").getAsBoolean()).append("}");
            if (i + 1 < catalog.size()) {
                out.append(',');
            }
            out.append('\n');
        }
        out.append("  ]\n");
        out.append("}\n");
        return out.toString();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
