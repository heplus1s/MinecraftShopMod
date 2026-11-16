package dev.jdguo.shop.price;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.jdguo.shop.bank.PayChoice;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * World price document. Version 3 adds typed issuer-payout plans; older pending rows retain their original semantics.
 * Unknown keys are written back. A newer version is refused so an older build cannot blank the save.
 */
public final class ShopSaved {
    public static final int VERSION = 3;

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
        root.addProperty("version", VERSION);
        return new ShopSaved(root);
    }

    public long journalRevision() { return root.has("journalRevision") ? root.get("journalRevision").getAsLong() : 0L; }

    public void beginTrade(String reference, UUID player, String item, int count, boolean buying,
                           String currency, BigDecimal amount, BigDecimal before, BigDecimal after) {
        JsonObject row = new JsonObject();
        row.addProperty("player", player.toString()); row.addProperty("item", item);
        row.addProperty("count", count); row.addProperty("buying", buying);
        row.addProperty("currency", currency); row.addProperty("amount", amount.toPlainString());
        row.addProperty("priceBefore", before.toPlainString()); row.addProperty("priceAfter", after.toPlainString());
        row.addProperty("createdAt", System.currentTimeMillis());
        trades().add(reference, row);
        tradeState(reference, "prepared");
    }
    public void beginIssuerPayout(String reference, UUID player, dev.jdguo.shop.trade.PrivatePayoutPlan plan) {
        beginTrade(reference, player, plan.itemId(), plan.count(), false, plan.currency().key(),
                plan.centralBudget(), plan.priceBefore(), plan.priceAfter());
        JsonObject row = trades().getAsJsonObject(reference);
        row.addProperty("kind", "issuer_offer_payout");
        row.add("payoutPlan", plan.toJson());
    }
    public void tradeState(String reference, String state) {
        JsonObject row = trades().getAsJsonObject(reference);
        if (row == null) throw new IllegalArgumentException("Unknown reference");
        row.addProperty("state", state);
        row.addProperty("updatedAt", System.currentTimeMillis());
        root.addProperty("journalRevision", journalRevision() + 1);
    }
    public String pendingTrade(UUID player) {
        for (var entry : trades().entrySet()) {
            JsonObject row = entry.getValue().getAsJsonObject();
            String state = row.get("state").getAsString();
            if (player.toString().equals(row.get("player").getAsString())
                    && !state.equals("completed") && !state.equals("cancelled")) return entry.getKey();
        }
        return null;
    }
    public JsonObject trade(String reference) {
        JsonObject row = trades().getAsJsonObject(reference);
        return row == null ? null : row.deepCopy();
    }
    public java.util.List<String> completedTrades(UUID player) {
        java.util.List<String> result = new java.util.ArrayList<>();
        for (var entry : trades().entrySet()) {
            JsonObject row = entry.getValue().getAsJsonObject();
            String state = row.get("state").getAsString();
            if (player.toString().equals(row.get("player").getAsString())
                    && (state.equals("completed") || state.equals("cancelled"))) result.add(entry.getKey());
        }
        return result;
    }
    public String pendingItem(String item) {
        for (var entry : trades().entrySet()) {
            JsonObject row = entry.getValue().getAsJsonObject();
            String state = row.get("state").getAsString();
            if (item.equals(row.get("item").getAsString())
                    && !state.equals("completed") && !state.equals("cancelled")) return entry.getKey();
        }
        return null;
    }
    private JsonObject trades() {
        if (!root.has("trades")) root.add("trades", new JsonObject());
        return root.getAsJsonObject("trades");
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

    public PayChoice payer(UUID player) {
        if (player == null) {
            return PayChoice.note();
        }
        JsonObject payers = payers();
        JsonElement element = payers.get(player.toString());
        if (element == null || !element.isJsonObject()) {
            return PayChoice.note();
        }
        JsonObject row = element.getAsJsonObject();
        String kind = row.has("kind") && row.get("kind").isJsonPrimitive() ? row.get("kind").getAsString() : "note";
        if (!"personal".equals(kind) || !row.has("id")) {
            return PayChoice.note();
        }
        try {
            UUID id = UUID.fromString(row.get("id").getAsString());
            String name = row.has("name") && row.get("name").isJsonPrimitive() ? row.get("name").getAsString() : id.toString();
            return PayChoice.personal(id, name);
        } catch (IllegalArgumentException error) {
            return PayChoice.note();
        }
    }

    public void setPayer(UUID player, PayChoice choice) {
        if (player == null || choice == null || choice.hard()) {
            return;
        }
        JsonObject payers = payers();
        JsonObject row;
        JsonElement existing = payers.get(player.toString());
        if (existing != null && existing.isJsonObject()) {
            row = existing.getAsJsonObject();
        } else {
            row = new JsonObject();
        }
        if (choice.personal()) {
            row.addProperty("kind", "personal");
            row.addProperty("id", choice.personalId().toString());
            row.addProperty("name", choice.name());
        } else {
            row.addProperty("kind", "note");
            row.remove("id");
        }
        payers.add(player.toString(), row);
    }

    private JsonObject prices() {
        return root.getAsJsonObject("prices");
    }

    private JsonObject payers() {
        if (!root.has("payers") || !root.get("payers").isJsonObject()) {
            root.add("payers", new JsonObject());
        }
        return root.getAsJsonObject("payers");
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
