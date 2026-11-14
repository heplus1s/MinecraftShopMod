package dev.jdguo.shop.price;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.math.BigDecimal;

/**
 * World price document. Version 1 stores each live diamond-equivalent under {@code prices}.
 * Unknown keys are written back. A newer version is refused so an older build cannot blank the save.
 */
public final class ShopSaved {
    public static final int VERSION = 1;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final JsonObject root;

    private ShopSaved(JsonObject root) {
        this.root = root;
    }

    public static ShopSaved empty() {
        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);
        root.add("prices", new JsonObject());
        return new ShopSaved(root);
    }

    public static ShopSaved parse(String json) {
        if (json == null || json.isBlank()) {
            return empty();
        }
        JsonElement element = JsonParser.parseString(json);
        if (!element.isJsonObject()) {
            throw new IllegalStateException("Shop save is not a JSON object");
        }
        JsonObject root = element.getAsJsonObject();
        if (root.has("version") && !root.get("version").isJsonNull()) {
            int version = root.get("version").getAsInt();
            if (version > VERSION) {
                throw new IllegalStateException("Shop save version " + version + " is newer than " + VERSION);
            }
        } else {
            root.addProperty("version", VERSION);
        }
        if (!root.has("prices")) {
            root.add("prices", new JsonObject());
        } else if (!root.get("prices").isJsonObject()) {
            throw new IllegalStateException("Shop save prices is not an object");
        }
        return new ShopSaved(root);
    }

    public String toJson() {
        return GSON.toJson(root);
    }

    public int size() {
        return prices().size();
    }

    public Read read(String itemId) {
        JsonObject prices = prices();
        if (itemId == null || !prices.has(itemId) || prices.get(itemId).isJsonNull()) {
            return Read.missing();
        }
        JsonElement element = prices.get(itemId);
        BigDecimal value = decimal(element);
        if (value == null) {
            return Read.broken();
        }
        return Read.value(value);
    }

    public void setPrice(String itemId, BigDecimal price) {
        JsonObject prices = prices();
        JsonElement existing = prices.get(itemId);
        String text = PriceMath.normalize(price).toPlainString();
        if (existing != null && existing.isJsonObject()) {
            existing.getAsJsonObject().addProperty("diamond", text);
            return;
        }
        JsonObject row = new JsonObject();
        row.addProperty("diamond", text);
        prices.add(itemId, row);
    }

    public void clearPrice(String itemId) {
        prices().remove(itemId);
    }

    private JsonObject prices() {
        return root.getAsJsonObject("prices");
    }

    private static BigDecimal decimal(JsonElement element) {
        try {
            if (element.isJsonPrimitive()) {
                return new BigDecimal(element.getAsString());
            }
            if (element.isJsonObject() && element.getAsJsonObject().has("diamond")
                    && element.getAsJsonObject().get("diamond").isJsonPrimitive()) {
                return new BigDecimal(element.getAsJsonObject().get("diamond").getAsString());
            }
        } catch (NumberFormatException | UnsupportedOperationException | ClassCastException ignored) {
            return null;
        }
        return null;
    }

    public record Read(boolean present, boolean corrupt, BigDecimal value) {
        public static Read missing() {
            return new Read(false, false, null);
        }

        public static Read broken() {
            return new Read(true, true, null);
        }

        public static Read value(BigDecimal value) {
            return new Read(true, false, value);
        }
    }
}
